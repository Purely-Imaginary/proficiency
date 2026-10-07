package dev.amman.proficiency;

import dev.amman.proficiency.event.TalentMath;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TalentMathTest {

    @Test
    void perRankAddsAndSubtracts() {
        assertEquals(1.15, TalentMath.perRank(3, 0.05), 1e-9);
        assertEquals(1.0, TalentMath.perRank(0, 0.05), 1e-9);
        assertEquals(0.76, TalentMath.lessPerRank(3, 0.08), 1e-9);
        assertEquals(0.0, TalentMath.lessPerRank(10, 0.25), 1e-9);
    }

    @Test
    void hyggeNeverAsksForNothing() {
        assertEquals(10, TalentMath.cozyThreshold(10, 0));
        assertEquals(4, TalentMath.cozyThreshold(10, 3));
        assertEquals(1, TalentMath.cozyThreshold(10, 9));
    }

    @Test
    void darknessShortensButStaysApplied() {
        assertEquals(260, TalentMath.darknessDuration(260, 0));
        assertEquals(65, TalentMath.darknessDuration(260, 3));
        assertEquals(1, TalentMath.darknessDuration(2, 4));
    }

    @Test
    void undergroundReductionIsCapped() {
        assertEquals(0.0, TalentMath.undergroundReduction(0.0), 1e-9);
        assertEquals(0.0, TalentMath.undergroundReduction(Double.NaN), 1e-9);
        assertEquals(0.2, TalentMath.undergroundReduction(0.5), 1e-9);
        assertEquals(TalentMath.MAX_UNDERGROUND_REDUCTION, TalentMath.undergroundReduction(5.0), 1e-9);
    }

    @Test
    void caveSenseGrowsWithPowerWithinBounds() {
        assertEquals(8, TalentMath.caveSenseRadius(1.0));
        assertEquals(10, TalentMath.caveSenseRadius(1.25));
        assertEquals(12, TalentMath.caveSenseRadius(4.0));
        assertEquals(8, TalentMath.caveSenseRadius(Double.NaN));
        assertEquals(1800, TalentMath.caveSenseTicks(1.5));
        assertEquals(6000, TalentMath.caveSenseTicks(100.0));
    }

    @Test
    void everyTenthIdenticalBlockPays() {
        TalentMath.Streak streak = new TalentMath.Streak();
        for (int i = 1; i < 10; i++) {
            assertFalse(streak.place("minecraft:stone"));
        }
        assertTrue(streak.place("minecraft:stone"));
        assertFalse(streak.place("minecraft:dirt"));
        assertEquals(1, streak.count());
        for (int i = 2; i < 10; i++) {
            streak.place("minecraft:dirt");
        }
        assertTrue(streak.place("minecraft:dirt"));
    }
}
