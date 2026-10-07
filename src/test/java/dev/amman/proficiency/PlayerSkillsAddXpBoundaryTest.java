package dev.amman.proficiency;

import dev.amman.proficiency.skill.PlayerSkills;
import dev.amman.proficiency.skill.Skill;
import dev.amman.proficiency.skill.SkillMath;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@link PlayerSkills#addXp} levels up with {@code if (xp[index] < need) break;}, so xp exactly
 * equal to the requirement must level (the loop condition is a strict less-than, not
 * less-or-equal). These pin that exact boundary down at more than one level, since a curve-based
 * {@code need} is only an integer at level 0.
 */
class PlayerSkillsAddXpBoundaryTest {

    @Test
    void xpExactlyEqualToTheRequirementLevelsUpAtLevelZero() {
        PlayerSkills skills = new PlayerSkills();
        float need = SkillMath.xpToNext(0);

        int gained = skills.addXp(Skill.MINING, need);

        assertEquals(1, gained, "xp exactly at the requirement must level, not sit at 100%");
        assertEquals(1, skills.level(Skill.MINING));
        assertEquals(0f, skills.xp(Skill.MINING), 1e-4f, "the exact amount spent should leave nothing banked");
    }

    @Test
    void xpExactlyEqualToTheRequirementLevelsUpMidCurve() {
        PlayerSkills skills = new PlayerSkills();
        skills.setLevel(Skill.WOODCUTTING, 24);
        float need = SkillMath.xpToNext(24);

        int gained = skills.addXp(Skill.WOODCUTTING, need);

        assertEquals(1, gained);
        assertEquals(25, skills.level(Skill.WOODCUTTING));
        assertEquals(0f, skills.xp(Skill.WOODCUTTING), 1e-2f);
    }

    @Test
    void xpOneUlpShortOfTheRequirementDoesNotLevel() {
        PlayerSkills skills = new PlayerSkills();
        float need = SkillMath.xpToNext(0);
        float justShort = Math.nextDown(need);

        int gained = skills.addXp(Skill.MINING, justShort);

        assertEquals(0, gained, "xp one ulp short of the requirement must not level");
        assertEquals(0, skills.level(Skill.MINING));
        assertTrue(skills.xp(Skill.MINING) > 0f);
    }

    @Test
    void exactBoundaryAtEveryLevelLevelsExactlyOnce() {
        for (int level = 0; level < SkillMath.MAX_LEVEL; level++) {
            PlayerSkills skills = new PlayerSkills();
            skills.setLevel(Skill.MINING, level);
            float need = SkillMath.xpToNext(level);

            int gained = skills.addXp(Skill.MINING, need);

            assertEquals(1, gained, "level " + level + ": exact requirement should gain exactly one level");
            assertEquals(level + 1, skills.level(Skill.MINING), "level " + level);
        }
    }
}
