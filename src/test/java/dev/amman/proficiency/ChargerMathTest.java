package dev.amman.proficiency;

import dev.amman.proficiency.perk.PerkEffect;
import dev.amman.proficiency.perk.Synergies;
import dev.amman.proficiency.perk.Synergy;
import dev.amman.proficiency.perk.Talent;
import dev.amman.proficiency.perk.Talents;
import dev.amman.proficiency.skill.ChargerMath;
import dev.amman.proficiency.skill.PlayerSkills;
import dev.amman.proficiency.skill.Skill;
import dev.amman.proficiency.skill.SkillCategory;
import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Idea 39: Charger's charge, first blood, Spearhead and anti-farm rules, and its talent numbers. */
class ChargerMathTest {

    // ---- The charge ---------------------------------------------------------------------------

    @Test
    void walkingFiveBlocksAtAMobIsACharge() {
        ChargerMath.Trail trail = new ChargerMath.Trail();
        // Walking speed, about 4.3 blocks a second: no sprint needed.
        for (int t = 0; t <= 40; t += 2) {
            trail.add(1000 + t, t * 0.2, 64, 0);
        }
        double closed = trail.closed(1040, 8.0, 64, 0, 10.0, 64, 0);
        assertEquals(8.0, closed, 1e-9);
        assertTrue(ChargerMath.isCharge(closed, 0));
    }

    @Test
    void standingStillIsNoCharge() {
        ChargerMath.Trail trail = new ChargerMath.Trail();
        for (int t = 0; t <= 40; t += 2) {
            trail.add(t, 0, 64, 0);
        }
        // The mob walked up to you: only your own movement counts.
        assertEquals(0.0, trail.closed(40, 0, 64, 0, 1.5, 64, 0), 1e-9);
        assertFalse(ChargerMath.isCharge(0.0, 0));
    }

    @Test
    void backingAwayFromAFollowerThenTurningIsNoCharge() {
        ChargerMath.Trail trail = new ChargerMath.Trail();
        // You walk 8 blocks backwards (+x to 0); a zombie follows 1.5 blocks off, on the side you
        // left. Measured from your old spots to where it is now, you "closed" 6.5 blocks.
        for (int t = 0; t <= 40; t += 2) {
            trail.add(t, 8.0 - t * 0.2, 64, 0);
        }
        assertEquals(0.0, trail.closed(40, 0, 64, 0, 1.5, 64, 0), 1e-9);
    }

    @Test
    void whenBothCloseInOnlyYourPartCounts() {
        ChargerMath.Trail trail = new ChargerMath.Trail();
        // You walk 3 blocks (0 to 3), the mob walks 5 (from 10 to 5): 8 closed, 3 of them yours.
        for (int t = 0; t <= 40; t += 2) {
            trail.add(t, t * 0.075, 64, 0);
        }
        double closed = trail.closed(40, 3.0, 64, 0, 5.0, 64, 0);
        assertEquals(3.0, closed, 1e-9);
        assertFalse(ChargerMath.isCharge(closed, 0));
    }

    @Test
    void runningPastAMobIsNoChargeOnIt() {
        ChargerMath.Trail trail = new ChargerMath.Trail();
        // You ran 8 blocks along +z past a mob beside your path, then turned to hit it.
        for (int t = 0; t <= 40; t += 2) {
            trail.add(t, 0, 64, t * 0.2);
        }
        // It stands 1 block to your side, where you are now: you moved across the line to it.
        assertTrue(trail.closed(40, 0, 64, 8.0, 1.0, 64, 8.0) < 1e-9);
    }

    @Test
    void onlyTheLastTwoSecondsCount() {
        ChargerMath.Trail trail = new ChargerMath.Trail();
        trail.add(0, -20, 64, 0);
        for (int t = 2; t <= 100; t += 2) {
            trail.add(t, 0, 64, 0);
        }
        assertEquals(0.0, trail.closed(100, 0, 64, 0, 2, 64, 0), 1e-9);
    }

