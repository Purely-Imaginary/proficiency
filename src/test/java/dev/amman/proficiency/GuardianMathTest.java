package dev.amman.proficiency;

import dev.amman.proficiency.perk.PerkEffect;
import dev.amman.proficiency.perk.Synergies;
import dev.amman.proficiency.perk.Synergy;
import dev.amman.proficiency.perk.Talent;
import dev.amman.proficiency.perk.Talents;
import dev.amman.proficiency.skill.GuardianMath;
import dev.amman.proficiency.skill.PlayerSkills;
import dev.amman.proficiency.skill.Skill;
import dev.amman.proficiency.skill.SkillCategory;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Idea 38: Guardian's danger scale, its five XP sources' rules, and its talent numbers. */
class GuardianMathTest {

    // ---- Danger -------------------------------------------------------------------------------

    @Test
    void aHealthyFriendPaysLittleAndADyingOnePaysMost() {
        assertEquals(0.25, GuardianMath.danger(1.0), 1e-9);
        assertEquals(1.125, GuardianMath.danger(0.5), 1e-9);
        assertEquals(2.0, GuardianMath.danger(0.0), 1e-9);
        assertTrue(GuardianMath.danger(0.2) > GuardianMath.danger(0.6));
    }

    @Test
    void aBrokenHealthShareReadsAsSafeNeverAsDanger() {
        assertEquals(0.25, GuardianMath.danger(Double.NaN), 1e-9);
        assertEquals(0.25, GuardianMath.danger(3.0), 1e-9);
        assertEquals(2.0, GuardianMath.danger(-1.0), 1e-9);
        assertEquals(1.0, GuardianMath.share(5, 0), 1e-9);
        assertEquals(0.25, GuardianMath.share(5, 20), 1e-9);
    }

    // ---- Cover and block ----------------------------------------------------------------------

    @Test
    void oneHitCountsAtMostTwentyAndOneMobAtMostForty() {
        assertEquals(6.0, GuardianMath.countedDamage(6, 0), 1e-9);
        assertEquals(20.0, GuardianMath.countedDamage(50, 0), 1e-9);
        assertEquals(5.0, GuardianMath.countedDamage(10, 35), 1e-9);
        assertEquals(0.0, GuardianMath.countedDamage(10, 40), 1e-9);
        assertEquals(0.0, GuardianMath.countedDamage(-3, 0), 1e-9);
        assertEquals(0.0, GuardianMath.countedDamage(Double.NaN, 0), 1e-9);
    }

    @Test
    void damageXpIsDamageTimesRateTimesDanger() {
        assertEquals(4 * 1.0 * 1.125, GuardianMath.damageXp(4, 1.0, GuardianMath.danger(0.5)), 1e-9);
        assertEquals(0.0, GuardianMath.damageXp(0, 1.0, 2.0), 1e-9);
        assertEquals(0.0, GuardianMath.damageXp(4, 0.0, 2.0), 1e-9);
    }

    @Test
    void aTurnIsRememberedTwentySecondsAndAPullNeedsAHitWithinTwo() {
        assertTrue(GuardianMath.remembered(100, 500));
        assertFalse(GuardianMath.remembered(100, 501));
        assertFalse(GuardianMath.remembered(-1, 10));
        assertTrue(GuardianMath.pulled(100, 140));
        assertFalse(GuardianMath.pulled(100, 141));
        assertFalse(GuardianMath.pulled(-1, 10));
    }

    // ---- Avenger, heal, revive ----------------------------------------------------------------

    @Test
    void anAvengerKillMustComeWithinFiveSeconds() {
        assertTrue(GuardianMath.avenges(1000, 1100));
        assertFalse(GuardianMath.avenges(1000, 1101));
        assertFalse(GuardianMath.avenges(-1, 5));
    }

    @Test
    void aHealPaysOnlyTheHealthItGivesBack() {
        assertEquals(4.0, GuardianMath.healed(4, 10, 20), 1e-9);
        assertEquals(2.0, GuardianMath.healed(8, 18, 20), 1e-9, "the overheal is not paid");
        assertEquals(0.0, GuardianMath.healed(8, 20, 20), 1e-9, "a full-health friend gains nothing");
        // Healing II on a friend at 4 of 20: 8 health back, at danger 1.65.
        assertEquals(8 * GuardianMath.danger(0.2), GuardianMath.healXp(8, 4, 20, 1.0), 1e-9);
        assertEquals(0.0, GuardianMath.healXp(8, 20, 20, 1.0), 1e-9);
    }

    @Test
    void aHealCountsOnlyAfterRealDangerInTheLastThirtySeconds() {
        assertTrue(GuardianMath.healCounts(0, 600));
        assertFalse(GuardianMath.healCounts(0, 601));
        assertFalse(GuardianMath.healCounts(-1, 5), "never hurt by the world: a PvP loop");
    }

    @Test
    void aCloseCallIsCrossingThirtyPercentAndStayingAlive() {
        assertTrue(GuardianMath.closeCall(10, 5, 20));
        assertTrue(GuardianMath.closeCall(6, 5.9, 20));
        assertFalse(GuardianMath.closeCall(5, 3, 20), "already under the line");
        assertFalse(GuardianMath.closeCall(10, 7, 20), "still above the line");
        assertFalse(GuardianMath.closeCall(10, 0, 20), "a death is not a close call");
        assertTrue(GuardianMath.reviveDone(0, 200));
        assertFalse(GuardianMath.reviveDone(0, 199));
    }

