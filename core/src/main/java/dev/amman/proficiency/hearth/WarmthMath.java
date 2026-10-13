package dev.amman.proficiency.hearth;

/**
 * Warmth, readiness and Provisioned (docs/HEARTH-SPEC.md, "Warmth" and "Setting out and coming
 * home"). Warmth is 0..1. In range it rises linearly to full in the readiness rise time (60 s with
 * no Homecoming) and does not rise in combat, though nothing is lost; out of range it drains in
 * {@code drainSeconds} (twice as fast). A real sleep or a Feast sets it to full at once.
 * Provisioned carries a strength away from home for a number of minutes, fading linearly to 0.
 */
public final class WarmthMath {

    /** Warmth this close to 1 counts as full (float rounding over 60 steps). */
    public static final double FULL = 1.0 - 1e-6;

    private WarmthMath() {
    }

    public static boolean isFull(double warmth) {
        return warmth >= FULL;
    }

    /**
     * One step of {@code seconds}.
     *
     * @param riseSeconds the readiness rise time ({@link HearthRules#warmthRiseSeconds}), already
     *                    shortened by any talent ({@link #withSpeedBonus})
     */
    public static double step(double warmth, boolean inRange, boolean inCombat, double seconds,
            double riseSeconds, double drainSeconds) {
        double w = Double.isNaN(warmth) ? 0.0 : Math.max(0.0, Math.min(1.0, warmth));
        if (!(seconds > 0)) {
            return w;
        }
        if (inRange) {
            if (inCombat || !(riseSeconds > 0)) {
                return w;
            }
            return Math.min(1.0, w + seconds / riseSeconds);
        }
        if (!(drainSeconds > 0)) {
            return 0.0;
        }
        return Math.max(0.0, w - seconds / drainSeconds);
    }

    /** A rise time made faster by {@code bonus} (0.10 = 10% faster, as Warm Welcome per rank). */
    public static double withSpeedBonus(double riseSeconds, double bonus) {
        return bonus > 0 ? riseSeconds / (1.0 + bonus) : riseSeconds;
    }

    /** Signed Warmth change per second in this state, for a HUD that interpolates between packets. */
    public static double ratePerSecond(double warmth, boolean inRange, boolean inCombat, double riseSeconds,
            double drainSeconds) {
        if (inRange) {
            return inCombat || warmth >= 1.0 || !(riseSeconds > 0) ? 0.0 : 1.0 / riseSeconds;
        }
        return warmth <= 0.0 || !(drainSeconds > 0) ? 0.0 : -1.0 / drainSeconds;
    }

    /** Whether damage taken at {@code lastDamageTick} still counts as combat at {@code now}. */
    public static boolean inCombat(long lastDamageTick, long now, double combatSeconds) {
        return lastDamageTick > Long.MIN_VALUE / 2 && now - lastDamageTick < Math.round(combatSeconds * 20.0);
    }

    /** The game time Provisioned ends when it starts at {@code now} and lasts {@code minutes}. */
    public static long provisionedUntil(long now, double minutes) {
        return now + Math.max(0L, Math.round(minutes * 1200.0));
    }

    /** Provisioned strength at {@code now}: {@code strength} at the start, fading linearly to 0 at {@code until}. */
    public static double provisioned(double strength, long from, long until, long now) {
        if (!(strength > 0) || until <= from || now >= until) {
            return 0.0;
        }
        if (now <= from) {
            return strength;
        }
        return strength * (until - now) / (double) (until - from);
    }
}