    @Test
    void fallingOntoAMobIsACharge() {
        ChargerMath.Trail trail = new ChargerMath.Trail();
        trail.add(0, 0, 80, 0);
        assertEquals(14.0, trail.closed(20, 0, 66, 0, 0, 65, 0), 1e-9);
    }

    @Test
    void aClearedTrailHasNoCharge() {
        ChargerMath.Trail trail = new ChargerMath.Trail();
        trail.add(0, -10, 64, 0);
        trail.clear();
        assertEquals(0, trail.size());
        assertEquals(0.0, trail.closed(10, 0, 64, 0, 1, 64, 0), 1e-9);
    }

    @Test
    void theTrailRingOverwritesOldSamples() {
        ChargerMath.Trail trail = new ChargerMath.Trail();
        for (int t = 0; t < 400; t += 2) {
            trail.add(t, t, 64, 0);
        }
        assertTrue(trail.size() <= 22);
    }

    @Test
    void headStartShortensTheChargeButNeverUnderTwo() {
        assertEquals(5.0, ChargerMath.chargeNeeds(0), 1e-9);
        assertEquals(3.5, ChargerMath.chargeNeeds(3), 1e-9);
        assertEquals(2.0, ChargerMath.chargeNeeds(100), 1e-9);
        assertFalse(ChargerMath.isCharge(4.9, 0));
        assertTrue(ChargerMath.isCharge(4.0, 2));
    }

    @Test
    void chargeXpGrowsWithDistanceCapsAtTwelveAndPaysMoreSprinting() {
        assertEquals(2.0, ChargerMath.chargeXp(5, false, 0.4), 1e-9);
        assertEquals(4.8, ChargerMath.chargeXp(12, false, 0.4), 1e-9);
        assertEquals(4.8, ChargerMath.chargeXp(40, false, 0.4), 1e-9);
        assertEquals(3.0, ChargerMath.chargeXp(5, true, 0.4), 1e-9);
        assertEquals(0.0, ChargerMath.chargeXp(0, true, 0.4), 1e-9);
        assertEquals(0.0, ChargerMath.chargeXp(5, true, 0), 1e-9);
    }

    // ---- First blood --------------------------------------------------------------------------

    @Test
    void firstBloodNeedsTenQuietSecondsAndAFreshMob() {
        assertTrue(ChargerMath.firstBlood(-1, 1000, 20, 20));
        assertFalse(ChargerMath.firstBlood(900, 1000, 20, 20), "hit 5 s ago");
        assertFalse(ChargerMath.firstBlood(800, 1000, 20, 20), "hit exactly 10 s ago");
        assertTrue(ChargerMath.firstBlood(799, 1000, 20, 20));
        assertTrue(ChargerMath.firstBlood(-1, 1000, 18, 20), "90% is still fresh");
        assertFalse(ChargerMath.firstBlood(-1, 1000, 17.9, 20), "a softened mob (drop tower)");
        assertFalse(ChargerMath.firstBlood(-1, 1000, 5, 0), "a broken max health");
    }

    @Test
    void firstBloodIsWorthTheMob() {
        assertEquals(1.0, ChargerMath.worth(20, false), 1e-9);
        assertEquals(0.5, ChargerMath.worth(4, false), 1e-9);
        assertEquals(3.0, ChargerMath.worth(300, false), 1e-9);
        assertEquals(3.0, ChargerMath.worth(20, true), 1e-9);
        assertEquals(0.5, ChargerMath.worth(Double.NaN, false), 1e-9);
        assertEquals(3.0, ChargerMath.firstBloodXp(3.0, 1.0), 1e-9);
        assertEquals(6.0, ChargerMath.spearheadKillXp(2.0, 3.0), 1e-9);
        assertEquals(0.0, ChargerMath.firstBloodXp(0, 3.0), 1e-9);
    }

    @Test
    void thePassiveAndBoostsMultiplyFirstBlood() {
        assertEquals(1.30, ChargerMath.firstBloodMultiplier(0.30, 1.0), 1e-9);
        assertEquals(1.95, ChargerMath.firstBloodMultiplier(0.30, 1.5), 1e-9);
        assertEquals(1.0, ChargerMath.firstBloodMultiplier(-1, Double.NaN), 1e-9);
    }

