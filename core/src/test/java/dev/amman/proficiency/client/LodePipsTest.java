package dev.amman.proficiency.client;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LodePipsTest {

    @Test
    void onePipPerChargeTheRowSpansTheBar() {
        for (int capacity = 1; capacity <= 4; capacity++) {
            LodePips.Pip[] pips = LodePips.of(0, capacity, 70);
            assertEquals(capacity, pips.length);
            assertEquals(0, pips[0].left());
            assertEquals(70, pips[capacity - 1].right());
            for (int i = 1; i < capacity; i++) {
                assertEquals(LodePips.GAP, pips[i].left() - pips[i - 1].right());
            }
        }
    }

    @Test
    void bankedChargesAreFullAndTheNextOneFillsFromTheLeft() {
        LodePips.Pip[] pips = LodePips.of(1.5, 3, 70);
        assertTrue(pips[0].banked());
        assertEquals(pips[0].right() - pips[0].left(), pips[0].filled());
        assertFalse(pips[1].banked());
        int w = pips[1].right() - pips[1].left();
        assertEquals(w / 2, pips[1].filled());
        assertEquals(0, pips[2].filled());
    }

    @Test
    void theFirstStoneShowsAPixelAndABadValueShowsNothing() {
        assertEquals(1, LodePips.of(0.004, 1, 70)[0].filled());
        assertEquals(0, LodePips.of(Double.NaN, 2, 70)[0].filled());
        assertEquals(0, LodePips.of(-1, 2, 70)[0].filled());
    }

    @Test
    void aChargeAboveTheCapacityFillsEveryPipAndNoMore() {
        LodePips.Pip[] pips = LodePips.of(9, 2, 70);
        assertTrue(pips[0].banked() && pips[1].banked());
    }

    @Test
    void theFlashFades() {
        assertEquals(1f, LodePips.flash(0));
        assertTrue(LodePips.flash(300) < 1f && LodePips.flash(300) > 0f);
        assertEquals(0f, LodePips.flash(LodePips.FLASH_MS));
        assertEquals(0f, LodePips.flash(-5));
    }
}
