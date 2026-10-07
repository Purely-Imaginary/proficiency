package dev.amman.proficiency;

import com.mojang.serialization.JsonOps;
import dev.amman.proficiency.skill.PlayerSkills;
import dev.amman.proficiency.skill.Skill;
import dev.amman.proficiency.skill.SkillMath;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PlayerSkillsTest {

    @Test
    void oneUseAtATimeEventuallyMaxesOut() {
        PlayerSkills skills = new PlayerSkills();
        int uses = 0;
        while (skills.level(Skill.MINING) < SkillMath.MAX_LEVEL && uses < 200_000) {
            skills.addXp(Skill.MINING, 1.0f);
            uses++;
        }
        assertEquals(SkillMath.MAX_LEVEL, skills.level(Skill.MINING));
        assertTrue(uses > 30_000 && uses < 60_000, "took " + uses + " uses");
    }

    @Test
    void aMaxedSkillStopsBanking() {
        PlayerSkills skills = new PlayerSkills();
        skills.setLevel(Skill.SWORDS, SkillMath.MAX_LEVEL);
        assertEquals(0, skills.addXp(Skill.SWORDS, 10_000f));
        assertEquals(0f, skills.xp(Skill.SWORDS));
        assertEquals(SkillMath.MAX_LEVEL, skills.level(Skill.SWORDS));
    }

    @Test
    void oneBigGrantCanCrossSeveralLevels() {
        PlayerSkills skills = new PlayerSkills();
        int gained = skills.addXp(Skill.RUNNING, 50f);
        assertTrue(gained > 1, "expected multiple levels, got " + gained);
        assertEquals(gained, skills.level(Skill.RUNNING));
    }

    @Test
    void deathWipesTheBarButNeverALevel() {
        PlayerSkills skills = new PlayerSkills();
        skills.setLevel(Skill.SWORDS, 40);
        skills.addXp(Skill.SWORDS, SkillMath.xpToNext(40) / 2f);
        skills.setLevel(Skill.MINING, 10);                        // level, empty bar
        // FISHING deliberately left at zero.

        Map<Skill, Float> lost = skills.applyDeathPenalty();

        assertEquals(1, lost.size(), "only a skill with a partial bar loses anything");
        assertEquals(0.5f, lost.get(Skill.SWORDS), 0.01f);
        assertEquals(40, skills.level(Skill.SWORDS));
        assertEquals(0f, skills.xp(Skill.SWORDS));
        assertEquals(10, skills.level(Skill.MINING));
        assertFalse(lost.containsKey(Skill.FISHING));
    }

    @Test
    void deathAtLevelZeroTakesOnlyTheBar() {
        PlayerSkills skills = new PlayerSkills();
        skills.addXp(Skill.AXES, 0.5f);
        skills.applyDeathPenalty();
        assertEquals(0, skills.level(Skill.AXES));
        assertEquals(0f, skills.xp(Skill.AXES));
    }

    @Test
    void anEmptyBarDeathIsANoOp() {
        PlayerSkills skills = new PlayerSkills();
        skills.setLevel(Skill.AXES, 30);
        skills.clearDirty();
        assertTrue(skills.applyDeathPenalty().isEmpty());
        assertEquals(30, skills.level(Skill.AXES));
        assertFalse(skills.isDirty(), "nothing changed, so nothing to sync");
    }

    @Test
    void survivesASaveAndLoadRoundTrip() {
        PlayerSkills original = new PlayerSkills();
        original.setLevel(Skill.WOODCUTTING, 17);
        original.addXp(Skill.ALCHEMY, 3.5f);

        var encoded = PlayerSkills.CODEC.encodeStart(JsonOps.INSTANCE, original)
                .getOrThrow(false, message -> { throw new AssertionError("encode failed: " + message); });
        PlayerSkills restored = PlayerSkills.CODEC.parse(JsonOps.INSTANCE, encoded)
                .getOrThrow(false, message -> { throw new AssertionError("decode failed: " + message); });

        for (Skill skill : Skill.VALUES) {
            assertEquals(original.level(skill), restored.level(skill), skill.id() + " level");
            assertEquals(original.xp(skill), restored.xp(skill), 1e-4, skill.id() + " xp");
        }
    }

    @Test
    void everySkillIdIsUniqueAndResolvable() {
        for (Skill skill : Skill.VALUES) {
            assertEquals(skill, Skill.byId(skill.id()));
        }
        assertEquals(Skill.VALUES.length,
                java.util.Arrays.stream(Skill.VALUES).map(Skill::id).distinct().count());
    }
}
