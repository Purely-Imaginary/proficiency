package dev.amman.proficiency;

import static org.junit.jupiter.api.Assertions.assertEquals;

import dev.amman.proficiency.event.SmeltTake;
import org.junit.jupiter.api.Test;

class SmeltTakeTest {

    @Test
    void plainClickPaysTheStackTaken() {
        assertEquals(3, SmeltTake.takenCount(3, false));
    }

    @Test
    void shiftClickFirstEventCarriesThePreMoveCount() {
        assertEquals(64, SmeltTake.takenCount(64, false));
    }

    @Test
    void shiftClickSecondEventIsEmptyAndPaysNothing() {
        assertEquals(0, SmeltTake.takenCount(0, false));
        assertEquals(0, SmeltTake.takenCount(0, true));
    }

    @Test
    void leftoverStillInTheFurnaceIsNotPaidTwice() {
        assertEquals(0, SmeltTake.takenCount(5, true));
    }

    @Test
    void negativeCountNeverPays() {
        assertEquals(0, SmeltTake.takenCount(-1, false));
    }
}
