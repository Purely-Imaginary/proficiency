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

    /** Charcoal Burner: one log in eight of a felled tree goes to the furnace. */
    public static int charcoalLogs(int felled) {
        return Math.max(0, felled) / 8;
    }

    /** Charcoal Burner: how many of {@code logs} fit a fuel slot already holding {@code held}. */
    public static int fuelRoom(int logs, int held, int maxStack) {
        return Math.max(0, Math.min(logs, maxStack - Math.max(0, held)));
    }

    /**
     * Dredger: what the vanilla break-speed penalties took, given back. Vanilla scales the speed by
     * {@code submerged} (0.2 under water without Aqua Affinity, 1 with it or when dry) and divides
     * by five when not on the ground; undoing both returns exactly the dry, grounded speed and
     * never more.
     */
    public static float dredgeFactor(float submerged, boolean airborne) {
        float water = submerged > 0f && submerged < 1f ? 1f / submerged : 1f;
        return water * (airborne ? 5f : 1f);
    }

    /** Fishing Trip: a friend's Treasure Haul makes your next roll certain for this many ticks. */
    public static final int FISHING_TRIP_TICKS = 60 * 20;
}
