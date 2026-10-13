package dev.amman.proficiency.hearth.client;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

/** The HUD's glyph group and the tracker's home clock: the Tales count, the line's eased room, the linger. */
class HearthHudRulesTest {

    // ---- Tales count ------------------------------------------------------------------------------

    @Test
    void talesCountCutsDownAndNeverShowsTenPointZero() {
        assertEquals("0", WarmthGlyph.talesCount(0));
        assertEquals("999", WarmthGlyph.talesCount(999));
        assertEquals("1.0k", WarmthGlyph.talesCount(1000));
        assertEquals("1.2k", WarmthGlyph.talesCount(1299));
        assertEquals("9.9k", WarmthGlyph.talesCount(9_960), "rounding would have shown 10.0k");
        assertEquals("9.9k", WarmthGlyph.talesCount(9_999));
        assertEquals("10k", WarmthGlyph.talesCount(10_000));
        assertEquals("10k", WarmthGlyph.talesCount(10_999));
        assertEquals("123k", WarmthGlyph.talesCount(123_456));
    }

    // ---- The skill line's room for the group ------------------------------------------------------

    @Test
    void theLineMakesRoomAsTheGlyphFadesInNotAllAtOnce() {
        assertEquals(0, WarmthGlyph.lineRoom(6, 8, 0f, 0, 0f));
        assertEquals(14, WarmthGlyph.lineRoom(6, 8, 1f, 0, 0f));
        int last = 0;
        for (int step = 1; step <= 20; step++) {
            int room = WarmthGlyph.lineRoom(6, 8, step / 20f, 0, 0f);
            assertTrue(room >= last, "the room only grows while the glyph fades in");
            assertTrue(room - last <= 1, "a 60 fps fade moves the line a pixel at a time, not " + (room - last));
            last = room;
        }
        assertEquals(1, WarmthGlyph.lineRoom(6, 8, 0.05f, 0, 0f), "the first frame of the fade moves it by one pixel");
    }

    @Test
    void crossingFromHomeToAwayTakesTheWiderOfTheTwo() {
        assertEquals(30, WarmthGlyph.lineRoom(6, 8, 0.2f, 24, 1f));
        assertEquals(14, WarmthGlyph.lineRoom(6, 8, 1f, 24, 0.1f));
        assertEquals(0, WarmthGlyph.lineRoom(6, 0, 1f, 0, 1f), "no group, no room");
        assertEquals(14, WarmthGlyph.lineRoom(6, 8, 3f, 0, -1f), "fades are clamped to 0..1");
    }

    // ---- The tracker's home clock -----------------------------------------------------------------

    @Test
    void theTrackerFadesInAtHomeAndLingersAfterLeaving() {
        TrackerPresence presence = new TrackerPresence();
        assertEquals(0f, presence.opacity(0), 1e-6, "never home: nothing lingers");
        presence.update(true, 10_000);
        assertEquals(0f, presence.opacity(10_000), 1e-6);
        assertEquals(0.5f, presence.opacity(10_000 + TrackerPresence.FADE_IN_MS / 2), 1e-6);
        assertEquals(1f, presence.opacity(20_000), 1e-6);
        presence.update(false, 30_000);
        assertEquals(1f, presence.opacity(30_000 + TrackerPresence.LINGER_MS - 1), 1e-6, "held while it lingers");
        assertEquals(0.5f, presence.opacity(30_000 + TrackerPresence.LINGER_MS + TrackerPresence.FADE_OUT_MS / 2), 1e-6);
        assertEquals(0f, presence.opacity(30_000 + TrackerPresence.LINGER_MS + TrackerPresence.FADE_OUT_MS), 1e-6);
    }

    @Test
    void aWalkOutWithTheGuiHiddenIsNotReplayedWhenItComesBack() {
        // F1 at home, walk out, F1 a minute later: the hidden HUD pass kept telling the clock, so the
        // first drawn frame sees a player who left a minute ago, not one who just left.
        TrackerPresence presence = new TrackerPresence();
        presence.update(true, 0);
        for (long t = 1000; t <= 61_000; t += 50) {
            presence.update(t < 2000, t);
        }
        assertEquals(0f, presence.opacity(61_000), 1e-6, "the linger ran out while the GUI was hidden");
    }

    @Test
    void comingBackStartsTheFadeAgainAndResetForgetsEverything() {
        TrackerPresence presence = new TrackerPresence();
        presence.update(true, 0);
        presence.update(false, 1000);
        presence.update(true, 2000);
        assertTrue(presence.home());
        assertEquals(0f, presence.opacity(2000), 1e-6, "a new arrival fades in again");
        presence.reset();
        assertEquals(0f, presence.opacity(3000), 1e-6);
        assertFalse(presence.home());
    }
}
