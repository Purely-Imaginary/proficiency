package dev.amman.proficiency.skill;

/**
 * Seam Sense and Lodestone: the Motherlode charge. With Seam Sense, plain stone no longer rolls
 * Motherlode (a proc on stone only paid two more cobblestone). Each stone adds a little to a charge
 * instead, and the next ore spends it. One full charge is a certain Motherlode; below one, the charge
 * is added to the ore's normal chance and the whole of it is spent on that ore, hit or miss.
 *
 * <p>The charge is one number from 0 to the capacity. Its whole part is the full charges banked,
 * its fraction the one being built. Lodestone raises the capacity from 1 to at most 4.
 */
public final class LodeMath {

    private LodeMath() {
    }

    /** Seam Sense, per stone, by rank: a full charge takes 250, 143 or 100 stones. */
    private static final double[] PER_STONE = {0.0, 0.004, 0.007, 0.010};

    public static final int MAX_CAPACITY = 4;

    public static double perStone(int seamSenseRank) {
        return PER_STONE[Math.max(0, Math.min(PER_STONE.length - 1, seamSenseRank))];
    }

    /** Full charges the player can bank: 1, and 1 more per Lodestone rank, never past 4. */
    public static int capacity(int lodestoneRank) {
        return Math.max(1, Math.min(MAX_CAPACITY, 1 + lodestoneRank));
    }

    /** The charge after one more stone, held at the capacity. A bad stored value counts as 0. */
    public static double charge(double lode, int seamSenseRank, int capacity) {
        return Math.min(capacity, sane(lode) + perStone(seamSenseRank));
    }

    /** Full charges in a charge value: the number the player sees, 0 to 4. */
    public static int full(double lode) {
        return (int) Math.floor(sane(lode) + 1e-9);
    }

    /**
     * What an ore strike does with the charge. With a full charge banked the Motherlode is certain
     * and one charge goes; without one, the partial charge is added to the roll and all of it goes.
     */
    public record Strike(boolean certain, double bonus, double left) {
    }

    public static Strike strike(double lode) {
        double value = sane(lode);
        if (full(value) >= 1) {
            return new Strike(true, 0.0, Math.max(0.0, value - 1.0));
        }
        return new Strike(false, value, 0.0);
    }

    /** Tempering's most per Motherlode, in durability points. */
    public static final int MEND_CAP = 4;

    /** The share of the wear Tempering gives back: at most 1 / (1 - 0.8) = 5 times the life. */
    public static final double MEND_SHARE = 0.8;

    /**
     * Tempering: the durability a Motherlode mends, given the wear the pickaxe took since the last
     * one. Points, not a share of the maximum (3% of a diamond pickaxe outran its wear), and never
     * more than 80% of the wear, so a pickaxe lasts at most five times as long and never becomes
     * unbreakable, however often Motherlode fires or whatever Unbreaking it has. A fraction is
     * rounded at random by the caller.
     */
    public static double mend(int wearSinceLast) {
        return wearSinceLast <= 0 ? 0.0 : Math.min(MEND_CAP, MEND_SHARE * wearSinceLast);
    }

    static double sane(double lode) {
        return Double.isFinite(lode) && lode > 0 ? lode : 0.0;
    }
}
