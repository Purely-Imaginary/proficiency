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
 * {@link PlayerSkills#copyFrom} is what carries a player's skills across death (the fresh
 * attachment created for the respawned entity copies from the one that died). It must deep-copy:
 * mutating the source afterwards must never reach back into the copy.
 */
class PlayerSkillsCopyFromTest {

    @Test
    void mutatingTheSourceAfterCopyFromDoesNotChangeTheCopy() {
        PlayerSkills source = new PlayerSkills();
        source.setLevel(Skill.MINING, 40);
        Talent apprentice = Talents.of(Skill.MINING).get(0);
        source.addRank(apprentice);
        source.markVisited("minecraft:plains");
        source.beginFrenzy(Skill.MINING, 400L, 6_000L);

        PlayerSkills destination = new PlayerSkills();
        destination.copyFrom(source);

        // Now mutate the source in every way that matters.
        source.setLevel(Skill.MINING, 90);
        Talent journeyman = Talents.of(Skill.MINING).get(1);
        source.addRank(journeyman);
        source.markVisited("minecraft:the_end");
        source.beginFrenzy(Skill.MINING, 999_999L, 999_999L);

        assertEquals(40, destination.level(Skill.MINING),
                "the copy's level must not follow the source's later change");
        assertFalse((destination.rank(journeyman) > 0),
                "a perk granted to the source after copyFrom must not appear in the copy");
        assertTrue((destination.rank(apprentice)) > 0);
        assertFalse(destination.hasVisited("minecraft:the_end"),
                "a place visited by the source after copyFrom must not appear in the copy");
        assertEquals(6_000L, destination.cooldownRemaining(Skill.MINING, 0L),
                "the copy's cooldown must not follow a frenzy the source began after copyFrom");
    }

    @Test
    void copyFromReplacesRatherThanMergesUnlockedPerks() {
        PlayerSkills source = new PlayerSkills();
        Talent sourcePerk = Talents.of(Skill.MINING).get(0);
        source.addRank(sourcePerk);

        PlayerSkills destination = new PlayerSkills();
        Talent destinationOnlyPerk = Talents.of(Skill.WOODCUTTING).get(0);
        destination.addRank(destinationOnlyPerk);

        destination.copyFrom(source);

        assertTrue((destination.rank(sourcePerk)) > 0);
        assertFalse((destination.rank(destinationOnlyPerk) > 0),
                "copyFrom must replace the destination's perk set, not merge into it");
    }

    @Test
    void copyFromCopiesCooldownsPerSkill() {
        PlayerSkills source = new PlayerSkills();
        source.beginFrenzy(Skill.MINING, 100L, 500L);
        source.beginFrenzy(Skill.WOODCUTTING, 200L, 9_000L);

        PlayerSkills destination = new PlayerSkills();
        destination.copyFrom(source);

        assertEquals(500L, destination.cooldownRemaining(Skill.MINING, 0L));
        assertEquals(9_000L, destination.cooldownRemaining(Skill.WOODCUTTING, 0L));
    }
}
