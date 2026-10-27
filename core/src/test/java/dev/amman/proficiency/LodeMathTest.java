package dev.amman.proficiency;

import dev.amman.proficiency.skill.LodeMath;
import dev.amman.proficiency.skill.PlayerSkills;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Seam Sense and Lodestone: the Motherlode charge built on stone and spent on ore. */
class LodeMathTest {

    private static int stonesToFill(int rank) {
        double lode = 0;
        int stones = 0;
        while (lode < 1.0 - 1e-9) {
            lode = LodeMath.charge(lode, rank, 1);
            stones++;
        }
        return stones;
    }

    @Test
    void aFullChargeIsSlowAndFasterWithEachRank() {
        assertEquals(250, stonesToFill(1));
        assertEquals(143, stonesToFill(2));
        assertEquals(100, stonesToFill(3));
        assertEquals(0.0, LodeMath.perStone(0));
    }

    @Test
    void lodestoneRaisesTheCapacityToFourAndNoFurther() {
        assertEquals(1, LodeMath.capacity(0));
        assertEquals(2, LodeMath.capacity(1));
        assertEquals(4, LodeMath.capacity(3));
        assertEquals(4, LodeMath.capacity(9));
        double lode = 0;
        for (int i = 0; i < 10_000; i++) {
            lode = LodeMath.charge(lode, 3, LodeMath.capacity(0));
        }
        assertEquals(1.0, lode, 1e-9);
    }

    @Test
    void aPartialChargeIsAddedToTheRollAndSpentWhole() {
        LodeMath.Strike strike = LodeMath.strike(0.35);
        assertFalse(strike.certain());
        assertEquals(0.35, strike.bonus(), 1e-9);
        assertEquals(0.0, strike.left(), 1e-9);
    }

    @Test
    void aFullChargeIsCertainAndSpendsOnlyOne() {
        LodeMath.Strike strike = LodeMath.strike(2.6);
        assertTrue(strike.certain());
        assertEquals(1.6, strike.left(), 1e-9);
        assertEquals(1, LodeMath.full(strike.left()));
        // Built up stone by stone, the float sum lands a hair under 1: that still counts as full.
        double lode = 0;
        for (int i = 0; i < 100; i++) {
            lode = LodeMath.charge(lode, 3, 4);
        }
        assertTrue(LodeMath.strike(lode).certain());
    }

    @Test
    void temperingMendsEightyPercentOfTheWearUpToFourPoints() {
        assertEquals(0.0, LodeMath.mend(0));
        assertEquals(0.8, LodeMath.mend(1), 1e-9);
        assertEquals(3.2, LodeMath.mend(4), 1e-9);
        assertEquals(4.0, LodeMath.mend(40), 1e-9);
        // Never more than 80%: at any proc rate a pickaxe lasts at most five times as long.
        for (int wear = 1; wear < 100; wear++) {
            assertTrue(LodeMath.mend(wear) <= wear * 0.8 + 1e-9);
        }
    }

    @Test
    void aBadStoredValueCountsAsEmpty() {
        assertFalse(LodeMath.strike(Double.NaN).certain());
        assertEquals(0.0, LodeMath.strike(-3).bonus());
    }

    @Test
    void theChargeIsSavedAndADeathTakesIt() throws Exception {
        PlayerSkills skills = new PlayerSkills();
        skills.setLode(1.5);
        var encoded = PlayerSkills.CODEC.encodeStart(com.mojang.serialization.JsonOps.INSTANCE, skills)
                .getOrThrow(message -> new AssertionError("encode failed: " + message));
        PlayerSkills restored = PlayerSkills.CODEC.parse(com.mojang.serialization.JsonOps.INSTANCE, encoded)
                .getOrThrow(message -> new AssertionError("decode failed: " + message));
        assertEquals(1.5, restored.lode(), 1e-6);
        PlayerSkills copy = new PlayerSkills();
        copy.copyFrom(restored);
        assertEquals(1.5, copy.lode(), 1e-6);
        restored.applyDeathPenalty();
        assertEquals(0.0, restored.lode());
    }
}
