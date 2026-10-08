package dev.amman.proficiency.telemetry;

import dev.amman.proficiency.skill.Skill;
import dev.amman.proficiency.skill.SkillMath;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

/**
 * Always-on balance telemetry: what each player earned, from which kind of source, in how much
 * active time. It only counts in memory. A grant is a hash lookup and a few additions, and
 * {@link #drain} turns what was counted since the last drain into JSON lines for the caller to
 * append to a file every few minutes. Nothing here touches the disk, the network or Minecraft, so
 * the maths is unit tested and the benchmark test can hold it to a time budget.
 *
 * <p>One drain line is one bucket: a player and a skill and a source kind for XP, a player and a
 * skill for time, levels, procs and deaths, and a player on their own for online time. Counters
 * are deltas, so the report adds the lines of a window together. The schema is versioned by the
 * {@code v} field of every line.
 */
public final class Telemetry {

    public static final int SCHEMA = 1;

    /** A skill counts as "in use" for this long after it last paid XP (active-time attribution). */
    public static final long ENGAGED_MS = 60_000L;

    public static final String KIND_BLOCK = "block";
    public static final String KIND_MOB = "mob";
    public static final String KIND_ITEM = "item";
    public static final String KIND_KILL = "kill";
    public static final String KIND_FIRST = "first-time";
    public static final String KIND_DISCOVERY = "discovery";
    public static final String KIND_MOVEMENT = "movement";
    public static final String KIND_DAMAGE = "damage";
    public static final String KIND_SHARE = "share";
    public static final String KIND_ROLE = "role";
    public static final String KIND_ACTION = "action";
    public static final String KIND_COMMAND = "command";
    public static final String KIND_OTHER = "other";

    private static final String SOURCE = "proficiency.xplog.source.";

    /** What an XP log source string stands for, as a short stable label. Allocation free. */
    public static String kind(String source) {
        if (source == null || source.isEmpty()) {
            return KIND_OTHER;
        }
        if (source.startsWith("first|")) {
            return KIND_FIRST;
        }
        if (source.startsWith("kill|")) {
            return KIND_KILL;
        }
        if (source.startsWith("block.")) {
            return KIND_BLOCK;
        }
        if (source.startsWith("entity.")) {
            return KIND_MOB;
        }
        if (source.startsWith("item.")) {
            return KIND_ITEM;
        }
        if (source.startsWith("biome.") || source.startsWith("dimension.")
                || source.startsWith("structure.")) {
            return KIND_DISCOVERY;
        }
        if (source.startsWith("proficiency.xplog.damage.")) {
            return KIND_DAMAGE;
        }
        if (source.startsWith(SOURCE)) {
            int at = SOURCE.length();
            if (source.startsWith("sprinting", at) || source.startsWith("sneaking", at)
                    || source.startsWith("swimming", at) || source.startsWith("jumping", at)) {
                return KIND_MOVEMENT;
            }
            if (source.startsWith("command", at)) {
                return KIND_COMMAND;
            }
            if (source.startsWith("company", at) || source.startsWith("convoy", at)
                    || source.startsWith("darkness", at) || source.startsWith("night_out", at)) {
                return KIND_SHARE;
            }
            if (source.startsWith("guardian_", at) || source.startsWith("charger_", at)
                    || source.startsWith("tactician_", at)) {
                return KIND_ROLE;
            }
            return KIND_ACTION;
        }
        return KIND_OTHER;
    }

    private static final class KindAgg {
        long grants;
        double base;
        double fin;
    }

    private static final class SkillAgg {
        final Map<String, KindAgg> kinds = new HashMap<>(8);
        long engagedMs;
        long lastGrantMs = Long.MIN_VALUE;
        int levelUps;
        int level = -1;
        int deaths;
        double xpLost;
        long procs;
        long rolls;
        long rollHits;
        double chanceSum;

