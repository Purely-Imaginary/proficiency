package dev.amman.proficiency.perk;

import dev.amman.proficiency.skill.Skill;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Predicate;
import java.util.function.ToIntFunction;

/**
 * What an item is still needed for: the rules behind the "Needed" block on item tooltips. An
 * {@link Index} maps item ids and tag ids to the unpaid talent nodes that want them; {@link #lines}
 * turns the entries of one hovered stack into sorted, counted lines. No Minecraft here: the game
 * side resolves a stack to its item id and tag ids and counts what the player carries.
 */
public final class MaterialNeeds {

    private MaterialNeeds() {
    }

    /** One node that wants one requirement. */
    public record Entry(Skill skill, Talent talent, Requirement requirement) {
    }

    /**
     * One line of the block: the node, how many it wants, how many the player carries and whether
     * the node can be unlocked right now ({@code unlocked}: level gate open and parents full, the click
     * path's rule; points do not count).
     */
    public record Line(Entry entry, int required, int held, boolean unlocked) {
        public int missing() {
            return Math.max(0, required - held);
        }
    }

    /** The material to nodes index. Built once per tree change, read on every hover. */
    public static final class Index {
        private final Map<String, List<Entry>> byItem = new HashMap<>();
        private final Map<String, List<Entry>> byTag = new HashMap<>();
        private int size;

        /** Entries for an item id and the ids of the tags it belongs to, in no set order. */
        public List<Entry> entriesFor(String itemId, Collection<String> tagIds) {
            List<Entry> out = new ArrayList<>();
            List<Entry> direct = byItem.get(itemId);
            if (direct != null) {
                out.addAll(direct);
            }
            for (String tag : tagIds) {
                List<Entry> viaTag = byTag.get(tag);
                if (viaTag != null) {
                    out.addAll(viaTag);
                }
            }
            return out;
        }

        public boolean isEmpty() {
            return size == 0;
        }

        public int size() {
            return size;
        }
    }

    /**
     * Indexes every node that still owes materials. A node already paid for is left out, and so is
     * a requirement for a mod that is not installed (the pay path does not ask for it either).
     */
    public static Index build(Predicate<Talent> paid, Predicate<Requirement> available) {
        Index index = new Index();
        for (Skill skill : Skill.VALUES) {
            for (Talent talent : Talents.of(skill)) {
                if (talent.materials().isEmpty() || paid.test(talent)) {
                    continue;
                }
                for (Requirement requirement : talent.materials()) {
                    if (!available.test(requirement)) {
                        continue;
                    }
                    Entry entry = new Entry(skill, talent, requirement);
                    if (requirement.tag() != null) {
                        index.byTag.computeIfAbsent(requirement.tag(), k -> new ArrayList<>()).add(entry);
                    } else {
                        index.byItem.computeIfAbsent(requirement.itemId(), k -> new ArrayList<>()).add(entry);
                    }
                    index.size++;
                }
            }
        }
        return index;
    }

    /**
     * Counts and sorts the entries. Nearest first: nodes that can be unlocked now, then the lowest
     * gate, then the smaller shortfall, then skill and node key so the order never flickers. A node
     * that matches through two requirements is one line, the one with the larger shortfall.
     */
    public static List<Line> lines(List<Entry> entries, ToIntFunction<Requirement> held, Predicate<Talent> unlockableNow) {
        Map<String, Line> byNode = new HashMap<>();
        for (Entry entry : entries) {
            Line line = new Line(entry, entry.requirement().count(), held.applyAsInt(entry.requirement()),
                    unlockableNow.test(entry.talent()));
            byNode.merge(entry.talent().key(), line, (a, b) -> b.missing() > a.missing() ? b : a);
        }
        List<Line> lines = new ArrayList<>(byNode.values());
        lines.sort(Comparator
                .comparing((Line l) -> !l.unlocked())
                .thenComparingInt(l -> l.entry().talent().requiredLevel())
                .thenComparingInt(Line::missing)
                .thenComparing(l -> l.entry().talent().key()));
        return lines;
    }

    /** What the player still has to find for the listed nodes: the sum of the shortfalls. */
    public static int totalMissing(List<Line> lines) {
        int total = 0;
        for (Line line : lines) {
            total += line.missing();
        }
        return total;
    }

    /** The lines for nodes that can be unlocked right now (they sort first). */
    public static List<Line> immediate(List<Line> lines) {
        return lines.stream().filter(Line::unlocked).toList();
    }

    /** The lines for nodes still gated by level or by an unfilled parent. */
    public static List<Line> later(List<Line> lines) {
        return lines.stream().filter(l -> !l.unlocked()).toList();
    }

    /** What the tooltip shows: the immediate lines, plus the later ones only when detailed (Shift). */
    public static List<Line> shown(List<Line> lines, boolean detailed) {
        return detailed ? lines : immediate(lines);
    }

    /** How many later lines the default view folds behind Shift, but only when something immediate shows. */
    public static int hidden(List<Line> lines, boolean detailed) {
        return detailed || immediate(lines).isEmpty() ? 0 : later(lines).size();
    }
}
