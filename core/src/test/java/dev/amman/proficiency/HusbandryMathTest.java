package dev.amman.proficiency;

import dev.amman.proficiency.perk.PerkEffect;
import dev.amman.proficiency.perk.Synergies;
import dev.amman.proficiency.perk.Synergy;
import dev.amman.proficiency.perk.Talent;
import dev.amman.proficiency.perk.Talents;
import dev.amman.proficiency.skill.HusbandryMath;
import dev.amman.proficiency.skill.HusbandryMath.Job;
import dev.amman.proficiency.skill.PlayerSkills;
import dev.amman.proficiency.skill.ProcFx;
import dev.amman.proficiency.skill.Skill;
import dev.amman.proficiency.skill.SkillCategory;
import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The Husbandry skill: its XP and anti-farm rules, the passive, the tree and its synergies. */
class HusbandryMathTest {

    // ---- XP ------------------------------------------------------------------------------------

    @Test
    void everyJobPaysAndOnlyTheRepeatableOnesCoolDown() {
        for (Job job : Job.values()) {
            assertTrue(job.defaultXp() > 0, job.name());
        }
        assertEquals(6000L, Job.BREED.cooldownTicks());
        assertEquals(2400L, Job.MILK.cooldownTicks());
        assertEquals(6000L, Job.BRUSH.cooldownTicks());
        assertEquals(0L, Job.SHEAR.cooldownTicks());
    }

    @Test
    void theRarerSpeciesPayHalfAgain() {
        assertEquals(1.0, HusbandryMath.speciesWorth("minecraft:cow"), 1e-9);
        assertEquals(1.5, HusbandryMath.speciesWorth("minecraft:panda"), 1e-9);
        assertEquals(1.5, HusbandryMath.speciesWorth("minecraft:wolf"), 1e-9);
        assertEquals(1.0, HusbandryMath.speciesWorth(null), 1e-9);
        assertEquals(15.0, HusbandryMath.payout(Job.BREED, "minecraft:panda", 2), 1e-9);
    }

    @Test
    void aCrowdedPenPaysLess() {
        assertEquals(1.0, HusbandryMath.penFactor(1), 1e-9);
        assertEquals(1.0, HusbandryMath.penFactor(HusbandryMath.PEN_FREE), 1e-9);
        assertEquals(0.5, HusbandryMath.penFactor(16), 1e-9);
        assertEquals(0.2, HusbandryMath.penFactor(40), 1e-9);
        assertEquals(HusbandryMath.PEN_FLOOR, HusbandryMath.penFactor(500), 1e-9);
        assertEquals(1.0, HusbandryMath.penFactor(0), 1e-9);
        double previous = 1.0;
        for (int n = 1; n < 200; n++) {
            double factor = HusbandryMath.penFactor(n);
            assertTrue(factor <= previous + 1e-12, "never pays more for a bigger pen at " + n);
            assertTrue(factor >= HusbandryMath.PEN_FLOOR);
            previous = factor;
        }
    }

    @Test
    void aJobPaysAgainOnlyAfterItsCooldown() {
        assertTrue(HusbandryMath.cooledDown(Job.MILK, -1L, 0L), "never paid");
        assertFalse(HusbandryMath.cooledDown(Job.MILK, 1000L, 1000L + 2399L));
        assertTrue(HusbandryMath.cooledDown(Job.MILK, 1000L, 1000L + 2400L));
        assertTrue(HusbandryMath.cooledDown(Job.MILK, 5000L, 100L), "a clock that went back never locks it");
        assertTrue(HusbandryMath.cooledDown(Job.SHEAR, 1000L, 1000L), "shearing has no cooldown of its own");
    }

    @Test
    void theSpotRuleStopsAtTwelveInFiveMinutes() {
        assertTrue(HusbandryMath.spotAllows(0));
        assertTrue(HusbandryMath.spotAllows(HusbandryMath.SPOT_LIMIT - 1));
        assertFalse(HusbandryMath.spotAllows(HusbandryMath.SPOT_LIMIT));
        assertTrue(HusbandryMath.inSpotWindow(0, 5999));
        assertFalse(HusbandryMath.inSpotWindow(0, 6000));
        assertFalse(HusbandryMath.inSpotWindow(100, 50), "a payout from the future is not remembered");
        assertFalse(HusbandryMath.inSpotWindow(-1, 50));
    }

