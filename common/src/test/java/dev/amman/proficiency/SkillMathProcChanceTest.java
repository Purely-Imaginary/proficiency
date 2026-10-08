package dev.amman.proficiency;

import dev.amman.proficiency.skill.Skill;
import dev.amman.proficiency.skill.SkillMath;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@link SkillMath#procChance} has a cliff at the unlock level and a climb after it. Config is not
 * loaded in these plain-JUnit tests, so {@code ProficiencyConfig} always falls back to its shipped
 * defaults: unlock level 25, floor 0.05. Every test here relies on those fallbacks rather than
 * hard-coding numbers pulled from a live config.
 */
class SkillMathProcChanceTest {

    private static final int UNLOCK = 25;
    private static final double FLOOR = 0.05;

    @Test
    void procChanceIsZeroBelowTheUnlockLevel() {
        for (Skill skill : Skill.VALUES) {
            assertEquals(0.0, SkillMath.procChance(skill, UNLOCK - 1), 1e-9, skill.id());
            assertEquals(0.0, SkillMath.procChance(skill, 0), 1e-9, skill.id());
        }
    }

    @Test
    void procChanceAtExactlyTheUnlockLevelEqualsTheFloor() {
        for (Skill skill : Skill.VALUES) {
            assertEquals(FLOOR, SkillMath.procChance(skill, UNLOCK), 1e-9, skill.id());
        }
    }

    @Test
    void procChanceAt100EqualsTheSkillsOwnCeiling() {
        for (Skill skill : Skill.VALUES) {
            assertEquals(skill.defaultProcChance(), SkillMath.procChance(skill, SkillMath.MAX_LEVEL),
                    1e-9, skill.id());
        }
    }

    @Test
    void procChanceClimbsMonotonicallyFromUnlockTo100() {
        for (Skill skill : Skill.VALUES) {
            double previous = SkillMath.procChance(skill, UNLOCK);
            for (int level = UNLOCK + 1; level <= SkillMath.MAX_LEVEL; level++) {
                double current = SkillMath.procChance(skill, level);
                assertTrue(current >= previous - 1e-9,
                        skill.id() + " proc chance fell from " + previous + " to " + current
                                + " going from level " + (level - 1) + " to " + level);
                previous = current;
            }
        }
    }

    /**
     * The method takes a bare {@code int} level with no clamp of its own. A level past 100 (a
     * negative one, or {@code Integer.MAX_VALUE}) must still return a sane, finite number rather
     * than throwing or dividing by zero.
     */
    @Test
    void outOfDomainLevelsStayFiniteAndDoNotThrow() {
        for (Skill skill : Skill.VALUES) {
            assertEquals(0.0, SkillMath.procChance(skill, -5), 1e-9, skill.id());
            double huge = SkillMath.procChance(skill, Integer.MAX_VALUE);
            assertTrue(Double.isFinite(huge), skill.id() + " was " + huge);
            assertEquals(skill.defaultProcChance(), huge, 1e-9, skill.id());
        }
    }
}
