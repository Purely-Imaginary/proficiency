package dev.amman.proficiency;

import dev.amman.proficiency.perk.Talent;
import dev.amman.proficiency.perk.Talents;
import dev.amman.proficiency.skill.PlayerSkills;
import dev.amman.proficiency.skill.Skill;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@code /proficiency reset <player>} calls {@link PlayerSkills#reset()} and is supposed to hand
 * back a totally clean sheet: no levels, no xp, no perks, no visited places, and no lingering
 * cooldown from an ability used the moment before the reset.
 */
class PlayerSkillsResetTest {

    @Test
    void resetClearsLevelsAndXp() {
        PlayerSkills skills = new PlayerSkills();
        skills.setLevel(Skill.MINING, 50);
        skills.addXp(Skill.WOODCUTTING, 5f);

        skills.reset();

        assertEquals(0, skills.level(Skill.MINING));
        assertEquals(0f, skills.xp(Skill.WOODCUTTING));
    }

    @Test
    void resetClearsUnlockedPerks() {
        PlayerSkills skills = new PlayerSkills();
        Talent perk = Talents.of(Skill.MINING).get(0);
        skills.addRank(perk);

        skills.reset();

        assertFalse((skills.rank(perk) > 0), "reset must clear unlocked perks");
    }

    @Test
    void resetClearsVisitedPlaces() {
        PlayerSkills skills = new PlayerSkills();
        skills.markVisited("minecraft:plains");

        skills.reset();

        assertFalse(skills.hasVisited("minecraft:plains"), "reset must clear visited places");
        assertEquals(0, skills.placesSeen());
    }

    @Test
    void resetClearsCooldowns() {
        // This is the one that currently fails: PlayerSkills#reset() clears levels, xp, unlocked
        // perks and visited places, but never touches cooldownUntil. A player who uses an active
        // ability and is reset a moment later keeps that cooldown, on a skill that (post-reset) is
        // level 0 and cannot even use the ability again until it expires on its own. Left in place
        // per instructions: this is a real bug, not a test mistake.
        PlayerSkills skills = new PlayerSkills();
        skills.beginFrenzy(Skill.MINING, 400L, 6_000L);

        skills.reset();

        assertEquals(0L, skills.cooldownRemaining(Skill.MINING, 0L),
                "reset must clear cooldowns, but PlayerSkills#reset() never touches cooldownUntil");
    }
}
