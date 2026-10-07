package dev.amman.proficiency;

import dev.amman.proficiency.skill.PlayerSkills;
import dev.amman.proficiency.skill.Skill;
import dev.amman.proficiency.skill.SkillMath;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * {@link PlayerSkills#setLevel} is what backs {@code /proficiency set}, an op command that takes
 * a bare int with no range check of its own before it reaches here. It must clamp both ends and
 * always zero out banked xp, even when it is only re-setting a level the skill is already at.
 */
class PlayerSkillsSetLevelClampTest {

    @Test
    void setLevelClampsAboveMaxToMaxLevel() {
        PlayerSkills skills = new PlayerSkills();
        skills.setLevel(Skill.MINING, 999);
        assertEquals(SkillMath.MAX_LEVEL, skills.level(Skill.MINING));
    }

    @Test
    void setLevelClampsAtIntegerMaxValue() {
        PlayerSkills skills = new PlayerSkills();
        skills.setLevel(Skill.MINING, Integer.MAX_VALUE);
        assertEquals(SkillMath.MAX_LEVEL, skills.level(Skill.MINING));
    }

    @Test
    void setLevelClampsBelowZeroToZero() {
        PlayerSkills skills = new PlayerSkills();
        skills.setLevel(Skill.MINING, -50);
        assertEquals(0, skills.level(Skill.MINING));
    }

    @Test
    void setLevelClampsAtIntegerMinValue() {
        PlayerSkills skills = new PlayerSkills();
        skills.setLevel(Skill.MINING, Integer.MIN_VALUE);
        assertEquals(0, skills.level(Skill.MINING));
    }

    @Test
    void setLevelAlwaysResetsBankedXp() {
        PlayerSkills skills = new PlayerSkills();
        skills.addXp(Skill.WOODCUTTING, 0.5f);
        assertEquals(0.5f, skills.xp(Skill.WOODCUTTING), 1e-6f, "sanity: xp actually banked first");

        skills.setLevel(Skill.WOODCUTTING, 10);

        assertEquals(0f, skills.xp(Skill.WOODCUTTING), "setLevel must clear banked xp");
        assertEquals(10, skills.level(Skill.WOODCUTTING));
    }

    @Test
    void setLevelResetsXpEvenWhenTheLevelDoesNotActuallyChange() {
        PlayerSkills skills = new PlayerSkills();
        skills.setLevel(Skill.WOODCUTTING, 10);
        skills.addXp(Skill.WOODCUTTING, 0.2f);

        skills.setLevel(Skill.WOODCUTTING, 10);

        assertEquals(0f, skills.xp(Skill.WOODCUTTING),
                "re-setting the same level must still zero any xp banked since the last set");
    }

    @Test
    void setLevelMarksTheStateDirty() {
        PlayerSkills skills = new PlayerSkills();
        skills.clearDirty();
        skills.setLevel(Skill.MINING, 5);
        assertEquals(true, skills.isDirty());
    }
}
