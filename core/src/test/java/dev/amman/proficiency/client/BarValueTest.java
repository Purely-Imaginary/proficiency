package dev.amman.proficiency.client;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.amman.proficiency.skill.Mastery;
import dev.amman.proficiency.skill.PlayerSkills;
import dev.amman.proficiency.skill.Skill;
import dev.amman.proficiency.skill.SkillMath;
import dev.amman.proficiency.skill.SkillTuning;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

/** The HUD bar's one continuous value: a Mastery star is a gain, never a drop from 100.95 to 100.02. */
class BarValueTest {

    @AfterEach
    void restore() {
        SkillTuning.install(SkillTuning.DEFAULTS);
    }

    private static void cap(int stars) {
        SkillTuning d = SkillTuning.DEFAULTS;
        SkillTuning.install(new SkillTuning() {
            @Override public double curveFloor() { return d.curveFloor(); }
            @Override public double curveBase() { return d.curveBase(); }
            @Override public double curveExponent() { return d.curveExponent(); }
            @Override public boolean enabled(Skill skill) { return true; }
            @Override public double maxBonus(Skill skill) { return d.maxBonus(skill); }
            @Override public boolean procsEnabled() { return true; }
            @Override public int procUnlockLevel() { return 25; }
            @Override public double procFloor() { return 0.05; }
            @Override public double procChance(Skill skill) { return d.procChance(skill); }
            @Override public int masteryMaxStars() { return stars; }
        });
    }

    @Test
    void earningAStarRaisesTheValueInsteadOfWrapping() {
        double before = BarValue.value(100, 0.95f, 0);
        double after = BarValue.value(100, 0.02f, 1);
        assertTrue(after > before, "a star read as a loss: " + before + " -> " + after);
        // The dots machinery then treats it as the small gain it is.
        XpGainDots dots = new XpGainDots();
        dots.snap(before, 0);
        dots.gain(before, after, 10);
        assertTrue(dots.dots().size() > 0, "a star must launch dots, not snap the bar");
    }

    @Test
    void levelNinetyNineToOneHundredIsAGainToo() {
        assertTrue(BarValue.value(100, 0f, 0) > BarValue.value(99, 0.9f, 0));
        assertEquals(0.0, BarValue.fill(BarValue.value(100, 0f, 0)), 1e-9, "level 100 starts an empty star bar");
    }

    @Test
    void theStarBarFillsBetweenStars() {
        assertEquals(0.5, BarValue.fill(BarValue.value(100, 0.5f, 2)), 1e-6);
        assertEquals(0.0, BarValue.fill(BarValue.value(100, 0.0f, 3)), 1e-6);
    }

    @Test
    void everyStarOrNoStarsAtAllReadsAFullBar() {
        assertEquals(1.0, BarValue.fill(BarValue.value(100, 1.0f, 5)), 0);
        assertEquals(100 + 5, BarValue.value(100, 1.0f, 5), 0);
        cap(3);
        assertEquals(1.0, BarValue.fill(BarValue.value(100, 1.0f, 5)), 0, "stars above a lowered cap read full");
        assertEquals(103, BarValue.value(100, 1.0f, 5), 0);
        cap(0);
        assertEquals(1.0, BarValue.fill(BarValue.value(100, 1.0f, 0)), 0, "Mastery off: level 100 is a full bar");
        assertEquals(100, BarValue.value(100, 1.0f, 0), 0);
    }

    @Test
    void theSparklineNeverReads101() {
        PlayerSkills skills = new PlayerSkills();
        skills.setLevel(Skill.MINING, SkillMath.MAX_LEVEL);
        assertTrue(BarValue.value(skills, Skill.MINING) < 101.0);
        assertEquals(Mastery.maxStars() > 0 ? 100.0 : 100.0, BarValue.value(skills, Skill.MINING), 1e-6);
    }
}
