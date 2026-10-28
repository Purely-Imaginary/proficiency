package dev.amman.proficiency.haul;

/**
 * The stamp at the end of the Haul card: a fixed, bounded ladder of six tiers by the session's total XP. The
 * ladder is a fun label, nothing reads it: no bonus, no unlock. The thresholds sit on the curve's own scale
 * (README "The curve": level 10 is 293 XP, level 25 1,919), so a short evening lands in the middle and only a
 * long, busy day reaches the top.
 */
public enum HaulRating {

    SCRAPS("scraps", 0, 0x9A938C),
    HONEST_WORK("honest", 100, 0xC9B79C),
    GOOD_HAUL("good", 400, 0x7FD06A),
    HEAVY_HAUL("heavy", 1200, 0x5FB8F0),
    GRAND_HAUL("grand", 3000, 0xFFC85A),
    LEGENDARY("legendary", 7500, 0xD58CFF);

    public static final HaulRating[] VALUES = values();

    private final String id;
    private final int minXp;
    private final int colour;

    HaulRating(String id, int minXp, int colour) {
        this.id = id;
        this.minXp = minXp;
        this.colour = colour;
    }

    public String id() {
        return id;
    }

    /** The lowest total XP that earns this tier. */
    public int minXp() {
        return minXp;
    }

    /** The stamp's ink, 0xRRGGBB. */
    public int colour() {
        return colour;
    }

    /** {@code proficiency.haul.rating.<id>}. */
    public String translationKey() {
        return "proficiency.haul.rating." + id;
    }

    /** 0 for the bottom tier, 5 for the top. */
    public int rank() {
        return ordinal();
    }

    /** The tier a session of {@code totalXp} earns. NaN and anything at or below zero is the bottom tier. */
    public static HaulRating of(double totalXp) {
        if (!(totalXp > 0)) {
            return SCRAPS;
        }
        HaulRating best = SCRAPS;
        for (HaulRating tier : VALUES) {
            if (totalXp >= tier.minXp) {
                best = tier;
            }
        }
        return best;
    }

    /** The ClickFx burst tier the stamp lands with: 0 for the two lowest, 1 for the middle, 2 for the top two. */
    public int burstTier() {
        return ordinal() >= GRAND_HAUL.ordinal() ? 2 : ordinal() >= GOOD_HAUL.ordinal() ? 1 : 0;
    }
}
