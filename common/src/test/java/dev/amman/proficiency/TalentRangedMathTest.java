package dev.amman.proficiency;

import dev.amman.proficiency.event.TalentRangedEvents;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/** The arithmetic behind Longshot, Aegis and Ballista, which is all that runs without a world. */
class TalentRangedMathTest {

    @Test
    void longshotCountsWholeEightBlockStepsUpToForty() {
        assertEquals(1.0, TalentRangedEvents.longshotMultiplier(3, 7.9), 1e-9);
        assertEquals(1.06, TalentRangedEvents.longshotMultiplier(3, 8.0), 1e-9);
        assertEquals(1.04, TalentRangedEvents.longshotMultiplier(1, 23.0), 1e-9);
        assertEquals(1.30, TalentRangedEvents.longshotMultiplier(3, 40.0), 1e-9);
        assertEquals(1.30, TalentRangedEvents.longshotMultiplier(3, 400.0), 1e-9);
    }

    @Test
    void longshotIgnoresNoRankAndNonsenseDistances() {
        assertEquals(1.0, TalentRangedEvents.longshotMultiplier(0, 40.0), 1e-9);
        assertEquals(1.0, TalentRangedEvents.longshotMultiplier(3, Double.NaN), 1e-9);
        assertEquals(1.0, TalentRangedEvents.longshotMultiplier(3, -5.0), 1e-9);
    }

    @Test
    void aegisAddsOneHeartUpToFourAndNeverTakesAway() {
        assertEquals(2.0f, TalentRangedEvents.aegisAbsorption(0f), 1e-6);
        assertEquals(7.0f, TalentRangedEvents.aegisAbsorption(5f), 1e-6);
        assertEquals(8.0f, TalentRangedEvents.aegisAbsorption(7f), 1e-6);
        assertEquals(12.0f, TalentRangedEvents.aegisAbsorption(12f), 1e-6);
    }

    @Test
    void ballistaFallsOffToHalfAtTheEdge() {
        assertEquals(1.0f, TalentRangedEvents.ballistaFalloff(0), 1e-6);
        assertEquals(0.75f, TalentRangedEvents.ballistaFalloff(1.5), 1e-6);
        assertEquals(0.5f, TalentRangedEvents.ballistaFalloff(3.0), 1e-6);
        assertEquals(0.5f, TalentRangedEvents.ballistaFalloff(9.0), 1e-6);
    }
}
