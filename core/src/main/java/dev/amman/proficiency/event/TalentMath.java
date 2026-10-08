package dev.amman.proficiency.event;

import java.util.Objects;

/**
 * The arithmetic behind the expansion talents, kept apart from the handlers so it can be unit
 * tested without a running game. Nothing in here touches a registry or an ItemStack.
 */
public final class TalentMath {

    /** The most Cave Lore will ever take off a hit, whatever the passive climbs to. */
    public static final double MAX_UNDERGROUND_REDUCTION = 0.30;

    /** Cave Sense's radius and length before proc power. */
    public static final int CAVE_SENSE_RADIUS = 8;
    public static final int CAVE_SENSE_TICKS = 1200;

    /** Radius 12 was 15,625 lookups; this is the ceiling however much power is bought. */
    public static final int CAVE_SENSE_MAX_RADIUS = 12;
    public static final int CAVE_SENSE_MAX_TICKS = 6000;

    private TalentMath() {
    }

    /** "N% per rank" as a multiplier: rank 3 at 0.05 is 1.15. */
    public static double perRank(int rank, double perRank) {
        return 1.0 + Math.max(0, rank) * perRank;
    }

    /** "N% lower per rank" as a multiplier, never below zero. */
    public static double lessPerRank(int rank, double perRank) {
        return Math.max(0.0, 1.0 - Math.max(0, rank) * perRank);
    }

    /** Hygge: two fewer decorations per rank, but a room always needs at least one. */
    public static int cozyThreshold(int base, int hyggeRank) {
        return Math.max(1, base - 2 * Math.max(0, hyggeRank));
    }

    /** Warden's Whisper: 25% shorter per rank, and a Darkness that was applied lasts one tick at least. */
    public static int darknessDuration(int duration, int rank) {
        if (duration <= 0) {
            return duration;
        }
        return Math.max(1, (int) Math.round(duration * lessPerRank(rank, 0.25)));
    }

    /** Cave Lore: 40% of the Spelunking passive, capped. */
    public static double undergroundReduction(double passive) {
        if (!(passive > 0)) {
            return 0.0;
        }
        return Math.min(MAX_UNDERGROUND_REDUCTION, passive * 0.4);
    }

    public static int caveSenseRadius(double power) {
        if (!Double.isFinite(power) || power < 1.0) {
            return CAVE_SENSE_RADIUS;
        }
        return (int) Math.min(CAVE_SENSE_MAX_RADIUS, Math.round(CAVE_SENSE_RADIUS * power));
    }

    public static int caveSenseTicks(double power) {
        if (!Double.isFinite(power) || power < 1.0) {
            return CAVE_SENSE_TICKS;
        }
        return (int) Math.min(CAVE_SENSE_MAX_TICKS, Math.round(CAVE_SENSE_TICKS * power));
    }

    /**
     * Bulk Order's run of identical blocks. Placing anything else starts a new run; every tenth
     * block of one run is the one that pays.
     */
    public static final class Streak {
        public static final int EVERY = 10;

        private String last;
        private int count;

        /** Records one placement and says whether it is a tenth. */
        public synchronized boolean place(String blockId) {
            if (Objects.equals(blockId, last)) {
                count++;
            } else {
                last = blockId;
                count = 1;
            }
            return count % EVERY == 0;
        }

        public synchronized int count() {
            return count;
        }
    }
}
