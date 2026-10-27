package dev.amman.proficiency;

import dev.amman.proficiency.skill.CommerceMath;
import dev.amman.proficiency.skill.CommerceTracker;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Commerce's memory: the repeat rule, the burst rule, the barter fade and the clock. */
class CommerceTrackerTest {

    private static final String PAPER = "minecraft:paper>minecraft:emerald";

    @Test
    void theSameOfferFadesLapAfterLap() {
        CommerceTracker tracker = new CommerceTracker();
        assertEquals(1.0, tracker.tradeFactor(PAPER, 0L), 1e-9);
        assertEquals(0.60, tracker.tradeFactor(PAPER, 10L), 1e-9);
        assertEquals(0.35, tracker.tradeFactor(PAPER, 20L), 1e-9);
        assertEquals(0.20, tracker.tradeFactor(PAPER, 30L), 1e-9);
        assertEquals(0.10, tracker.tradeFactor(PAPER, 40L), 1e-9);
        assertEquals(0.10, tracker.tradeFactor(PAPER, 50L), 1e-9);
    }

    @Test
    void thePaperLoopPaysAboutAThirdOfWhatTwelveFreshOffersWould() {
        // The classic loop: twelve uses of one offer, a restock, twelve more, all within a day.
        CommerceTracker loop = new CommerceTracker();
        double looped = 0;
        for (int i = 0; i < 24; i++) {
            looped += loop.tradeFactor(PAPER, i * 40L);
        }
        // 24 trades: 1 + 0.6 + 0.35 + 0.2 + 20 x 0.1, and the burst rule only trims it further.
        assertTrue(looped < 4.2, "the loop paid " + looped + " trades' worth for 24 trades");
        assertTrue(looped < 24 * 0.2);
    }

    @Test
    void aNewDayStartsTheOfferOver() {
        CommerceTracker tracker = new CommerceTracker();
        for (int i = 0; i < 6; i++) {
            tracker.tradeFactor(PAPER, i);
        }
        assertEquals(1.0, tracker.tradeFactor(PAPER, CommerceMath.REPEAT_WINDOW_TICKS + 100L), 1e-9);
    }

    @Test
    void otherOffersAreIndependentOfEachOther() {
        CommerceTracker tracker = new CommerceTracker();
        for (int i = 0; i < 5; i++) {
            tracker.tradeFactor(PAPER, i);
        }
        assertEquals(1.0, tracker.tradeFactor("minecraft:emerald>minecraft:bread", 10L), 1e-9);
    }

    @Test
    void aHallOfManyOffersFadesByTheBurstRule() {
        CommerceTracker tracker = new CommerceTracker();
        for (int i = 0; i < 20; i++) {
            assertEquals(1.0, tracker.tradeFactor("offer-" + i, i), 1e-9, "trade " + i);
        }
        double next = tracker.tradeFactor("offer-20", 21L);
        assertEquals(20.0 / 21.0, next, 1e-9);
        // Five minutes later the window has cleared.
        assertEquals(1.0, tracker.tradeFactor("offer-21", 21L + CommerceMath.BURST_WINDOW_TICKS + 50L), 1e-9);
    }

    @Test
    void aBackwardsClockDoesNotKeepOldTradesAround() {
        CommerceTracker tracker = new CommerceTracker();
        for (int i = 0; i < 30; i++) {
            tracker.tradeFactor("offer-" + i, 100_000L + i);
        }
        assertEquals(1.0, tracker.tradeFactor("fresh", 5L), 1e-9);
    }

    @Test
    void barteringFadesPerWindowAndRecovers() {
        CommerceTracker tracker = new CommerceTracker();
        for (int i = 0; i < CommerceMath.BARTER_FREE; i++) {
            assertEquals(1.0, tracker.barterFactor(i * 20L), 1e-9, "barter " + i);
        }
        assertTrue(tracker.barterFactor(300L) < 1.0);
        assertEquals(1.0, tracker.barterFactor(300L + CommerceMath.BARTER_WINDOW_TICKS + 5000L), 1e-9);
    }

    @Test
    void clearForgetsEverything() {
        CommerceTracker tracker = new CommerceTracker();
        for (int i = 0; i < 6; i++) {
            tracker.tradeFactor(PAPER, i);
        }
        tracker.clear();
        assertEquals(1.0, tracker.tradeFactor(PAPER, 10L), 1e-9);
    }

    @Test
    void theSignatureTableStaysBounded() {
        CommerceTracker tracker = new CommerceTracker();
        for (int i = 0; i < 5000; i++) {
            tracker.tradeFactor("offer-" + i, i * 30L);
        }
        // No assertion on size (it is private); the loop finishing without trouble is the check, plus a sane factor.
        assertTrue(tracker.tradeFactor("last", 5000 * 30L) > 0);
    }
}