        boolean empty() {
            return kinds.isEmpty() && engagedMs == 0 && levelUps == 0 && deaths == 0 && procs == 0
                    && rolls == 0;
        }
    }

    private static final class PlayerAgg {
        String name;
        final SkillAgg[] skills = new SkillAgg[Skill.VALUES.length];
        long onlineMs;
        long activeMs;
        int deaths;
        int streakLost;

        PlayerAgg(String name) {
            this.name = name;
        }
    }

    private final Map<UUID, PlayerAgg> players = new HashMap<>();

    private PlayerAgg player(UUID id, String name) {
        PlayerAgg agg = players.get(id);
        if (agg == null) {
            agg = new PlayerAgg(name);
            players.put(id, agg);
        } else if (!agg.name.equals(name)) {
            agg.name = name;
        }
        return agg;
    }

    private static SkillAgg skill(PlayerAgg player, Skill skill) {
        SkillAgg agg = player.skills[skill.ordinal()];
        if (agg == null) {
            agg = new SkillAgg();
            player.skills[skill.ordinal()] = agg;
        }
        return agg;
    }

    /** One XP grant. {@code level} is the skill's level after it, kept for the time-to-level estimate. */
    public synchronized void grant(UUID id, String name, Skill skill, String source, double base,
            double finalXp, int level, long nowMs) {
        SkillAgg agg = skill(player(id, name), skill);
        String kind = kind(source);
        KindAgg bucket = agg.kinds.get(kind);
        if (bucket == null) {
            bucket = new KindAgg();
            agg.kinds.put(kind, bucket);
        }
        bucket.grants++;
        bucket.base += base;
        bucket.fin += finalXp;
        agg.lastGrantMs = nowMs;
        agg.level = level;
    }

    public synchronized void levelUps(UUID id, String name, Skill skill, int count, int level) {
        SkillAgg agg = skill(player(id, name), skill);
        agg.levelUps += count;
        agg.level = level;
    }

    /** A natural proc roll: its chance and whether it landed. Not called for frenzy or forced procs. */
    public synchronized void roll(UUID id, String name, Skill skill, double chance, boolean hit) {
        SkillAgg agg = skill(player(id, name), skill);
        agg.rolls++;
        agg.chanceSum += chance;
        if (hit) {
            agg.rollHits++;
        }
    }

    /** Every proc that fired, however it was decided (a roll, a frenzy, a forced one, a talent). */
    public synchronized void proc(UUID id, String name, Skill skill) {
        skill(player(id, name), skill).procs++;
    }

    /**
     * A death: each skill whose bar was wiped with the XP it held, and the streak stacks lost.
     * {@code xpLost} and {@code levels} are indexed by skill ordinal; zero means that bar was empty.
     */
    public synchronized void death(UUID id, String name, double[] xpLost, int[] levels, int streakLost) {
        PlayerAgg agg = player(id, name);
        agg.deaths++;
        agg.streakLost += streakLost;
        for (Skill skill : Skill.VALUES) {
            double lost = skill.ordinal() < xpLost.length ? xpLost[skill.ordinal()] : 0.0;
            if (lost > 0) {
                SkillAgg s = skill(agg, skill);
                s.deaths++;
                s.xpLost += lost;
                if (skill.ordinal() < levels.length) {
                    s.level = levels[skill.ordinal()];
                }
            }
        }
    }

    /**
     * A once-a-second look at one online player. Online time always counts; active time only when
     * the player is not AFK (the survival streak's notion: XP earned within its window), and then
     * every skill that paid XP in the last {@link #ENGAGED_MS} gets the time too.
     */
    public synchronized void tick(UUID id, String name, boolean active, long elapsedMs, long nowMs) {
        PlayerAgg agg = player(id, name);
        agg.onlineMs += elapsedMs;
        if (!active) {
            return;
        }
        agg.activeMs += elapsedMs;
        for (SkillAgg s : agg.skills) {
            if (s != null && s.lastGrantMs != Long.MIN_VALUE && nowMs - s.lastGrantMs <= ENGAGED_MS) {
                s.engagedMs += elapsedMs;
            }
        }
    }

