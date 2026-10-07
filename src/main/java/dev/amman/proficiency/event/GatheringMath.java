package dev.amman.proficiency.event;

import org.jetbrains.annotations.Nullable;

/**
 * The arithmetic the gathering talents lean on, kept free of registries so it can be unit tested.
 * Nothing in here may touch a Block, an Item or an ItemStack.
 */
public final class GatheringMath {

    private GatheringMath() {
    }

    /**
     * Timber's log limit after proc power. The base limit is a lag guard, not a design number, so
     * power may raise it only up to {@code ceiling}: past that one swing on a jungle giant or a
     * mega spruce is a visible stall on a busy server.
     */
    public static int timberLimit(int base, double power, int ceiling) {
        double scaled = base * Math.max(1.0, power);
        return (int) Math.min(ceiling, Math.round(scaled));
    }

    /**
     * The sapling a log would have grown from, by the vanilla naming convention: {@code oak_log}
     * becomes {@code oak_sapling}, and a stripped log counts as its unstripped self. Returns null
     * for anything that does not follow the convention ({@code crimson_stem}, {@code oak_wood}),
     * so a modded log that names itself differently is skipped rather than guessed at.
     */
    @Nullable
    public static String saplingPathFor(String logPath) {
        String path = logPath.startsWith("stripped_") ? logPath.substring("stripped_".length()) : logPath;
        if (!path.endsWith("_log") || path.length() <= "_log".length()) {
            return null;
        }
        return path.substring(0, path.length() - "_log".length()) + "_sapling";
    }

    /** "N% per rank" as a probability, never above certainty. */
    public static double perRank(int rank, double perRankChance) {
        return Math.min(1.0, Math.max(0, rank) * perRankChance);
    }
}
