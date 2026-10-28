package dev.amman.proficiency.client;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class UnlockSlamMathTest {

    @Test
    void phasesRunInOrderAndEndInsideTheBudget() {
        UnlockSlamMath.Phase last = UnlockSlamMath.Phase.FLARE;
        for (long t = 0; t <= UnlockSlamMath.endAt(1) + 10; t += 5) {
            UnlockSlamMath.Phase p = UnlockSlamMath.phase(t, 1);
            assertTrue(p.ordinal() >= last.ordinal(), "phases never go back at " + t);
            last = p;
        }
        assertEquals(UnlockSlamMath.Phase.DONE, last);
        // The card is at rest by about 1.6 s; the glow fades out by about 2 s.
        long rest = UnlockSlamMath.impactAt(0) + UnlockSlamMath.SETTLE_MS;
        assertTrue(rest >= 1500 && rest <= 1800, "at rest at " + rest);
        assertTrue(UnlockSlamMath.endAt(1) <= 2100, "ends at " + UnlockSlamMath.endAt(1));
        assertTrue(UnlockSlamMath.endAt(2) - UnlockSlamMath.endAt(1) == UnlockSlamMath.SECOND_DELAY_MS);
        assertTrue(UnlockSlamMath.TURN_AT > UnlockSlamMath.RISE_AT && UnlockSlamMath.TURN_AT < UnlockSlamMath.SLAM_AT,
                "the page turns under the flying socket");
    }

    @Test
    void riseEasesOutAndDimFollowsIt() {
        assertEquals(0f, UnlockSlamMath.rise(0), 1e-6);
        assertEquals(1f, UnlockSlamMath.rise(UnlockSlamMath.CHARGE_AT), 1e-6);
        float half = UnlockSlamMath.rise(UnlockSlamMath.RISE_AT + UnlockSlamMath.RISE_MS / 2);
        assertTrue(half > 0.8f, "ease out: most of the lift early, " + half);
        assertEquals(UnlockSlamMath.DIM, UnlockSlamMath.dim(UnlockSlamMath.CHARGE_AT + 100, 1), 1e-6);
        assertEquals(0f, UnlockSlamMath.dim(UnlockSlamMath.impactAt(0), 1), 1e-6);
        assertTrue(UnlockSlamMath.dim(UnlockSlamMath.impactAt(0), 2) > 0f, "the second card still falls through the dark");
    }

    @Test
    void materialsLandInsideTheChargeInOrder() {
        for (int n = 1; n <= UnlockSlamMath.MAX_ICONS; n++) {
            long prev = Long.MIN_VALUE;
            for (int i = 0; i < n; i++) {
                long at = UnlockSlamMath.arrivalAt(i, n);
                assertTrue(at > prev);
                assertTrue(at > UnlockSlamMath.CHARGE_AT && at <= UnlockSlamMath.CHARGE_AT + UnlockSlamMath.CHARGE_MS);
                assertTrue(UnlockSlamMath.launchAt(i, n) >= 0, "never launched before the slam began");
                assertEquals(0f, UnlockSlamMath.flight(UnlockSlamMath.launchAt(i, n), i, n), 1e-6);
                assertEquals(1f, UnlockSlamMath.flight(at, i, n), 1e-6);
                prev = at;
            }
            assertEquals(UnlockSlamMath.CHARGE_AT + UnlockSlamMath.CHARGE_MS, UnlockSlamMath.arrivalAt(n - 1, n),
                    "the last lands at the end of the charge");
            assertEquals(n, UnlockSlamMath.landed(UnlockSlamMath.SLAM_AT, n));
        }
    }

    @Test
    void eachLandingAdvancesTheMorph() {
        int n = 5;
        float before = UnlockSlamMath.morph(UnlockSlamMath.arrivalAt(2, n) - 1, n);
        float after = UnlockSlamMath.morph(UnlockSlamMath.arrivalAt(2, n) + UnlockSlamMath.STEP_MS, n);
        assertTrue(after - before > 0.1f, "a step per landing: " + before + " -> " + after);
        assertEquals(0f, UnlockSlamMath.morph(UnlockSlamMath.CHARGE_AT, n), 1e-6);
        assertEquals(1f, UnlockSlamMath.morph(UnlockSlamMath.SLAM_AT, n), 1e-3);
        assertEquals(1f, UnlockSlamMath.morph(UnlockSlamMath.SLAM_AT, 0), 1e-3);
        float last = -1f;
        for (long t = 0; t < UnlockSlamMath.SLAM_AT; t += 3) {
            float m = UnlockSlamMath.morph(t, n);
            assertTrue(m >= last - 1e-6, "the morph never runs back");
            last = m;
        }
        assertEquals(1f, UnlockSlamMath.pulse(UnlockSlamMath.arrivalAt(0, n), n), 1e-6);
        assertTrue(UnlockSlamMath.fullFlash(UnlockSlamMath.arrivalAt(n - 1, n), n) == 1f);
        assertEquals(0f, UnlockSlamMath.fullFlash(UnlockSlamMath.arrivalAt(n - 2, n), n), 1e-6);
        assertTrue(UnlockSlamMath.tickPitch(4, 5) > UnlockSlamMath.tickPitch(0, 5), "rising pitch");
    }

    @Test
    void iconsAreCappedAndFair() {
        assertArrayEquals(new int[] {4, 2}, UnlockSlamMath.icons(new int[] {4, 2}));
        int[] big = UnlockSlamMath.icons(new int[] {32, 16});
        assertEquals(UnlockSlamMath.MAX_ICONS, big[0] + big[1]);
        assertEquals(8, big[0]);
        assertEquals(4, big[1]);
        int[] many = UnlockSlamMath.icons(new int[] {64, 1, 1});
        assertEquals(UnlockSlamMath.MAX_ICONS, many[0] + many[1] + many[2]);
        assertEquals(1, many[1], "a small pile still shows");
        int[] fifteen = UnlockSlamMath.icons(new int[] {1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1});
        int sum = 0;
        for (int v : fifteen) {
            sum += v;
        }
        assertEquals(UnlockSlamMath.MAX_ICONS, sum);
        assertArrayEquals(new int[0], UnlockSlamMath.icons(new int[0]));
    }

    @Test
    void flightsStartOffScreenAndEndOnTheSocket() {
        int w = 480;
        int h = 270;
        for (int i = 0; i < UnlockSlamMath.MAX_ICONS; i++) {
            float sx = UnlockSlamMath.startX(i, w);
            float sy = UnlockSlamMath.startY(i, h);
            assertTrue(sx < 0 || sx > w || sy < 0 || sy > h, "icon " + i + " starts off the screen");
            assertEquals(sx, UnlockSlamMath.curve(i, 0f, sx, sy, 200, 100, false), 1e-3);
            assertEquals(sy, UnlockSlamMath.curve(i, 0f, sx, sy, 200, 100, true), 1e-3);
            assertEquals(200f, UnlockSlamMath.curve(i, 1f, sx, sy, 200, 100, false), 1e-3);
            assertEquals(100f, UnlockSlamMath.curve(i, 1f, sx, sy, 200, 100, true), 1e-3);
        }
        // Curved, not straight: the midpoint sits off the line.
        float mx = UnlockSlamMath.curve(0, 0.5f, -18, 100, 200, 100, false);
        float my = UnlockSlamMath.curve(0, 0.5f, -18, 100, 200, 100, true);
        assertTrue(Math.abs(my - 100) > 15, "arcs: " + mx + "," + my);
    }

    @Test
    void slamSquashesOvershootsAndSettles() {
        assertEquals(UnlockSlamMath.DROP_SCALE, UnlockSlamMath.impactScale(UnlockSlamMath.SLAM_AT, 0), 1e-5);
        assertEquals(0f, UnlockSlamMath.drop(UnlockSlamMath.SLAM_AT, 0), 1e-6);
        assertEquals(1f, UnlockSlamMath.drop(UnlockSlamMath.impactAt(0), 0), 1e-6);
        float half = UnlockSlamMath.drop(UnlockSlamMath.SLAM_AT + UnlockSlamMath.DROP_MS / 2, 0);
        assertTrue(half < 0.4f, "accelerates into the slot: " + half);
        assertTrue(UnlockSlamMath.impactScale(UnlockSlamMath.impactAt(0), 0) < 0.95f, "squash");
        float max = 0f;
        for (long dt = 30; dt < UnlockSlamMath.SETTLE_MS; dt++) {
            max = Math.max(max, UnlockSlamMath.impactScale(UnlockSlamMath.impactAt(0) + dt, 0));
        }
        assertTrue(max > 1.01f && max < 1.06f, "overshoot " + max);
        assertEquals(1f, UnlockSlamMath.impactScale(UnlockSlamMath.impactAt(0) + UnlockSlamMath.SETTLE_MS, 0), 1e-6);
        assertEquals(0f, UnlockSlamMath.drop(UnlockSlamMath.SLAM_AT + UnlockSlamMath.SECOND_DELAY_MS, 1), 1e-6,
                "the second card leaves later");
        assertTrue(UnlockSlamMath.drop(UnlockSlamMath.impactAt(0), 1) < 0.05f, "and is still up when the first lands");
        assertTrue(UnlockSlamMath.jolt(UnlockSlamMath.impactAt(0) + 25, 0) > 2f);
        assertEquals(0f, UnlockSlamMath.jolt(UnlockSlamMath.impactAt(0) - 1, 0), 1e-6);
        assertEquals(1f, UnlockSlamMath.glow(UnlockSlamMath.impactAt(0), 0), 1e-6);
        assertEquals(0f, UnlockSlamMath.glow(UnlockSlamMath.endAt(1), 0), 1e-6);
    }

    @Test
    void theSecondCardPeelsBeforeTheSlam() {
        assertEquals(0f, UnlockSlamMath.peel(UnlockSlamMath.CHARGE_AT + UnlockSlamMath.CHARGE_MS), 1e-6);
        assertTrue(UnlockSlamMath.CHARGE_AT + UnlockSlamMath.CHARGE_MS + UnlockSlamMath.PEEL_MS
                <= UnlockSlamMath.SLAM_AT + UnlockSlamMath.SECOND_DELAY_MS, "out from under before it drops");
        assertEquals(1f, UnlockSlamMath.peel(UnlockSlamMath.SLAM_AT + UnlockSlamMath.SECOND_DELAY_MS), 1e-6);
    }

    @Test
    void dustIsThrownOutAndFades() {
        for (int k = 0; k < UnlockSlamMath.DUST; k++) {
            float a = Math.abs(UnlockSlamMath.dustX(k, 50, 40));
            float b = Math.abs(UnlockSlamMath.dustX(k, 300, 40));
            assertTrue(b > a, "moves outwards");
            assertTrue(UnlockSlamMath.dustY(k, 60) < 0, "kicked up first");
            assertTrue(UnlockSlamMath.dustAlpha(k, 0) == 1f);
            assertEquals(0f, UnlockSlamMath.dustAlpha(k, UnlockSlamMath.DUST_MS), 1e-6);
        }
    }
}
