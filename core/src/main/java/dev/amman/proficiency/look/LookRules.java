package dev.amman.proficiency.look;

import java.util.Locale;

/**
 * What the Jade tooltip says about a block or a mob (the optional Jade plugin, 1.16.0): the
 * decisions and the wire flags, with no Minecraft in them. The numbers themselves come from the
 * same resolvers the XP code uses; this class only decides what is shown and how it is printed.
 */
public final class LookRules {

    /** The block was placed by a player, or the mob came from an egg or a command: it pays nothing. */
    public static final int PLACED = 1;
    /** The first-time bonus of this kind has not been paid to the looking player yet. */
    public static final int FIRST = 2;
    /** A mob entry: {@code xp} is a hit, {@code kill} the kill bonus. */
    public static final int MOB = 4;

    private LookRules() {
    }

    /** What a looked-at block shows. */
    public enum Verdict {
        /** The held tool would earn XP: skill, amount, and the first-time mark when it applies. */
        PAYS,
        /** A kind that would pay, but a player placed it: "pays nothing". */
        PLACED,
        /** Nothing: no rule, the tool cannot harvest it, an unripe crop, or the skill is switched off. */
        HIDDEN
    }

    /**
     * @param skillFound  a break rule gives this tool and block a skill (an unripe crop and a block with
     *                    no rule give none)
     * @param canHarvest  the held tool would get the block's drop (an ore a wooden pickaxe cannot take
     *                    pays nothing, so the tooltip says nothing)
     * @param placed      a player placed the block ({@code PlacedBlocks.isUnpaid})
     * @param skillOn     the skill is enabled in the server config
     * @param xp          the XP the break would pay now
     */
    public static Verdict block(boolean skillFound, boolean canHarvest, boolean placed, boolean skillOn, double xp) {
        if (!skillFound || !canHarvest || !skillOn) {
            return Verdict.HIDDEN;
        }
        if (placed) {
            return Verdict.PLACED;
        }
        return xp > 0 && Double.isFinite(xp) ? Verdict.PAYS : Verdict.HIDDEN;
    }

    /** True when a mob entry is worth a line: some hit or kill XP, and the skill is switched on. */
    public static boolean mobPays(boolean skillOn, double hit, double kill) {
        return skillOn && (hit > 0 || kill > 0) && Double.isFinite(hit) && Double.isFinite(kill);
    }

    /**
     * The XP number as the banners print it: whole above 10, one decimal below, and "0.1" for a
     * crumb so a paying line never reads "+0.0".
     */
    public static String formatXp(double xp) {
        if (xp >= 10) {
            return String.valueOf(Math.round(xp));
        }
        return String.format(Locale.ROOT, "%.1f", Math.max(0.1, xp));
    }

    public static boolean has(int flags, int flag) {
        return (flags & flag) != 0;
    }

    /** The Hearth line of the tooltip: stage, then the lane while one is known. */
    public static String hearthHeadline(String stage, String lane) {
        return lane == null || lane.isEmpty() ? stage : stage + " · " + lane;
    }

    /** Whole percent of Warmth, clamped to 0 to 100, from a 0 to 1 fraction. */
    public static int warmthPercent(double warmth) {
        if (!(warmth > 0)) {
            return 0;
        }
        return (int) Math.min(100, Math.round(warmth * 100));
    }
}