    // ---- Anti-farm ----------------------------------------------------------------------------

    @Test
    void oneSpotPaysSixteenTimesInFiveMinutes() {
        assertTrue(ChargerMath.spotAllows(0));
        assertTrue(ChargerMath.spotAllows(15));
        assertFalse(ChargerMath.spotAllows(16));
        assertTrue(ChargerMath.inSpotWindow(0, 5999));
        assertFalse(ChargerMath.inSpotWindow(0, 6000));
        assertFalse(ChargerMath.inSpotWindow(100, 50));
        assertEquals(3, ChargerMath.CHARGE_PAYS_PER_MOB);
    }

    // ---- Spearhead, Trust the Line, Shield and Spear ----------------------------------------

    @Test
    void aheadAndBehindAreHalfPlanes() {
        // Facing +z.
        assertTrue(ChargerMath.ahead(0, 1, 0.5, 3));
        assertFalse(ChargerMath.ahead(0, 1, 0, -3));
        assertTrue(ChargerMath.behind(0, 1, 1, -3));
        assertFalse(ChargerMath.behind(0, 1, 3, 0), "beside you is neither");
        assertFalse(ChargerMath.ahead(0, 1, 3, 0));
        // Float noise from a yaw of 0 must not make a player beside you count as behind.
        assertFalse(ChargerMath.behind(1.2e-16, 1, -6, 0));
        assertFalse(ChargerMath.behind(0, 1, 0, -0.4), "less than half a block behind");
        assertTrue(ChargerMath.behind(0, 2, 0, -0.6), "facing need not be unit length");
    }

    @Test
    void spearheadBonusGrowsWithAGuardianBehindAndTheSynergy() {
        assertEquals(0.10, ChargerMath.spearheadBonus(false, false, false), 1e-9);
        assertEquals(0.20, ChargerMath.spearheadBonus(true, false, false), 1e-9);
        assertEquals(0.15, ChargerMath.spearheadBonus(false, true, false), 1e-9);
        assertEquals(0.20, ChargerMath.spearheadBonus(false, true, true), 1e-9);
        assertEquals(0.5, ChargerMath.spearheadKnockback(false), 1e-9);
        assertEquals(1.0, ChargerMath.spearheadKnockback(true), 1e-9);
    }

    @Test
    void trustTheLineCutsFriendlyFireOnlyInSpearhead() {
        assertEquals(1.0, ChargerMath.trustMultiplier(3, false), 1e-9, "outside Spearhead: no cut");
        assertEquals(1.0, ChargerMath.trustMultiplier(0, true), 1e-9);
        assertEquals(0.50, ChargerMath.trustMultiplier(1, true), 1e-9);
        assertEquals(0.35, ChargerMath.trustMultiplier(2, true), 1e-9);
        assertEquals(0.20, ChargerMath.trustMultiplier(3, true), 1e-9);
        assertEquals(0.20, ChargerMath.trustMultiplier(9, true), 1e-9);
    }

    // ---- Sustain, Breach, Charge! ---------------------------------------------------------------

    @Test
    void lifestealOnlyInTheFiveSecondsAfterACharge() {
        assertTrue(ChargerMath.lifestealOpen(1000, 1000));
        assertTrue(ChargerMath.lifestealOpen(1000, 1100));
        assertFalse(ChargerMath.lifestealOpen(1000, 1101));
        assertFalse(ChargerMath.lifestealOpen(-1, 1000));
        assertEquals(0.7f, ChargerMath.lifesteal(7, 0), 1e-6);
        assertEquals(1.0f, ChargerMath.lifesteal(30, 0), 1e-6, "capped at 1 health");
        assertEquals(1.75f, ChargerMath.lifesteal(7, 3), 1e-6);
        assertEquals(2.5f, ChargerMath.lifesteal(30, 3), 1e-6);
        assertEquals(0f, ChargerMath.lifesteal(-3, 3), 1e-6);
    }

