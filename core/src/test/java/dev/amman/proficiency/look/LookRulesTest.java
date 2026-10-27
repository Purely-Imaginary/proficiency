package dev.amman.proficiency.look;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.amman.proficiency.look.LookRules.Verdict;
import org.junit.jupiter.api.Test;

class LookRulesTest {

    @Test
    void anOreTheToolCanHarvestPays() {
        assertEquals(Verdict.PAYS, LookRules.block(true, true, false, true, 2.5));
    }

    @Test
    void aPlacedBlockOfAPayingKindSaysItPaysNothing() {
        assertEquals(Verdict.PLACED, LookRules.block(true, true, true, true, 2.5));
    }

    @Test
    void anOreTheToolCannotHarvestShowsNothing() {
        assertEquals(Verdict.HIDDEN, LookRules.block(true, false, false, true, 2.5));
        // Even a placed one: nothing would be paid either way, and the line would be noise.
        assertEquals(Verdict.HIDDEN, LookRules.block(true, false, true, true, 2.5));
    }

    @Test
    void noRuleOrAnUnripeCropOrASwitchedOffSkillShowsNothing() {
        assertEquals(Verdict.HIDDEN, LookRules.block(false, true, false, true, 1.0));
        assertEquals(Verdict.HIDDEN, LookRules.block(true, true, false, false, 1.0));
        assertEquals(Verdict.HIDDEN, LookRules.block(true, true, true, false, 1.0));
    }

    @Test
    void aZeroOrBrokenAmountShowsNothing() {
        assertEquals(Verdict.HIDDEN, LookRules.block(true, true, false, true, 0.0));
        assertEquals(Verdict.HIDDEN, LookRules.block(true, true, false, true, Double.NaN));
        assertEquals(Verdict.HIDDEN, LookRules.block(true, true, false, true, Double.POSITIVE_INFINITY));
    }

    @Test
    void mobsNeedSomeXpAndAnEnabledSkill() {
        assertTrue(LookRules.mobPays(true, 1.0, 0.0));
        assertTrue(LookRules.mobPays(true, 0.0, 3.0));
        assertFalse(LookRules.mobPays(true, 0.0, 0.0));
        assertFalse(LookRules.mobPays(false, 1.0, 3.0));
        assertFalse(LookRules.mobPays(true, Double.NaN, 3.0));
    }

    @Test
    void xpPrintsLikeTheBanner() {
        assertEquals("12", LookRules.formatXp(12.4));
        assertEquals("10", LookRules.formatXp(10.0));
        assertEquals("1.2", LookRules.formatXp(1.24));
        assertEquals("0.6", LookRules.formatXp(0.575));
        assertEquals("0.1", LookRules.formatXp(0.01));
    }

    @Test
    void flagsAreDistinctBits() {
        int all = LookRules.PLACED | LookRules.FIRST | LookRules.MOB;
        assertEquals(7, all);
        assertTrue(LookRules.has(all, LookRules.FIRST));
        assertFalse(LookRules.has(LookRules.PLACED, LookRules.FIRST));
    }

    @Test
    void hearthHeadlineAndWarmth() {
        assertEquals("Hall · Gothic", LookRules.hearthHeadline("Hall", "Gothic"));
        assertEquals("Hall", LookRules.hearthHeadline("Hall", ""));
        assertEquals("Hall", LookRules.hearthHeadline("Hall", null));
        assertEquals(0, LookRules.warmthPercent(-1));
        assertEquals(0, LookRules.warmthPercent(Double.NaN));
        assertEquals(37, LookRules.warmthPercent(0.37));
        assertEquals(100, LookRules.warmthPercent(4));
    }
}
