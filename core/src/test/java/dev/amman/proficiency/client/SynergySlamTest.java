package dev.amman.proficiency.client;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import org.junit.jupiter.api.Test;

class SynergySlamTest {

    @Test
    void iconsAccelerateIntoTheImpactAndStayThere() {
        assertEquals(0f, SynergySlamMath.approach(0), 1e-6);
        assertEquals(1f, SynergySlamMath.approach(SynergySlamMath.IMPACT_MS), 1e-6);
        assertEquals(1f, SynergySlamMath.approach(SynergySlamMath.TOTAL_MS), 1e-6);
        float prevStep = 0f;
        float prev = 0f;
        for (long t = 30; t <= SynergySlamMath.IMPACT_MS; t += 30) {
            float a = SynergySlamMath.approach(t);
            assertTrue(a - prev >= prevStep - 1e-6, "each step is at least as long as the last: it speeds up");
            prevStep = a - prev;
            prev = a;
        }
    }

    @Test
    void shakeIsThreePixelsAtMostAndOnlyInItsWindow() {
        int peak = 0;
        for (long t = -50; t < SynergySlamMath.TOTAL_MS; t++) {
            int x = SynergySlamMath.shakeX(t, SynergySlamMath.SHAKE_PX);
            int y = SynergySlamMath.shakeY(t, SynergySlamMath.SHAKE_PX);
            peak = Math.max(peak, Math.max(Math.abs(x), Math.abs(y)));
            if (t < SynergySlamMath.IMPACT_MS || t >= SynergySlamMath.IMPACT_MS + SynergySlamMath.SHAKE_MS) {
                assertEquals(0, x);
                assertEquals(0, y);
            }
        }
        assertTrue(peak >= 2 && peak <= 3, "peak " + peak);
    }

    @Test
    void chipPopsThenSettlesThenShrinksOnItsFlight() {
        assertEquals(0f, SynergySlamMath.chipScale(SynergySlamMath.IMPACT_MS - 1), 1e-6);
        float top = 0f;
        for (long t = SynergySlamMath.IMPACT_MS; t < SynergySlamMath.FLY_START; t++) {
            top = Math.max(top, SynergySlamMath.chipScale(t));
        }
        assertEquals(1.3f, top, 0.02f);
        assertEquals(1f, SynergySlamMath.chipScale(SynergySlamMath.FLY_START - 1), 1e-3);
        assertEquals(0.55f, SynergySlamMath.chipScale(SynergySlamMath.TOTAL_MS), 1e-3);
        assertEquals(0f, SynergySlamMath.flight(SynergySlamMath.FLY_START), 1e-6);
        assertEquals(1f, SynergySlamMath.flight(SynergySlamMath.TOTAL_MS), 1e-6);
    }

    @Test
    void flashAndPlateFollowTheirWindows() {
        assertEquals(0f, SynergySlamMath.flash(SynergySlamMath.IMPACT_MS - 1), 0f);
        assertEquals(1f, SynergySlamMath.flash(SynergySlamMath.IMPACT_MS), 1e-6);
        assertEquals(0f, SynergySlamMath.flash(SynergySlamMath.IMPACT_MS + SynergySlamMath.FLASH_MS), 0f);
        assertEquals(0f, SynergySlamMath.plateAlpha(SynergySlamMath.PLATE_IN_START), 1e-6);
        assertEquals(1f, SynergySlamMath.plateAlpha(1000), 1e-6);
        assertEquals(0f, SynergySlamMath.plateAlpha(SynergySlamMath.PLATE_OUT_START + SynergySlamMath.PLATE_OUT_MS), 1e-6);
        assertEquals(1f, SynergySlamMath.plateSettle(1000), 1e-6);
        assertEquals(1f, SynergySlamMath.iconAlpha(100), 0f);
        assertEquals(0f, SynergySlamMath.iconAlpha(SynergySlamMath.IMPACT_MS + 200), 1e-6);
    }

    @Test
    void totalLengthIsAboutOnePointEightSeconds() {
        assertEquals(1800, SynergySlamMath.TOTAL_MS);
        assertFalse(SynergySlamMath.done(1799));
        assertTrue(SynergySlamMath.done(1800));
    }

