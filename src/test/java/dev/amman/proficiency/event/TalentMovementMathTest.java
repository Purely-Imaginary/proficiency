package dev.amman.proficiency.event;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TalentMovementMathTest {

    @Test
    void featherAndCatLandingMultiplyRatherThanAdd() {
        assertEquals(1.0, TalentMovementEvents.fallFactor(0, 0, true), 1e-9);
        assertEquals(0.7, TalentMovementEvents.fallFactor(3, 0, false), 1e-9);
        // 0.7 * 0.7, not 1 - 0.6.
        assertEquals(0.49, TalentMovementEvents.fallFactor(3, 3, true), 1e-9);
    }

    @Test
    void catLandingNeedsTheCrouch() {
        assertEquals(1.0, TalentMovementEvents.fallFactor(0, 3, false), 1e-9);
        assertEquals(0.7, TalentMovementEvents.fallFactor(0, 3, true), 1e-9);
    }

    @Test
    void absurdRanksNeverTurnDamageIntoHealing() {
        assertEquals(0.0, TalentMovementEvents.fallFactor(20, 20, true), 1e-9);
    }

    @Test
    void behindMeansTheBackConeOnly() {
        // Target faces +z.
        assertTrue(TalentMovementEvents.behind(0, 1, 0, -3));
        assertTrue(TalentMovementEvents.behind(0, 1, 1, -3));
        assertFalse(TalentMovementEvents.behind(0, 1, 0, 3), "in front");
        assertFalse(TalentMovementEvents.behind(0, 1, 3, 0), "beside");
        assertFalse(TalentMovementEvents.behind(0, 1, 0, 0), "standing inside it");
    }
}
