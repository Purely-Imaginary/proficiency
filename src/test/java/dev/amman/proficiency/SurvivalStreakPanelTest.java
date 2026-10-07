package dev.amman.proficiency;

import dev.amman.proficiency.skill.PlayerSkills;
import dev.amman.proficiency.skill.SurvivalStreak;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The numbers the skills panel's streak footer draws, at the shipped defaults: a stack an hour,
 * fifty of them, 1% each. Ported from the Fabric branch's own streak engine when the two merged.
 */
class SurvivalStreakPanelTest {

    private static final long HOUR = 72_000L;

    private static PlayerSkills withTicks(long ticks) {
        PlayerSkills skills = new PlayerSkills();
        skills.setStreakTicks(ticks);
        return skills;
    }

    @Test
    void theCapIsFiftyPercentAndTheStepBarReadsFullThere() {
        assertTrue(SurvivalStreak.enabled());
        assertEquals(50, SurvivalStreak.capPercent());
        PlayerSkills capped = withTicks(500 * HOUR);
        assertEquals(50, SurvivalStreak.stacks(capped));
        assertTrue(SurvivalStreak.atCap(capped));
        assertEquals(1f, SurvivalStreak.stepProgress(capped), 0f);
        assertEquals(1f, SurvivalStreak.capFill(capped), 0f);
        assertFalse(SurvivalStreak.atCap(withTicks(50 * HOUR - 1)));
    }

    @Test
    void stepProgressIsTheFractionOfTheCurrentHour() {
        assertEquals(0f, SurvivalStreak.stepProgress(withTicks(0)), 0f);
        assertEquals(0.5f, SurvivalStreak.stepProgress(withTicks(3 * HOUR + HOUR / 2)), 1e-6f);
        assertEquals(0.5f, SurvivalStreak.capFill(withTicks(25 * HOUR)), 1e-6f);
        assertEquals(0f, SurvivalStreak.capFill(withTicks(HOUR - 1)), 0f);
    }
}
