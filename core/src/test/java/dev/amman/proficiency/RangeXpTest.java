package dev.amman.proficiency;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.amman.proficiency.skill.RangeXp;
import dev.amman.proficiency.skill.Skill;
import org.junit.jupiter.api.Test;

class RangeXpTest {

    private static final double RATE = RangeXp.DEFAULT_PER_DOUBLING;

    @Test
    void eachDoublingPastEightBlocksAddsTheSameShare() {
        assertEquals(1.0, RangeXp.factor(8, RATE), 1e-9);
        assertEquals(1.5, RangeXp.factor(16, RATE), 1e-9);
        assertEquals(2.0, RangeXp.factor(32, RATE), 1e-9);
        assertEquals(2.5, RangeXp.factor(64, RATE), 1e-9);
        assertEquals(3.0, RangeXp.factor(128, RATE), 1e-9);
    }

    @Test
    void closeShotsPayThePlainHitAndFarShotsAreCapped() {
        assertEquals(1.0, RangeXp.factor(0, RATE), 1e-9);
        assertEquals(1.0, RangeXp.factor(5, RATE), 1e-9);
        assertEquals(3.0, RangeXp.factor(500, RATE), 1e-9);
        assertEquals(1.0, RangeXp.factor(Double.NaN, RATE), 1e-9);
        assertEquals(1.0, RangeXp.factor(64, 0), 1e-9);
    }

    @Test
    void theCurveFlattens() {
        double first = RangeXp.factor(24, RATE) - RangeXp.factor(8, RATE);
        double second = RangeXp.factor(40, RATE) - RangeXp.factor(24, RATE);
        assertTrue(first > second, "16 more blocks near pay more than 16 more blocks far");
    }

    @Test
    void onlyBowsAndCrossbowsPayForDistance() {
        assertEquals(2.0, RangeXp.factor(Skill.ARCHERY, 32, RATE), 1e-9);
        assertEquals(2.0, RangeXp.factor(Skill.CROSSBOWS, 32, RATE), 1e-9);
        assertEquals(1.0, RangeXp.factor(Skill.TRIDENTS, 32, RATE), 1e-9);
        assertEquals(1.0, RangeXp.factor(Skill.SWORDS, 32, RATE), 1e-9);
    }
}
