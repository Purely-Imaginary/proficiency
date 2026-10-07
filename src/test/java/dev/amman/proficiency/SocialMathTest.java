package dev.amman.proficiency;

import dev.amman.proficiency.perk.PerkEffect;
import dev.amman.proficiency.perk.Talents;
import dev.amman.proficiency.skill.PlayerSkills;
import dev.amman.proficiency.skill.Skill;
import dev.amman.proficiency.skill.SkillCategory;
import dev.amman.proficiency.skill.SocialMath;
import dev.amman.proficiency.skill.SocialMath.Company;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Idea 35: the Social skill's XP rule and the company bonus its tree changes. */
class SocialMathTest {

    private static final double CAMARADERIE = 0.15;
    private static final double MENTOR = 0.50;

    // ---- XP: a share of what company added, nothing else --------------------------------------

    @Test
    void socialGetsAShareOfTheExtraCompanyAdded() {
        // 11.5 XP after camaraderie x1.15: company added 1.5 of it, half of that is 0.75.
        assertEquals(0.75, SocialMath.share(11.5, 1.15, 0.5), 1e-9);
        // Mentor x1.5 on 15 XP added 5; half is 2.5.
        assertEquals(2.5, SocialMath.share(15.0, 1.5, 0.5), 1e-9);
    }

    @Test
    void noCompanyFactorPaysNothing() {
        assertEquals(0.0, SocialMath.share(10.0, 1.0, 0.5));
        assertEquals(0.0, SocialMath.share(10.0, 0.9, 0.5));
    }

    @Test
    void nothingEarnedOrABrokenNumberPaysNothing() {
        assertEquals(0.0, SocialMath.share(0.0, 1.15, 0.5));
        assertEquals(0.0, SocialMath.share(-3.0, 1.15, 0.5));
        assertEquals(0.0, SocialMath.share(Double.NaN, 1.15, 0.5));
        assertEquals(0.0, SocialMath.share(10.0, Double.POSITIVE_INFINITY, 0.5));
        assertEquals(0.0, SocialMath.share(10.0, 1.15, 0.0));
    }

    // ---- The company bonus ---------------------------------------------------------------------

    @Test
    void untrainedCompanyIsTheOldBonus() {
        SocialMath.Talents none = SocialMath.Talents.NONE;
        assertEquals(0.0, SocialMath.extra(Company.NONE, CAMARADERIE, MENTOR, none, 0.0));
        assertEquals(0.15, SocialMath.extra(new Company(1, false, 0), CAMARADERIE, MENTOR, none, 0.0), 1e-9);
        assertEquals(0.50, SocialMath.extra(new Company(1, true, 0), CAMARADERIE, MENTOR, none, 0.0), 1e-9);
        // Without Strength in Numbers a crowd pays the same as one friend.
        assertEquals(0.15, SocialMath.extra(new Company(5, false, 0), CAMARADERIE, MENTOR, none, 0.0), 1e-9);
        assertEquals(24.0, SocialMath.radius(24.0, none));
        assertEquals(20, SocialMath.mentorGap(20, none));
    }

    @Test
    void eachTalentMovesItsOwnNumber() {
        SocialMath.Talents full = new SocialMath.Talents(3, 3, 3, 3, 3, 3, false);
        assertEquals(36.0, SocialMath.radius(24.0, full), 1e-9);
        assertEquals(8, SocialMath.mentorGap(20, full));
        // Camaraderie 0.15 + 3 x 0.05.
        assertEquals(0.30, SocialMath.extra(new Company(1, false, 0), CAMARADERIE, MENTOR, full, 0.0), 1e-9);
        // Mentor 0.50 + 3 x 0.05.
        assertEquals(0.65, SocialMath.extra(new Company(1, true, 0), CAMARADERIE, MENTOR, full, 0.0), 1e-9);
    }

