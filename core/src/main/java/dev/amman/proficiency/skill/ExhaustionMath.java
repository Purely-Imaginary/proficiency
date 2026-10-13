package dev.amman.proficiency.skill;

/**
 * The one exhaustion watcher's numbers (common {@code event.ExhaustionWatch}). Vanilla has no
 * exhaustion event, so everything that shaves hunger watches the number: the Hearth's Hearty, the
 * Nightwalker talent Night Owl and the Endurance talent Lean Times. Each keeps a share of what
 * exhaustion climbed since the last look; the shares multiply, so two of them never read each
 * other's change as a climb or a drop.
 */
public final class ExhaustionMath {

    /**
     * What vanilla's {@code FoodData.tick} takes off exhaustion when it is above this, for one food
     * point (or one point of saturation). A GameTest runs the real tick across it, so a vanilla change
     * fails a test instead of drifting the numbers.
     */
    public static final float ROLL_OVER = 4.0f;

    private ExhaustionMath() {
    }

    /**
     * The share of a climb that stays when several watchers each keep their own share: the product,
     * each clamped to 0..1 (a NaN counts as keeping all).
     */
    public static double keep(double... shares) {
        double keep = 1.0;
        for (double share : shares) {
            keep *= Double.isNaN(share) ? 1.0 : Math.max(0.0, Math.min(1.0, share));
        }
        return keep;
    }

    /**
     * The exhaustion to write back after a look: of whatever it climbed from {@code before} to
     * {@code now}, only {@code keep} stays. A drop (the food tick rolled over, or a refund) changes
     * nothing; {@code now} itself comes back when nothing changes.
     */
    public static float scaleClimb(float before, float now, double keep) {
        if (!(now > before) || !(keep < 1.0)) {
            return now;
        }
        double kept = (now - before) * Math.max(0.0, keep);
        return (float) Math.max(0.0, before + kept);
    }

    /**
     * Exhaustion as it stands once the food tick has taken its roll-over: {@link #ROLL_OVER} less when
     * it is above {@link #ROLL_OVER} (vanilla compares with {@code >}), else the same. The watcher
     * records this at the head of the tick, so its look after the tick sees only what the tick added.
     */
    public static float afterRollOver(float exhaustion) {
        return exhaustion > ROLL_OVER ? exhaustion - ROLL_OVER : exhaustion;
    }

    /**
     * A refund of {@code amount} that the shares scale like the charge it gives back: the exhaustion
     * that comes off is {@code amount x keep}, never more than there is. Handing back the whole
     * {@code amount} after a charge that was already shaved to {@code keep} would hand back more than
     * was taken.
     */
    public static float refundTaken(float exhaustion, double amount, double keep) {
        if (!(amount > 0) || !(exhaustion > 0)) {
            return 0f;
        }
        double scaled = amount * Math.max(0.0, Math.min(1.0, Double.isNaN(keep) ? 1.0 : keep));
        return (float) Math.min(exhaustion, scaled);
    }
}
