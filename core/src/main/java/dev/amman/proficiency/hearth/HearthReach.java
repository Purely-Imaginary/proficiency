package dev.amman.proficiency.hearth;

/**
 * Reach: a Hearth's range grows with the Homebuilder XP earned at it (docs/HEARTH-REACH-SPEC.md).
 * Every Homebuilder gain a player makes while in range is added to the Hearth's Reach, which never
 * drains. The radius climbs from the base range to the maximum as Reach fills to {@code fullXp},
 * in whole blocks so the index and the ring change only when the edge actually moves. Pure.
 */
public final class HearthReach {

    private HearthReach() {
    }

    /** How full Reach is, 0..1. A full of 0 or less means "always full". */
    public static double fill(double reach, double fullXp) {
        if (!(fullXp > 0)) {
            return 1.0;
        }
        if (!(reach > 0)) {
            return 0.0;
        }
        return Math.min(1.0, reach / fullXp);
    }

    /**
     * The range radius in whole blocks: base at no Reach, max when full, straight between. A max
     * below the base is read as the base (Reach never shrinks a range).
     */
    public static int radius(double base, double max, double reach, double fullXp) {
        double top = Math.max(base, max);
        return (int) Math.floor(base + (top - base) * fill(reach, fullXp));
    }
}