    @Test
    void theCrowdCountsAtMostThreeExtraPlayers() {
        SocialMath.Talents crowd = new SocialMath.Talents(0, 0, 0, 0, 0, 3, false);
        double two = SocialMath.extra(new Company(2, false, 0), CAMARADERIE, MENTOR, crowd, 0.0);
        double four = SocialMath.extra(new Company(4, false, 0), CAMARADERIE, MENTOR, crowd, 0.0);
        double ten = SocialMath.extra(new Company(10, false, 0), CAMARADERIE, MENTOR, crowd, 0.0);
        assertEquals(0.15 + 0.06, two, 1e-9);
        assertEquals(0.15 + 0.18, four, 1e-9);
        assertEquals(four, ten, 1e-9);
    }

    @Test
    void teachingAddsOnTopAndThePassiveScalesTheWhole() {
        double taught = SocialMath.extra(new Company(1, false, SocialMath.teaching(3)), CAMARADERIE, MENTOR,
                SocialMath.Talents.NONE, 0.0);
        assertEquals(0.15 + 0.12, taught, 1e-9);
        double scaled = SocialMath.extra(new Company(1, false, 0), CAMARADERIE, MENTOR, SocialMath.Talents.NONE, 0.30);
        assertEquals(0.15 * 1.30, scaled, 1e-9);
        assertEquals(0.0, SocialMath.teaching(-2));
    }

    @Test
    void theMentorGapNeverDropsBelowOneExceptForHeartOfTheGroup() {
        assertEquals(1, SocialMath.mentorGap(4, new SocialMath.Talents(0, 0, 0, 3, 0, 0, false)));
        assertEquals(0, SocialMath.mentorGap(20, new SocialMath.Talents(0, 0, 0, 0, 0, 0, true)));
    }

    @Test
    void theBonusLingersOnlyWithStayAWhileAndOnlyForItsTime() {
        assertFalse(SocialMath.lingers(100, 90, 0), "no rank, no linger");
        assertTrue(SocialMath.lingers(300, 100, 1));
        assertFalse(SocialMath.lingers(301, 100, 1));
        assertTrue(SocialMath.lingers(700, 100, 3));
        assertFalse(SocialMath.lingers(701, 100, 3));
        assertFalse(SocialMath.lingers(50, 100, 3), "a clock that went backwards is not company");
    }

    @Test
    void goodCompanyIsTwentyPercentAndGrowsWithPower() {
        assertEquals(1.20, SocialMath.goodCompany(1.0), 1e-9);
        assertEquals(1.30, SocialMath.goodCompany(1.5), 1e-9);
        assertEquals(1.20, SocialMath.goodCompany(0.0), 1e-9);
    }

    // ---- The skill and its tree ----------------------------------------------------------------

    @Test
    void socialIsItsOwnCategoryAndItsTreeFitsInAHundredPoints() {
        assertEquals(SkillCategory.SOCIAL, Skill.SOCIAL.category());
        assertEquals(99, Talents.fullTreeCost(Skill.SOCIAL));
        PlayerSkills skills = new PlayerSkills();
        skills.setLevel(Skill.SOCIAL, 100);
        skills.fillTree(Skill.SOCIAL);
        assertTrue(skills.pointsSpent(Skill.SOCIAL) <= skills.pointsEarned(Skill.SOCIAL));
        // Kinship 5 x 5% and the capstone 15% on the passive.
        assertEquals(0.40, skills.rankSum(Skill.SOCIAL, PerkEffect.BONUS), 1e-9);
    }

    @Test
    void everyTalentTheCompanyCodeReadsIsOnTheTree() {
        for (String tag : new String[] {"company_radius", "camaraderie_up", "mentor_up", "mentor_gap",
                "company_linger", "company_crowd", "teaching", "in_step", "heart_of_group"}) {
            assertTrue(Talents.of(Skill.SOCIAL).stream().anyMatch(t -> tag.equals(t.special())), tag);
        }
    }
}