    @Test
    void firstBloodsShieldAndCrashIn() {
        assertEquals(160, ChargerMath.absorbTicks(0));
        assertEquals(400, ChargerMath.absorbTicks(3));
        assertEquals(0, ChargerMath.absorbAmplifier(2));
        assertEquals(1, ChargerMath.absorbAmplifier(3));
        assertTrue(ChargerMath.absorbReady(-1, 50));
        assertFalse(ChargerMath.absorbReady(100, 299));
        assertTrue(ChargerMath.absorbReady(100, 300));
    }

    @Test
    void breachIsASmallConeAhead() {
        assertTrue(ChargerMath.inCone(0, 1, 0, 3));
        assertTrue(ChargerMath.inCone(0, 1, 1, 2));
        assertFalse(ChargerMath.inCone(0, 1, 3, 1), "too far to the side");
        assertFalse(ChargerMath.inCone(0, 1, 0, 5), "too far");
        assertFalse(ChargerMath.inCone(0, 1, 0, -2), "behind");
        assertEquals(0.8, ChargerMath.breachKnockback(1.0), 1e-9);
        assertEquals(1.2, ChargerMath.breachKnockback(1.5), 1e-9);
        assertEquals(30, ChargerMath.breachStaggerTicks(0.2));
        assertEquals(45, ChargerMath.breachStaggerTicks(1.5));
    }

    @Test
    void warbringerDoublesTheDash() {
        assertEquals(1.2, ChargerMath.dashSpeed(false), 1e-9);
        assertEquals(2.4, ChargerMath.dashSpeed(true), 1e-9);
    }

    // ---- The skill and its tree ---------------------------------------------------------------

    @Test
    void chargerIsACombatSkillAddedAfterGuardian() {
        assertSame(Skill.CHARGER, Skill.VALUES[32]);
        assertSame(SkillCategory.COMBAT, Skill.CHARGER.category());
        assertSame(Skill.CHARGER, Skill.byId("charger"));
    }

    @Test
    void theTreeFillsAtLevelOneHundred() {
        assertTrue(Talents.fullTreeCost(Skill.CHARGER) <= 100, "tree costs " + Talents.fullTreeCost(Skill.CHARGER));
        PlayerSkills skills = new PlayerSkills();
        skills.setLevel(Skill.CHARGER, 100);
        skills.fillTree(Skill.CHARGER);
        Talents.of(Skill.CHARGER).forEach(talent -> assertTrue(skills.isFull(talent), talent.key()));
    }

    @Test
    void trustTheLineOpensAtLevelTen() {
        Talent trust = Talents.get(Skill.CHARGER, "trust_the_line");
        assertEquals("trust_line", trust.special());
        assertEquals(10, trust.requiredLevel());
        assertEquals(3, trust.maxRank());
    }

    @Test
    void shieldAndSpearBridgesGuardian() {
        Synergy pair = Synergies.byId("shield_and_spear");
        PlayerSkills skills = new PlayerSkills();
        skills.setRank(Talents.get(Skill.CHARGER, "trust_the_line"), 3);
        assertFalse(Synergies.isActive(skills, pair));
        skills.setRank(Talents.get(Skill.GUARDIAN, "bodyguard"), 3);
        assertTrue(Synergies.isActive(skills, pair));
        assertTrue(Synergies.hasSpecial(skills, "shield_and_spear"));
        assertEquals(1.10, Synergies.multiplier(skills, Skill.CHARGER, PerkEffect.BONUS), 1e-9);
    }

    @Test
    void nothingInTheTreeCopiesCourageOrGuardian() {
        // Courage owns the odds (crowds, Rally, Challenge); Guardian owns protecting others
        // (Intercept, Taunt, shared Absorption). Charger's nodes are about the charge.
        Set<String> theirs = new java.util.HashSet<>();
        for (Skill other : new Skill[] {Skill.COURAGE, Skill.GUARDIAN}) {
            for (Talent talent : Talents.of(other)) {
                if (talent.special() != null) {
                    theirs.add(talent.special());
                }
            }
        }
        for (Talent talent : Talents.of(Skill.CHARGER)) {
            assertFalse(talent.special() != null && theirs.contains(talent.special()), talent.key());
        }
    }
}