    /** Whether anything was counted since the last drain. */
    public synchronized boolean isEmpty() {
        return players.isEmpty();
    }

    /**
     * The lines for everything counted since the last drain, which is then reset. {@code metaFields}
     * is extra JSON fields for the one meta line (curve numbers and the like), without braces.
     */
    public synchronized List<String> drain(long nowMs, String metaFields) {
        List<String> lines = new ArrayList<>();
        if (players.isEmpty()) {
            return lines;
        }
        lines.add("{\"v\":" + SCHEMA + ",\"t\":" + nowMs + ",\"type\":\"meta\""
                + (metaFields == null || metaFields.isEmpty() ? "" : "," + metaFields) + "}");
        for (Map.Entry<UUID, PlayerAgg> entry : players.entrySet()) {
            PlayerAgg p = entry.getValue();
            String head = "{\"v\":" + SCHEMA + ",\"t\":" + nowMs + ",\"player\":" + str(p.name)
                    + ",\"uuid\":\"" + entry.getKey() + "\"";
            lines.add(head + ",\"type\":\"player\",\"online_s\":" + num(p.onlineMs / 1000.0)
                    + ",\"active_s\":" + num(p.activeMs / 1000.0) + ",\"deaths\":" + p.deaths
                    + ",\"streak_lost\":" + p.streakLost + "}");
            for (Skill skill : Skill.VALUES) {
                SkillAgg s = p.skills[skill.ordinal()];
                if (s == null || s.empty()) {
                    continue;
                }
                for (Map.Entry<String, KindAgg> kind : s.kinds.entrySet()) {
                    KindAgg k = kind.getValue();
                    lines.add(head + ",\"type\":\"xp\",\"skill\":\"" + skill.id() + "\",\"kind\":\""
                            + kind.getKey() + "\",\"n\":" + k.grants + ",\"base\":" + num(k.base)
                            + ",\"xp\":" + num(k.fin) + "}");
                }
                int level = Math.max(0, s.level);
                lines.add(head + ",\"type\":\"skill\",\"skill\":\"" + skill.id() + "\",\"level\":"
                        + level + ",\"need\":" + num(level >= SkillMath.MAX_LEVEL ? 0 : SkillMath.xpToNext(level))
                        + ",\"active_s\":" + num(s.engagedMs / 1000.0)
                        + ",\"levelups\":" + s.levelUps + ",\"procs\":" + s.procs
                        + ",\"rolls\":" + s.rolls + ",\"roll_hits\":" + s.rollHits
                        + ",\"chance_sum\":" + num(s.chanceSum) + ",\"deaths\":" + s.deaths
                        + ",\"xp_lost\":" + num(s.xpLost) + "}");
            }
        }
        // Counters restart at zero. A skill that stays quiet has no row until it earns again.
        players.clear();
        return lines;
    }

    static String num(double value) {
        if (!Double.isFinite(value)) {
            return "0";
        }
        if (value == Math.rint(value) && Math.abs(value) < 1e12) {
            return Long.toString((long) value);
        }
        return String.format(Locale.ROOT, "%.4f", value);
    }

    static String str(String value) {
        StringBuilder out = new StringBuilder(value.length() + 2).append('"');
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            switch (c) {
                case '"' -> out.append("\\\"");
                case '\\' -> out.append("\\\\");
                case '\n' -> out.append("\\n");
                case '\r' -> out.append("\\r");
                case '\t' -> out.append("\\t");
                default -> {
                    if (c < 0x20) {
                        out.append(String.format("\\u%04x", (int) c));
                    } else {
                        out.append(c);
                    }
                }
            }
        }
        return out.append('"').toString();
    }
}
