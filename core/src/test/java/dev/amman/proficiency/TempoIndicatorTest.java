package dev.amman.proficiency;

import dev.amman.proficiency.client.TempoFlames;
import dev.amman.proficiency.skill.TempoSync;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TempoIndicatorTest {

    @Test
    void theFireGrowsWithTheChainAndIsHottestAtTheCap() {
        assertEquals(0, TempoFlames.heat(0, 25));
        assertEquals(1, TempoFlames.heat(1, 25));
        assertEquals(1, TempoFlames.heat(5, 25));
        assertEquals(2, TempoFlames.heat(6, 25));
        assertEquals(5, TempoFlames.heat(25, 25));
        assertEquals(5, TempoFlames.heat(99, 25));
        assertEquals(0, TempoFlames.heat(3, 0));
    }

    @Test
    void aLapsedWindowSinksToSmokeAndGoes() {
        assertEquals(0f, TempoFlames.lapse(0, 60 * 50), 0f);
        assertEquals(0f, TempoFlames.lapse(3000, 3000), 0f);
        assertEquals(0.5f, TempoFlames.lapse(3000 + TempoFlames.DIE_MS / 2, 3000), 0.001f);
        assertEquals(1f, TempoFlames.lapse(3000 + TempoFlames.DIE_MS, 3000), 0f);
        assertEquals(1f, TempoFlames.lapse(99999, 3000), 0f);
        // the flames sink one heat at a time, then only smoke is left
        assertEquals(5, TempoFlames.shownHeat(5, 0f));
        assertEquals(5, TempoFlames.shownHeat(5, 0.01f));
        assertEquals(3, TempoFlames.shownHeat(5, 0.15f));
        assertEquals(0, TempoFlames.shownHeat(5, TempoFlames.SINK));
        assertEquals(0f, TempoFlames.smoke(0f), 0f);
        assertEquals(1f, TempoFlames.smoke(0.3f), 0f);
        assertTrue(TempoFlames.smoke(0.95f) < 0.3f);
        assertEquals(0f, TempoFlames.smoke(1f), 0f);
        assertEquals(1f, TempoFlames.numberFade(0f), 0f);
        assertEquals(0f, TempoFlames.numberFade(TempoFlames.SINK), 0f);
    }

    @Test
    void flareDecays() {
        assertEquals(1f, TempoFlames.flare(0), 0f);
        assertTrue(TempoFlames.flare(250) < 0.3f);
        assertEquals(0f, TempoFlames.flare(TempoFlames.FLARE_MS), 0f);
        assertEquals(0f, TempoFlames.flare(-5), 0f);
    }

    @Test
    void theAnimationRunsOnTheClockNotTheFrameRate() {
        assertEquals(0, TempoFlames.frame(0));
        assertEquals(1, TempoFlames.frame(TempoFlames.FRAME_MS));
        assertEquals(0, TempoFlames.frame(TempoFlames.FRAME_MS * TempoFlames.FRAMES));
        // the label's rise: the same place after 100 ms in one step or in many small ones
        float big = TempoFlames.ease(0f, 5f, 100);
        float small = 0f;
        for (int i = 0; i < 10; i++) {
            small = TempoFlames.ease(small, 5f, 10);
        }
        assertEquals(big, small, 0.05f);
        assertEquals(5f, TempoFlames.ease(0f, 5f, 10_000), 0f);
    }

    @Test
    void smallFontCoversTheMultiplier() {
        for (char c : TempoFlames.text(1234).toCharArray()) {
            assertTrue(TempoFlames.glyph(c) >= 0);
        }
        assertEquals(4 + 4 + 2 + 4 + 3, TempoFlames.width("x1.10"));
    }

    @Test
    void multiplierText() {
        assertEquals("x1.30", TempoFlames.text(1300));
        assertEquals("x1.02", TempoFlames.text(1020));
        assertEquals("x1.50", TempoFlames.text(1500));
        assertEquals("x2.00", TempoFlames.text(2000));
    }

    @Test
    void syncsOnChangeNotEveryTick() {
        // first ever
        assertTrue(TempoSync.shouldSend(-1, 0, 0, 3, 1, 100, 60));
        // same skill, same steps, next tick: nothing
        assertFalse(TempoSync.shouldSend(3, 7, 100, 3, 7, 101, 60));
        assertFalse(TempoSync.shouldSend(3, 7, 100, 3, 7, 109, 60));
        // step moved
        assertTrue(TempoSync.shouldSend(3, 7, 100, 3, 8, 101, 60));
        // chain broken back to 0
        assertTrue(TempoSync.shouldSend(3, 7, 100, 3, 0, 300, 60));
        // another skill took over
        assertTrue(TempoSync.shouldSend(3, 7, 100, 4, 7, 101, 60));
        // capped chain: heartbeat after a sixth of the window
        assertTrue(TempoSync.shouldSend(3, 25, 100, 3, 25, 110, 60));
        // a clock that went backwards
        assertTrue(TempoSync.shouldSend(3, 7, 500, 3, 7, 100, 60));
    }

    @Test
    void aChainBreaksAndRestarts() {
        dev.amman.proficiency.skill.TempoChain chain = new dev.amman.proficiency.skill.TempoChain();
        for (int i = 0; i < 4; i++) {
            chain.advance(100 + i * 10, 60, 25, 1200);
        }
        assertEquals(3, chain.steps); // the first action of a cold chain is step 0
        chain.advance(400, 60, 25, 1200); // lapsed: restarts at 0
        assertEquals(0, chain.steps);
        chain.advance(410, 60, 25, 1200);
        assertEquals(1, chain.steps);
        chain.advance(100, 60, 25, 1200); // clock went backwards
        assertEquals(0, chain.steps);
        for (int i = 0; i < 40; i++) {
            chain.advance(1000 + i, 60, 25, 1200);
        }
        assertEquals(25, chain.steps);
    }

    @Test
    void twoSkillsInOneActionSendOncePerChangeNotTwice() {
        dev.amman.proficiency.skill.TempoChain a = new dev.amman.proficiency.skill.TempoChain();
        dev.amman.proficiency.skill.TempoChain b = new dev.amman.proficiency.skill.TempoChain();
        for (int tick = 100; tick < 110; tick++) {
            a.advance(tick, 60, 25, 1200);
            b.advance(tick, 60, 25, 1200);
            assertTrue(a.takeAnnouncement(tick, 60));
            assertTrue(b.takeAnnouncement(tick, 60));
        }
        // the same step again (a held, capped chain) is silent until the heartbeat
        a.advance(110, 60, 10, 1200);
        a.advance(111, 60, 10, 1200);
        assertTrue(a.takeAnnouncement(111, 60));
        a.advance(112, 60, 10, 1200);
        assertFalse(a.takeAnnouncement(112, 60));
        assertTrue(a.takeAnnouncement(112 + 10, 60));
    }

    @Test
    void theClientPulsesOnTheFirstStepAfterALapseAndKeepsSkillsApart() {
        dev.amman.proficiency.client.TempoState state = new dev.amman.proficiency.client.TempoState(4);
        state.accept(1, 1, 25, 1020, 3000, 1000);
        assertTrue(state.flare(1, 1000) > 0.9f);
        state.accept(1, 1, 25, 1020, 3000, 1100); // heartbeat, same step
        assertEquals(0f, state.flare(1, 1000 + 600), 0f);
        // another skill does not disturb it
        state.accept(2, 3, 25, 1060, 3000, 1200);
        assertTrue(state.alpha(1, 1200) > 0f);
        assertTrue(state.alpha(2, 1200) > 0f);
        assertEquals(0f, state.alpha(0, 1200), 0f);
        // lapsed: still dying down (smoke) for a moment, then gone
        assertEquals(0f, state.alpha(1, 1100 + 3000 + 700), 0f);
        assertTrue(state.visible(1, 1100 + 3000 + 700));
        assertFalse(state.visible(1, 1100 + 3000 + TempoFlames.DIE_MS + 1));
        // a step while it is still smoking flares again
        state.accept(1, 1, 25, 1020, 3000, 1100 + 3000 + 700);
        assertTrue(state.flare(1, 1100 + 3000 + 700) > 0.9f);
        long later = 9000;
        state.accept(1, 1, 25, 1020, 3000, later);
        assertTrue(state.flare(1, later) > 0.9f);
        assertTrue(state.alpha(1, later) > 0f);
        state.clear();
        assertEquals(0f, state.alpha(1, later), 0f);
    }
}
