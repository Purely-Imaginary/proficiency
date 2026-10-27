package dev.amman.proficiency;

import dev.amman.proficiency.skill.Achievements;
import dev.amman.proficiency.skill.PlayerSkills;
import dev.amman.proficiency.skill.Skill;
import dev.amman.proficiency.skill.SkillCategory;
import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The state-derived advancements: a veteran's save earns exactly what it shows, nothing more. */
class AchievementsTest {

    @Test
    void aFreshSaveEarnsOnlyTheRoot() {
        assertEquals(Set.of(Achievements.ROOT), Achievements.earned(new PlayerSkills()));
    }

    @Test
    void levelTiersAreBestOfCategory() {
        PlayerSkills skills = new PlayerSkills();
        Skill combat = null;
        for (Skill s : Skill.VALUES) {
            if (s.category() == SkillCategory.COMBAT) {
                combat = s;
                break;
            }
        }
        skills.setLevel(combat, 26);
        Set<String> ids = Achievements.earned(skills);
        assertTrue(ids.contains("level_combat_10") && ids.contains("level_combat_25"));
        assertFalse(ids.contains("level_combat_50"));
        assertFalse(ids.contains("level_gathering_10"));
    }

    @Test
    void jackNeedsEverySkill() {
        PlayerSkills skills = new PlayerSkills();
        for (Skill s : Skill.VALUES) {
            skills.setLevel(s, 10);
        }
        assertTrue(Achievements.earned(skills).contains(Achievements.JACK));
        skills.setLevel(Skill.VALUES[0], 9);
        assertFalse(Achievements.earned(skills).contains(Achievements.JACK));
        assertFalse(Achievements.earned(skills).contains(Achievements.MASTER_OF_ALL));
    }

    @Test
    void starsNeedALevelHundredSkill() {
        PlayerSkills skills = new PlayerSkills();
        Skill s = Skill.VALUES[0];
        skills.setLevel(s, 100);
        skills.setStars(s, 1);
        Set<String> ids = Achievements.earned(skills);
        assertTrue(ids.contains(Achievements.FIRST_STAR));
        assertFalse(ids.contains(Achievements.FIVE_STARS));
        skills.setStars(s, 5);
        assertTrue(Achievements.earned(skills).contains(Achievements.FIVE_STARS));
    }

    @Test
    void expertiseFullNeedsEveryNode() {
        PlayerSkills skills = new PlayerSkills();
        assertFalse(Achievements.earned(skills).contains(Achievements.FIRST_EXPERTISE));
        skills.fillExpertise();
        Set<String> ids = Achievements.earned(skills);
        assertTrue(ids.contains(Achievements.FIRST_EXPERTISE) && ids.contains(Achievements.EXPERTISE_FULL));
    }

    @Test
    void stageMilestoneKeys() {
        assertEquals(3, Achievements.stageOfMilestone("1234|stage:3"));
        assertEquals(0, Achievements.stageOfMilestone("1234|feast"));
        assertEquals(0, Achievements.stageOfMilestone("1234|stage:x"));
        assertEquals(6, Achievements.stageOfMilestone("a|stage:9"));
    }

    @Test
    void grandMasterNeedsAllFiveStarsWhateverTheCap() {
        PlayerSkills skills = new PlayerSkills();
        Skill s = Skill.VALUES[0];
        skills.setLevel(s, 100);
        skills.setStars(s, 4);
        assertFalse(Achievements.earned(skills).contains(Achievements.FIVE_STARS));
    }

    @Test
    void onlyTheBarsNeedsBarsLostAndManyLevels() {
        assertFalse(Achievements.onlyTheBars(1500, 0));
        assertFalse(Achievements.onlyTheBars(999, 4));
        assertTrue(Achievements.onlyTheBars(1000, 1));
    }

    @Test
    void siegeLevelsClampAndUnknownIsZero() {
        assertEquals(0, Achievements.siegeLevelOf(0));
        assertEquals(3, Achievements.siegeLevelOf(3));
        assertEquals(5, Achievements.siegeLevelOf(9));
        assertTrue(Achievements.isSiegeMilestone("77|siege"));
        assertFalse(Achievements.isSiegeMilestone("77|feast"));
    }

    @Test
    void idsAreUniqueAndComplete() {
        Set<String> all = Achievements.all();
        assertEquals(1 + SkillCategory.values().length * 4 + 8 + 6 + 5 + 5, all.size());
        assertTrue(all.containsAll(Achievements.earned(new PlayerSkills())));
    }
}
