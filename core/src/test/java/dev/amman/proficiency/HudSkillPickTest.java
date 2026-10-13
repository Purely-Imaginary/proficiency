package dev.amman.proficiency;

import dev.amman.proficiency.skill.HudSkillPick;
import dev.amman.proficiency.skill.Skill;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class HudSkillPickTest {

    private static final long NEVER = Long.MAX_VALUE;

    @Test
    void aHeldItemKeepsItsOwnSkill() {
        assertEquals(Skill.SWORDS, HudSkillPick.pick(Skill.SWORDS, false, 0, true, 0));
        assertNull(HudSkillPick.pick(null, false, 0, false, 0));
    }

    @Test
    void anIdleEmptyHandShowsRunningNotUnarmed() {
        assertEquals(Skill.RUNNING, HudSkillPick.pick(Skill.UNARMED, true, NEVER, false, NEVER));
        assertEquals(Skill.RUNNING, HudSkillPick.pick(Skill.UNARMED, true, HudSkillPick.UNARMED_HOLD_MS, false,
                HudSkillPick.JUMP_HOLD_MS));
    }

    @Test
    void aRecentJumpShowsJumping() {
        assertEquals(Skill.JUMPING, HudSkillPick.pick(Skill.UNARMED, true, NEVER, false, 0));
        assertEquals(Skill.JUMPING, HudSkillPick.pick(Skill.UNARMED, true, NEVER, false, HudSkillPick.JUMP_HOLD_MS - 1));
    }

    @Test
    void unarmedShowsOnlyOnAGainOrItsAbility() {
        assertEquals(Skill.UNARMED, HudSkillPick.pick(Skill.UNARMED, true, 0, false, 0));
        assertEquals(Skill.UNARMED, HudSkillPick.pick(Skill.UNARMED, true, NEVER, true, NEVER));
        // A punch while sprinting or crouching still shows the fist.
        assertEquals(Skill.UNARMED, HudSkillPick.pick(Skill.RUNNING, true, 10, false, NEVER));
        assertEquals(Skill.UNARMED, HudSkillPick.pick(Skill.SNEAKING, true, 10, false, NEVER));
    }

    @Test
    void theOtherEmptyHandMomentsStay() {
        assertEquals(Skill.SWIMMING, HudSkillPick.pick(Skill.SWIMMING, true, NEVER, false, 0));
        assertEquals(Skill.SNEAKING, HudSkillPick.pick(Skill.SNEAKING, true, NEVER, false, 0));
        assertEquals(Skill.ENDURANCE, HudSkillPick.pick(Skill.ENDURANCE, true, NEVER, false, NEVER));
    }
}
