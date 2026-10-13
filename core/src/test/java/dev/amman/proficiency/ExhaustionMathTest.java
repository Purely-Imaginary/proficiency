package dev.amman.proficiency;

import dev.amman.proficiency.skill.ExhaustionMath;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The one exhaustion watcher: shares multiply, a climb is scaled once, a drop is left alone. */
class ExhaustionMathTest {

    @Test
    void sharesMultiplyAndAreClamped() {
        assertEquals(1.0, ExhaustionMath.keep(), 1e-12);
        assertEquals(0.6, ExhaustionMath.keep(0.6), 1e-12);
        // Hearty VI at full Warmth (0.6), Night Owl at 0.5, Lean Times at 0.55.
        assertEquals(0.6 * 0.5 * 0.55, ExhaustionMath.keep(0.6, 0.5, 0.55), 1e-12);
        assertEquals(0.0, ExhaustionMath.keep(0.6, -2.0), 1e-12);
        assertEquals(0.5, ExhaustionMath.keep(0.5, 7.0, Double.NaN), 1e-12);
    }

    @Test
    void onlyAClimbIsScaled() {
        assertEquals(1.3f, ExhaustionMath.scaleClimb(1.0f, 1.5f, 0.6), 1e-6);
        // A drop: the food tick took a point, or a refund came first. Nothing to scale.
        assertEquals(0.2f, ExhaustionMath.scaleClimb(3.9f, 0.2f, 0.5), 1e-6);
        // No share: what is there stays.
        assertEquals(1.5f, ExhaustionMath.scaleClimb(1.0f, 1.5f, 1.0), 1e-6);
        assertEquals(1.0f, ExhaustionMath.scaleClimb(1.0f, 1.5f, 0.0), 1e-6);
    }

    @Test
    void aClimbThatWouldPassFourIsKeptUnderIt() {
        // The look at the head of the food tick: 3.9 + 0.2 at half would be 4.0, which vanilla
        // does not turn into a food point (it needs more than 4.0).
        float kept = ExhaustionMath.scaleClimb(3.9f, 4.1f, 0.5);
        assertEquals(4.0f, kept, 1e-6);
    }

    @Test
    void theFoodTickTakesFourOnlyWhenAboveIt() {
        assertEquals(4.0f, ExhaustionMath.ROLL_OVER, 0f);
        assertEquals(0.2f, ExhaustionMath.afterRollOver(4.2f), 1e-6);
        assertEquals(4.0f, ExhaustionMath.afterRollOver(4.0f), 0f, "vanilla compares with >, 4.0 itself stays");
        assertEquals(1.0f, ExhaustionMath.afterRollOver(1.0f), 0f);
        assertEquals(0.0f, ExhaustionMath.afterRollOver(0.0f), 0f);
    }

    /**
     * The look at the tail of the food tick: the head recorded the value after the roll-over, so what the
     * tick added is the whole climb (the look used to see 4.2 against 5.2 and scale 1.0 instead of 5.0).
     */
    @Test
    void theLookAfterTheTickScalesWhatTheTickAddedEvenWhenItTookAPoint() {
        float keptAtHead = ExhaustionMath.scaleClimb(0.0f, 7.0f, 0.6);          // 4.2
        assertEquals(4.2f, keptAtHead, 1e-5);
        float recorded = ExhaustionMath.afterRollOver(keptAtHead);               // vanilla leaves 0.2
        float afterTick = recorded + 4.0f;                                       // saturated regeneration adds 4.0
        float kept = ExhaustionMath.scaleClimb(recorded, afterTick, 0.6);
        assertEquals(0.2f + 4.0f * 0.6f, kept, 1e-5);
        // Recording the head value unchanged (4.2) saw a climb of nothing and kept all of it.
        assertEquals(afterTick, ExhaustionMath.scaleClimb(keptAtHead, afterTick, 0.6), 1e-5);
    }

    @Test
    void aRefundIsScaledByTheSharesLikeTheChargeItGivesBack() {
        // Long Haul III hands back 30% of a sprint's charge c; Night Owl III (0.5) and Lean Times III (0.55)
        // shaved the charge itself to 0.275 c. The refund must be of that, never of the whole c.
        double keep = ExhaustionMath.keep(0.5, 0.55);
        float base = 1.0f;
        float c = 0.4f;
        float afterCharge = ExhaustionMath.scaleClimb(base, base + c, keep);
        float taken = ExhaustionMath.refundTaken(afterCharge, c * 0.3, keep);
        assertEquals(base + 0.7 * keep * c, afterCharge - taken, 1e-5, "0.7 of what stayed");
        assertTrue(afterCharge - taken > base, "sprinting never lowers exhaustion below where it was");
        // The unscaled refund the shares used to meet: b + 0.275c - 0.3c < b.
        assertTrue(afterCharge - (float) (c * 0.3) < base);
    }

    @Test
    void aRefundNeverTakesMoreThanThereIsOrLessThanNothing() {
        assertEquals(0.3f, ExhaustionMath.refundTaken(0.3f, 1.0, 1.0), 1e-6);
        assertEquals(0.0f, ExhaustionMath.refundTaken(0.0f, 1.0, 1.0), 0f);
        assertEquals(0.0f, ExhaustionMath.refundTaken(2.0f, -1.0, 1.0), 0f);
        assertEquals(0.0f, ExhaustionMath.refundTaken(2.0f, 1.0, 0.0), 0f);
        assertEquals(0.5f, ExhaustionMath.refundTaken(2.0f, 1.0, 0.5), 1e-6);
        assertEquals(1.0f, ExhaustionMath.refundTaken(2.0f, 1.0, Double.NaN), 1e-6, "NaN keeps all");
        assertEquals(1.0f, ExhaustionMath.refundTaken(2.0f, 1.0, 7.0), 1e-6, "a share above 1 counts as 1");
    }
}
