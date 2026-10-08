package dev.amman.proficiency;

import static org.junit.jupiter.api.Assertions.assertEquals;

import dev.amman.proficiency.perk.PerkEffect;
import dev.amman.proficiency.skill.PlayerSkills;
import dev.amman.proficiency.skill.Skill;
import org.junit.jupiter.api.Test;

/**
 * The tree screen's numbers come apart into ranks, synergies and Discipline; those parts must
 * multiply back to exactly what gameplay reads, for every skill and effect, with a full tree.
 */
class SkillNumbersSourceTest {

    @Test
    void thePartsMultiplyBackToTheGameplayValue() {
        PlayerSkills skills = new PlayerSkills();
        for (Skill skill : Skill.VALUES) {
            skills.setLevel(skill, 100);
            skills.fillTree(skill);
        }
        for (Skill skill : Skill.VALUES) {
            for (PerkEffect effect : PerkEffect.values()) {
                if (effect == PerkEffect.DEATH_WARD) {
                    continue;
                }
                PlayerSkills.Modifier m = skills.modifier(skill, effect);
                double raw = m.ranks() * m.synergies() * m.discipline();
                double clamped = effect == PerkEffect.ABILITY_COOLDOWN ? Math.max(0.25, raw) : Math.max(0, raw);
                assertEquals(clamped, m.total(), 1e-12, skill + " " + effect);
                assertEquals(m.total(), skills.perkModifier(skill, effect), 1e-12);
            }
            assertEquals(skills.bonus(skill), skills.bonusAt(skill, skills.level(skill)), 1e-12);
            assertEquals(skills.procChance(skill), skills.procChanceAt(skill, skills.level(skill)), 1e-12);
            assertEquals(skills.perkModifier(skill, PerkEffect.PROC_POWER), skills.procPower(skill), 1e-12);
        }
    }

    @Test
    void theNextLevelIsHigherBelowTheTop() {
        PlayerSkills skills = new PlayerSkills();
        skills.setLevel(Skill.MINING, 40);
        assertEquals(true, skills.bonusAt(Skill.MINING, 41) > skills.bonus(Skill.MINING));
        assertEquals(true, skills.procChanceAt(Skill.MINING, 41) > skills.procChance(Skill.MINING));
    }
}
