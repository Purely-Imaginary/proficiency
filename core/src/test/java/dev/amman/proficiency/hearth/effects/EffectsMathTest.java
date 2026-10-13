package dev.amman.proficiency.hearth.effects;

import dev.amman.proficiency.hearth.BoonType;
import dev.amman.proficiency.hearth.HearthData;
import dev.amman.proficiency.hearth.HearthPool;
import dev.amman.proficiency.hearth.HearthRules;
import dev.amman.proficiency.hearth.TempHp;
import dev.amman.proficiency.hearth.WarmthMath;
import dev.amman.proficiency.skill.ExhaustionMath;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The boons' numbers: Hearty, Comfort regen, Fortified, and the abuse checks they carry (9, 11). */
class EffectsMathTest {

    private static final HearthData D = HearthData.DEFAULTS;

    @Test
    void heartyIsTheHomeValueTimesWarmthOrProvisionedWhicheverIsMore() {
        assertEquals(0.20, EffectsMath.hearty(0.40, 0.5, 0.0), 1e-12);
        assertEquals(0.30, EffectsMath.hearty(0.40, 0.5, 0.30), 1e-12);
        assertEquals(0.40, EffectsMath.hearty(0.40, 1.0, 0.30), 1e-12);
        assertEquals(0.0, EffectsMath.hearty(Double.NaN, 1.0, Double.NaN), 1e-12);
        assertEquals(0.6, EffectsMath.exhaustionFactor(0.40), 1e-12);
        assertEquals(1.0, EffectsMath.exhaustionFactor(0.0), 1e-12);
        assertEquals(0.0, EffectsMath.exhaustionFactor(3.0), 1e-12);
    }

    @Test
    void theWatcherKeepsOnlyTheFactorOfAClimb() {
        // The watcher's own math (ExhaustionMath); EffectsMath.scaleClimb is a deprecated delegate to it.
        assertEquals(1.6f, ExhaustionMath.scaleClimb(1.0f, 2.0f, 0.6), 1e-6);
        // No climb, or the bar rolled over: nothing changes.
        assertEquals(1.0f, ExhaustionMath.scaleClimb(1.0f, 1.0f, 0.6), 1e-6);
        assertEquals(0.2f, ExhaustionMath.scaleClimb(3.9f, 0.2f, 0.6), 1e-6);
        // No boon: the number is left alone.
        assertEquals(2.0f, ExhaustionMath.scaleClimb(1.0f, 2.0f, 1.0), 1e-6);
    }

    @Test
    void fortifiedCutsByValueTimesWarmth() {
        assertEquals(0.8, EffectsMath.fortifiedFactor(0.20, 1.0), 1e-12);
        assertEquals(0.9, EffectsMath.fortifiedFactor(0.20, 0.5), 1e-12);
        assertEquals(1.0, EffectsMath.fortifiedFactor(0.20, 0.0), 1e-12);
    }

    @Test
    void regenGivesAHeartPerIntervalAtFullWarmthAndNeverBanks() {
        double p = 0;
        int hearts = 0;
        for (int s = 0; s < 40; s++) {
            p = EffectsMath.regenProgress(p, 1.0, 4, false);
            if (EffectsMath.heartDue(p)) {
                hearts++;
                p -= 1;
            }
        }
        assertEquals(10, hearts, "tier VI: a heart every 4 s");
        // Half Warmth: half the rate.
        p = 0;
        hearts = 0;
        for (int s = 0; s < 40; s++) {
            p = EffectsMath.regenProgress(p, 0.5, 4, false);
            if (EffectsMath.heartDue(p)) {
                hearts++;
                p -= 1;
            }
        }
        assertEquals(5, hearts);
        // A full player banks one heart at most; combat starts the clock over.
        p = 0;
        for (int s = 0; s < 100; s++) {
            p = EffectsMath.regenProgress(p, 1.0, 4, false);
        }
        assertEquals(1.0, p, 1e-12);
        assertEquals(0.0, EffectsMath.regenProgress(0.9, 1.0, 4, true), 1e-12);
    }

    /** Abuse check 9: temp HP follows the Comfort-capped regen tier and the current max health. */
    @Test
    void tempHpFollowsTheComfortCappedTier() {
        HearthPool regen = D.poolWithBoon(BoonType.REGEN_OUT_OF_COMBAT);
        double perTier = D.pool(regen).absorptionPerTier();
        // Tier VI charge at a stage VI Hearth with Character 50, but Comfort 7: the half cap is III.
        int tier = HearthRules.effectiveTier(regen, 9000, 6, 7, D);
        assertEquals(3, tier);
        assertEquals(6.0, TempHp.cap(20, tier, perTier), 1e-9, "30% of 20, not 60%");
        int full = HearthRules.effectiveTier(regen, 9000, 6, 12, D);
        assertEquals(6, full);
        assertEquals(24.0, TempHp.cap(40, full, perTier), 1e-9);
        // Back to 20 max health: the cap is 12, but what is left (24) is never clawed back.
        assertEquals(24.0, TempHp.next(24, TempHp.cap(20, full, perTier)), 1e-9);
        assertEquals(24.0, TempHp.modifier(24, 24, TempHp.cap(20, full, perTier)), 1e-9);
    }

    /** Abuse check 11, the pure part: half at 30 s, full at 60 s, 10 s outside costs 20 s back. */
    @Test
    void warmthRisesInAMinuteAndTenSecondsOutCostTwentyBack() {
        double rise = HearthRules.warmthRiseSeconds(0, D);
        double drain = D.warmth().drainSeconds();
        assertEquals(60, rise, 1e-9);
        double w = 0;
        for (int s = 0; s < 30; s++) {
            w = WarmthMath.step(w, true, false, 1, rise, drain);
        }
        assertEquals(0.5, w, 1e-9);
        for (int s = 0; s < 30; s++) {
            w = WarmthMath.step(w, true, false, 1, rise, drain);
        }
        assertTrue(WarmthMath.isFull(w));
        for (int s = 0; s < 10; s++) {
            w = WarmthMath.step(w, false, false, 1, rise, drain);
        }
        int back = 0;
        while (!WarmthMath.isFull(w)) {
            w = WarmthMath.step(w, true, false, 1, rise, drain);
            back++;
        }
        assertEquals(20, back);
        // Homecoming's readiness at VI fills it in 30 s; a 30% talent bonus on top shortens that.
        assertEquals(30, HearthRules.warmthRiseSeconds(6, D), 1e-9);
        assertEquals(30 / 1.3, WarmthMath.withSpeedBonus(HearthRules.warmthRiseSeconds(6, D), 0.3), 1e-9);
        // In combat nothing is gained, nothing lost.
        assertEquals(0.4, WarmthMath.step(0.4, true, true, 1, rise, drain), 1e-12);
        assertFalse(WarmthMath.isFull(0.99));
    }

    @Test
    void provisionedLastsByReadinessTier() {
        assertEquals(15, HearthRules.provisionedMinutes(0, D), 1e-9);
        assertEquals(20, HearthRules.provisionedMinutes(3, D), 1e-9);
        assertEquals(30, HearthRules.provisionedMinutes(6, D), 1e-9);
        assertEquals(28, EffectsMath.percent(0.28));
    }
}
