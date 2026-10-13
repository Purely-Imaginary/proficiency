package dev.amman.proficiency.perk;

import dev.amman.proficiency.skill.SkillCategory;

import java.util.EnumMap;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Expertise: one tree across all skills, paid for with points that come from breadth and depth.
 * One point per 100 levels summed over every skill (35 at most) and one per 5 Mastery stars summed
 * over every skill (35 at most), so 70 at the very end. The points are worked out from the levels
 * and stars each time, never stored, so they cannot drift from what the player has.
 *
 * <p>The rules the tree keeps ({@code ExpertiseTest}): the whole tree costs no more than the 70
 * points a player can earn, no node excludes another, and nothing softens a death. Every effect is
 * a multiplier on a {@link PerkEffect} that the skills already read through
 * {@code PlayerSkills.modifier}, so the tree screen's breakdown shows it as its own part.
 * At a full tree a skill gets roughly +6 to +10% on each number: a step, not a second build.
 * Design: docs/EXPERTISE-SPEC.md.
 */
public final class Expertise {

    /** Summed skill levels per point. */
    public static final int LEVELS_PER_POINT = 100;
    /** Summed Mastery stars per point. */
    public static final int STARS_PER_POINT = 5;
    /** Most points from levels: 35 skills at level 100. */
    public static final int MAX_FROM_LEVELS = 35;
    /** Most points from stars: 35 skills at five stars. */
    public static final int MAX_FROM_STARS = 35;
    public static final int MAX_POINTS = MAX_FROM_LEVELS + MAX_FROM_STARS;
    /** Vanilla XP levels a respec costs per this many points refunded (at least one level). */
    public static final int RESPEC_POINTS_PER_LEVEL = 10;

    private static final Set<SkillCategory> MARTIAL = EnumSet.of(SkillCategory.COMBAT);
    private static final Set<SkillCategory> CRAFT = EnumSet.of(SkillCategory.GATHERING, SkillCategory.CRAFTING,
            SkillCategory.MASTERY);
    private static final Set<SkillCategory> ROAMING = EnumSet.of(SkillCategory.MOVEMENT, SkillCategory.EXPEDITION,
            SkillCategory.CONSTRUCTION, SkillCategory.SOCIAL, SkillCategory.SURVIVAL);
    private static final Set<SkillCategory> ALL = EnumSet.noneOf(SkillCategory.class);

    private static final Map<String, ExpertiseNode> BY_ID = new LinkedHashMap<>();

    static {
        // Row 0: the trunk. A little more XP everywhere.
        node("breadth", Talent.Kind.ROOT, 0, 2, 3, 1, List.of(), ALL, PerkEffect.XP_RATE, 0.01);
        // Row 1: each branch's passive. Martial is combat, Craft makes and gathers, Roaming is the rest.
        node("martial", Talent.Kind.NODE, 1, 0, 3, 2, List.of("breadth"), MARTIAL, PerkEffect.BONUS, 0.02);
        node("craft", Talent.Kind.NODE, 1, 2, 3, 2, List.of("breadth"), CRAFT, PerkEffect.BONUS, 0.02);
        node("roaming", Talent.Kind.NODE, 1, 4, 3, 2, List.of("breadth"), ROAMING, PerkEffect.BONUS, 0.02);
        // Row 2: each branch's signature chance.
        node("keen_edge", Talent.Kind.NODE, 2, 0, 2, 2, List.of("martial"), MARTIAL, PerkEffect.PROC_CHANCE, 0.03);
        node("lucky_hands", Talent.Kind.NODE, 2, 2, 2, 2, List.of("craft"), CRAFT, PerkEffect.PROC_CHANCE, 0.03);
        node("good_fortune", Talent.Kind.NODE, 2, 4, 2, 2, List.of("roaming"), ROAMING, PerkEffect.PROC_CHANCE, 0.03);
        // Row 3: the bridges, for every skill.
        node("weight", Talent.Kind.BRIDGE, 3, 1, 3, 3, List.of("keen_edge", "lucky_hands"), ALL,
                PerkEffect.PROC_POWER, 0.02);
        node("rhythm", Talent.Kind.BRIDGE, 3, 3, 2, 3, List.of("lucky_hands", "good_fortune"), ALL,
                PerkEffect.TEMPO, 0.05);
        // Row 4: the ability, and more XP in the middle.
        node("long_fury", Talent.Kind.NODE, 4, 0, 2, 3, List.of("weight"), ALL, PerkEffect.ABILITY_DURATION, 0.03);
        node("scholar", Talent.Kind.NODE, 4, 2, 2, 2, List.of("weight", "rhythm"), ALL, PerkEffect.XP_RATE, 0.01);
        node("second_wind", Talent.Kind.NODE, 4, 4, 2, 3, List.of("rhythm"), ALL, PerkEffect.ABILITY_COOLDOWN, -0.03);
        // Row 5: the capstone. A bit of everything, once.
        add(new ExpertiseNode("polymath", Talent.Kind.CAPSTONE, 5, 2, 1, 6,
                List.of("long_fury", "scholar", "second_wind"), effects(
                        PerkEffect.XP_RATE, 0.02, PerkEffect.BONUS, 0.02, PerkEffect.PROC_CHANCE, 0.02), ALL));
    }

