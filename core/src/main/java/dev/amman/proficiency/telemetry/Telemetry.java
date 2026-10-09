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

    /**
     * A grant that follows a long quiet spell (more than {@link #ENGAGED_MS}) only opens a short
     * window, so a skill that pays once in a while is not credited a full minute per payout.
     */
    public static final long SOLO_MS = 10_000L;

    /** A player counts as at the controls for this long after they last moved, turned or earned real XP. */
    public static final long INPUT_GRACE_MS = 60_000L;

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
    /** The extra XP a grant gained from the player's rested pool. Not a source: a bonus on top of one. */
    public static final String KIND_RESTED = "rested";
    /** Social XP a teacher was paid for what a student spent of the pool the teacher filled. */
    public static final String KIND_TEACHING = "teaching";
    /** XP for the extra blocks an area tool (hammer, excavator, vein miner) broke with the first one. */
    public static final String KIND_AOE = "aoe";
    public static final String KIND_OTHER = "other";

    private static final String SOURCE = "proficiency.xplog.source.";

    /** Kinds that pay with no one at the controls (or are an operator's), so they prove no activity. */
    static boolean isPassive(String kind) {
        return KIND_SHARE.equals(kind) || KIND_MOVEMENT.equals(kind) || KIND_COMMAND.equals(kind)
                || KIND_TEACHING.equals(kind);
    }

    /**
     * As {@link #kind(String)}, knowing the skill. Endurance and Blocking are paid for the hits a
     * player takes, and the source they log is the attacker's mob id; that is damage taken, not a
     * mob kill, so it must not read as the "mob" kind.
     */
    public static String kind(Skill skill, String source) {
        if (source != null && source.startsWith("entity.")
                && (skill == Skill.ENDURANCE || skill == Skill.BLOCKING)) {
            return KIND_DAMAGE;
        }
        return kind(source);
    }

    /** What an XP log source string stands for, as a short stable label. Allocation free. */
    public static String kind(String source) {
        if (source == null || source.isEmpty()) {
            // A grant that names no source (a spell cast, a redstone part) is a plain action.
            return KIND_ACTION;
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
            if (source.startsWith("teaching", at)) {
                return KIND_TEACHING;
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
        long engagedUntilMs = Long.MIN_VALUE;
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

        boolean timingLive(long nowMs) {
            return engagedUntilMs != Long.MIN_VALUE && nowMs - engagedUntilMs <= ENGAGED_MS;
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

    /**
     * What a drain must not forget: per player and skill the last grant, the engaged-until clock and
     * the last known level. Without it every flush would cut the minute a skill was still "in use"
     * and a quiet skill's next row would claim level 0.
     */
    private final Map<UUID, long[][]> carry = new HashMap<>();

    /** Per player: last position and look seen by {@link #activeNow}, and when it last changed. */
    private final Map<UUID, double[]> inputs = new HashMap<>();

    private PlayerAgg player(UUID id, String name) {
        PlayerAgg agg = players.get(id);
        if (agg == null) {
            agg = new PlayerAgg(name);
            long[][] kept = carry.get(id);
            if (kept != null) {
                for (int i = 0; i < kept.length; i++) {
                    if (kept[i] != null) {
                        SkillAgg s = new SkillAgg();
                        s.lastGrantMs = kept[i][0];
                        s.engagedUntilMs = kept[i][1];
                        s.level = (int) kept[i][2];
                        agg.skills[i] = s;
                    }
                }
            }
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
        grant(id, name, skill, source, base, finalXp, level, nowMs, null);
    }

    /** As above; a non-null {@code kindOverride} files the grant under that kind instead of the source's. */
    public synchronized void grant(UUID id, String name, Skill skill, String source, double base,
            double finalXp, int level, long nowMs, String kindOverride) {
        SkillAgg agg = skill(player(id, name), skill);
        String kind = kindOverride != null ? kindOverride : kind(skill, source);
        KindAgg bucket = agg.kinds.get(kind);
        if (bucket == null) {
            bucket = new KindAgg();
            agg.kinds.put(kind, bucket);
        }
        bucket.grants++;
        bucket.base += base;
        bucket.fin += finalXp;
        agg.level = level;
        // An operator's /skills addxp is not play: it must not open the "in use" window.
        if (!KIND_COMMAND.equals(kind)) {
            long gap = agg.lastGrantMs == Long.MIN_VALUE ? Long.MAX_VALUE : nowMs - agg.lastGrantMs;
            agg.engagedUntilMs = nowMs + (gap <= ENGAGED_MS ? ENGAGED_MS : SOLO_MS);
            agg.lastGrantMs = nowMs;
        }
        // Real play (not a trickle that pays on its own) also proves someone is at the controls.
        if (!isPassive(kind)) {
            inputs.computeIfAbsent(id, k -> new double[6])[5] = nowMs;
        }
    }

    /**
     * XP a grant gained from the rested pool, recorded as its own kind so the balance report can
     * show it. It adds to the XP total but is not a grant: it counts no grant and opens no
     * "in use" window, since the grant it rode on already did.
     */
    public synchronized void rested(UUID id, String name, Skill skill, double finalXp) {
        if (!(finalXp > 0) || Double.isInfinite(finalXp)) {
            return;
        }
        SkillAgg agg = skill(player(id, name), skill);
        KindAgg bucket = agg.kinds.computeIfAbsent(KIND_RESTED, k -> new KindAgg());
        bucket.fin += finalXp;
    }

    /**
     * Whether the player is at the controls: they moved more than a block, turned, or earned XP
     * from real play within {@link #INPUT_GRACE_MS}. Passive trickles (company, darkness, night
     * out, sprinting into a wall) deliberately do not count, so an AFK player is not "active".
     */
    public synchronized boolean activeNow(UUID id, double x, double y, double z, float yaw, float pitch,
            long nowMs) {
        double[] in = inputs.get(id);
        if (in == null) {
            in = new double[6];
            inputs.put(id, in);
            in[0] = x;
            in[1] = y;
            in[2] = z;
            in[3] = yaw;
            in[4] = pitch;
            in[5] = nowMs;
            return true;
        }
        double dx = x - in[0];
        double dy = y - in[1];
        double dz = z - in[2];
        boolean moved = dx * dx + dy * dy + dz * dz > 1.0;
        boolean turned = Math.abs(yaw - in[3]) > 0.5 || Math.abs(pitch - in[4]) > 0.5;
        if (moved || turned) {
            in[0] = x;
            in[1] = y;
            in[2] = z;
            in[3] = yaw;
            in[4] = pitch;
            in[5] = Math.max(in[5], nowMs);
        }
        return nowMs - in[5] <= INPUT_GRACE_MS;
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
        // Perks and talents can push a chance past 1 (Tidecaller x2, Charger, Deep Delver); a roll
        // cannot be more certain than certain, and the report's p*(1-p) goes negative above 1.
        agg.chanceSum += Double.isFinite(chance) ? Math.max(0.0, Math.min(1.0, chance)) : 0.0;
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
            if (s != null && s.engagedUntilMs != Long.MIN_VALUE && nowMs <= s.engagedUntilMs) {
                s.engagedMs += elapsedMs;
            }
        }
    }

    /** Forgets everything, counters and carried timing alike (a new session must not inherit the last one). */
    public synchronized void reset() {
        players.clear();
        carry.clear();
        inputs.clear();
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
                // A window that saw no grant has no level to report; the report keeps the last known one.
                String levelFields = "";
                if (s.level >= 0) {
                    levelFields = ",\"level\":" + s.level + ",\"need\":"
                            + num(s.level >= SkillMath.MAX_LEVEL ? 0 : SkillMath.xpToNext(s.level));
                }
                lines.add(head + ",\"type\":\"skill\",\"skill\":\"" + skill.id() + "\"" + levelFields
                        + ",\"active_s\":" + num(s.engagedMs / 1000.0)
                        + ",\"levelups\":" + s.levelUps + ",\"procs\":" + s.procs
                        + ",\"rolls\":" + s.rolls + ",\"roll_hits\":" + s.rollHits
                        + ",\"chance_sum\":" + num(s.chanceSum) + ",\"deaths\":" + s.deaths
                        + ",\"xp_lost\":" + num(s.xpLost) + "}");
            }
        }
        // Counters restart at zero. A skill that stays quiet has no row until it earns again, but
        // its timing and level carry over so a flush does not cut a running "in use" window.
        carry.clear();
        for (Map.Entry<UUID, PlayerAgg> entry : players.entrySet()) {
            long[][] kept = null;
            for (Skill skill : Skill.VALUES) {
                SkillAgg s = entry.getValue().skills[skill.ordinal()];
                if (s == null || (s.level < 0 && !s.timingLive(nowMs))) {
                    continue;
                }
                if (kept == null) {
                    kept = new long[Skill.VALUES.length][];
                }
                kept[skill.ordinal()] = new long[] {s.lastGrantMs, s.engagedUntilMs, s.level};
            }
            if (kept != null) {
                carry.put(entry.getKey(), kept);
            }
        }
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
