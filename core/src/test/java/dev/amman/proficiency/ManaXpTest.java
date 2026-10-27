package dev.amman.proficiency;

import static org.junit.jupiter.api.Assertions.assertEquals;

import dev.amman.proficiency.xp.ManaXp;
import org.junit.jupiter.api.Test;

class ManaXpTest {

    @Test
    void scalesWithTheManaSpent() {
        assertEquals(0.5, ManaXp.forCost(0.5, 20), 1e-9);    // the reference spell pays the rule
        assertEquals(1.25, ManaXp.forCost(0.5, 50), 1e-9);
        assertEquals(0.25, ManaXp.forCost(0.5, 10), 1e-9);
    }

    @Test
    void cheapSpellsStillPayALittleAndFreeOnesNothing() {
        assertEquals(ManaXp.MIN_XP, ManaXp.forCost(0.5, 1), 1e-9);
        assertEquals(0.0, ManaXp.forCost(0.5, 0), 1e-9);
        assertEquals(0.0, ManaXp.forCost(0.0, 100), 1e-9);    // a rule that pays nothing stays nothing
    }

    @Test
    void hugeSpellsAreCapped() {
        assertEquals(6.0, ManaXp.forCost(0.5, 240), 1e-9);
        assertEquals(6.0, ManaXp.forCost(0.5, 5000), 1e-9);
    }
}
