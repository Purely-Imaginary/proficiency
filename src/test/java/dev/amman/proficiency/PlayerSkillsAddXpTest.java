package dev.amman.proficiency;

import dev.amman.proficiency.skill.PlayerSkills;
import dev.amman.proficiency.skill.Skill;
import dev.amman.proficiency.skill.SkillMath;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@link PlayerSkills#addXp} guards against non-positive input with a single {@code amount <= 0}
 * check. IEEE 754 makes every relational comparison against NaN false, including {@code <= 0}, so
 * that guard does not actually catch NaN. These pin down what absurd inputs do, rather
 * than what the one existing guard suggests they do.
 */
class PlayerSkillsAddXpTest {

    @Test
    void negativeAmountsAreRejectedAndChangeNothing() {
        PlayerSkills skills = new PlayerSkills();
        skills.addXp(Skill.MINING, 3f);
        float before = skills.xp(Skill.MINING);
        skills.clearDirty();

        int gained = skills.addXp(Skill.MINING, -50f);

        assertEquals(0, gained);
        assertEquals(before, skills.xp(Skill.MINING));
        assertFalse(skills.isDirty(), "a rejected grant should not even mark the skill dirty");
    }

    @Test
    void zeroAmountIsRejectedAndChangesNothing() {
        PlayerSkills skills = new PlayerSkills();
        assertEquals(0, skills.addXp(Skill.MINING, 0f));
        assertEquals(0, skills.level(Skill.MINING));
        assertEquals(0f, skills.xp(Skill.MINING));
    }

    /**
     * NaN used to slip past the {@code amount <= 0} guard, because every relational comparison
     * against NaN is false. It then poisoned the banked xp, and the loop's {@code xp < need} check
     * was false for the same reason, so it walked the skill to 100 in one call and the max-level
     * cleanup wiped the evidence. Silent, free, and invisible in the logs. Found by a test rather
     * than by a player, which is the only acceptable way to find it.
     */
    @Test
    void nanIsRejectedRatherThanSilentlyMaxingTheSkill() {
        PlayerSkills skills = new PlayerSkills();
        assertEquals(0, skills.addXp(Skill.MINING, Float.NaN));
        assertEquals(0, skills.level(Skill.MINING));
        assertEquals(0f, skills.xp(Skill.MINING));
        assertFalse(skills.isDirty());
    }

    /**
     * Infinity was the same hole as NaN wearing a different hat: it passed the positive check and
     * then satisfied every level-up comparison, maxing the skill in one grant. Both are rejected by
     * one {@code Float.isFinite} test now.
     */
    @Test
    void positiveInfinityIsRejectedRatherThanMaxingTheSkill() {
        PlayerSkills skills = new PlayerSkills();
        assertEquals(0, skills.addXp(Skill.MINING, Float.POSITIVE_INFINITY));
        assertEquals(0, skills.level(Skill.MINING));
        assertEquals(0f, skills.xp(Skill.MINING));
        assertFalse(skills.isDirty());
    }

    @Test
    void hugeFiniteAmountMaxesTheSkillWithoutHanging() {
        PlayerSkills skills = new PlayerSkills();

        int gained = skills.addXp(Skill.SWORDS, Float.MAX_VALUE);

        assertEquals(SkillMath.MAX_LEVEL, skills.level(Skill.SWORDS));
        assertEquals(SkillMath.MAX_LEVEL, gained);
        assertEquals(0f, skills.xp(Skill.SWORDS));
    }

    @Test
    void negativeInfinityIsRejectedLikeAnyOtherNonPositiveAmount() {
        PlayerSkills skills = new PlayerSkills();
        assertEquals(0, skills.addXp(Skill.SWORDS, Float.NEGATIVE_INFINITY));
        assertEquals(0, skills.level(Skill.SWORDS));
        assertEquals(0f, skills.xp(Skill.SWORDS));
    }
}
