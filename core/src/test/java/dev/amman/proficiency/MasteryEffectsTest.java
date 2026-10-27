package dev.amman.proficiency;

import dev.amman.proficiency.skill.Mastery;
import dev.amman.proficiency.skill.MasteryEffects;
import dev.amman.proficiency.skill.PlayerSkills;
import dev.amman.proficiency.skill.Skill;
import dev.amman.proficiency.skill.SkillMath;
import dev.amman.proficiency.skill.SkillTuning;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The Mastery riders' table: every skill has one, it grows with every star, and death leaves it alone. */
class MasteryEffectsTest {

    @AfterEach
    void restoreTuning() {
        SkillTuning.install(SkillTuning.DEFAULTS);
    }

    private static SkillTuning capped(int cap) {
        return new SkillTuning() {
            @Override public double curveFloor() { return DEFAULTS.curveFloor(); }
            @Override public double curveBase() { return DEFAULTS.curveBase(); }
            @Override public double curveExponent() { return DEFAULTS.curveExponent(); }
            @Override public boolean enabled(Skill skill) { return true; }
            @Override public double maxBonus(Skill skill) { return DEFAULTS.maxBonus(skill); }
            @Override public boolean procsEnabled() { return true; }
            @Override public int procUnlockLevel() { return 25; }
            @Override public double procFloor() { return 0.05; }
            @Override public double procChance(Skill skill) { return DEFAULTS.procChance(skill); }
            @Override public int masteryMaxStars() { return cap; }
        };
    }

    @Test
    void everySkillHasARiderThatGrowsWithEveryStar() {
        for (Skill skill : Skill.VALUES) {
            MasteryEffects.Effect effect = MasteryEffects.of(skill);
            assertNotNull(effect, skill + " has no Mastery rider");
            assertEquals(0.0, effect.at(0), skill + " star 0");
            for (int star = 1; star <= Mastery.MAX_STARS; star++) {
                assertTrue(effect.at(star) > 0, skill + " star " + star + " must do something");
                if (star > 1) {
                    // Every star is felt: a step up at each of them, never a plateau.
                    assertTrue(effect.at(star) > effect.at(star - 1), skill + " must grow at star " + star);
                }
            }
            assertTrue(effect.at(Mastery.MAX_STARS) > effect.at(1), skill + " star 5 must beat star 1");
            assertEquals(effect.at(Mastery.MAX_STARS), effect.at(9), skill + " clamps above 5");
        }
    }

    @Test
    void theRiderNeedsLevelOneHundredAndTheConfiguredStars() {
        PlayerSkills skills = new PlayerSkills();
        skills.setStars(Skill.SWORDS, 3);
        skills.setLevel(Skill.SWORDS, 99);
        assertEquals(0, MasteryEffects.stars(skills, Skill.SWORDS), "below 100 nothing counts");
        skills.setLevel(Skill.SWORDS, SkillMath.MAX_LEVEL);
        skills.setStars(Skill.SWORDS, 3);
        assertEquals(3, MasteryEffects.stars(skills, Skill.SWORDS));
        assertEquals(7.0, MasteryEffects.value(skills, Skill.SWORDS));
        skills.setStars(Skill.SWORDS, 0);
        assertEquals(0.0, MasteryEffects.value(skills, Skill.SWORDS));
    }

    @Test
    void aLoweredCapLowersTheRiderAndZeroSwitchesItOff() {
        PlayerSkills skills = new PlayerSkills();
        skills.setLevel(Skill.MINING, SkillMath.MAX_LEVEL);
        skills.setStars(Skill.MINING, 5);
        SkillTuning.install(capped(2));
        assertEquals(2, MasteryEffects.stars(skills, Skill.MINING));
        SkillTuning.install(capped(0));
        assertEquals(0, MasteryEffects.stars(skills, Skill.MINING));
        assertEquals(0.0, MasteryEffects.value(skills, Skill.MINING));
    }

    @Test
    void aDeathNeverTakesTheRiderAway() {
        PlayerSkills skills = new PlayerSkills();
        skills.setLevel(Skill.AXES, SkillMath.MAX_LEVEL);
        skills.setStars(Skill.AXES, 4);
        double before = MasteryEffects.value(skills, Skill.AXES);
        skills.applyDeathPenalty();
        assertEquals(before, MasteryEffects.value(skills, Skill.AXES));
        assertEquals(SkillMath.MAX_LEVEL, skills.level(Skill.AXES));
    }

    @Test
    void everyRiderHasItsEnglishText() throws Exception {
        String en = java.nio.file.Files.readString(TestPaths.LANG.resolve("en_us.json"));
        for (Skill skill : Skill.VALUES) {
            MasteryEffects.Effect effect = MasteryEffects.of(skill);
            for (String key : new String[] {effect.nameKey(), effect.descKey(), effect.shortKey()}) {
                assertTrue(en.contains("\"" + key + "\""), key + " is missing from en_us");
            }
        }
    }

    @Test
    void valuesAreWrittenPlainly() {
        assertEquals("6", MasteryEffects.format(6.0));
        assertEquals("1.5", MasteryEffects.format(1.5));
        assertEquals(60, MasteryEffects.ticks(3.0));
    }
}
