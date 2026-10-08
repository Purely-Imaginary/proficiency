package dev.amman.proficiency;

import dev.amman.proficiency.perk.PerkEffect;
import dev.amman.proficiency.perk.Synergies;
import dev.amman.proficiency.perk.Talents;
import dev.amman.proficiency.skill.NightwalkerMath;
import dev.amman.proficiency.skill.NightwalkerMath.Second;
import dev.amman.proficiency.skill.NightwalkerMath.Trickle;
import dev.amman.proficiency.skill.PlayerSkills;
import dev.amman.proficiency.skill.SharePot;
import dev.amman.proficiency.skill.Skill;
import dev.amman.proficiency.skill.SkillCategory;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Idea 36: Nightwalker's XP rules, anti-farm rules and talent numbers. */
class NightwalkerMathTest {

    // ---- What counts as dark ------------------------------------------------------------------

    @Test
    void darkIsBlockLightZeroToThree() {
        assertTrue(NightwalkerMath.isDark(0, 0, false), "a cave");
        assertTrue(NightwalkerMath.isDark(3, 0, false), "a dim cave edge");
        assertFalse(NightwalkerMath.isDark(4, 0, false), "next to a torch's reach");
        assertFalse(NightwalkerMath.isDark(14, 0, true), "a torch at night");
    }

    @Test
    void skyLightCountsByDayAndIsIgnoredAtNight() {
        assertFalse(NightwalkerMath.isDark(0, 15, false), "open field by day");
        assertFalse(NightwalkerMath.isDark(0, 4, false), "a room with a window by day");
        assertTrue(NightwalkerMath.isDark(0, 3, false), "a closed room by day");
        assertTrue(NightwalkerMath.isDark(0, 15, true), "open field at night");
    }

    // ---- XP source 1: a share of other grants in the dark -------------------------------------

    @Test
    void theShareIsTakenBeforePlayerMultipliers() {
        assertEquals(2.0, NightwalkerMath.share(10.0, 1.0, 1.0, 0.20), 1e-9);
        // The source skill's own configured rate counts, so a slowed skill pays less.
        assertEquals(1.0, NightwalkerMath.share(10.0, 0.5, 1.0, 0.20), 1e-9);
        // A first-time bonus's tier counts too.
        assertEquals(6.0, NightwalkerMath.share(15.0, 1.0, 2.0, 0.20), 1e-9);
    }

    @Test
    void nothingOrABrokenNumberPaysNothing() {
        assertEquals(0.0, NightwalkerMath.share(0.0, 1.0, 1.0, 0.2));
        assertEquals(0.0, NightwalkerMath.share(-5.0, 1.0, 1.0, 0.2));
        assertEquals(0.0, NightwalkerMath.share(Double.NaN, 1.0, 1.0, 0.2));
        assertEquals(0.0, NightwalkerMath.share(10.0, Double.POSITIVE_INFINITY, 1.0, 0.2));
        assertEquals(0.0, NightwalkerMath.share(10.0, 1.0, 1.0, 0.0), "share 0 switches it off");
    }

    @Test
    void thePotPaysFirstThenEveryFiveSeconds() {
        long interval = NightwalkerMath.PAY_INTERVAL_TICKS;
        assertTrue(SharePot.due(1000, Long.MIN_VALUE, interval), "the first payout");
        assertFalse(SharePot.due(1099, 1000, interval));
        assertTrue(SharePot.due(1100, 1000, interval));
        assertTrue(SharePot.due(10, 1000, interval), "a clock that went back");
    }

    // ---- XP source 2: the outdoor night trickle, and its anti-AFK rules ------------------------

    private static Second walk(int i) {
        // Two blocks a second east, turning the head a little each second.
        return new Second(true, true, true, false, false, false, i * 2.0, 0.0, i * 5f, 0f);
    }

    @Test
    void aFullActiveMinuteOutdoorsAtNightPaysOnce() {
        Trickle trickle = new Trickle();
        int paid = 0;
        for (int i = 0; i <= NightwalkerMath.TRICKLE_SECONDS; i++) {
            if (trickle.step(walk(i))) {
                paid++;
            }
        }
        assertEquals(1, paid, "the first second only sets the start point, then 60 active seconds pay once");
        assertEquals(0, trickle.active());
    }

