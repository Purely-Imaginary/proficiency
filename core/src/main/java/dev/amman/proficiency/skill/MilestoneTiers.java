package dev.amman.proficiency.skill;

import dev.amman.proficiency.perk.Talent;
import dev.amman.proficiency.perk.Talents;

import java.util.function.Predicate;

/**
 * The five levels that get a ceremony (10, 25, 50, 75, 100) and what each one opened. Pure, so the
 * unit tests pin down "a jump over several levels plays only the highest one" and "an old milestone
 * never plays again". The server decides (it knows the level before and after a gain); the client
 * only plays what the level-up packet names.
 */
public final class MilestoneTiers {

    public static final int[] TIERS = {10, 25, 50, 75, 100};
    /** The prefix of the per-skill "already celebrated" marks, kept out of the places-seen count. */
    public static final String MARK_PREFIX = "ms:";

    private MilestoneTiers() {
    }

    /** 0 to 4 for a tier level, -1 for any other number. */
    public static int index(int tier) {
        for (int i = 0; i < TIERS.length; i++) {
            if (TIERS[i] == tier) {
                return i;
            }
        }
        return -1;
    }

    public static String mark(Skill skill, int tier) {
        return MARK_PREFIX + skill.id() + ":" + tier;
    }

    /**
     * The highest tier in {@code (from, to]} that {@code seen} has not celebrated, or 0. A gain that
     * jumps 9 to 30 crosses 10 and 25 and plays the 25 only; one that crosses nothing plays nothing.
     */
    public static int fresh(int from, int to, Predicate<Integer> seen) {
        for (int i = TIERS.length - 1; i >= 0; i--) {
            int tier = TIERS[i];
            if (tier > from && tier <= to && !seen.test(tier)) {
                return tier;
            }
        }
        return 0;
    }

    /** Every tier in {@code (from, to]}, in order, so the caller can mark them all celebrated. */
    public static int[] crossed(int from, int to) {
        int n = 0;
        for (int tier : TIERS) {
            if (tier > from && tier <= to) {
                n++;
            }
        }
        int[] out = new int[n];
        int k = 0;
        for (int tier : TIERS) {
            if (tier > from && tier <= to) {
                out[k++] = tier;
            }
        }
        return out;
    }

    /** What a tier opened: counts and flags, turned into lines by the client. */
    public record Opened(int treeNodes, boolean signature, boolean ability, boolean stars, boolean expertise) {

        public int lines() {
            return (treeNodes > 0 ? 1 : 0) + (signature ? 1 : 0) + (ability ? 1 : 0) + (stars ? 1 : 0)
                    + (expertise ? 1 : 0);
        }
    }

    /**
     * What reaching {@code tier} in {@code skill} opened since the tier before it: the talent nodes
     * whose required level lies in that span, the signature proc and the ability if their unlock
     * levels do, the Mastery stars at 100, and Expertise at 100 (stars feed it). A tier that opened
     * nothing else says its levels count toward Expertise, so no ceremony has an empty list.
     */
    public static Opened opened(Skill skill, int tier, int procUnlock, int abilityUnlock) {
        int i = index(tier);
        int prev = i <= 0 ? 0 : TIERS[i - 1];
        int nodes = 0;
        for (Talent talent : Talents.of(skill)) {
            if (talent.requiredLevel() > prev && talent.requiredLevel() <= tier) {
                nodes++;
            }
        }
        boolean signature = procUnlock > prev && procUnlock <= tier;
        boolean ability = abilityUnlock > prev && abilityUnlock <= tier;
        boolean stars = tier >= SkillMath.MAX_LEVEL;
        boolean expertise = stars;
        if (nodes == 0 && !signature && !ability && !stars) {
            expertise = true;
        }
        return new Opened(nodes, signature, ability, stars, expertise);
    }
}
