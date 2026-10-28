package dev.amman.proficiency.look;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.amman.proficiency.look.PlayerLookRules.Gains;
import dev.amman.proficiency.look.PlayerLookRules.Pick;
import dev.amman.proficiency.look.PlayerLookRules.Side;
import dev.amman.proficiency.skill.Skill;
import dev.amman.proficiency.skill.SocialMath;
import java.util.List;
import org.junit.jupiter.api.Test;

class PlayerLookRulesTest {

    private static int[] levels(Object... pairs) {
        int[] out = new int[Skill.VALUES.length];
        for (int i = 0; i < pairs.length; i += 2) {
            out[((Skill) pairs[i]).ordinal()] = (Integer) pairs[i + 1];
        }
        return out;
    }

    private static Side side(int[] levels, double radius, double teach, int gap) {
        return new Side(levels, radius, teach, gap, SocialMath.Talents.NONE, 0, 0);
    }

    @Test
    void theTopThreeAreTheHighestLevelsInOrder() {
        List<Pick> top = PlayerLookRules.top(levels(Skill.MINING, 40, Skill.SWORDS, 72, Skill.FARMING, 55,
                Skill.COOKING, 3), 3);
        assertEquals(List.of(new Pick(Skill.SWORDS, 72), new Pick(Skill.FARMING, 55), new Pick(Skill.MINING, 40)), top);
    }

    @Test
    void aTieKeepsTheSkillOrderSoTheSamePlayerAlwaysShowsTheSameThree() {
        List<Pick> top = PlayerLookRules.top(levels(Skill.COOKING, 50, Skill.MINING, 50, Skill.FARMING, 50,
                Skill.SWORDS, 50), 3);
        assertEquals(3, top.size());
        int last = -1;
        for (Pick pick : top) {
            assertTrue(pick.skill().ordinal() > last, "ties in Skill order");
            last = pick.skill().ordinal();
        }
        assertEquals(top, PlayerLookRules.top(levels(Skill.COOKING, 50, Skill.MINING, 50, Skill.FARMING, 50,
                Skill.SWORDS, 50), 3));
    }

    @Test
    void levelZeroSkillsAreNeverShownAndAFreshPlayerShowsNone() {
        assertTrue(PlayerLookRules.top(new int[0], 3).isEmpty());
        assertEquals(1, PlayerLookRules.top(levels(Skill.MINING, 1), 3).size());
    }

    @Test
    void companyAppliesInsideMyRadiusAndNotBeyondIt() {
        Side me = side(levels(Skill.MINING, 10), 24, 24, 5);
        Side them = side(levels(Skill.MINING, 10), 24, 24, 5);
        Gains near = PlayerLookRules.gains(24.0, me, them, 0.15, 0.25, 0.0, false, true);
        assertTrue(near.has(PlayerLookRules.IN_COMPANY));
        assertEquals(0.15, near.companyExtra(), 1e-9);
        Gains far = PlayerLookRules.gains(24.01, me, them, 0.15, 0.25, 0.0, false, true);
        assertFalse(far.has(PlayerLookRules.IN_COMPANY));
        assertEquals(0.0, far.companyExtra(), 1e-9);
        assertTrue(far.isEmpty());
        assertEquals(24.0, far.companyRadius(), 1e-9);
    }

    @Test
    void theRadiusThatCountsIsTheLookersNotTheOthers() {
        Side me = side(levels(Skill.MINING, 10), 8, 8, 5);
        Side them = side(levels(Skill.MINING, 10), 40, 40, 5);
        assertFalse(PlayerLookRules.gains(20.0, me, them, 0.15, 0.25, 0.0, false, true).has(PlayerLookRules.IN_COMPANY));
        assertTrue(PlayerLookRules.gains(20.0, them, me, 0.15, 0.25, 0.0, false, true).has(PlayerLookRules.IN_COMPANY));
    }

    @Test
    void aMentorSwapsCamaraderieForTheMentorBonusAndNeedsTheGap() {
        Side me = side(levels(Skill.SWORDS, 10), 24, 24, 5);
        Side lead4 = side(levels(Skill.SWORDS, 14), 24, 24, 5);
        Side lead5 = side(levels(Skill.SWORDS, 15), 24, 24, 5);
        assertFalse(PlayerLookRules.gains(3, me, lead4, 0.15, 0.25, 0.0, false, true).has(PlayerLookRules.MENTOR));
        Gains g = PlayerLookRules.gains(3, me, lead5, 0.15, 0.25, 0.0, false, true);
        assertTrue(g.has(PlayerLookRules.MENTOR));
        assertEquals(0.25, g.companyExtra(), 1e-9);
        assertEquals(List.of(Skill.SWORDS), g.mentors());
    }

