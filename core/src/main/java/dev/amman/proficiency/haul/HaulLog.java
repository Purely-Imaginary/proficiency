package dev.amman.proficiency.haul;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import dev.amman.proficiency.skill.Skill;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.ToDoubleFunction;

/**
 * One player's session since the last Haul card: what each skill earned and the session's counters. The server
 * keeps one per player, saves it with the player (so it survives a relog until it is shown) and empties it when
 * the card goes out ({@link #take}). A death keeps it: the haul is what was earned, and levels are never lost.
 *
 * <p>Pure: the game calls the {@code on*} methods from the XP grant, the Tempo announce, the synergy wake-up,
 * the discovery banner and the Hearth credit. Every input is checked, so a NaN or a negative amount is ignored.
 */
public final class HaulLog {

    /** Most synergy ids kept; the card only shows the count. */
    static final int MAX_SYNERGIES = 64;

    /** One skill's share of the session. {@code from} is the bar position at its first gain, in level units. */
    public static final class Entry {
        float xp;
        float from;
        int levels;
        int stars;

        Entry(float from) {
            this.from = from;
        }

        public float xp() {
            return xp;
        }

        public float from() {
            return from;
        }

        public int levels() {
            return levels;
        }

        public int stars() {
            return stars;
        }
    }

    private record EntryState(float xp, float from, int levels, int stars) {
        static final Codec<EntryState> CODEC = RecordCodecBuilder.create(i -> i.group(
                Codec.FLOAT.optionalFieldOf("xp", 0f).forGetter(EntryState::xp),
                Codec.FLOAT.optionalFieldOf("from", 0f).forGetter(EntryState::from),
                Codec.INT.optionalFieldOf("levels", 0).forGetter(EntryState::levels),
                Codec.INT.optionalFieldOf("stars", 0).forGetter(EntryState::stars)
        ).apply(i, EntryState::new));
    }

    public static final Codec<HaulLog> CODEC = RecordCodecBuilder.create(i -> i.group(
            Codec.unboundedMap(Codec.STRING, EntryState.CODEC).optionalFieldOf("skills", Map.of())
                    .forGetter(HaulLog::entryStates),
            Codec.INT.optionalFieldOf("points", 0).forGetter(log -> log.points),
            Codec.STRING.optionalFieldOf("tempoSkill", "").forGetter(log -> log.tempoSkill == null ? "" : log.tempoSkill.id()),
            Codec.INT.optionalFieldOf("tempoSteps", 0).forGetter(log -> log.tempoSteps),
            Codec.INT.optionalFieldOf("tempoPermille", 0).forGetter(log -> log.tempoPermille),
            Codec.INT.optionalFieldOf("found", 0).forGetter(log -> log.found),
            Codec.STRING.listOf().optionalFieldOf("synergies", List.of()).forGetter(log -> List.copyOf(log.synergies)),
            Codec.DOUBLE.optionalFieldOf("charge", 0.0).forGetter(log -> log.charge),
            Codec.LONG.optionalFieldOf("since", 0L).forGetter(log -> log.since)
    ).apply(i, HaulLog::fromParts));

    private final Map<Skill, Entry> entries = new EnumMap<>(Skill.class);
    private int points;
    private Skill tempoSkill;
    private int tempoSteps;
    private int tempoPermille;
    private int found;
    private final Set<String> synergies = new LinkedHashSet<>();
    private double charge;
    /** Epoch millis of the session's first entry, 0 while empty. */
    private long since;

    public HaulLog() {
    }

    private static HaulLog fromParts(Map<String, EntryState> skills, int points, String tempoSkill, int tempoSteps,
            int tempoPermille, int found, List<String> synergies, double charge, long since) {
        HaulLog log = new HaulLog();
        skills.forEach((id, state) -> {
            Skill skill = Skill.byId(id);
            if (skill != null) {
                Entry entry = new Entry(sane(state.from()));
                entry.xp = sane(state.xp());
                entry.levels = Math.max(0, state.levels());
                entry.stars = Math.max(0, state.stars());
                log.entries.put(skill, entry);
            }
        });
        log.points = Math.max(0, points);
        log.tempoSkill = tempoSkill.isEmpty() ? null : Skill.byId(tempoSkill);
        log.tempoSteps = log.tempoSkill == null ? 0 : Math.max(0, tempoSteps);
        log.tempoPermille = log.tempoSkill == null ? 0 : Math.max(0, tempoPermille);
        log.found = Math.max(0, found);
        for (String id : synergies) {
            if (log.synergies.size() < MAX_SYNERGIES) {
                log.synergies.add(id);
            }
        }
        log.charge = Double.isFinite(charge) ? Math.max(0, charge) : 0;
        log.since = Math.max(0, since);
        return log;
    }

    private Map<String, EntryState> entryStates() {
        Map<String, EntryState> out = new java.util.HashMap<>();
        entries.forEach((skill, e) -> out.put(skill.id(), new EntryState(e.xp, e.from, e.levels, e.stars)));
        return out;
    }

