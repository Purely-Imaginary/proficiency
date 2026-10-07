package dev.amman.proficiency.skill;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

/**
 * The last few XP gains of one player, kept per skill, newest first, for the list under the
 * synergies in each skill's tree screen. Server-side and per session only: it answers "what did I
 * just earn in this skill, and from what", which is worthless after a relog, so nothing is saved.
 *
 * <p>Each skill has its own ring of {@link #CAPACITY} lines. A shared ring let a run of Wayfaring
 * gains push every other skill's history out, so a tree showed another skill's XP and none of
 * its own.
 *
 * <p>A gain of the same source as the skill's newest line, inside {@link #MERGE_WINDOW_MILLIS},
 * is added to that line instead of taking a new one. Mining a vein of iron is one line that
 * counts up, "+12.0 · Iron Ore ×5", rather than eight lines of iron. The window stops a line
 * from yesterday afternoon soaking up this morning's first block.
 *
 * <p>Pure: no Minecraft types, so the merging and the rings are unit tested. Skills are ordinals
 * and sources are translation keys, which is also exactly what goes over the wire.
 */
public final class XpLog {

    /** Lines kept and sent per skill. The panel has room for about this many under the synergies. */
    public static final int CAPACITY = 8;

    /** Same skill, same source, and no more than this since the last one: one line. */
    public static final long MERGE_WINDOW_MILLIS = 30_000L;

    /**
     * One line of the log.
     *
     * @param skill   the skill's ordinal
     * @param source  a translation key for what paid, or "" when the call site gave none
     * @param amount  the XP actually added, after every multiplier, summed over merged gains
     * @param base    the XP the call sites asked for, before any multiplier, summed the same way
     * @param count   how many gains were merged into this line
     * @param first   wall-clock millis of the oldest gain in it
     * @param at      wall-clock millis of the newest gain in it
     * @param factors the newest gain's multipliers, {@link XpFactors#encode} form, "" for none
     */
    public record Entry(int skill, String source, float amount, float base, int count, long first,
            long at, String factors) {
    }

    /** Per skill ordinal, newest first. Never longer than {@link #CAPACITY}. */
    private final Map<Integer, List<Entry>> bySkill = new HashMap<>();
    /** Skills whose lines changed since the last {@link #takeDirty()}. */
    private final Set<Integer> dirty = new TreeSet<>();

    /** A gain with no multipliers: what the player got is what was asked for. */
    public void add(int skill, String source, float amount, long now) {
        add(skill, source, amount, amount, "", now);
    }

    public void add(int skill, String source, float amount, float base, String factors, long now) {
        if (!Float.isFinite(amount) || amount <= 0) {
            return;
        }
        float asked = Float.isFinite(base) && base > 0 ? base : 0f;
        String key = source == null ? "" : source;
        String newestFactors = factors == null ? "" : factors;
        List<Entry> entries = bySkill.computeIfAbsent(skill, s -> new ArrayList<>(CAPACITY + 1));
        if (!entries.isEmpty()) {
            Entry head = entries.get(0);
            if (head.source().equals(key) && now - head.at() <= MERGE_WINDOW_MILLIS) {
                entries.set(0, new Entry(skill, key, head.amount() + amount, head.base() + asked,
                        head.count() + 1, head.first(), now, newestFactors));
                dirty.add(skill);
                return;
            }
        }
        entries.add(0, new Entry(skill, key, amount, asked, 1, now, now, newestFactors));
        while (entries.size() > CAPACITY) {
            entries.remove(entries.size() - 1);
        }
        dirty.add(skill);
    }

    /** One skill's lines, newest first; empty when it earned nothing this session. */
    public List<Entry> entries(int skill) {
        List<Entry> entries = bySkill.get(skill);
        return entries == null ? List.of() : List.copyOf(entries);
    }

    /** Every skill's lines in one list, newest first. */
    public List<Entry> entries() {
        List<Entry> all = new ArrayList<>();
        for (List<Entry> entries : bySkill.values()) {
            all.addAll(entries);
        }
        all.sort(Comparator.comparingLong(Entry::at).reversed());
        return List.copyOf(all);
    }

    /**
     * The skills whose lines changed since the last call, ascending, and clears them. The sync
     * tick asks this, so a player who earns nothing is sent nothing, and a miner is sent Mining.
     */
    public List<Integer> takeDirty() {
        List<Integer> changed = List.copyOf(dirty);
        dirty.clear();
        return changed;
    }
}
