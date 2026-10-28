package dev.amman.proficiency.look;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class LookPacingTest {

    @Test
    void aBurstIsAnsweredAtOnce() {
        LookPacing.Bucket b = new LookPacing.Bucket();
        for (int i = 0; i < 6; i++) {
            assertTrue(b.take(100), "question " + i);
        }
        assertFalse(b.take(100));
    }

    @Test
    void aSweepOfOneQuestionPerTickIsNeverThrottled() {
        LookPacing.Bucket b = new LookPacing.Bucket();
        for (int t = 0; t < 11; t++) {
            assertTrue(b.take(t), "tick " + t);
        }
    }

    @Test
    void aSustainedFloodIsCappedAtTenPerSecond() {
        LookPacing.Bucket b = new LookPacing.Bucket();
        int answered = 0;
        for (int t = 0; t < 200; t++) {
            for (int k = 0; k < 5; k++) {
                if (b.take(t)) {
                    answered++;
                }
            }
        }
        assertTrue(answered <= 6 + 100 + 1, "answered " + answered);
        assertTrue(answered >= 100, "answered " + answered);
    }

    @Test
    void aThrottledAskIsServedAfterTwoTicks() {
        LookPacing.Bucket b = new LookPacing.Bucket();
        for (int i = 0; i < 6; i++) {
            b.take(0);
        }
        assertFalse(b.take(0));
        assertTrue(b.take(2));
    }

    @Test
    void aClockThatGoesBackDoesNotFreezeTheBucket() {
        LookPacing.Bucket b = new LookPacing.Bucket();
        b.take(1000);
        assertTrue(b.take(5));
    }

    @Test
    void aNewTargetIsAskedWithinOneTick() {
        assertFalse(LookPacing.shouldAsk(true, false, false, 10, Long.MIN_VALUE, 10, true));
        assertTrue(LookPacing.shouldAsk(true, false, false, 11, Long.MIN_VALUE, 10, true));
        assertTrue(LookPacing.shouldAsk(true, false, false, 5, Long.MIN_VALUE, Long.MIN_VALUE, false));
    }

    @Test
    void refreshAndQuietTimings() {
        assertFalse(LookPacing.shouldAsk(false, true, false, 109, 100, 100, false));
        assertTrue(LookPacing.shouldAsk(false, true, false, 110, 100, 100, false));
        assertFalse(LookPacing.shouldAsk(false, true, true, 119, 100, 100, false));
        assertTrue(LookPacing.shouldAsk(false, true, true, 120, 100, 100, false));
        assertFalse(LookPacing.shouldAsk(false, true, false, 130, 100, 100, true));
    }

    @Test
    void aRetryIsAskedAgainTheNextTick() {
        assertFalse(LookPacing.shouldAsk(false, false, false, 20, Long.MIN_VALUE, 20, false));
        assertTrue(LookPacing.shouldAsk(false, false, false, 21, Long.MIN_VALUE, 20, false));
        assertEquals("retry", LookPacing.RETRY);
    }
}
