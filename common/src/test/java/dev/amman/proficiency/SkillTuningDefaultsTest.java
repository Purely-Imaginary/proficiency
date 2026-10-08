package dev.amman.proficiency;

import dev.amman.proficiency.config.ProficiencyConfig;
import dev.amman.proficiency.skill.Skill;
import dev.amman.proficiency.skill.SkillTuning;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

/**
 * The skill maths in core reads its numbers through {@link SkillTuning}. Its defaults must be the
 * server config's own fallbacks, number for number, or a test without a config would test other
 * numbers than a server without one runs.
 */
class SkillTuningDefaultsTest {

    @Test
    void defaultsAreTheConfigFallbacks() {
        assertFalse(ProficiencyConfig.SPEC.isLoaded());
        SkillTuning d = SkillTuning.DEFAULTS;
        assertEquals(ProficiencyConfig.curveFloor(), d.curveFloor());
        assertEquals(ProficiencyConfig.curveBase(), d.curveBase());
        assertEquals(ProficiencyConfig.curveExponent(), d.curveExponent());
        assertEquals(ProficiencyConfig.procsEnabled(), d.procsEnabled());
        assertEquals(ProficiencyConfig.procUnlockLevel(), d.procUnlockLevel());
        assertEquals(ProficiencyConfig.procFloor(), d.procFloor());
        assertEquals(ProficiencyConfig.restedCapFactor(), d.restedCapFactor());
        assertEquals(ProficiencyConfig.restedFullHours(), d.restedFullHours());
        assertEquals(ProficiencyConfig.restedExtra(), d.restedExtra());
        assertEquals(ProficiencyConfig.teachFactor(), d.restedTeachFactor());
        assertEquals(ProficiencyConfig.teacherShare(), d.restedTeacherShare());
        for (Skill skill : Skill.VALUES) {
            assertEquals(ProficiencyConfig.enabled(skill), d.enabled(skill), skill.id());
            assertEquals(ProficiencyConfig.maxBonus(skill), d.maxBonus(skill), skill.id());
            assertEquals(ProficiencyConfig.procChance(skill), d.procChance(skill), skill.id());
        }
    }

    @Test
    void theConfigInstallsItselfWhenItLoads() {
        assertEquals(ProficiencyConfig.SPEC.isLoaded(), false);
        // Class init of the config ran in the test above or here; either way it replaced DEFAULTS.
        org.junit.jupiter.api.Assertions.assertNotSame(SkillTuning.DEFAULTS, SkillTuning.current());
        assertEquals(ProficiencyConfig.curveFloor(), SkillTuning.current().curveFloor());
    }
}
