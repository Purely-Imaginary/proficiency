package dev.amman.proficiency;

import dev.amman.proficiency.skill.CritRules;
import dev.amman.proficiency.skill.Skill;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CritRulesTest {

    @Test
    void onlyAMeleeCritPaysOrRollsMore() {
        for (Skill skill : new Skill[] {Skill.SWORDS, Skill.AXES, Skill.MACES, Skill.TRIDENTS, Skill.UNARMED}) {
            assertEquals(1.5, CritRules.xpFactor(skill, true), 1e-9, skill.id());
            assertEquals(2.0, CritRules.procChanceScale(skill, true), 1e-9, skill.id());
            assertEquals(1.0, CritRules.xpFactor(skill, false), 1e-9, skill.id());
        }
        for (Skill skill : new Skill[] {Skill.ARCHERY, Skill.CROSSBOWS, Skill.MINING, Skill.JUMPING}) {
            assertFalse(CritRules.isMelee(skill), skill.id());
            assertEquals(1.0, CritRules.xpFactor(skill, true), 1e-9, skill.id());
            assertEquals(1.0, CritRules.procChanceScale(skill, true), 1e-9, skill.id());
        }
    }

    @Test
    void aDoubledChanceNeverPassesCertain() {
        assertEquals(0.3, CritRules.scaledChance(0.15, 2.0), 1e-9);
        assertEquals(1.0, CritRules.scaledChance(0.7, 2.0), 1e-9);
        assertEquals(0.0, CritRules.scaledChance(0.0, 2.0), 1e-9);
        assertEquals(0.0, CritRules.scaledChance(Double.NaN, 2.0), 1e-9);
        assertEquals(0.2, CritRules.scaledChance(0.2, 1.0), 1e-9);
    }

    @Test
    void lungeCoolsDownNineSixThreeSeconds() {
        assertEquals(0, CritRules.lungeCooldownTicks(0));
        assertEquals(180, CritRules.lungeCooldownTicks(1));
        assertEquals(120, CritRules.lungeCooldownTicks(2));
        assertEquals(60, CritRules.lungeCooldownTicks(3));
        assertEquals(60, CritRules.lungeCooldownTicks(9));
        assertTrue(CritRules.lungeReady(100, Long.MIN_VALUE));
        assertFalse(CritRules.lungeReady(100, 101));
        assertTrue(CritRules.lungeReady(101, 101));
    }

    @Test
    void leapingBladeRaisesACritAndNeverLowersOne() {
        assertEquals(1.5f, CritRules.critDamage(1.0f, false), 1e-6);
        assertEquals(1.5f, CritRules.critDamage(1.5f, false), 1e-6);
        assertEquals(1.75f, CritRules.critDamage(1.5f, true), 1e-6);
        // Another mod's bigger crit stays.
        assertEquals(2.0f, CritRules.critDamage(2.0f, true), 1e-6);
    }

    @Test
    void rendAndFlowNumbers() {
        assertEquals(1.15f, CritRules.rendMultiplier(100, 100), 1e-6);
        assertEquals(1.0f, CritRules.rendMultiplier(101, 100), 1e-6);
        assertEquals(0, CritRules.flowTicks(0));
        assertEquals(20, CritRules.flowTicks(1));
        assertEquals(60, CritRules.flowTicks(3));
        assertEquals(60, CritRules.flowTicks(5));
    }
}
