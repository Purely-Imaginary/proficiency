package dev.amman.proficiency.hearth.effects;

/**
 * The numbers of the player-side boons (docs/HEARTH-SPEC.md, "Warmth", "Temp HP" and the boons
 * table). Pure, so the per-player loop and the damage hook only fetch tiers and call these.
 *
 * <ul>
 * <li><b>Hearty</b>: hunger drains {@code value x Warmth} slower at home. Away, Provisioned carries
 * a fading value; the stronger of the two counts, never both.</li>
 * <li><b>Comfort regen</b>: one heart per {@code secondsPerHeart}, at the rate Warmth allows, only
 * out of combat. The clock never banks more than one heart.</li>
 * <li><b>Fortified</b>: a mob's hit at home is cut by {@code value x Warmth}.</li>
 * </ul>
 */
public final class EffectsMath {

    private EffectsMath() {
    }

    private static double clamp01(double value) {
        return Double.isNaN(value) ? 0.0 : Math.max(0.0, Math.min(1.0, value));
    }

    /**
     * The Hearty strength right now: the home value scaled by Warmth, or what Provisioned still
     * carries, whichever is more (they never add up).
     */
    public static double hearty(double homeValue, double warmth, double provisioned) {
        return Math.max(clamp01(homeValue) * clamp01(warmth), clamp01(provisioned));
    }

    /** The share of exhaustion that still counts under a Hearty strength: {@code 1 - strength}. */
    public static double exhaustionFactor(double hearty) {
        return 1.0 - clamp01(hearty);
    }

    /**
     * Of whatever exhaustion climbed since the last look, only {@code factor} stays; a drop changes
     * nothing. The one watcher ({@code event.ExhaustionWatch}, Hearty with Night Owl and Lean Times)
     * uses {@link dev.amman.proficiency.skill.ExhaustionMath#scaleClimb}, which this calls; it is kept
     * because the contract lists it (the tests are in {@code ExhaustionMathTest}).
     *
     * @return the value to write back; {@code now} itself when nothing changes
     * @deprecated call {@link dev.amman.proficiency.skill.ExhaustionMath#scaleClimb}
     */
    @Deprecated
    public static float scaleClimb(float before, float now, double factor) {
        return dev.amman.proficiency.skill.ExhaustionMath.scaleClimb(before, now, factor);
    }

    /** Fortified: the multiplier for a mob's hit, {@code 1 - value x Warmth}. */
    public static double fortifiedFactor(double value, double warmth) {
        return 1.0 - clamp01(value) * clamp01(warmth);
    }

    /**
     * Comfort regen's clock after one second. In combat it starts over; otherwise it gains
     * {@code warmth / secondsPerHeart} and never holds more than one heart, so a full player does
     * not bank hearts for later.
     */
    public static double regenProgress(double progress, double warmth, double secondsPerHeart, boolean inCombat) {
        if (inCombat || !(secondsPerHeart > 0)) {
            return 0.0;
        }
        double p = Double.isNaN(progress) ? 0.0 : Math.max(0.0, progress);
        return Math.min(1.0, p + clamp01(warmth) / secondsPerHeart);
    }

    /** Whether the regen clock holds a whole heart. */
    public static boolean heartDue(double progress) {
        return progress >= 1.0 - 1e-9;
    }

    /** Whole percent, for chat lines (0.28 is 28). */
    public static int percent(double fraction) {
        return (int) Math.round(clamp01(fraction) * 100.0);
    }
}