    // ---- The passive, Intercept, Shield Wall and the tree's numbers ---------------------------

    @Test
    void thePassiveIsCappedAtHalf() {
        assertEquals(1.0, GuardianMath.passiveMultiplier(0), 1e-9);
        assertEquals(0.85, GuardianMath.passiveMultiplier(0.15), 1e-9);
        assertEquals(0.5, GuardianMath.passiveMultiplier(3.0), 1e-9);
    }

    @Test
    void interceptTakesHalfAndLessWithPower() {
        assertEquals(4f, GuardianMath.interceptDamage(8f, 1.0), 1e-6);
        assertEquals(2f, GuardianMath.interceptDamage(8f, 2.0), 1e-6);
        assertEquals(4f, GuardianMath.interceptDamage(8f, 0.5), 1e-6, "power below 1 never makes it worse");
        assertEquals(0f, GuardianMath.interceptDamage(-1f, 1.0), 1e-6);
    }

    @Test
    void bodyguardWidensInterceptAndShieldWall() {
        assertEquals(6.0, GuardianMath.interceptRadius(0), 1e-9);
        assertEquals(12.0, GuardianMath.interceptRadius(3), 1e-9);
        assertEquals(8.0, GuardianMath.shieldWallRadius(0), 1e-9);
        assertEquals(14.0, GuardianMath.shieldWallRadius(3), 1e-9);
    }

    @Test
    void theTreesNumbers() {
        assertEquals(3f, GuardianMath.blockHeal(3), 1e-6);
        assertEquals(0f, GuardianMath.blockHeal(0), 1e-6);
        assertEquals(800, GuardianMath.shareTicks(2400, 1));
        assertEquals(2400, GuardianMath.shareTicks(2400, 3));
        assertEquals(0, GuardianMath.shareTicks(2400, 0));
        assertTrue(GuardianMath.swornReady(-1, 5));
        assertFalse(GuardianMath.swornReady(1000, 12999));
        assertTrue(GuardianMath.swornReady(1000, 13000));
        assertEquals(7f, GuardianMath.swornDamage(14f), 1e-6);
        // A 1-heart guardian does not die for an ally's 4-heart blow.
        assertFalse(GuardianMath.swornSurvives(4f, 2f, 0f));
        assertFalse(GuardianMath.swornSurvives(Float.MAX_VALUE / 2, 20f, 0f));
        assertTrue(GuardianMath.swornSurvives(4f, 2f, 4f));
        assertTrue(GuardianMath.swornSurvives(5f, 20f, 0f));
    }

    @Test
    void guardianIsACombatSkillAddedAfterCourage() {
        // 32nd; Charger (idea 39) and later skills come after it.
        assertSame(Skill.GUARDIAN, Skill.VALUES[31]);
        assertSame(SkillCategory.COMBAT, Skill.GUARDIAN.category());
        assertSame(Skill.GUARDIAN, Skill.byId("guardian"));
    }

    @Test
    void theTreeFillsAtLevelOneHundred() {
        assertTrue(Talents.fullTreeCost(Skill.GUARDIAN) <= 100, "tree costs " + Talents.fullTreeCost(Skill.GUARDIAN));
        PlayerSkills skills = new PlayerSkills();
        skills.setLevel(Skill.GUARDIAN, 100);
        skills.fillTree(Skill.GUARDIAN);
        Talents.of(Skill.GUARDIAN).forEach(talent -> assertTrue(skills.isFull(talent), talent.key()));
    }

    @Test
    void bulwarkBridgesBlockingAndOathkeeperBridgesCourage() {
        Synergy bulwark = Synergies.byId("bulwark");
        PlayerSkills skills = new PlayerSkills();
        skills.setRank(Talents.get(Skill.BLOCKING, "reinforced"), 5);
        assertFalse(Synergies.isActive(skills, bulwark));
        skills.setRank(Talents.get(Skill.GUARDIAN, "bodyguard"), 3);
        assertTrue(Synergies.isActive(skills, bulwark));
        assertTrue(Synergies.hasSpecial(skills, "bulwark"));
        assertEquals(1.10, Synergies.multiplier(skills, Skill.GUARDIAN, PerkEffect.BONUS), 1e-9);

        Synergy oath = Synergies.byId("oathkeeper");
        skills.setRank(Talents.get(Skill.COURAGE, "hot_blood"), 5);
        assertFalse(Synergies.isActive(skills, oath));
        skills.setRank(Talents.get(Skill.GUARDIAN, "warding"), 5);
        assertTrue(Synergies.isActive(skills, oath));
        assertTrue(Synergies.hasSpecial(skills, "oathkeeper"));
        assertEquals(1.10, Synergies.multiplier(skills, Skill.COURAGE, PerkEffect.XP_RATE), 1e-9);
    }

    @Test
    void nothingInTheTreeCopiesBastionOrEndurance() {
        // Blocking owns Bastion (players near your raised shield take less damage); Endurance owns
        // your own survival. Guardian's nodes act on or for someone else.
        for (Talent talent : Talents.of(Skill.GUARDIAN)) {
            String special = talent.special();
            assertFalse("bastion".equals(special) || "aegis".equals(special) || "last_stand".equals(special)
                    || "indomitable".equals(special) || "hurt_regen".equals(special), talent.key());
        }
    }
}
