package dev.amman.proficiency;

import static org.junit.jupiter.api.Assertions.assertEquals;

import dev.amman.proficiency.event.SmeltXp;
import org.junit.jupiter.api.Test;

class SmeltXpTest {

    @Test
    void ironPaysOneAnItemAsBefore() {
        assertEquals(1.0, SmeltXp.perItem(0.7), 1e-9);
    }

    @Test
    void charcoalPaysAFifthOfIron() {
        assertEquals(0.15 / 0.7, SmeltXp.perItem(0.15), 1e-9);
    }

    @Test
    void cheapSmeltsStopAtTheFloor() {
        assertEquals(SmeltXp.MIN_PER_ITEM, SmeltXp.perItem(0.0), 1e-9);
        assertEquals(SmeltXp.MIN_PER_ITEM, SmeltXp.perItem(0.05), 1e-9);
    }

    @Test
    void richSmeltsStopAtTheCeiling() {
        assertEquals(SmeltXp.MAX_PER_ITEM, SmeltXp.perItem(2.0), 1e-9);
    }

    @Test
    void noRecipeFoundPaysAsBefore() {
        assertEquals(1.0, SmeltXp.perItem(-1), 1e-9);
        assertEquals(1.0, SmeltXp.perItem(Double.NaN), 1e-9);
    }
}
