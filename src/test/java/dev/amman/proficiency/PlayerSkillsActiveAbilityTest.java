package dev.amman.proficiency;

import dev.amman.proficiency.skill.PlayerSkills;
import dev.amman.proficiency.skill.Skill;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@link PlayerSkills#beginFrenzy}, {@link PlayerSkills#cooldownRemaining} and
 * {@link PlayerSkills#frenzyRemaining} are the arithmetic {@code ActiveService} leans on. Exercised
 * here directly on {@link PlayerSkills} rather than through {@code ActiveService}, since the
 * service itself needs a real {@code ServerPlayer} for its level's game time.
 */
class PlayerSkillsActiveAbilityTest {

    @Test
    void freshSkillsHaveNoCooldownAndNoFrenzy() {
        PlayerSkills skills = new PlayerSkills();
        assertEquals(0L, skills.cooldownRemaining(Skill.MINING, 0L));
        assertEquals(0L, skills.frenzyRemaining(Skill.MINING, 0L));
        assertEquals(0L, skills.cooldownRemaining(Skill.MINING, 1_000_000L));
        assertEquals(0L, skills.frenzyRemaining(Skill.MINING, 1_000_000L));
    }

    @Test
    void beginFrenzySetsBothTheFrenzyWindowAndTheLongerCooldown() {
        PlayerSkills skills = new PlayerSkills();
        skills.beginFrenzy(Skill.MINING, 400L, 6_000L);
        assertEquals(400L, skills.frenzyRemaining(Skill.MINING, 0L));
        assertEquals(6_000L, skills.cooldownRemaining(Skill.MINING, 0L));
    }

    @Test
    void cooldownRemainingIsExactlyZeroAtTheExpiryTick() {
        PlayerSkills skills = new PlayerSkills();
        skills.beginFrenzy(Skill.MINING, 400L, 6_000L);
        // now == cooldownUntil: the ability should be usable again, not still one tick short.
        assertEquals(0L, skills.cooldownRemaining(Skill.MINING, 6_000L));
        // One tick earlier it must still be on cooldown.
        assertEquals(1L, skills.cooldownRemaining(Skill.MINING, 5_999L));
    }

    @Test
    void frenzyRemainingIsExactlyZeroAtTheExpiryTick() {
        PlayerSkills skills = new PlayerSkills();
        skills.beginFrenzy(Skill.MINING, 400L, 6_000L);
        assertEquals(0L, skills.frenzyRemaining(Skill.MINING, 400L));
        assertEquals(1L, skills.frenzyRemaining(Skill.MINING, 399L));
    }

    @Test
    void aFrenzyWhoseEndIsAlreadyBeforeNowReportsZeroNotNegative() {
        PlayerSkills skills = new PlayerSkills();
        // frenzyEnd is in the past relative to "now": e.g. a frenzy begun long ago and never
        // re-queried until well after it should have ended.
        skills.beginFrenzy(Skill.MINING, 100L, 200L);
        assertEquals(0L, skills.frenzyRemaining(Skill.MINING, 10_000L));
        assertEquals(0L, skills.cooldownRemaining(Skill.MINING, 10_000L));
    }

    @Test
    void gameTimeGoingBackwardsMakesRemainingDurationsGrowInsteadOfShrink() {
        // A world whose game time got reset backwards (e.g. restored from an older save) is not
        // impossible. beginFrenzy stores absolute end ticks, so cooldownRemaining/frenzyRemaining
        // are simple subtraction with a floor at zero: they do not know "now" ever moved forward
        // past 1_000 and back down, so a backwards jump reports MORE time remaining than the
        // original 20s/5min window, not less. Pinning this down because it is surprising, not
        // because it looks fixable without ActiveService tracking real elapsed time itself.
        PlayerSkills skills = new PlayerSkills();
        long start = 10_000L;
        skills.beginFrenzy(Skill.MINING, start + 400L, start + 6_000L);
        assertEquals(400L, skills.frenzyRemaining(Skill.MINING, start));
        assertEquals(6_000L, skills.cooldownRemaining(Skill.MINING, start));

        // Time resets backwards, e.g. to 0.
        long rewound = 0L;
        assertEquals(10_400L, skills.frenzyRemaining(Skill.MINING, rewound),
                "remaining frenzy grew past its original 400-tick duration after time rewound");
        assertEquals(16_000L, skills.cooldownRemaining(Skill.MINING, rewound),
                "remaining cooldown grew past its original 6000-tick duration after time rewound");
    }

    @Test
    void cooldownIsPerSkillAndDoesNotLeakToOthers() {
        PlayerSkills skills = new PlayerSkills();
        skills.beginFrenzy(Skill.MINING, 400L, 6_000L);
        assertEquals(0L, skills.cooldownRemaining(Skill.WOODCUTTING, 0L));
        assertEquals(0L, skills.frenzyRemaining(Skill.WOODCUTTING, 0L));
    }

    @Test
    void beginFrenzyMarksTheStateDirty() {
        PlayerSkills skills = new PlayerSkills();
        skills.clearDirty();
        skills.beginFrenzy(Skill.MINING, 400L, 6_000L);
        assertTrue(skills.isDirty());
    }
}