    // ---- Recording -------------------------------------------------------------------------------

    /**
     * A paid grant: {@code banked} XP kept, the bar stood at {@code unitsBefore} (level units) before it,
     * {@code levels} levels and {@code stars} Mastery stars came of it, and {@code points} talent points.
     * Returns true when the log changed. A skill's first gain fixes where its bar starts on the card.
     */
    public boolean onGrant(Skill skill, float banked, float unitsBefore, int levels, int stars, int points, long now) {
        if (skill == null) {
            return false;
        }
        float xp = sane(banked);
        int lv = Math.max(0, levels);
        int st = Math.max(0, stars);
        if (xp <= 0f && lv == 0 && st == 0) {
            return false;
        }
        Entry entry = entries.computeIfAbsent(skill, s -> new Entry(sane(unitsBefore)));
        entry.xp += xp;
        entry.levels += lv;
        entry.stars += st;
        this.points += Math.max(0, points);
        start(now);
        return true;
    }

    /** A Tempo chain was announced; the best multiplier of the session is kept (the first on a tie). */
    public boolean onTempo(Skill skill, int steps, int permille, long now) {
        if (skill == null || permille <= 1000 || permille <= tempoPermille) {
            return false;
        }
        tempoSkill = skill;
        tempoSteps = Math.max(0, steps);
        tempoPermille = permille;
        start(now);
        return true;
    }

    /** A first-time discovery: a new kind, biome, dimension or structure. */
    public boolean onFound(long now) {
        found++;
        start(now);
        return true;
    }

    /** A synergy woke. The same synergy twice in a session (a respec and back) counts once. */
    public boolean onSynergy(String id, long now) {
        if (id == null || id.isEmpty() || synergies.size() >= MAX_SYNERGIES || !synergies.add(id)) {
            return false;
        }
        start(now);
        return true;
    }

    /** Hearth charge this player's work put in. */
    public boolean onCharge(double amount, long now) {
        if (!(amount > 0) || Double.isInfinite(amount)) {
            return false;
        }
        charge += amount;
        start(now);
        return true;
    }

    private void start(long now) {
        if (since <= 0) {
            since = Math.max(1, now);
        }
    }

    // ---- Reading ---------------------------------------------------------------------------------

    public boolean isEmpty() {
        return entries.isEmpty() && points == 0 && tempoSkill == null && found == 0 && synergies.isEmpty()
                && !(charge > 0);
    }

    public Entry entry(Skill skill) {
        return entries.get(skill);
    }

    public double totalXp() {
        double total = 0;
        for (Entry e : entries.values()) {
            total += e.xp;
        }
        return total;
    }

    public int totalLevels() {
        int total = 0;
        for (Entry e : entries.values()) {
            total += e.levels;
        }
        return total;
    }

    public int points() {
        return points;
    }

    public int found() {
        return found;
    }

    public int synergies() {
        return synergies.size();
    }

    public double charge() {
        return charge;
    }

    public int tempoPermille() {
        return tempoPermille;
    }

    public long since() {
        return since;
    }

    /**
     * The card for this log: a row per skill with XP, from where its bar started to {@code unitsNow} of the skill,
     * the most XP first. Does not empty the log; {@link #take} does.
     */
    public HaulSummary summary(int mode, ToDoubleFunction<Skill> unitsNow, long now) {
        List<HaulSummary.Row> rows = new ArrayList<>();
        entries.forEach((skill, e) -> {
            if (e.xp > 0f || e.levels > 0 || e.stars > 0) {
                float to = (float) unitsNow.applyAsDouble(skill);
                if (!Float.isFinite(to)) {
                    to = e.from;
                }
                rows.add(new HaulSummary.Row(skill.ordinal(), e.xp, e.from, Math.max(e.from, to), e.levels, e.stars));
            }
        });
        int minutes = since > 0 && now > since ? (int) Math.min(Integer.MAX_VALUE, (now - since) / 60_000L) : 0;
        return new HaulSummary(mode, minutes, HaulSummary.sorted(rows), points,
                tempoSkill == null ? -1 : tempoSkill.ordinal(), tempoSteps, tempoPermille, found, synergies.size(),
                (float) charge);
    }

    /** The card, and the log starts over: what the server does when it shows the haul. */
    public HaulSummary take(ToDoubleFunction<Skill> unitsNow, long now) {
        HaulSummary summary = summary(HaulSummary.CARD, unitsNow, now);
        clear();
        return summary;
    }

    public void clear() {
        entries.clear();
        points = 0;
        tempoSkill = null;
        tempoSteps = 0;
        tempoPermille = 0;
        found = 0;
        synergies.clear();
        charge = 0;
        since = 0;
    }

    private static float sane(float value) {
        return Float.isFinite(value) ? Math.max(0f, value) : 0f;
    }
}