    @Test
    void teachingReachesTheTeachersRadiusNotTheStudents() {
        Side me = side(levels(Skill.MINING, 10), 24, 24, 5);
        Side teacher = side(levels(Skill.MINING, 60), 24, 30, 5);
        assertTrue(PlayerLookRules.gains(28, me, teacher, 0.15, 0.25, 0.0, false, true).has(PlayerLookRules.CAN_TEACH_ME));
        assertFalse(PlayerLookRules.gains(31, me, teacher, 0.15, 0.25, 0.0, false, true).has(PlayerLookRules.CAN_TEACH_ME));
        // Company (24) ends before teaching (30): at 28 teaching applies and company does not.
        assertFalse(PlayerLookRules.gains(28, me, teacher, 0.15, 0.25, 0.0, false, true).has(PlayerLookRules.IN_COMPANY));
    }

    @Test
    void iTeachThemWithMyOwnRadiusAndTheirGap() {
        Side me = side(levels(Skill.MINING, 60), 24, 30, 5);
        Side them = side(levels(Skill.MINING, 10), 24, 24, 5);
        Gains g = PlayerLookRules.gains(29, me, them, 0.15, 0.25, 0.0, false, true);
        assertTrue(g.has(PlayerLookRules.I_CAN_TEACH));
        assertEquals(List.of(Skill.MINING), g.students());
        assertFalse(PlayerLookRules.gains(31, me, them, 0.15, 0.25, 0.0, false, true).has(PlayerLookRules.I_CAN_TEACH));
    }

    @Test
    void teachingOffTakesTheTeachingFlagsAwayButNotCompany() {
        Side me = side(levels(Skill.MINING, 10), 24, 24, 5);
        Side teacher = side(levels(Skill.MINING, 60), 24, 24, 5);
        Gains g = PlayerLookRules.gains(3, me, teacher, 0.15, 0.25, 0.0, false, false);
        assertFalse(g.has(PlayerLookRules.CAN_TEACH_ME));
        assertTrue(g.has(PlayerLookRules.IN_COMPANY));
    }

    @Test
    void theTeachingTalentAddsOnlyFromABetterSocialPlayer() {
        Side me = new Side(levels(Skill.MINING, 10), 24, 24, 5, SocialMath.Talents.NONE, 0, 20);
        Side better = new Side(levels(Skill.MINING, 10), 24, 24, 5, SocialMath.Talents.NONE, 2, 30);
        Side worse = new Side(levels(Skill.MINING, 10), 24, 24, 5, SocialMath.Talents.NONE, 2, 10);
        assertEquals(0.15 + 0.08, PlayerLookRules.gains(3, me, better, 0.15, 0.25, 0.0, false, true).companyExtra(), 1e-9);
        assertEquals(0.15, PlayerLookRules.gains(3, me, worse, 0.15, 0.25, 0.0, false, true).companyExtra(), 1e-9);
    }

    @Test
    void aSharedHearthShowsAtAnyDistance() {
        Side me = side(levels(Skill.MINING, 10), 2, 2, 5);
        Gains g = PlayerLookRules.gains(100, me, me, 0.15, 0.25, 0.0, true, true);
        assertTrue(g.has(PlayerLookRules.SAME_HEARTH));
        assertFalse(g.isEmpty());
    }

    @Test
    void theSocialPassiveScalesTheWholeExtraAndAPairGetsNoCrowdBonus() {
        SocialMath.Talents crowd = new SocialMath.Talents(0, 0, 0, 0, 0, 3, false);
        Side me = new Side(levels(Skill.MINING, 10), 24, 24, 5, crowd, 0, 0);
        Side them = side(levels(Skill.MINING, 10), 24, 24, 5);
        assertEquals(0.15 * 1.1, PlayerLookRules.gains(3, me, them, 0.15, 0.25, 0.1, false, true).companyExtra(), 1e-9);
    }

    @Test
    void mentorListsAreCappedAndBiggestLeadFirst() {
        int[] a = new int[Skill.VALUES.length];
        int[] b = new int[Skill.VALUES.length];
        for (Skill skill : Skill.VALUES) {
            a[skill.ordinal()] = 20 + skill.ordinal();
        }
        Side me = side(b, 24, 24, 5);
        Side them = side(a, 24, 24, 5);
        Gains g = PlayerLookRules.gains(1, me, them, 0.15, 0.25, 0.0, false, true);
        assertEquals(PlayerLookRules.LIST_MAX, g.mentors().size());
        assertEquals(Skill.VALUES[Skill.VALUES.length - 1], g.mentors().get(0));
    }

    @Test
    void percentRoundsToAWholeNumber() {
        assertEquals(15, PlayerLookRules.percent(0.15));
        assertEquals(23, PlayerLookRules.percent(0.2299));
    }
}
