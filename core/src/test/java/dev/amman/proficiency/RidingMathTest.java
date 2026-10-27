package dev.amman.proficiency;

import dev.amman.proficiency.perk.PerkEffect;
import dev.amman.proficiency.perk.Synergies;
import dev.amman.proficiency.perk.Synergy;
import dev.amman.proficiency.perk.Talent;
import dev.amman.proficiency.perk.Talents;
import dev.amman.proficiency.skill.PlayerSkills;
import dev.amman.proficiency.skill.ProcFx;
import dev.amman.proficiency.skill.RidingMath;
import dev.amman.proficiency.skill.RidingMath.Kind;
import dev.amman.proficiency.skill.Skill;
import dev.amman.proficiency.skill.SkillCategory;
import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The Riding skill: rates, the elytra bonus, the passive, the tree and its synergies. */
class RidingMathTest {

    // ---- Rates ---------------------------------------------------------------------------------

    @Test
    void everyVanillaMountHasARateAndTheRestFallBackToTheCatchAll() {
        Map<String, Double> rates = RidingMath.parseRates(null);
        assertEquals(0.030, RidingMath.rate(rates, "minecraft:horse"), 1e-12);
        assertEquals(0.035, RidingMath.rate(rates, "minecraft:camel"), 1e-12);
        assertEquals(0.045, RidingMath.rate(rates, "minecraft:pig"), 1e-12);
        assertEquals(0.045, RidingMath.rate(rates, "minecraft:strider"), 1e-12);
        assertEquals(0.030, RidingMath.rate(rates, "somemod:dragon"), 1e-12, "a modded mount pays the catch-all");
    }

    @Test
    void boatsAndMinecartsAreGroupedByTheirName() {
        Map<String, Double> rates = RidingMath.parseRates(List.of());
        for (String boat : List.of("minecraft:oak_boat", "minecraft:bamboo_raft", "minecraft:cherry_chest_boat",
                "minecraft:bamboo_chest_raft", "boatmod:steam_boat")) {
            assertEquals(0.040, RidingMath.rate(rates, boat), 1e-12, boat);
            assertSame(Kind.BOAT, RidingMath.kindOf(boat), boat);
        }
        for (String cart : List.of("minecraft:minecart", "minecraft:chest_minecart", "minecraft:hopper_minecart")) {
            assertEquals(0.012, RidingMath.rate(rates, cart), 1e-12, cart);
            assertSame(Kind.MINECART, RidingMath.kindOf(cart), cart);
        }
        for (String notBoat : List.of("aircraftmod:aircraft", "mod:spacecraft", "mod:airboat_gun_raftless")) {
            assertSame(Kind.MOUNT, RidingMath.kindOf(notBoat), notBoat);
        }
        assertSame(Kind.MOUNT, RidingMath.kindOf("minecraft:horse"));
        assertSame(Kind.ELYTRA, RidingMath.kindOf("minecraft:elytra"));
    }

    @Test
    void theConfigOverridesTheDefaultsAndIgnoresTypos() {
        Map<String, Double> rates = RidingMath.parseRates(List.of(
                "minecraft:horse=0.1", " Minecraft:Camel = 0.2 ", "#boat=0", "garbage", "=0.5", "minecraft:pig=",
                "minecraft:strider=abc", "minecraft:donkey=-1", "minecraft:mule=NaN", "modded:thing=0.07"));
        assertEquals(0.1, RidingMath.rate(rates, "minecraft:horse"), 1e-12);
        assertEquals(0.2, RidingMath.rate(rates, "minecraft:camel"), 1e-12);
        assertEquals(0.0, RidingMath.rate(rates, "minecraft:oak_boat"), 1e-12, "a rate of 0 switches a vehicle off");
        assertEquals(0.045, RidingMath.rate(rates, "minecraft:pig"), 1e-12);
        assertEquals(0.045, RidingMath.rate(rates, "minecraft:strider"), 1e-12);
        assertEquals(0.030, RidingMath.rate(rates, "minecraft:donkey"), 1e-12);
        assertEquals(0.030, RidingMath.rate(rates, "minecraft:mule"), 1e-12);
        assertEquals(0.07, RidingMath.rate(rates, "modded:thing"), 1e-12);
    }

    @Test
    void theDefaultLinesRoundTrip() {
        Map<String, Double> back = RidingMath.parseRates(RidingMath.defaultRateLines());
        assertEquals(RidingMath.DEFAULT_RATES, back);
    }

    @Test
    void onlyBoatsAndMountsAreAskedToSteer() {
        assertTrue(Kind.MOUNT.needsInput());
        assertTrue(Kind.BOAT.needsInput());
        assertFalse(Kind.MINECART.needsInput());
        assertFalse(Kind.ELYTRA.needsInput());
    }

    // ---- Elytra --------------------------------------------------------------------------------

