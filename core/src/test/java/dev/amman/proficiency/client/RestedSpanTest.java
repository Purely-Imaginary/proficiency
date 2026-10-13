package dev.amman.proficiency.client;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import org.junit.jupiter.api.Test;

/** The rested segment is eaten by incoming XP, never pushed along by the fill animation. */
class RestedSpanTest {

    private static final int W = 100;

    @Test
    void atRestTheSegmentStartsAtTheFill() {
        assertArrayEquals(new int[] {30, 70}, RestedSpan.of(30.30, 30.30, 0.40f, W));
    }

    @Test
    void theFarEndStaysFixedWhileTheFillAnimates() {
        // A gain took the real value from 30.30 to 30.50 and the pool's reach from 0.40 to 0.20.
        double real = 30.50;
        float reach = 0.20f;
        assertArrayEquals(new int[] {30, 70}, RestedSpan.of(30.30, real, reach, W));
        assertArrayEquals(new int[] {40, 70}, RestedSpan.of(30.40, real, reach, W));
        assertArrayEquals(new int[] {50, 70}, RestedSpan.of(30.50, real, reach, W));
    }

    @Test
    void theEndNeverPassesTheBar() {
        assertArrayEquals(new int[] {80, 100}, RestedSpan.of(30.80, 30.80, 0.90f, W));
    }

    @Test
    void anyPoolShowsAtLeastOnePixel() {
        assertArrayEquals(new int[] {50, 51}, RestedSpan.of(30.50, 30.50, 0.001f, W));
    }

    @Test
    void nothingWithoutAPool() {
        assertNull(RestedSpan.of(30.30, 30.30, 0f, W));
    }

    @Test
    void nothingWhileTheFillFinishesTheLevelBefore() {
        assertNull(RestedSpan.of(30.90, 31.10, 0.30f, W));
    }

    @Test
    void nothingOnceTheFillHasCaughtUpWithTheEnd() {
        assertNull(RestedSpan.of(30.70, 30.70, 0f, W));
        assertNull(RestedSpan.of(30.71, 30.50, 0.20f, W));
    }
}
