package dev.amman.proficiency.hearth;

/**
 * Temp HP from Comfort regen (docs/HEARTH-SPEC.md, "Temp HP"): absorption up to
 * {@code absorptionPerTier} of max health per tier (the Comfort-capped regen tier), filled at the
 * regen rate only at full health, full Warmth, out of combat, in range. It stays until damage
 * spends it.
 *
 * <ul>
 * <li>No stacking: the Hearth tops up to {@code max(current absorption, its cap)}, never adding to
 * other sources.</li>
 * <li>1.21.1: the {@code max_absorption} modifier ({@code proficiency:hearth_absorption}) is only
 * ever grown while absorption is above 0, and removed once absorption reaches 0. Shrinking it would
 * clamp the absorption through vanilla's attribute update and claw temp HP back.</li>
 * </ul>
 */
public final class TempHp {

    /** One regen step adds one heart. */
    public static final double STEP = 2.0;

    private TempHp() {
    }

    /** The Hearth's cap in health points: {@code maxHealth x perTier x tier}. */
    public static double cap(double maxHealth, int tier, double perTier) {
        if (!(maxHealth > 0) || !(perTier > 0) || tier <= 0) {
            return 0.0;
        }
        return maxHealth * perTier * Math.min(tier, HearthData.TIERS);
    }

    /** Absorption after one regen step: up by {@link #STEP} towards the cap, never down. */
    public static double next(double absorption, double cap) {
        double current = Double.isNaN(absorption) ? 0.0 : Math.max(0.0, absorption);
        if (current >= cap) {
            return current;
        }
        return Math.min(cap, current + STEP);
    }

    /**
     * The size the {@code max_absorption} modifier should have: 0 (remove it) once absorption is
     * gone, otherwise never smaller than it already is.
     */
    public static double modifier(double currentModifier, double absorption, double cap) {
        if (!(absorption > 0)) {
            return 0.0;
        }
        return Math.max(Math.max(0.0, currentModifier), cap);
    }
}
