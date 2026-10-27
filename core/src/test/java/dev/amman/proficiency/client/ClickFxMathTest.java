package dev.amman.proficiency.client;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class ClickFxMathTest {

    @Test
    void wiggleSwingsBothWaysAndDiesOut() {
        int flips = 0;
        float last = 0f;
        float max = 0f;
        for (long t = 1; t < ClickFxMath.NO_MS; t++) {
            float v = ClickFxMath.wiggle(t, 3f);
            max = Math.max(max, Math.abs(v));
            if (v * last < 0) {
                flips++;
            }
            if (v != 0f) {
                last = v;
            }
        }
        assertTrue(flips >= 3, "a left/right dance, not a nudge: " + flips);
        assertTrue(max <= 3f && max > 1.5f);
        assertEquals(0f, ClickFxMath.wiggle(0, 3f), 1e-6);
        assertEquals(0f, ClickFxMath.wiggle(ClickFxMath.NO_MS, 3f));
        assertEquals(0f, ClickFxMath.wiggle(-5, 3f));
        assertTrue(Math.abs(ClickFxMath.wiggle(235, 3f)) < 0.2f, "damped by the end");
    }

    @Test
    void shakeRisesWithLevel() {
        float lo = 0f;
        float hi = 0f;
        for (long t = 0; t < 400; t++) {
            lo = Math.max(lo, Math.abs(ClickFxMath.shakeX(t, 0f)));
            hi = Math.max(hi, Math.abs(ClickFxMath.shakeX(t, 1f)));
        }
        assertTrue(hi > lo * 2.5f, lo + " vs " + hi);
        assertTrue(hi <= 2.7f);
    }

    @Test
    void chargePitchRisesAndStaysSane() {
        assertTrue(ClickFxMath.chargePitch(0f) < ClickFxMath.chargePitch(0.5f));
        assertTrue(ClickFxMath.chargePitch(0.5f) < ClickFxMath.chargePitch(1f));
        assertEquals(ClickFxMath.chargePitch(1f), ClickFxMath.chargePitch(9f));
        assertEquals(0f, ClickFxMath.chargeLevel(-1));
        assertEquals(1f, ClickFxMath.chargeLevel(ClickFxMath.PENDING_MS * 3));
    }

    @Test
    void popOvershootsThenSettles() {
        float peak = 0f;
        float trough = 9f;
        for (long t = 0; t < ClickFxMath.POP_MS; t++) {
            float s = ClickFxMath.pop(t, 0.2f);
            peak = Math.max(peak, s);
            trough = Math.min(trough, s);
        }
        assertTrue(peak > 1.18f && peak < 1.22f, "overshoot " + peak);
        assertTrue(trough < 1f, "a little undershoot");
        assertEquals(1f, ClickFxMath.pop(ClickFxMath.POP_MS, 0.2f));
        assertEquals(1f, ClickFxMath.pop(0, 0.2f), 1e-5);
    }

    @Test
    void sparksStayInsideTheirRadiusPlusFallAndFade() {
        for (int seed = 0; seed < 6; seed++) {
            for (int i = 0; i < ClickFxMath.MAX_BURST; i++) {
                for (long t = 0; t < ClickFxMath.BURST_MS; t += 40) {
                    float x = ClickFxMath.burstX(i, seed, t, 30f);
                    float y = ClickFxMath.burstY(i, seed, t, 30f);
                    assertTrue(Math.abs(x) <= 30.01f);
                    assertTrue(y <= 30f + 30f * 0.35f * 0.000045f * 720 * 720 + 0.01f);
                }
                assertEquals(0f, ClickFxMath.sparkAlpha(i, seed, ClickFxMath.BURST_MS + 1));
                assertEquals(1f, ClickFxMath.sparkAlpha(i, seed, 0));
                assertTrue(ClickFxMath.sparkLife(i, seed) <= ClickFxMath.BURST_MS);
            }
        }
    }

    @Test
    void burstsAreDeterministicAndBounded() {
        assertEquals(ClickFxMath.burstX(3, 9, 200, 24f), ClickFxMath.burstX(3, 9, 200, 24f));
        assertTrue(ClickFxMath.sparkCount(2) <= ClickFxMath.MAX_BURST);
        assertTrue(ClickFxMath.sparkCount(0) < ClickFxMath.sparkCount(1));
        assertTrue(ClickFxMath.sparkCount(1) < ClickFxMath.sparkCount(2));
    }

    @Test
    void ringFlashEdgeEnvelopes() {
        assertEquals(5f, ClickFxMath.ringRadius(0, 5f, 30f), 1e-4);
        assertEquals(30f, ClickFxMath.ringRadius(ClickFxMath.RING_MS, 5f, 30f), 1e-4);
        assertEquals(1f, ClickFxMath.flash(0), 1e-4);
        assertEquals(0f, ClickFxMath.flash(ClickFxMath.FLASH_MS));
        assertEquals(0f, ClickFxMath.edge(0));
        assertEquals(1f, ClickFxMath.edge(90), 1e-4);
        assertEquals(0f, ClickFxMath.edge(ClickFxMath.EDGE_MS));
        assertTrue(ClickFxMath.red(10) > 0.4f);
        assertEquals(0f, ClickFxMath.red(ClickFxMath.RED_MS));
    }

    @Test
    void rippleDelayGrowsWithDistanceAndCaps() {
        assertTrue(ClickFxMath.rippleDelay(10) < ClickFxMath.rippleDelay(100));
        assertEquals(420L, ClickFxMath.rippleDelay(5000));
    }

    @Test
    void chimeRisesWithFillAndTier() {
        assertTrue(ClickFxMath.chimePitch(0.2f, 0) < ClickFxMath.chimePitch(1f, 0));
        assertTrue(ClickFxMath.chimePitch(1f, 0) < ClickFxMath.chimePitch(1f, 2));
        assertTrue(ClickFxMath.chimePitch(1f, 2) <= 2.0f);
    }
}