    // ---- The passive ---------------------------------------------------------------------------

    @Test
    void growthAndPetHealthAreOnePlusThePassive() {
        assertEquals(1.0, HusbandryMath.growthMultiplier(0), 1e-9);
        assertEquals(1.0, HusbandryMath.growthMultiplier(-1), 1e-9);
        assertEquals(1.5, HusbandryMath.growthMultiplier(0.5), 1e-9);
        assertEquals(1.5, HusbandryMath.petHealthMultiplier(0.5), 1e-9);
        assertEquals(1.0, HusbandryMath.petHealthMultiplier(0), 1e-9);
    }

    @Test
    void aBredBabyStartsCloserToAdult() {
        assertEquals(-24000, HusbandryMath.bredBabyAge(-24000, 0, false));
        assertEquals(-16000, HusbandryMath.bredBabyAge(-24000, 0.5, false));
        assertEquals(-12000, HusbandryMath.bredBabyAge(-24000, 0, true));
        assertEquals(-8000, HusbandryMath.bredBabyAge(-24000, 0.5, true));
        assertEquals(-1, HusbandryMath.bredBabyAge(-1, 100.0, true), "never past still a baby");
        assertEquals(0, HusbandryMath.bredBabyAge(0, 0.5, true), "an adult is left alone");
        assertEquals(6000, HusbandryMath.bredBabyAge(6000, 0.5, true));
    }

    // ---- The tree's numbers --------------------------------------------------------------------

    @Test
    void theTreeMechanicsScaleWithRank() {
        assertEquals(0, HusbandryMath.gentleTemper(0));
        assertEquals(18, HusbandryMath.gentleTemper(3));
        assertEquals(0.0, HusbandryMath.tripletsChance(0), 1e-9);
        assertEquals(0.30, HusbandryMath.tripletsChance(3), 1e-9);
        assertEquals(0.60, HusbandryMath.pailChance(3), 1e-9);
        assertEquals(3, HusbandryMath.extraWool(3));
        assertEquals(0, HusbandryMath.extraWool(-1));
        assertEquals(1.3, HusbandryMath.sharedStrength(3), 1e-9);
        assertEquals(1.0, HusbandryMath.sharedStrength(0), 1e-9);
    }

    @Test
    void aWolfLearnsAThirdOfYourWeaponSkillPerRank() {
        assertEquals(1.0, HusbandryMath.wolfLearns(0.8, 0), 1e-9);
        assertEquals(1.0, HusbandryMath.wolfLearns(0.0, 3), 1e-9);
        assertEquals(1.0, HusbandryMath.wolfLearns(Double.NaN, 3), 1e-9);
        assertEquals(1.0 + 0.8 / 3, HusbandryMath.wolfLearns(0.8, 1), 1e-9);
        assertEquals(1.8, HusbandryMath.wolfLearns(0.8, 3), 1e-9);
        assertEquals(1.8, HusbandryMath.wolfLearns(0.8, 9), 1e-9, "more ranks than the tree has change nothing");
    }

    @Test
    void faithfulFriendWaitsTenMinutesPerOwner() {
        assertTrue(HusbandryMath.faithfulReady(-1L, 0L));
        assertFalse(HusbandryMath.faithfulReady(0L, HusbandryMath.FAITHFUL_COOLDOWN_TICKS - 1));
        assertTrue(HusbandryMath.faithfulReady(0L, HusbandryMath.FAITHFUL_COOLDOWN_TICKS));
        assertEquals(0.25, HusbandryMath.FAITHFUL_HEALTH, 1e-9);
    }

    // ---- The skill and its tree ------------------------------------------------------------------

    @Test
    void husbandryIsSkillThirtySixAndAGatheringOne() {
        assertSame(Skill.HUSBANDRY, Skill.VALUES[35]);
        assertSame(Skill.HOMEBUILDER, Skill.VALUES[34]);
        assertSame(SkillCategory.GATHERING, Skill.HUSBANDRY.category());
        assertSame(Skill.HUSBANDRY, Skill.byId("husbandry"));
        assertNotNull(ProcFx.recipe(Skill.HUSBANDRY));
    }

