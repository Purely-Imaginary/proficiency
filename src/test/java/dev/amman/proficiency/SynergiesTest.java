package dev.amman.proficiency;

import dev.amman.proficiency.perk.PerkEffect;
import dev.amman.proficiency.perk.Synergies;
import dev.amman.proficiency.perk.Synergy;
import dev.amman.proficiency.perk.Talent;
import dev.amman.proficiency.perk.Talents;
import dev.amman.proficiency.skill.PlayerSkills;
import dev.amman.proficiency.skill.Skill;
import dev.amman.proficiency.skill.SkillCategory;
import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The synergies between trees, and Discipline under them. */
class SynergiesTest {

    @Test
    void everyNeedNamesARealNodeAtAReachableRank() {
        for (Synergy synergy : Synergies.all()) {
            for (Synergy.Need need : synergy.requires()) {
                Talent talent = need.talent();
                assertNotNull(talent, synergy.id() + " needs missing " + need.skill().id() + "/" + need.talentId());
                assertTrue(need.minRank() >= 1 && need.minRank() <= talent.maxRank(), synergy.id());
            }
        }
    }

    /** A synergy between trees has to span trees: one skill with itself is just a node. */
    @Test
    void everyPairingSpansAtLeastTwoSkills() {
        for (Synergy synergy : Synergies.all()) {
            if (synergy.grandmastersNeeded() > 0) {
                continue;
            }
            Set<Skill> skills = new HashSet<>();
            synergy.requires().forEach(need -> skills.add(need.skill()));
            assertTrue(skills.size() >= 2, synergy.id());
            assertFalse(synergy.grants().isEmpty() && synergy.special() == null,
                    synergy.id() + " does nothing");
        }
    }

    @Test
    void idsAreUnique() {
        Set<String> ids = new HashSet<>();
        Synergies.all().forEach(synergy -> assertTrue(ids.add(synergy.id()), synergy.id()));
    }

    /** The event code reads these tags by name; a rename here would silently switch one off. */
    @Test
    void theSpecialsTheEventCodeReadsExist() {
        Set<String> specials = new HashSet<>();
        Synergies.all().forEach(synergy -> specials.add(synergy.special()));
        for (String tag : new String[] {"forge_smelt", "woodsman", "deep_delver", "ambush", "juggernaut", "tidecaller"}) {
            assertTrue(specials.contains(tag), tag);
        }
    }

    @Test
    void aSynergyWakesOnlyWhenEveryNeedIsMet() {
        PlayerSkills skills = new PlayerSkills();
        Synergy woodsman = Synergies.byId("woodsmans_edge");
        skills.setRank(Talents.get(Skill.WOODCUTTING, "timber_call"), 5);
        assertFalse(Synergies.isActive(skills, woodsman));
        assertEquals(1.0, skills.perkModifier(Skill.AXES, PerkEffect.BONUS), 1e-9);
        skills.setRank(Talents.get(Skill.AXES, "bloodlust"), 4);
        assertFalse(Synergies.isActive(skills, woodsman));
        skills.setRank(Talents.get(Skill.AXES, "bloodlust"), 5);
        assertTrue(Synergies.isActive(skills, woodsman));
        assertTrue(Synergies.hasSpecial(skills, "woodsman"));
    }

    @Test
    void aSynergyMultipliesOnTopOfTheTreesSum() {
        PlayerSkills skills = new PlayerSkills();
        skills.setRank(Talents.get(Skill.AXES, "heavy_blows"), 5); // +25% passive
        skills.setRank(Talents.get(Skill.AXES, "bloodlust"), 5);
        skills.setRank(Talents.get(Skill.WOODCUTTING, "timber_call"), 5);
        // (1 + 0.25) tree, x1.10 Woodsman's Edge, x(1 + Discipline) for 10 combat points = 1%.
        double expected = 1.25 * 1.10 * 1.01;
        assertEquals(expected, skills.perkModifier(Skill.AXES, PerkEffect.BONUS), 1e-9);
    }

    @Test
    void renaissanceCountsCapstones() {
        PlayerSkills skills = new PlayerSkills();
        Synergy renaissance = Synergies.byId("renaissance");
        skills.fillTree(Skill.MINING);
        skills.fillTree(Skill.SWORDS);
        assertFalse(Synergies.isActive(skills, renaissance));
        skills.fillTree(Skill.COOKING);
        assertTrue(Synergies.isActive(skills, renaissance));
        assertTrue(Synergies.multiplier(skills, Skill.JUMPING, PerkEffect.XP_RATE) >= 1.10 - 1e-9,
                "Renaissance reaches every skill, even an untouched one");
    }

    @Test
    void disciplineClimbsPerTenPointsAndCaps() {
        PlayerSkills skills = new PlayerSkills();
        assertEquals(0.0, Synergies.discipline(skills, SkillCategory.COMBAT), 1e-9);
        skills.setRank(Talents.get(Skill.SWORDS, "swordplay"), 5);
        skills.setRank(Talents.get(Skill.ARCHERY, "fletching"), 5);
        assertEquals(0.01, Synergies.discipline(skills, SkillCategory.COMBAT), 1e-9);
        assertEquals(0.0, Synergies.discipline(skills, SkillCategory.GATHERING), 1e-9,
                "points in combat trees do nothing for gathering");
        skills.fillTree(Skill.SWORDS);
        skills.fillTree(Skill.AXES);
        skills.fillTree(Skill.MACES);
        assertEquals(Synergies.DISCIPLINE_CAP, Synergies.discipline(skills, SkillCategory.COMBAT), 1e-9);
    }

    /** Every skill should be in at least one pairing, so no tree is a dead end for synergies. */
    @Test
    void everyTreeFeedsSomePairing() {
        Set<Skill> covered = new HashSet<>();
        for (Synergy synergy : Synergies.all()) {
            synergy.requires().forEach(need -> covered.add(need.skill()));
        }
        assertEquals(Skill.VALUES.length, covered.size(), "only " + covered.size() + " skills feed a synergy");
    }
}
