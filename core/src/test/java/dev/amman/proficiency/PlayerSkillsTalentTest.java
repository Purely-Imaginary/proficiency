package dev.amman.proficiency;

import dev.amman.proficiency.perk.PerkEffect;
import dev.amman.proficiency.perk.Talent;
import dev.amman.proficiency.perk.TalentOutcome;
import dev.amman.proficiency.perk.Talents;
import dev.amman.proficiency.skill.PlayerSkills;
import dev.amman.proficiency.skill.Skill;
import dev.amman.proficiency.skill.SkillMath;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The rules for putting a rank in, and what the ranks add up to. */
class PlayerSkillsTalentTest {

    private static Talent node(Skill skill, String id) {
        return Talents.get(skill, id);
    }

    @Test
    void pointsAreOnePerLevel() {
        PlayerSkills skills = new PlayerSkills();
        skills.setLevel(Skill.MINING, 37);
        assertEquals(37, skills.pointsEarned(Skill.MINING));
        skills.setLevel(Skill.MINING, 100);
        assertEquals(100, skills.pointsEarned(Skill.MINING));
    }

    @Test
    void aNodeWaitsForItsParentToFill() {
        PlayerSkills skills = new PlayerSkills();
        skills.setLevel(Skill.MINING, 40);
        Talent root = node(Skill.MINING, "pickwork");
        Talent honed = node(Skill.MINING, "efficiency");
        assertEquals(TalentOutcome.PARENT_NOT_FULL, skills.check(honed));
        skills.setRank(root, 4);
        assertEquals(TalentOutcome.PARENT_NOT_FULL, skills.check(honed), "four of five is not full");
        skills.setRank(root, 5);
        assertEquals(TalentOutcome.OK, skills.check(honed));
    }

    @Test
    void aBridgeWaitsForBothSides() {
        PlayerSkills skills = new PlayerSkills();
        skills.setLevel(Skill.SWORDS, 60);
        skills.setRank(node(Skill.SWORDS, "swordplay"), 5);
        skills.setRank(node(Skill.SWORDS, "keen_edge"), 5);
        Talent crosscut = node(Skill.SWORDS, "crosscut");
        assertEquals(TalentOutcome.PARENT_NOT_FULL, skills.check(crosscut));
        skills.setRank(node(Skill.SWORDS, "opening"), 5);
        assertEquals(TalentOutcome.OK, skills.check(crosscut));
    }

    @Test
    void levelAndPointsGateSeparately() {
        PlayerSkills skills = new PlayerSkills();
        Talent root = node(Skill.FARMING, "tilling");
        assertEquals(TalentOutcome.NOT_ENOUGH_POINTS, skills.check(root), "level 0 has no points");
        skills.setLevel(Skill.FARMING, 9);
        skills.setRank(root, 4);
        assertEquals(TalentOutcome.NOT_ENOUGH_POINTS, skills.check(root), "level 9 is four points");
        skills.setLevel(Skill.FARMING, 10);
        assertEquals(TalentOutcome.OK, skills.check(root));
        skills.setRank(root, 5);
        skills.setLevel(Skill.FARMING, 9);
        assertEquals(TalentOutcome.LEVEL_TOO_LOW, skills.check(node(Skill.FARMING, "bountiful")),
                "row one needs level 10, even with the trunk full after a death");
        assertEquals(TalentOutcome.MAXED, skills.check(root));
    }

    @Test
    void ranksOfOneEffectAddWithinATree() {
        PlayerSkills skills = new PlayerSkills();
        skills.setRank(node(Skill.MINING, "gem_cutter"), 3);   // +10% power per rank
        skills.setRank(node(Skill.MINING, "rich_veins"), 3);   // +15% power per rank
        assertEquals(0.75, skills.rankSum(Skill.MINING, PerkEffect.PROC_POWER), 1e-9);
        assertEquals(1.75, skills.perkModifier(Skill.MINING, PerkEffect.PROC_POWER), 1e-9);
    }

    @Test
    void noRanksMeansNoChange() {
        PlayerSkills skills = new PlayerSkills();
        for (Skill skill : Skill.VALUES) {
            skills.setLevel(skill, 37);
            for (PerkEffect effect : PerkEffect.values()) {
                if (effect != PerkEffect.DEATH_WARD) {
                    assertEquals(1.0, skills.perkModifier(skill, effect), 1e-9, skill.id() + " " + effect);
                }
            }
            assertEquals(SkillMath.bonus(skill, 37), skills.bonus(skill), 1e-9);
        }
    }