    @Test
    void standingStillPaysNothing() {
        Trickle trickle = new Trickle();
        for (int i = 0; i < 600; i++) {
            assertFalse(trickle.step(new Second(true, true, true, false, false, false, 5, 5, i * 7f, 0f)),
                    "AFK in place paid at second " + i);
        }
        assertEquals(0, trickle.active());
    }

    @Test
    void movingWithoutEverLookingAroundPaysNothing() {
        // A water stream or a minecart pushes you along with the head fixed.
        Trickle trickle = new Trickle();
        for (int i = 0; i < 600; i++) {
            assertFalse(trickle.step(new Second(true, true, true, false, false, false, i * 3.0, 0, 90f, 10f)));
        }
    }

    @Test
    void aLookLastsThirtySecondsThenTheCountStops() {
        Trickle trickle = new Trickle();
        trickle.step(walk(0));
        trickle.step(walk(1)); // a turn: counts
        assertEquals(1, trickle.active());
        for (int i = 2; i < 40; i++) {
            trickle.step(new Second(true, true, true, false, false, false, i * 2.0, 0, 5f, 0f));
        }
        // Seconds 2..31 are within 30 s of the last turn; after that nothing counts.
        assertEquals(1 + NightwalkerMath.LOOK_WINDOW_SECONDS, trickle.active());
    }

    @Test
    void bedWaterRidingDayIndoorsAndLightEachStopTheCount() {
        Second[] blocked = {
            new Second(true, true, true, true, false, false, 0, 0, 0, 0),   // in bed
            new Second(true, true, true, false, true, false, 0, 0, 0, 0),   // riding
            new Second(true, true, true, false, false, true, 0, 0, 0, 0),   // in water
            new Second(true, false, true, false, false, false, 0, 0, 0, 0), // day
            new Second(false, true, true, false, false, false, 0, 0, 0, 0), // indoors
            new Second(true, true, false, false, false, false, 0, 0, 0, 0), // by a torch
        };
        for (Second base : blocked) {
            assertFalse(NightwalkerMath.counts(base, true, 0), base.toString());
        }
        assertTrue(NightwalkerMath.counts(new Second(true, true, true, false, false, false, 0, 0, 0, 0), true, 0));
        assertFalse(NightwalkerMath.counts(new Second(true, true, true, false, false, false, 0, 0, 0, 0), false, 0),
                "no movement");
    }

    @Test
    void aResetDropsThePartialMinute() {
        Trickle trickle = new Trickle();
        for (int i = 0; i < 30; i++) {
            trickle.step(walk(i));
        }
        assertTrue(trickle.active() > 0);
        trickle.reset();
        assertEquals(0, trickle.active());
    }

    // ---- The passive, the proc, the ability, the talents ---------------------------------------

    @Test
    void darkSightGrowsWithFarSightAndStaysUnderNightVision() {
        assertEquals(0.0, NightwalkerMath.darkSight(0.0, 0));
        assertEquals(0.10, NightwalkerMath.darkSight(0.10, 0), 1e-9);
        assertEquals(0.16, NightwalkerMath.darkSight(0.10, 3), 1e-9);
        assertEquals(NightwalkerMath.DARK_SIGHT_CAP, NightwalkerMath.darkSight(5.0, 3), 1e-9);
        assertTrue(NightwalkerMath.DARK_SIGHT_CAP < 1.0);
    }

    @Test
    void moonlitIsTenSecondsAndGrowsWithPower() {
        assertEquals(200, NightwalkerMath.moonlitTicks(1.0));
        assertEquals(260, NightwalkerMath.moonlitTicks(1.3));
        assertEquals(200, NightwalkerMath.moonlitTicks(0.0));
    }

    @Test
    void eclipseHidesOnlyFarMobsInTheDark() {
        assertTrue(NightwalkerMath.eclipseHides(12 * 12, true));
        assertFalse(NightwalkerMath.eclipseHides(8 * 8, true), "at 8 blocks it still sees you");
        assertFalse(NightwalkerMath.eclipseHides(20 * 20, false), "a mob in the light keeps you");
    }