    @Test
    void sidesAreDistinctPerIcon() {
        assertEquals(-1, SynergySlamMath.fromX(0, 2));
        assertEquals(1, SynergySlamMath.fromX(1, 2));
        assertEquals(0, SynergySlamMath.fromY(1, 2));
        // The third icon of three drops in from above.
        assertEquals(0, SynergySlamMath.fromX(2, 3));
        assertEquals(-1, SynergySlamMath.fromY(2, 3));
    }

    @Test
    void queuePlaysOneAtATimeInOrder() {
        SynergySlamQueue<String> q = new SynergySlamQueue<>();
        assertTrue(q.idle());
        assertTrue(q.add("a"));
        assertTrue(q.add("b"));
        assertNull(q.tick(1000));
        assertEquals("a", q.current());
        assertEquals(1, q.waiting());
        // One waiting: the clock runs at 1.35x.
        assertNull(q.tick(1000 + 600));
        assertEquals(810, q.timeline());
        // Finishes after 1800 / 1.35 ms, and the next starts on the same call.
        assertEquals("a", q.tick(1000 + 1334));
        assertEquals("b", q.current());
        assertEquals(0, q.timeline());
        // Now alone, the clock runs at 1x.
        assertNull(q.tick(2334 + 900));
        assertEquals(900, q.timeline());
        assertEquals("b", q.tick(2334 + 1800));
        assertTrue(q.idle());
    }

    @Test
    void queueRefusesDuplicatesAndStaysBounded() {
        SynergySlamQueue<String> q = new SynergySlamQueue<>();
        assertTrue(q.add("a"));
        assertFalse(q.add("a"));
        q.tick(0);
        assertFalse(q.add("a"), "already playing");
        for (int i = 0; i < 20; i++) {
            q.add("x" + i);
        }
        assertEquals(SynergySlamQueue.MAX_WAITING, q.waiting());
        q.clear();
        assertTrue(q.idle());
    }

    @Test
    void aBurstPlaysFasterButNeverBelowHalfTime() {
        assertEquals(1f, SynergySlamMath.speed(0));
        assertTrue(SynergySlamMath.speed(1) > 1f);
        assertTrue(SynergySlamMath.speed(5) <= 2f);
        // Five discoveries at once: the whole burst stays under five seconds.
        SynergySlamQueue<Integer> q = new SynergySlamQueue<>();
        for (int i = 0; i < 5; i++) {
            q.add(i);
        }
        long now = 0;
        int finished = 0;
        while (!q.idle() && now < 20000) {
            if (q.tick(now) != null) {
                finished++;
            }
            now += 10;
        }
        assertEquals(5, finished);
        assertTrue(now < 6500, "burst took " + now);
    }

    @Test
    void tallyTicksHoldsAndResets() {
        SynergyTally tally = new SynergyTally();
        assertEquals(0, tally.count(0));
        tally.land(1000);
        assertEquals(1, tally.count(1001));
        assertEquals(0f, tally.alpha(1000), 1e-6);
        assertEquals(1f, tally.alpha(1500), 1e-6);
        assertTrue(tally.pop(1000) > 0.99f);
        assertEquals(0f, tally.pop(1000 + SynergyTally.POP_MS), 0f);
        tally.land(2000);
        assertEquals(2, tally.count(2001));
        assertEquals(1f, tally.alpha(2000), 1e-6, "a second landing only pops, it does not fade in again");
        // Gone after the hold and fade; the next landing starts from one.
        long gone = 2000 + SynergyTally.HOLD_MS + SynergyTally.FADE_OUT_MS;
        assertEquals(0, tally.count(gone));
        tally.land(gone + 10);
        assertEquals(1, tally.count(gone + 11));
    }

    @Test
    void watchKeepsTheFirstLookAsABaselineAndReportsOnlyNewcomers() {
        SynergyWatch watch = new SynergyWatch();
        assertTrue(watch.observe(List.of("a", "b")).isEmpty(), "the join sync is old news");
        assertTrue(watch.observe(List.of("a", "b")).isEmpty());
        assertEquals(List.of("c", "d"), watch.observe(List.of("a", "c", "b", "d")));
        // One fades, then wakes again: it is a discovery again.
        assertTrue(watch.observe(List.of("a", "b", "d")).isEmpty());
        assertEquals(List.of("c"), watch.observe(List.of("a", "b", "c", "d")));
        watch.reset();
        assertTrue(watch.observe(List.of("a", "b", "c", "d", "e")).isEmpty());
    }
}