    @Test
    void theTreeFillsAtLevelOneHundredForAtMostOneHundredPoints() {
        int cost = Talents.fullTreeCost(Skill.HUSBANDRY);
        assertTrue(cost <= 100, "costs " + cost);
        PlayerSkills skills = new PlayerSkills();
        skills.setLevel(Skill.HUSBANDRY, 100);
        skills.fillTree(Skill.HUSBANDRY);
        Talents.of(Skill.HUSBANDRY).forEach(talent -> assertTrue(skills.isFull(talent), talent.key()));
    }

    @Test
    void theTreeHasEightBespokeMechanicsAndNoneIsACopy() {
        Set<String> mine = new HashSet<>();
        for (Talent talent : Talents.of(Skill.HUSBANDRY)) {
            if (talent.special() != null) {
                mine.add(talent.special());
            }
        }
        assertEquals(Set.of("gentle_hands", "shear_more", "wolf_learns", "big_litter", "fresh_pail",
                "shared_strength", "faithful_friend", "herd_master"), mine);
        Set<String> theirs = new HashSet<>();
        for (Skill other : Skill.VALUES) {
            if (other == Skill.HUSBANDRY) {
                continue;
            }
            for (Talent talent : Talents.of(other)) {
                if (talent.special() != null) {
                    theirs.add(talent.special());
                }
            }
        }
        for (String special : mine) {
            assertFalse(theirs.contains(special), special + " is another tree's tag");
        }
    }

    @Test
    void theRootOpensAtLevelTenAndTheCapstoneAtOneHundred() {
        assertEquals("stockman", Talents.root(Skill.HUSBANDRY).id());
        assertEquals(100, Talents.get(Skill.HUSBANDRY, "herd_master").requiredLevel());
        assertEquals(90, Talents.get(Skill.HUSBANDRY, "faithful_friend").requiredLevel());
    }

    @Test
    void tenSynergiesPairHusbandryWithTheTreesItWorksBeside() {
        String[] ids = {"farm_to_table", "feed_the_herd", "packmaster", "fresh_dairy", "stable_roof",
                "village_herd", "watchdog", "trail_companion", "brave_hounds", "honey_tonic"};
        Set<Skill> partners = new HashSet<>();
        for (String id : ids) {
            Synergy synergy = Synergies.byId(id);
            assertNotNull(synergy, id);
            boolean mine = false;
            for (Synergy.Need need : synergy.requires()) {
                if (need.skill() == Skill.HUSBANDRY) {
                    mine = true;
                } else {
                    partners.add(need.skill());
                }
                assertTrue(need.minRank() <= Talents.get(need.skill(), need.talentId()).maxRank(), id);
            }
            assertTrue(mine, id + " does not need Husbandry");
        }
        assertTrue(partners.containsAll(Set.of(Skill.FARMING, Skill.BEASTSLAYING, Skill.COOKING,
                Skill.HOMEBUILDER, Skill.SOCIAL, Skill.GUARDIAN)), "partners: " + partners);
    }

    @Test
    void aSynergyWakesWhenBothHalvesAreBought() {
        Synergy pair = Synergies.byId("packmaster");
        PlayerSkills skills = new PlayerSkills();
        skills.setRank(Talents.get(Skill.HUSBANDRY, "wolf_learns"), 2);
        assertFalse(Synergies.isActive(skills, pair));
        skills.setRank(Talents.get(Skill.BEASTSLAYING, "tracking"), 3);
        assertTrue(Synergies.isActive(skills, pair));
        assertEquals(1.10, Synergies.multiplier(skills, Skill.HUSBANDRY, PerkEffect.BONUS), 1e-9);
    }

    @Test
    void farmingKeepsItsTwinNodeUnderTheOldId() {
        Talent herdsman = Talents.get(Skill.FARMING, "husbandry");
        assertEquals("twins", herdsman.special());
    }
}