    @Test
    void talentNumbers() {
        assertEquals(0.30, NightwalkerMath.nightOwlRefund(3), 1e-9);
        assertEquals(0.0, NightwalkerMath.nightOwlRefund(0));
        assertEquals(1.18, NightwalkerMath.darkbornMultiplier(3), 1e-9);
        assertEquals(0.4, NightwalkerMath.phantomWard(3), 1e-9);
        assertEquals(1.0, NightwalkerMath.phantomWard(0), 1e-9);
        assertEquals(65, NightwalkerMath.darknessTicks(260, 3));
        assertEquals(260, NightwalkerMath.darknessTicks(260, 0));
        assertEquals(20, NightwalkerMath.darknessTicks(40, 3), "never under a second");
        assertTrue(NightwalkerMath.inSanctuary(16 * 16));
        assertFalse(NightwalkerMath.inSanctuary(17 * 17));
    }

    // ---- The skill and its tree ----------------------------------------------------------------

    @Test
    void nightwalkerIsSurvivalAndItsTreeFitsInAHundredPoints() {
        assertEquals(SkillCategory.SURVIVAL, Skill.NIGHTWALKER.category());
        assertEquals(99, Talents.fullTreeCost(Skill.NIGHTWALKER));
        PlayerSkills skills = new PlayerSkills();
        skills.setLevel(Skill.NIGHTWALKER, 100);
        skills.fillTree(Skill.NIGHTWALKER);
        assertTrue(skills.pointsSpent(Skill.NIGHTWALKER) <= skills.pointsEarned(Skill.NIGHTWALKER));
        // Dark-Adapted Eyes 5 x 5% and the capstone 15% on the passive.
        assertEquals(0.40, skills.rankSum(Skill.NIGHTWALKER, PerkEffect.BONUS), 1e-9);
    }

    @Test
    void everyMechanicTheNightwalkerCodeReadsIsOnTheTree() {
        for (String tag : new String[] {"night_hunger", "dark_sight", "darkborn", "phantom_ward",
                "darkness_ward", "sanctuary", "hunters_moon"}) {
            assertTrue(Talents.of(Skill.NIGHTWALKER).stream().anyMatch(t -> tag.equals(t.special())), tag);
        }
    }

    /** Sneaking owns stealth. Nightwalker must not repeat any of its mechanics. */
    @Test
    void nightwalkerRepeatsNoSneakingMechanic() {
        for (var sneaking : Talents.of(Skill.SNEAKING)) {
            if (sneaking.special() == null) {
                continue;
            }
            assertFalse(Talents.of(Skill.NIGHTWALKER).stream().anyMatch(t -> sneaking.special().equals(t.special())),
                    sneaking.special());
        }
    }

    @Test
    void nightHunterBridgesSneakingAndNightwalker() {
        var synergy = Synergies.byId("night_hunter");
        assertEquals("night_hunter", synergy.special());
        PlayerSkills skills = new PlayerSkills();
        skills.setRank(Talents.get(Skill.SNEAKING, "backstab"), 3);
        assertFalse(Synergies.isActive(skills, synergy));
        skills.setRank(Talents.get(Skill.NIGHTWALKER, "darkborn_bane"), 3);
        assertTrue(Synergies.isActive(skills, synergy));
    }

    // ---- Night, from the time (the client's cached sky darkness never updates) -----------------

    /**
     * Regression, 2026-10-07: Dark Sight under the open sky read {@code level.isNight()}, which a
     * client computes once when its level is created, so it stayed off all night for anyone who
     * joined by day. Night is now worked out from the time of day every time it is asked.
     */
    @Test
    void nightFollowsTheTimeOfDay() {
        assertFalse(NightwalkerMath.isNight(false, 0.0f, 0f, 0f), "noon is not night");
        assertFalse(NightwalkerMath.isNight(false, 0.2f, 0f, 0f), "late afternoon is not night");
        assertTrue(NightwalkerMath.isNight(false, 0.3f, 0f, 0f), "after dusk is night");
        assertTrue(NightwalkerMath.isNight(false, 0.5f, 0f, 0f), "midnight is night");
        assertFalse(NightwalkerMath.isNight(false, 0.85f, 0f, 0f), "morning is not night");
    }

    @Test
    void aThunderstormCountsAsNightAndAFixedTimeDimensionNever() {
        assertTrue(NightwalkerMath.isNight(false, 0.0f, 1f, 1f), "vanilla treats a thunderstorm as night");
        assertFalse(NightwalkerMath.isNight(false, 0.0f, 1f, 0f), "plain rain at noon is still day");
        assertFalse(NightwalkerMath.isNight(true, 0.5f, 0f, 0f), "the Nether and the End have no night");
    }
}
