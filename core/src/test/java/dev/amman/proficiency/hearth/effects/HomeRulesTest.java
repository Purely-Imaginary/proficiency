package dev.amman.proficiency.hearth.effects;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Edges, the real-sleep rule and the HUD's send-on-change rule. */
class HomeRulesTest {

    @Test
    void edgesNameWhatTheSecondCrossed() {
        assertEquals(HomeRules.Edge.NONE, HomeRules.edge(true, true, true, true));
        assertEquals(HomeRules.Edge.ENTER, HomeRules.edge(false, true, false, false));
        assertEquals(HomeRules.Edge.LEAVE, HomeRules.edge(true, false, false, true));
        assertEquals(HomeRules.Edge.SET_OUT, HomeRules.edge(true, false, true, true));
        // The Hearth went out under the player: no cue, no Provisioned.
        assertEquals(HomeRules.Edge.NONE, HomeRules.edge(true, false, true, false));
    }

    @Test
    void onlyARealNightsSleepCounts() {
        assertTrue(HomeRules.realSleep(true, true, Long.MIN_VALUE, 1000));
        // Clicked a bed and got up: the timer never reached 100.
        assertFalse(HomeRules.realSleep(false, true, 900, 1000));
        // Woken at dawn: day now, but seen asleep at night a moment ago.
        assertTrue(HomeRules.realSleep(true, false, 980, 1000));
        // A day nap a mod allowed: never seen asleep at night.
        assertFalse(HomeRules.realSleep(true, false, Long.MIN_VALUE, 1000));
        assertFalse(HomeRules.realSleep(true, false, 1000 - HomeRules.NIGHT_SLEEP_GRACE_TICKS - 1, 1000));
    }

    @Test
    void theHudGoesOutOnlyWhenTheClientCannotPredictIt() {
        HomeRules.Hud rising = new HomeRules.Hud(true, 5L, 0.2f, 1f / 60, 0f, 0L, 0L, 0f, 0, 1000);
        assertTrue(HomeRules.needsSend(null, rising, 1000));
        // Twenty seconds later the client's line is right: nothing to send.
        HomeRules.Hud later = new HomeRules.Hud(true, 5L, 0.2f + 20f / 60, 1f / 60, 0f, 0L, 0L, 0f, 0, 1400);
        assertFalse(HomeRules.needsSend(rising, later, 1400));
        // Combat stopped the rise: a new rate.
        HomeRules.Hud held = new HomeRules.Hud(true, 5L, 0.53f, 0f, 0f, 0L, 0L, 0f, 0, 1400);
        assertTrue(HomeRules.needsSend(rising, held, 1400));
        // Drift beyond the tolerance (a Feast filled it).
        HomeRules.Hud full = new HomeRules.Hud(true, 5L, 1f, 1f / 60, 0f, 0L, 0L, 0f, 0, 1400);
        assertTrue(HomeRules.needsSend(rising, full, 1400));
        // A flag, the home, Provisioned or Tales.
        assertTrue(HomeRules.needsSend(rising, new HomeRules.Hud(true, 5L, 0.2f, 1f / 60, 0f, 0L, 0L, 0f, 1, 1000), 1000));
        assertTrue(HomeRules.needsSend(rising, new HomeRules.Hud(false, 0L, 0.2f, 1f / 60, 0f, 0L, 0L, 0f, 0, 1000), 1000));
        assertTrue(HomeRules.needsSend(rising, new HomeRules.Hud(true, 5L, 0.2f, 1f / 60, 0.4f, 1000L, 9000L, 0f, 0, 1000), 1000));
        assertTrue(HomeRules.needsSend(rising, new HomeRules.Hud(true, 5L, 0.2f, 1f / 60, 0f, 0L, 0L, 3f, 0, 1000), 1000));
    }
}
