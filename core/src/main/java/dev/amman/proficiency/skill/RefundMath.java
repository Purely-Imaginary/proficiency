package dev.amman.proficiency.skill;

/**
 * Masonry and Decorating: whether a placed block is free.
 *
 * <p>A placement gives back at most one item, the block you placed. Every source is a chance that
 * this block is free, and the chances combine as independent rolls:
 * {@code P = 1 - (1 - proc)(1 - passive)(1 - keystone)}, capped at {@link #DEFAULT_FREE_CAP} (or
 * {@link #DEFAULT_FREE_CAP_KEYSTONE} with Master Mason or Master Decorator). The signature proc is
 * one of the rolls. During the ability every proc lands, so the proc chance counts as 1 and the
 * block is free at the cap. Bulk Order makes one block of a run certain and lowers the chance of the
 * others in the run, so the long-run share stays at the same total.
 *
 * <p>Before this a proc refunded three blocks or more (four or five at level 49), so a builder came
 * out ahead from about level 47, and Master Mason made every block free.
 */
public final class RefundMath {

    public static final double DEFAULT_FREE_CAP = 0.55;
    public static final double DEFAULT_FREE_CAP_KEYSTONE = 0.75;
    public static final double DEFAULT_KEYSTONE_CHANCE = 0.30;

    /** Stonecutter and Feng Shui: a free-block chance per rank (they made the triple bigger before). */
    public static final double FREE_CHANCE_PER_RANK = 0.04;

    /** Lamplighter: a light is free this often per rank. */
    public static final double LAMPLIGHTER_PER_RANK = 0.15;

    private RefundMath() {
    }

    /** {@code 1 - prod(1 - c)}: the chance that at least one of independent rolls lands. */
    public static double combine(double... chances) {
        double none = 1.0;
        for (double c : chances) {
            none *= 1.0 - clamp(c);
        }
        return 1.0 - none;
    }

    /** The cap that applies: the higher one with the keystone. */
    public static double cap(boolean keystone, double cap, double capKeystone) {
        return clamp(keystone ? Math.max(cap, capKeystone) : cap);
    }

    /** Bulk Order: every Nth identical full block in a row is free. 10, 8, 6 for ranks 1 to 3; 0 = off. */
    public static int bulkEvery(int rank) {
        if (rank <= 0) {
            return 0;
        }
        return Math.max(2, 12 - 2 * Math.min(rank, 3));
    }

    /** The share of a run's blocks that Bulk Order makes free. */
    public static double bulkShare(int rank) {
        int every = bulkEvery(rank);
        return every == 0 ? 0.0 : 1.0 / every;
    }

    /**
     * The long-run share of placements that are free.
     *
     * @param proc     the signature proc's chance (1 during the ability)
     * @param passive  the other chances already combined (passive refund, Stonecutter, Lamplighter)
     * @param keystone Master Mason's own chance (0 without it)
     * @param bulk     Bulk Order's share in a run of one block (0 off)
     * @param cap      the cap from {@link #cap}
     */
    public static double total(double proc, double passive, double keystone, double bulk, double cap) {
        return Math.min(clamp(cap), combine(proc, passive, keystone, bulk));
    }

    /**
     * In a run, the blocks Bulk Order does not make certain: the chance that keeps the run's share
     * at {@code total}. With no cap in the way this is the chance without Bulk Order.
     */
    public static double offStreak(double total, double bulk) {
        if (bulk <= 0) {
            return clamp(total);
        }
        if (bulk >= 1) {
            return 0.0;
        }
        return clamp((total - bulk) / (1.0 - bulk));
    }

    /** The chance the block is free when the proc landed, so that both branches average to {@code free}. */
    public static double freeGivenProc(double proc, double free) {
        if (proc <= 0) {
            return clamp(free);
        }
        return clamp(Math.min(1.0, free / proc));
    }

    /** The chance the block is free when the proc did not land. */
    public static double freeGivenNoProc(double proc, double free) {
        if (proc >= 1) {
            return 0.0;
        }
        return clamp(Math.max(0.0, (free - Math.max(0.0, proc)) / (1.0 - Math.max(0.0, proc))));
    }

    /** Expected items a placement costs, the table in the CHANGELOG: {@code 1 - total}. */
    public static double cost(double proc, double passive, double keystone, double bulk, double cap) {
        return 1.0 - total(proc, passive, keystone, bulk, cap);
    }

    private static double clamp(double v) {
        if (!(v > 0)) {
            return 0.0;
        }
        return Math.min(1.0, v);
    }
}
