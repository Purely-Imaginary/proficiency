package dev.amman.proficiency;

import dev.amman.proficiency.skill.Skill;
import dev.amman.proficiency.skill.SkillMath;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SkillMathTest {

    @Test
    void costPerLevelOnlyEverClimbs() {
        float previous = 0;
        for (int level = 0; level < SkillMath.MAX_LEVEL; level++) {
            float cost = SkillMath.xpToNext(level);
            assertTrue(cost >= previous, "cost fell at level " + level);
            previous = cost;
        }
    }

    @Test
    void aMaxedSkillIsALongButFiniteGrind() {
        float total = SkillMath.totalXpFor(SkillMath.MAX_LEVEL);
        // Roughly 44k XP with the default curve (floor 8, base 2, exponent 1.35). The window is
        // wide on purpose: this guards against an accidental order-of-magnitude change, not
        // against retuning.
        assertTrue(total > 30_000 && total < 60_000, "total to 100 was " + total);
    }

    @Test
    void halfwayIsFarCheaperThanTheSecondHalf() {
        float toFifty = SkillMath.totalXpFor(50);
        float toHundred = SkillMath.totalXpFor(100);
        assertTrue(toFifty < toHundred * 0.30f,
                "level 50 should be well under a third of the way: " + toFifty + " of " + toHundred);
    }

    @Test
    void progressIsClamped() {
        assertEquals(0f, SkillMath.progress(0, 0f));
        assertEquals(1f, SkillMath.progress(SkillMath.MAX_LEVEL, 0f));
        assertTrue(SkillMath.progress(5, Float.MAX_VALUE) <= 1.0f);
    }

    @Test
    void bonusRunsFromNothingToTheConfiguredMaximum() {
        for (Skill skill : Skill.VALUES) {
            assertEquals(0.0, SkillMath.bonus(skill, 0), 1e-9);
            assertEquals(skill.defaultMaxBonus(), SkillMath.bonus(skill, SkillMath.MAX_LEVEL), 1e-9);
        }
    }
}