    private Expertise() {
    }

    private static void node(String id, Talent.Kind kind, int row, int col, int maxRank, int cost,
            List<String> parents, Set<SkillCategory> categories, PerkEffect effect, double perRank) {
        add(new ExpertiseNode(id, kind, row, col, maxRank, cost, parents, effects(effect, perRank), categories));
    }

    private static Map<PerkEffect, Double> effects(Object... parts) {
        Map<PerkEffect, Double> map = new EnumMap<>(PerkEffect.class);
        for (int i = 0; i < parts.length; i += 2) {
            map.put((PerkEffect) parts[i], (Double) parts[i + 1]);
        }
        return java.util.Collections.unmodifiableMap(map);
    }

    private static void add(ExpertiseNode node) {
        BY_ID.put(node.id(), new ExpertiseNode(node.id(), node.kind(), node.row(), node.col(), node.maxRank(),
                node.costPerRank(), List.copyOf(node.parents()), node.perRank(),
                java.util.Collections.unmodifiableSet(node.categories().isEmpty()
                        ? EnumSet.noneOf(SkillCategory.class) : EnumSet.copyOf(node.categories()))));
    }

    /** Every node, trunk first, in reading order. */
    public static List<ExpertiseNode> nodes() {
        return List.copyOf(BY_ID.values());
    }

    public static ExpertiseNode byId(String id) {
        return BY_ID.get(id);
    }

    /** What the whole tree costs. Never more than {@link #MAX_POINTS}: a finished player fills it. */
    public static int fullCost() {
        return BY_ID.values().stream().mapToInt(ExpertiseNode::totalCost).sum();
    }

    /** Points from levels summed over every skill: one per 100, at most 35. */
    public static int pointsFromLevels(int totalLevels) {
        return Math.max(0, Math.min(MAX_FROM_LEVELS, totalLevels / LEVELS_PER_POINT));
    }

    /** Points from Mastery stars summed over every skill: one per 5, at most 35. */
    public static int pointsFromStars(int totalStars) {
        return Math.max(0, Math.min(MAX_FROM_STARS, totalStars / STARS_PER_POINT));
    }

    public static int points(int totalLevels, int totalStars) {
        return pointsFromLevels(totalLevels) + pointsFromStars(totalStars);
    }

    /**
     * The branch a node's effects reach, as a word for the screen: {@code all} for every skill, else
     * {@code martial}, {@code craft} or {@code roaming}. Lang key {@code proficiency.expertise.branch.<it>}.
     */
    public static String branch(ExpertiseNode node) {
        if (node.categories().isEmpty()) {
            return "all";
        }
        if (node.categories().contains(SkillCategory.COMBAT)) {
            return "martial";
        }
        return node.categories().contains(SkillCategory.GATHERING) ? "craft" : "roaming";
    }

    /** Summed levels the next level point needs, or -1 when levels give no more. */
    public static int nextLevelsAt(int totalLevels) {
        return pointsFromLevels(totalLevels) >= MAX_FROM_LEVELS ? -1
                : (totalLevels / LEVELS_PER_POINT + 1) * LEVELS_PER_POINT;
    }

    /** Summed stars the next star point needs, or -1 when stars give no more. */
    public static int nextStarsAt(int totalStars) {
        return pointsFromStars(totalStars) >= MAX_FROM_STARS ? -1
                : (totalStars / STARS_PER_POINT + 1) * STARS_PER_POINT;
    }

    /** Vanilla XP levels to empty a tree with {@code spent} points in it; 0 for an empty tree. */
    public static int respecPrice(int spent) {
        return spent <= 0 ? 0 : Math.max(1, spent / RESPEC_POINTS_PER_LEVEL);
    }
}
