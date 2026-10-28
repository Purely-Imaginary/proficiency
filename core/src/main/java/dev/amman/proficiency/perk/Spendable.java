package dev.amman.proficiency.perk;

import dev.amman.proficiency.skill.PlayerSkills;
import dev.amman.proficiency.skill.Skill;
import dev.amman.proficiency.skill.SkillCategory;

import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Which talent ranks a player can spend NOW (1.55.0), and why the other points wait. One rule for every
 * screen: the HUD brazier, the Face brazier, the dial markers, the trees, the ready toast.
 *
 * <p>A rank is spendable now when its node is open ({@link PlayerSkills#isUnlockableNow}: level gate and
 * parents, the same two rules {@link PlayerSkills#check} runs), its point cost fits the skill's unspent
 * points, and, on the rank that fills the node, its materials are paid already or carried in full (the
 * same count the pay path makes). The inventory is not known here: the caller passes {@link Materials}.
 *
 * <p>{@code readyRanks} counts ranks, not nodes: the open ranks are taken cheapest first while the points
 * last, so two open 1-point ranks and 1 unspent point are "1 ready". Nodes the open ones would open next are
 * not counted (they are not open yet).
 */
public final class Spendable {

    private Spendable() {
    }

    /** The inventory, as the pay path sees it. */
    public interface Materials {
        /** How many of this requirement the player carries (tags and the whole inventory). */
        int held(Requirement requirement);

        /** Whether this requirement can be met here at all (an item of a missing mod is never asked for). */
        boolean available(Requirement requirement);
    }

    /** One material short on a node whose filling rank waits for it while the points are there: "Cobblestone x12 (have 4)". */
    public record Short(Talent talent, Requirement requirement, int have, int need) {
    }

    /** An open node whose next rank costs more than the points left over after the ready ranks: "costs 2, you have 1". */
    public record Costly(Talent talent, int cost, int have) {
    }

    /**
     * One skill.
     *
     * @param unspent        the skill's unspent points
     * @param readyRanks     ranks that can be bought now (see the class note)
     * @param leftover       unspent points the ready ranks do not use: the points that wait for something
     * @param materialNodes  open nodes whose filling rank waits for items alone (the points left over pay for it)
     * @param materials      what those nodes are short of, node by node
     * @param costs          open nodes with ranks left whose cost is above the points left over
     * @param nextLevel      the level the next row of nodes opens at, 0 for none
     * @param prerequisites  nodes to fill first: parents of level-ready nodes that are not open themselves
     * @param treeFull       every node is full: the points have nowhere to go
     */
    public record Report(Skill skill, int unspent, int readyRanks, int leftover, int materialNodes,
                         List<Short> materials, List<Costly> costs, int nextLevel, List<Talent> prerequisites,
                         boolean treeFull) {

        static Report none(Skill skill, boolean treeFull) {
            return new Report(skill, 0, 0, 0, 0, List.of(), List.of(), 0, List.of(), treeFull);
        }

        /** Points wait here for something (materials, a level, a parent, more points). */
        public boolean waiting() {
            return leftover > 0 && !treeFull;
        }

        /** Whether anything explains the waiting points. */
        public boolean hasReasons() {
            return !materials.isEmpty() || !costs.isEmpty() || nextLevel > 0 || !prerequisites.isEmpty();
        }
    }

    /** Every skill at once. */
    public record Summary(Map<Skill, Report> reports, int ready, int materialNodes, int unspent) {

        public Report of(Skill skill) {
            Report r = reports.get(skill);
            return r == null ? Report.none(skill, false) : r;
        }

        public int ready(Skill skill) {
            return of(skill).readyRanks();
        }

        public int readyIn(SkillCategory category) {
            int sum = 0;
            for (Report r : reports.values()) {
                if (r.skill().category() == category) {
                    sum += r.readyRanks();
                }
            }
            return sum;
        }

        /** Skills whose points wait for something, in skill order. */
        public List<Report> waiting() {
            List<Report> out = new ArrayList<>();
            for (Report r : reports.values()) {
                if (r.waiting()) {
                    out.add(r);
                }
            }
            return out;
        }

        /** The skills that went from 0 ready ranks (in {@code before}) to at least 1 here. */
        public List<Skill> newlyReady(Summary before) {
            List<Skill> out = new ArrayList<>();
            for (Report r : reports.values()) {
                if (r.readyRanks() > 0 && (before == null || before.ready(r.skill()) == 0)) {
                    out.add(r.skill());
                }
            }
            return out;
        }

        public static Summary empty() {
            return new Summary(Map.of(), 0, 0, 0);
        }
    }

    /** The requirements a node still asks for when it fills: none once paid, and none a missing mod owns. */
    public static List<Requirement> owed(PlayerSkills skills, Talent talent, Materials materials) {
        if (skills.hasPaid(talent) || talent.materials().isEmpty()) {
            return List.of();
        }
        List<Requirement> out = new ArrayList<>();
        for (Requirement r : talent.materials()) {
            if (materials.available(r)) {
                out.add(r);
            }
        }
        return out;
    }

    /** Whether the next rank of {@code talent} can be bought now: open, affordable, and stocked if it fills. */
    public static boolean nextReady(PlayerSkills skills, Talent talent, Materials materials) {
        if (!skills.isUnlockableNow(talent) || talent.costPerRank() > skills.pointsAvailable(talent.skill())) {
            return false;
        }
        return skills.rank(talent) + 1 < talent.maxRank() || shorts(skills, talent, materials).isEmpty();
    }

    /** What the node is short of for its filling rank (empty when paid or stocked). */
    public static List<Short> shorts(PlayerSkills skills, Talent talent, Materials materials) {
        List<Short> out = new ArrayList<>();
        for (Requirement r : owed(skills, talent, materials)) {
            int have = Math.max(0, materials.held(r));
            if (have < r.count()) {
                out.add(new Short(talent, r, have, r.count()));
            }
        }
        return out;
    }

    public static Report of(PlayerSkills skills, Skill skill, Materials materials) {
        List<Talent> tree = Talents.of(skill);
        boolean anyLeft = false;
        for (Talent t : tree) {
            if (!skills.isFull(t)) {
                anyLeft = true;
                break;
            }
        }
        int unspent = skills.pointsAvailable(skill);
        if (unspent <= 0 || !anyLeft) {
            return new Report(skill, Math.max(0, unspent), 0, Math.max(0, unspent), 0, List.of(), List.of(), 0,
                    List.of(), !anyLeft);
        }
        int level = skills.level(skill);
        List<Talent> open = new ArrayList<>();
        List<List<Short>> openShorts = new ArrayList<>();
        List<Integer> openFree = new ArrayList<>();
        List<Talent> readyRanks = new ArrayList<>();
        int nextLevel = Integer.MAX_VALUE;
        int anyLevel = Integer.MAX_VALUE;
        Set<Talent> prereq = new LinkedHashSet<>();
        for (Talent t : tree) {
            if (skills.isFull(t)) {
                continue;
            }
            if (!skills.isUnlockableNow(t)) {
                boolean parentsFull = true;
                List<Talent> missing = new ArrayList<>();
                for (String pid : t.parents()) {
                    Talent p = Talents.get(skill, pid);
                    if (p != null && !skills.isFull(p)) {
                        parentsFull = false;
                        missing.add(p);
                    }
                }
                if (level < t.requiredLevel()) {
                    anyLevel = Math.min(anyLevel, t.requiredLevel());
                    if (parentsFull) {
                        nextLevel = Math.min(nextLevel, t.requiredLevel());
                    }
                } else {
                    for (Talent p : missing) {
                        if (!skills.isUnlockableNow(p)) {
                            prereq.add(p);
                        }
                    }
                }
                continue;
            }
            open.add(t);
            List<Short> shorts = shorts(skills, t, materials);
            openShorts.add(shorts);
            int left = t.maxRank() - skills.rank(t);
            int free = shorts.isEmpty() ? left : left - 1;
            openFree.add(free);
            for (int i = 0; i < free; i++) {
                readyRanks.add(t);
            }
        }
        // The ready ranks: cheapest first while the points last (a stable sort keeps tree order among equals).
        readyRanks.sort(java.util.Comparator.comparingInt(Talent::costPerRank));
        int budget = unspent;
        int ready = 0;
        Map<Talent, Integer> counted = new java.util.HashMap<>();
        for (Talent t : readyRanks) {
            if (t.costPerRank() > budget) {
                break;
            }
            budget -= t.costPerRank();
            ready++;
            counted.merge(t, 1, Integer::sum);
        }
        // What the points left over wait for, node by node: a rank they cannot pay for, or a filling rank whose
        // items are short while the points are there.
        List<Short> shortList = new ArrayList<>();
        List<Costly> costs = new ArrayList<>();
        int materialNodes = 0;
        for (int i = 0; i < open.size(); i++) {
            Talent t = open.get(i);
            int took = counted.getOrDefault(t, 0);
            int rest = t.maxRank() - skills.rank(t) - took;
            if (rest <= 0) {
                continue;
            }
            if (took < openFree.get(i) || t.costPerRank() > budget) {
                costs.add(new Costly(t, t.costPerRank(), budget));
            } else {
                materialNodes++;
                shortList.addAll(openShorts.get(i));
            }
        }
        // A row whose parents are full waits only for the level: always worth saying. A row further down is named
        // only when nothing else explains the points (else "Lv 30" hides the items the root still needs).
        int gate = nextLevel != Integer.MAX_VALUE ? nextLevel
                : shortList.isEmpty() && costs.isEmpty() && anyLevel != Integer.MAX_VALUE ? anyLevel : 0;
        return new Report(skill, unspent, ready, budget, materialNodes, List.copyOf(shortList), List.copyOf(costs),
                gate, List.copyOf(prereq), false);
    }

    public static Summary all(PlayerSkills skills, Materials materials) {
        Map<Skill, Report> map = new EnumMap<>(Skill.class);
        int ready = 0;
        int mats = 0;
        int unspent = 0;
        for (Skill skill : Skill.VALUES) {
            Report r = of(skills, skill, materials);
            map.put(skill, r);
            ready += r.readyRanks();
            mats += r.materialNodes();
            unspent += r.unspent();
        }
        return new Summary(Collections.unmodifiableMap(map), ready, mats, unspent);
    }
}
