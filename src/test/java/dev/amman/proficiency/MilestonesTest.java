package dev.amman.proficiency;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.amman.proficiency.config.ProficiencyConfig;
import dev.amman.proficiency.skill.Milestones;
import org.junit.jupiter.api.Test;

/** Every tenth level is announced, once, including when one gain jumps past it. */
class MilestonesTest {

    @Test
    void everyTenthLevelIsAMilestone() {
        for (int level = 1; level <= 100; level++) {
            int got = Milestones.crossed(level - 1, level, 10, 10);
            assertEquals(level % 10 == 0 ? level : -1, got, "level " + level);
        }
    }

    @Test
    void aJumpAnnouncesTheTenthItCrossed() {
        assertEquals(10, Milestones.crossed(9, 11, 10, 10));
        assertEquals(20, Milestones.crossed(8, 23, 10, 10));
        assertEquals(-1, Milestones.crossed(11, 19, 10, 10));
        assertEquals(100, Milestones.crossed(97, 100, 10, 10));
    }

    @Test
    void theFloorAndOddInputsAreRespected() {
        assertEquals(-1, Milestones.crossed(24, 25, 25, 50));
        assertEquals(50, Milestones.crossed(49, 50, 25, 50));
        assertEquals(-1, Milestones.crossed(5, 5, 10, 10));
        assertEquals(-1, Milestones.crossed(12, 3, 10, 10));
        assertEquals(-1, Milestones.crossed(0, 5, 0, 1));
    }

    @Test
    void thresholdsCountWhenJumpedOver() {
        assertTrue(Milestones.passed(24, 26, 25));
        assertTrue(Milestones.passed(24, 25, 25));
        assertFalse(Milestones.passed(25, 26, 25));
    }

    @Test
    void defaultsAreEveryTenFromTen() {
        assertFalse(ProficiencyConfig.SPEC.isLoaded());
        assertEquals(10, ProficiencyConfig.milestoneInterval());
        assertEquals(10, ProficiencyConfig.milestoneMinLevel());
        assertTrue(ProficiencyConfig.announceMilestones());
    }
}
