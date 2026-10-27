package dev.amman.proficiency.hearth.stages;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class ChunkHoldTest {

    @Test
    void heldWhileBuilding() {
        assertTrue(ChunkHold.keep(true, true, 0, 30));
    }

    @Test
    void notHeldWhenOffOrStopped() {
        assertFalse(ChunkHold.keep(false, true, 0, 30));
        assertFalse(ChunkHold.keep(true, false, 0, 30));
    }

    @Test
    void shortWaitIsHeldLongWaitIsNot() {
        assertTrue(ChunkHold.keep(true, true, 30 * 60, 30));
        assertFalse(ChunkHold.keep(true, true, 30 * 60 + 1, 30));
    }

    @Test
    void zeroLimitReleasesAtOnceOnAnyWait() {
        assertTrue(ChunkHold.keep(true, true, 0, 0));
        assertFalse(ChunkHold.keep(true, true, 1, 0));
    }

    @Test
    void stageBoxFitsInsideTheTicketArea() {
        // The ticket loads 2 chunks around the Hearth's chunk (distance 2). A stage box reaches RADIUS blocks
        // (Reach growth widens the range, not the box), so it is always inside one neighbouring chunk.
        assertTrue(HearthDesigns.RADIUS <= 16);
    }
}
