package dev.amman.proficiency.client;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class ReadyGlintTest {

    @Test
    void onlyCoolingToReadyFires() {
        ReadyGlint.Detector d = new ReadyGlint.Detector(3);
        assertFalse(d.observe(0, true, false), "ready at login: baseline only");
        assertFalse(d.observe(0, true, false), "still ready");
        assertFalse(d.observe(0, true, true), "ready to cooling is not a glint");
        assertFalse(d.observe(0, true, true), "still cooling");
        assertTrue(d.observe(0, true, false), "cooling to ready");
        assertFalse(d.observe(0, true, false), "fires once");
    }

    @Test
    void coolingAtLoginStillGlintsWhenItEnds() {
        ReadyGlint.Detector d = new ReadyGlint.Detector(1);
        assertFalse(d.observe(0, true, true));
        assertTrue(d.observe(0, true, false));
    }

    @Test
    void lockedAndUnlockingNeverFire() {
        ReadyGlint.Detector d = new ReadyGlint.Detector(1);
        assertFalse(d.observe(0, false, false));
        assertFalse(d.observe(0, true, false), "unlocking while idle");
        assertFalse(d.observe(0, true, true));
        assertFalse(d.observe(0, false, false), "locked again (reset)");
        assertFalse(d.observe(0, true, false), "a reset cooldown is not a finished one");
    }

    @Test
    void slotsAreIndependentAndClearForgets() {
        ReadyGlint.Detector d = new ReadyGlint.Detector(2);
        d.observe(0, true, true);
        d.observe(1, true, false);
        assertFalse(d.observe(1, true, false));
        assertTrue(d.observe(0, true, false));
        d.observe(1, true, true);
        d.clear();
        assertFalse(d.observe(1, true, false), "after clear the next reading is a baseline");
    }

    @Test
    void chimeGate() {
        ReadyGlint.Gate g = new ReadyGlint.Gate();
        assertTrue(g.allow(1000));
        assertFalse(g.allow(1100));
        assertFalse(g.allow(1000 + ReadyGlint.CHIME_GAP_MS - 1));
        assertTrue(g.allow(1000 + ReadyGlint.CHIME_GAP_MS));
        g.clear();
        assertTrue(g.allow(5));
    }

    @Test
    void curves() {
        assertEquals(-1f, ReadyGlint.sweep(0));
        assertEquals(0f, ReadyGlint.sweep(ReadyGlint.SWEEP_DELAY_MS), 1e-6);
        assertEquals(0.5f, ReadyGlint.sweep(ReadyGlint.SWEEP_DELAY_MS + ReadyGlint.SWEEP_MS / 2), 1e-4);
        assertEquals(-1f, ReadyGlint.sweep(ReadyGlint.SWEEP_DELAY_MS + ReadyGlint.SWEEP_MS));
        assertEquals(1f, ReadyGlint.sweepAlpha(ReadyGlint.SWEEP_DELAY_MS + ReadyGlint.SWEEP_MS / 2), 1e-4);
        assertEquals(1f, ReadyGlint.flash(0), 1e-6);
        assertEquals(0f, ReadyGlint.flash(ReadyGlint.GLINT_MS));
        assertTrue(ReadyGlint.running(0));
        assertFalse(ReadyGlint.running(ReadyGlint.GLINT_MS));
        assertFalse(ReadyGlint.running(-1));
    }
}
