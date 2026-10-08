package dev.amman.proficiency;

import dev.amman.proficiency.event.TalentMeleeEvents;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/** The arithmetic behind Combo, Berserker and Meteor, which the descriptions promise in numbers. */
class TalentMeleeMathTest {

    @Test
    void comboBuildsInsideTheWindowAndCapsAtFive() {
        int stacks = 0;
        for (int i = 0; i < 10; i++) {
            stacks = TalentMeleeEvents.comboStacks(stacks, 20);
        }
        assertEquals(5, stacks);
        assertEquals(1, TalentMeleeEvents.comboStacks(0, 30));
    }

    @Test
    void aLatePunchStartsTheComboAgain() {
        assertEquals(0, TalentMeleeEvents.comboStacks(4, 31));
        assertEquals(0, TalentMeleeEvents.comboStacks(4, -5));
    }

    @Test
    void berserkerCountsWholeTenthsOfMissingHealth() {
        assertEquals(1.0f, TalentMeleeEvents.berserkerMultiplier(3, 20f, 20f), 1e-6);
        assertEquals(1.0f, TalentMeleeEvents.berserkerMultiplier(3, 18.5f, 20f), 1e-6);
        assertEquals(1.09f, TalentMeleeEvents.berserkerMultiplier(3, 18f, 20f), 1e-6);
        assertEquals(1.0f + 0.03f * 3 * 9, TalentMeleeEvents.berserkerMultiplier(3, 1f, 20f), 1e-5);
        assertEquals(1.0f, TalentMeleeEvents.berserkerMultiplier(0, 1f, 20f), 1e-6);
        assertEquals(1.0f, TalentMeleeEvents.berserkerMultiplier(3, 1f, 0f), 1e-6);
    }

    @Test
    void meteorPaysPerBlockUpToTwenty() {
        assertEquals(1.0f, TalentMeleeEvents.meteorMultiplier(5, 0f), 1e-6);
        assertEquals(1.2f, TalentMeleeEvents.meteorMultiplier(5, 1.9f), 1e-6);
        assertEquals(1.0f + 0.04f * 5 * 20, TalentMeleeEvents.meteorMultiplier(5, 20f), 1e-5);
        assertEquals(1.0f + 0.04f * 5 * 20, TalentMeleeEvents.meteorMultiplier(5, 80f), 1e-5);
        assertEquals(1.0f, TalentMeleeEvents.meteorMultiplier(5, Float.NaN), 1e-6);
    }
}
