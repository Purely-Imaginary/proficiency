package dev.amman.proficiency;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.amman.proficiency.skill.AoeBreaks;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class AoeBreaksTest {

    private final UUID alice = UUID.randomUUID();
    private final UUID bob = UUID.randomUUID();

    @Test
    void firstBreakInATickIsThePrimary() {
        AoeBreaks aoe = new AoeBreaks();
        assertFalse(aoe.classify(alice, 100, 1L));
        assertFalse(aoe.isExtra(alice, 100, 1L));
    }

    @Test
    void everyOtherPositionInTheSameTickIsAnExtra() {
        AoeBreaks aoe = new AoeBreaks();
        assertFalse(aoe.classify(alice, 100, 1L));
        for (long pos = 2; pos <= 9; pos++) {
            assertTrue(aoe.classify(alice, 100, pos), "pos " + pos);
        }
        assertFalse(aoe.isExtra(alice, 100, 1L));
        assertTrue(aoe.isExtra(alice, 100, 5L));
    }

    @Test
    void aNewTickStartsAfresh() {
        AoeBreaks aoe = new AoeBreaks();
        aoe.classify(alice, 100, 1L);
        aoe.classify(alice, 100, 2L);
        assertFalse(aoe.classify(alice, 101, 3L));
        assertFalse(aoe.isExtra(alice, 101, 2L));
        // A verdict from an older tick never leaks forward.
        assertFalse(aoe.isExtra(alice, 100, 2L));
    }

    @Test
    void playersDoNotShareATick() {
        AoeBreaks aoe = new AoeBreaks();
        assertFalse(aoe.classify(alice, 100, 1L));
        assertFalse(aoe.classify(bob, 100, 2L));
        assertTrue(aoe.classify(alice, 100, 3L));
        assertTrue(aoe.classify(bob, 100, 4L));
    }

    @Test
    void thePrimaryPositionFiredTwiceStaysThePrimary() {
        AoeBreaks aoe = new AoeBreaks();
        assertFalse(aoe.classify(alice, 7, 1L));
        assertFalse(aoe.classify(alice, 7, 1L));
        assertTrue(aoe.classify(alice, 7, 2L));
        assertTrue(aoe.classify(alice, 7, 2L));
    }

    @Test
    void unknownPlayerOrPositionIsNotAnExtra() {
        AoeBreaks aoe = new AoeBreaks();
        assertFalse(aoe.isExtra(alice, 1, 1L));
        aoe.classify(alice, 1, 1L);
        assertFalse(aoe.isExtra(alice, 1, 99L));
        aoe.forget(alice);
        assertFalse(aoe.isExtra(alice, 1, 1L));
    }

    @Test
    void extraXpIsTheShareOfTheNormalXp() {
        assertEquals(0.25, AoeBreaks.extraXp(1.0, AoeBreaks.DEFAULT_SHARE), 1e-12);
        assertEquals(2.0, AoeBreaks.extraXp(8.0, 0.25), 1e-12);
        assertEquals(0.0, AoeBreaks.extraXp(8.0, 0.0), 0.0);
        assertEquals(0.0, AoeBreaks.extraXp(0.0, 0.25), 0.0);
        assertEquals(0.0, AoeBreaks.extraXp(Double.NaN, 0.25), 0.0);
        assertEquals(8.0, AoeBreaks.extraXp(8.0, 3.0), 1e-12, "a share above 1 never pays more than the full XP");
    }

    @Test
    void aHammerSwingPaysOnePointTwoFiveBlocksWorthWithTheDefaultShare() {
        // A 3x3 hammer: one full block and eight extras at 25% is 3 blocks' worth, not 9.
        double total = 1.0 + 8 * AoeBreaks.extraXp(1.0, AoeBreaks.DEFAULT_SHARE);
        assertEquals(3.0, total, 1e-12);
    }

    @Test
    void aCancelledPrimaryGivesTheSlotBack() {
        AoeBreaks aoe = new AoeBreaks();
        assertFalse(aoe.classify(alice, 100, 1L));
        aoe.release(alice, 100, 0, 1L);
        assertFalse(aoe.classify(alice, 100, 2L), "the next block is the primary");
        assertTrue(aoe.classify(alice, 100, 3L));
        // With an extra already seen the slot is not given back.
        aoe.release(alice, 100, 0, 2L);
        assertTrue(aoe.classify(alice, 100, 4L));
    }

    @Test
    void sameCoordinatesInTwoDimensionsAreTwoBlocks() {
        AoeBreaks aoe = new AoeBreaks();
        assertFalse(aoe.classify(alice, 100, 1, 7L));
        assertTrue(aoe.classify(alice, 100, 2, 7L));
        assertTrue(aoe.isExtra(alice, 100, 2, 7L));
        assertFalse(aoe.isExtra(alice, 100, 1, 7L));
    }

    @Test
    void staleStatesOfPlayersWhoNeverLeaveArePruned() {
        AoeBreaks aoe = new AoeBreaks();
        for (int i = 0; i < 300; i++) {
            aoe.classify(java.util.UUID.randomUUID(), 10, 1L);
        }
        // A much later break prunes the old ones without touching the current player's verdict.
        assertFalse(aoe.classify(alice, 5000, 1L));
        assertTrue(aoe.classify(alice, 5000, 2L));
    }
}