    @Test
    void aTreeNeverTouchesAnotherSkill() {
        PlayerSkills skills = new PlayerSkills();
        skills.setRank(node(Skill.WOODCUTTING, "sharp_axe"), 5);
        assertEquals(1.0, skills.perkModifier(Skill.MINING, PerkEffect.BONUS), 1e-9);
    }

    @Test
    void theCooldownNeverFallsBelowAQuarter() {
        PlayerSkills skills = new PlayerSkills();
        skills.fillTree(Skill.CROSSBOWS);
        double modifier = skills.perkModifier(Skill.CROSSBOWS, PerkEffect.ABILITY_COOLDOWN);
        assertEquals(1.0 - 0.15 - 0.20, modifier, 1e-9, "Quickload and Siege Master together");
        assertTrue(modifier >= 0.25);
    }

    @Test
    void wardKeepsItsShareOfTheBarAndFullWardKeepsAll() {
        PlayerSkills skills = new PlayerSkills();
        skills.setLevel(Skill.SWORDS, 60);
        skills.setLevel(Skill.BLOCKING, 60);
        float half = SkillMath.xpToNext(60) / 2f;
        skills.addXp(Skill.SWORDS, half);
        skills.addXp(Skill.BLOCKING, half);
        skills.setRank(node(Skill.BLOCKING, "tenacity"), 3);      // 60% kept
        var lost = skills.applyDeathPenalty();
        assertEquals(0.5f, lost.get(Skill.SWORDS), 0.001f);
        assertEquals(0.2f, lost.get(Skill.BLOCKING), 0.001f, "half a bar, sixty percent of it kept");
        assertEquals(half * 0.6f, skills.xp(Skill.BLOCKING), 0.01f);
        assertEquals(60, skills.level(Skill.BLOCKING));

        PlayerSkills immovable = new PlayerSkills();
        immovable.setLevel(Skill.BLOCKING, 99);
        immovable.addXp(Skill.BLOCKING, SkillMath.xpToNext(99) / 2f);
        immovable.fillTree(Skill.BLOCKING);                       // 100% kept
        float before = immovable.xp(Skill.BLOCKING);
        assertTrue(immovable.applyDeathPenalty().isEmpty(), "a full Blocking tree loses nothing");
        assertEquals(before, immovable.xp(Skill.BLOCKING));
    }

    @Test
    void deathNeverTakesPointsOrTalents() {
        PlayerSkills skills = new PlayerSkills();
        skills.setLevel(Skill.MINING, 99);
        skills.addXp(Skill.MINING, SkillMath.xpToNext(99) - 1f);
        skills.fillTree(Skill.MINING);
        skills.setRank(node(Skill.MINING, "hard_hat"), 0);        // the only ward in the tree
        int spent = skills.pointsSpent(Skill.MINING);
        skills.applyDeathPenalty();
        assertEquals(99, skills.level(Skill.MINING));
        assertEquals(0f, skills.xp(Skill.MINING));
        assertEquals(spent, skills.pointsSpent(Skill.MINING));
    }

    @Test
    void respecRefundsPointsButRemembersMaterials() {
        PlayerSkills skills = new PlayerSkills();
        skills.setLevel(Skill.WOODCUTTING, 100);
        skills.fillTree(Skill.WOODCUTTING);
        int spent = skills.pointsSpent(Skill.WOODCUTTING);
        assertEquals(Talents.fullTreeCost(Skill.WOODCUTTING), spent);

        assertEquals(spent, skills.respec(Skill.WOODCUTTING));
        assertEquals(0, skills.pointsSpent(Skill.WOODCUTTING));
        assertEquals(100, skills.pointsAvailable(Skill.WOODCUTTING));
        assertTrue(skills.hasPaid(node(Skill.WOODCUTTING, "sawmill")),
                "the eucalyptus was handed over once; it is not asked for again");
    }

    @Test
    void grandmastersCountFullCapstonesOnly() {
        PlayerSkills skills = new PlayerSkills();
        skills.fillTree(Skill.MINING);
        skills.fillTree(Skill.SWORDS);
        skills.setRank(node(Skill.ARCHERY, "marksman"), 0);
        assertEquals(2, skills.grandmasters());
    }
}