    @Test
    void elytraPaysMoreForSkimmingTheGroundAndForSpeed() {
        assertEquals(1.0, RidingMath.elytraFactor(100.0, 5.0), 1e-9, "high and slow is the plain rate");
        assertEquals(1.5, RidingMath.elytraFactor(2.0, 5.0), 1e-9, "on the deck");
        assertEquals(1.5, RidingMath.elytraFactor(100.0, 30.0), 1e-9, "fast");
        assertEquals(2.0, RidingMath.elytraFactor(0.0, 45.0), 1e-9, "both is the cap");
        assertEquals(1.25, RidingMath.elytraFactor(14.0, 5.0), 1e-9, "half way up the fade");
        assertEquals(1.25, RidingMath.elytraFactor(100.0, 20.0), 1e-9, "half way up the speed ramp");
        assertTrue(RidingMath.elytraFactor(-1.0, 20.0) <= 1.25 + 1e-9, "no ground below is no low bonus");
        assertEquals(1.0, RidingMath.elytraFactor(Double.NaN, Double.NaN), 1e-9);
    }

    @Test
    void elytraWearIsSparedOnlyWhenItRoseAndTheRollIsLow() {
        // Level 100, no talents: bonus 0.30 spares 45%.
        assertEquals(0.45, RidingMath.elytraSpared(0.30), 1e-9);
        assertEquals(0.9, RidingMath.elytraSpared(5.0), 1e-9, "never more than 90%");
        assertEquals(0.0, RidingMath.elytraSpared(0.0), 1e-9);
        assertEquals(10, RidingMath.elytraWear(10, 11, 0.30, 0.2), "a wear point taken and given back");
        assertEquals(11, RidingMath.elytraWear(10, 11, 0.30, 0.9), "the roll fell short");
        assertEquals(11, RidingMath.elytraWear(10, 11, 0.0, 0.0), "no passive, no sparing");
        assertEquals(9, RidingMath.elytraWear(10, 9, 0.30, 0.0), "a repair is left alone");
        assertEquals(10, RidingMath.elytraWear(10, 10, 0.30, 0.0), "no wear, nothing to give back");
        assertEquals(13, RidingMath.elytraWear(10, 14, 0.30, 0.0), "at most one point back per step");
    }

    // ---- The passive ---------------------------------------------------------------------------

    @Test
    void theSpeedAndJumpPassivesScale() {
        assertEquals(1.0, RidingMath.speedMultiplier(0.0), 1e-9);
        assertEquals(1.30, RidingMath.speedMultiplier(0.30), 1e-9);
        assertEquals(1.15, RidingMath.jumpMultiplier(0.30), 1e-9);
        assertEquals(1.0, RidingMath.jumpMultiplier(-1.0), 1e-9);
    }

    // ---- The tree's numbers --------------------------------------------------------------------

    @Test
    void theLanceHitsHarderAtAGallop() {
        assertEquals(1.0, RidingMath.lanceMultiplier(0, 1.0), 1e-9);
        assertEquals(1.06, RidingMath.lanceMultiplier(1, 0.1), 1e-9);
        assertEquals(1.18, RidingMath.lanceMultiplier(3, 0.1), 1e-9);
        assertEquals(1.36, RidingMath.lanceMultiplier(3, RidingMath.GALLOP_SPEED), 1e-9);
    }

    @Test
    void bardingAndTheArcherScale() {
        assertEquals(1.0, RidingMath.bardingMultiplier(0), 1e-9);
        assertEquals(0.76, RidingMath.bardingMultiplier(3), 1e-9);
        assertEquals(1.0, RidingMath.archerMultiplier(0), 1e-9);
        assertEquals(1.24, RidingMath.archerMultiplier(3), 1e-9);
    }

    @Test
    void theWhistleReachesFurtherAndRestsLessPerRank() {
        assertEquals(0.0, RidingMath.whistleRange(0), 1e-9);
        assertEquals(64.0, RidingMath.whistleRange(1), 1e-9);
        assertEquals(96.0, RidingMath.whistleRange(2), 1e-9);
        assertEquals(128.0, RidingMath.whistleRange(3), 1e-9);
        assertEquals(2000L, RidingMath.whistleCooldown(1));
        assertEquals(1600L, RidingMath.whistleCooldown(2));
        assertEquals(1200L, RidingMath.whistleCooldown(3));
        assertTrue(RidingMath.whistleReady(-1L, 0L, 1));
        assertFalse(RidingMath.whistleReady(0L, 1999L, 1));
        assertTrue(RidingMath.whistleReady(0L, 2000L, 1));
        assertTrue(RidingMath.whistleReady(5000L, 100L, 1), "a clock that went back is not a cooldown");
    }

    @Test
    void highSeatRocketSaverAndTrampleScale() {
        assertEquals(1.5, RidingMath.seatReach(3), 1e-9);
        assertEquals(0.0, RidingMath.seatReach(0), 1e-9);
        assertEquals(0.45, RidingMath.rocketSaveChance(3), 1e-9);
        assertEquals(0.0, RidingMath.rocketSaveChance(0), 1e-9);
        assertEquals(3.0, RidingMath.trampleDamage(0), 1e-9);
        assertEquals(8.0, RidingMath.trampleDamage(100), 1e-9);
        assertEquals(8.0, RidingMath.trampleDamage(500), 1e-9);
    }

