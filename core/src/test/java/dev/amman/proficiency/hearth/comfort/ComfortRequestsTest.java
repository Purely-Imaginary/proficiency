package dev.amman.proficiency.hearth.comfort;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/** The comfort lane's in-memory bookkeeping: change flags, the empty-frame watch, scan retries, placer notes. */
class ComfortRequestsTest {

    private static final String HEARTH = "minecraft:overworld|0,64,0";
    private static final UUID ALICE = UUID.fromString("00000000-0000-0000-0000-0000000000a1");
    private static final UUID BOB = UUID.fromString("00000000-0000-0000-0000-0000000000b2");

    @BeforeEach
    @AfterEach
    void clean() {
        ComfortRequests.clear();
    }

    @Test
    void aChangeFlagsTheHearthAndAScanClearsItAndTheEntry() {
        assertFalse(ComfortRequests.dirty(HEARTH));
        ComfortRequests.touch(HEARTH);
        assertTrue(ComfortRequests.dirty(HEARTH));
        ComfortRequests.scanned(HEARTH);
        assertFalse(ComfortRequests.dirty(HEARTH));
        assertEquals(0, ComfortRequests.size(), "nothing left to remember: the entry goes");
    }

    @Test
    void theWatchOfAnEmptyFrameEndsAfterTwoMinutesEvenThroughTheScansItCauses() {
        long now = 1000;
        long scans = 0;
        // Each scan sees the empty frame, so it watches again and asks for the next one 30 s later.
        for (int i = 0; i < 40; i++, now += 600) {
            ComfortRequests.scanned(HEARTH);
            ComfortRequests.watch(HEARTH, now);
            if (ComfortRequests.dirty(HEARTH)) {
                scans++;
            }
        }
        // 600 ticks apart for 2400 ticks: the first look plus four more, then the watch is over.
        assertEquals(5, scans, "the window is 2 minutes from the first sighting, not from the latest scan");
        assertFalse(ComfortRequests.dirty(HEARTH), "and it stays over: only the 5-minute rescan has the frame now");
        assertEquals(1, ComfortRequests.size(), "the watch is still remembered, so it cannot restart by itself");
    }

    @Test
    void aNewChangeRearmsTheWatchAndNothingPendingEndsIt() {
        ComfortRequests.watch(HEARTH, 0);
        ComfortRequests.scanned(HEARTH);
        ComfortRequests.watch(HEARTH, ComfortRequests.WATCH_TICKS + 1);
        assertFalse(ComfortRequests.dirty(HEARTH), "the window is over");
        ComfortRequests.touch(HEARTH);
        ComfortRequests.scanned(HEARTH);
        ComfortRequests.watch(HEARTH, ComfortRequests.WATCH_TICKS + 2);
        assertTrue(ComfortRequests.dirty(HEARTH), "somebody placed something: a frame may be new, look again for two minutes");
        ComfortRequests.scanned(HEARTH);
        ComfortRequests.unwatch(HEARTH);
        assertEquals(0, ComfortRequests.size(), "a scan that sees nothing pending leaves nothing behind");
    }

    @Test
    void aDeferredScanWaitsForItsClockAndTheFlagStays() {
        ComfortRequests.touch(HEARTH);
        ComfortRequests.defer(HEARTH, 700);
        assertTrue(ComfortRequests.deferred(HEARTH, 100));
        assertTrue(ComfortRequests.deferred(HEARTH, 699));
        assertFalse(ComfortRequests.deferred(HEARTH, 700));
        assertTrue(ComfortRequests.dirty(HEARTH), "the change is still wanted once the chunk is back");
        ComfortRequests.scanned(HEARTH);
        assertFalse(ComfortRequests.deferred(HEARTH, 100), "a scan that ran clears the retry clock");
        assertEquals(0, ComfortRequests.size());
        // A Hearth that never scanned has no request at all: deferring creates one, and a scan removes it.
        ComfortRequests.defer(HEARTH, 50);
        assertTrue(ComfortRequests.deferred(HEARTH, 10));
        ComfortRequests.scanned(HEARTH);
        assertEquals(0, ComfortRequests.size());
    }

    @Test
    void aHearthThatIsGoneTakesEverythingWithIt() {
        ComfortRequests.touch(HEARTH).notePlacer("kind:seat", 5L, ALICE);
        ComfortRequests.watch(HEARTH, 10);
        ComfortRequests.defer(HEARTH, 99);
        ComfortRequests.drop(HEARTH);
        assertNull(ComfortRequests.get(HEARTH));
        assertEquals(0, ComfortRequests.size());
    }

    @Test
    void placerNotesKeepTheOldestPlacerOfAStandingBlockFirst() {
        ComfortRequests.Request request = ComfortRequests.touch(HEARTH);
        request.notePlacer("kind:seat", 10L, ALICE);
        request.notePlacer("kind:seat", 11L, BOB);
        assertEquals(List.of(new ComfortRequests.Note(10L, ALICE), new ComfortRequests.Note(11L, BOB)),
                request.notes("kind:seat"));
        assertEquals(2, request.notes());
        // The same spot placed again by somebody else: the latest placer owns it, the order stays.
        request.notePlacer("kind:seat", 10L, BOB);
        assertEquals(List.of(new ComfortRequests.Note(10L, BOB), new ComfortRequests.Note(11L, BOB)),
                request.notes("kind:seat"));
        assertEquals(2, request.notes());
    }

    @Test
    void aBrokenBlockTakesItsNotesAndTheMilestoneTakesTheRest() {
        ComfortRequests.Request request = ComfortRequests.touch(HEARTH);
        request.notePlacer("kind:seat", 10L, ALICE);
        request.notePlacer("id:minecraft:oak_stairs", 10L, ALICE);
        request.notePlacer("kind:seat", 11L, BOB);
        request.forgetAt(10L);
        assertEquals(List.of(new ComfortRequests.Note(11L, BOB)), request.notes("kind:seat"));
        assertTrue(request.notes("id:minecraft:oak_stairs").isEmpty());
        assertEquals(1, request.notes());
        request.forget("kind:seat");
        assertEquals(0, request.notes());
        assertTrue(request.notes("kind:seat").isEmpty());
        ComfortRequests.scanned(HEARTH);
        assertEquals(0, ComfortRequests.size());
    }

    @Test
    void notesAreBoundedPerKindAndInAllAndTheNewestWin() {
        ComfortRequests.Request request = ComfortRequests.touch(HEARTH);
        for (int i = 0; i < ComfortRequests.MAX_NOTES_PER_KEY + 5; i++) {
            request.notePlacer("kind:seat", i, ALICE);
        }
        List<ComfortRequests.Note> seat = request.notes("kind:seat");
        assertEquals(ComfortRequests.MAX_NOTES_PER_KEY, seat.size());
        assertEquals(5L, seat.get(0).pos(), "the five oldest were let go");
        for (int k = 0; k < ComfortRequests.MAX_NOTES; k++) {
            request.notePlacer("id:block_" + k, k, BOB);
        }
        assertEquals(ComfortRequests.MAX_NOTES, request.notes(), "a Hearth never holds more than the cap");
        request.notePlacer("id:one_more", 1L, BOB);
        assertTrue(request.notes("id:one_more").isEmpty(), "past the cap a new note is not taken");
    }
}