    @Test
    void softLandingLastsFourSecondsAndWaitsTwenty() {
        assertTrue(RidingMath.softLandingActive(100L, 100L));
        assertTrue(RidingMath.softLandingActive(100L, 180L));
        assertFalse(RidingMath.softLandingActive(100L, 181L));
        assertFalse(RidingMath.softLandingActive(-1L, 5L));
        assertFalse(RidingMath.softLandingActive(100L, 99L));
        assertTrue(RidingMath.softLandingReady(-1L, 0L));
        assertFalse(RidingMath.softLandingReady(0L, 399L));
        assertTrue(RidingMath.softLandingReady(0L, 400L));
    }

    @Test
    void tailwindScalesWithProcPower() {
        assertEquals(120, RidingMath.tailwindTicks(1.0));
        assertEquals(240, RidingMath.tailwindTicks(2.0));
        assertEquals(20, RidingMath.tailwindTicks(0.0), "never an instant effect");
        assertEquals(0.5, RidingMath.tailwindPush(1.0), 1e-9);
        assertEquals(1.5, RidingMath.tailwindPush(50.0), 1e-9, "capped at three times");
    }

    // ---- The skill and its tree ----------------------------------------------------------------

    @Test
    void ridingIsSkillThirtySevenAndAMovementOne() {
        assertSame(Skill.RIDING, Skill.VALUES[36]);
        assertSame(Skill.HUSBANDRY, Skill.VALUES[35]);
        assertSame(SkillCategory.MOVEMENT, Skill.RIDING.category());
        assertSame(Skill.RIDING, Skill.byId("riding"));
        assertNotNull(ProcFx.recipe(Skill.RIDING));
    }

    @Test
    void theTreeFillsAtLevelOneHundredForAtMostOneHundredPoints() {
        int cost = Talents.fullTreeCost(Skill.RIDING);
        assertTrue(cost <= 100, "costs " + cost);
        PlayerSkills skills = new PlayerSkills();
        skills.setLevel(Skill.RIDING, 100);
        skills.fillTree(Skill.RIDING);
        Talents.of(Skill.RIDING).forEach(talent -> assertTrue(skills.isFull(talent), talent.key()));
    }

    @Test
    void theTreeHasEightBespokeMechanicsAndNoneIsACopy() {
        Set<String> mine = new HashSet<>();
        for (Talent talent : Talents.of(Skill.RIDING)) {
            if (talent.special() != null) {
                mine.add(talent.special());
            }
        }
        assertEquals(Set.of("mounted_lance", "barding", "mounted_shot", "call_mount", "high_seat", "rocket_saver",
                "soft_landing", "trample"), mine);
        Set<String> theirs = new HashSet<>();
        for (Skill other : Skill.VALUES) {
            if (other == Skill.RIDING) {
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
        assertEquals("horsemanship", Talents.root(Skill.RIDING).id());
        assertEquals(100, Talents.get(Skill.RIDING, "stampede").requiredLevel());
        assertEquals(90, Talents.get(Skill.RIDING, "soft_landing").requiredLevel());
    }

    @Test
    void tenSynergiesPairRidingWithTheTreesItRidesBeside() {
        String[] ids = {"open_country", "ferryman", "heavy_cavalry", "horse_whisperer", "horse_archer",
                "mounted_hunt", "rocketeer", "hard_rider", "outrider", "cavalry_shield"};
        Set<Skill> partners = new HashSet<>();
        for (String id : ids) {
            Synergy synergy = Synergies.byId(id);
            assertNotNull(synergy, id);
            boolean mine = false;
            for (Synergy.Need need : synergy.requires()) {
                if (need.skill() == Skill.RIDING) {
                    mine = true;
                } else {
                    partners.add(need.skill());
                }
                assertTrue(need.minRank() <= Talents.get(need.skill(), need.talentId()).maxRank(), id);
            }
            assertTrue(mine, id + " does not need Riding");
        }
        assertTrue(partners.containsAll(Set.of(Skill.RUNNING, Skill.SWIMMING, Skill.CHARGER,
                Skill.HUSBANDRY, Skill.ARCHERY)), "partners: " + partners);
    }

    @Test
    void aSynergyWakesWhenBothHalvesAreBought() {
        Synergy pair = Synergies.byId("horse_archer");
        PlayerSkills skills = new PlayerSkills();
        skills.setRank(Talents.get(Skill.RIDING, "saddle_archer"), 2);
        assertFalse(Synergies.isActive(skills, pair));
        skills.setRank(Talents.get(Skill.ARCHERY, "steady_aim"), 3);
        assertTrue(Synergies.isActive(skills, pair));
        assertEquals(1.10, Synergies.multiplier(skills, Skill.RIDING, PerkEffect.PROC_CHANCE), 1e-9);
    }
}
