package dev.amman.proficiency.hearth;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/** Tiers, ceiling, Comfort caps and the effective tier (docs/HEARTH-SPEC.md, abuse checks 9 and 15). */
class HearthRulesTest {

    private static final HearthData D = HearthData.DEFAULTS;

    @Test
    void chargeTiersFollowTheThresholds() {
        assertEquals(0, HearthRules.chargeTier(0, D));
        assertEquals(0, HearthRules.chargeTier(199.9, D));
        assertEquals(1, HearthRules.chargeTier(200, D));
        assertEquals(3, HearthRules.chargeTier(1400, D));
        assertEquals(5, HearthRules.chargeTier(7999, D));
        assertEquals(6, HearthRules.chargeTier(1e9, D));
        assertEquals(0, HearthRules.chargeTier(Double.NaN, D));
    }

    @Test
    void progressRunsFromOneThresholdToTheNext() {
        assertEquals(0.5, HearthRules.progress(100, D), 1e-9);
        assertEquals(0.5, HearthRules.progress(400, D), 1e-9);
        assertEquals(1.0, HearthRules.progress(9000, D), 1e-9);
    }

    @Test
    void characterGatesTheCeilingFromStageThreeUp() {
        assertEquals(2, HearthRules.characterStage(0, D));
        assertEquals(2, HearthRules.characterStage(7, D));
        assertEquals(3, HearthRules.characterStage(8, D));
        assertEquals(4, HearthRules.characterStage(14, D));
        assertEquals(5, HearthRules.characterStage(39, D));
        assertEquals(6, HearthRules.characterStage(40, D));
        // Abuse check 15: a full Hall in a one-material dirt hut stays at II.
        assertEquals(2, HearthRules.ceiling(4, 1, D));
        assertEquals(4, HearthRules.ceiling(4, 50, D));
        assertEquals(0, HearthRules.ceiling(0, 50, D));
    }

    @Test
    void comfortCapsHalfPoolsAtHalfTheComfort() {
        assertEquals(3, HearthRules.comfortCap(HearthPool.REGEN, 7, D));
        assertEquals(6, HearthRules.comfortCap(HearthPool.REGEN, 12, D));
        assertEquals(5, HearthRules.comfortCap(HearthPool.HOMECOMING, 11, D));
        assertEquals(0, HearthRules.comfortCap(HearthPool.REGEN, 1, D));
    }

    @Test
    void comfortCapsTheOthersAtVAndVI() {
        assertEquals(4, HearthRules.comfortCap(HearthPool.HEARTY, 0, D));
        assertEquals(4, HearthRules.comfortCap(HearthPool.HEARTY, 7, D));
        assertEquals(5, HearthRules.comfortCap(HearthPool.FORTIFIED, 8, D));
        assertEquals(5, HearthRules.comfortCap(HearthPool.WORKSHOP, 9, D));
        assertEquals(6, HearthRules.comfortCap(HearthPool.WORKSHOP, 10, D));
        assertEquals(8, HearthRules.comfortNeeded(HearthPool.HEARTY, 5, D));
        assertEquals(10, HearthRules.comfortNeeded(HearthPool.HEARTY, 6, D));
        assertEquals(0, HearthRules.comfortNeeded(HearthPool.HEARTY, 4, D));
        assertEquals(12, HearthRules.comfortNeeded(HearthPool.REGEN, 6, D));
    }

    @Test
    void theEffectiveTierIsTheLeastOfTheThree() {
        // Abuse check 9: tier VI charge with Comfort 7 gives regen III (30% temp HP), not VI.
        assertEquals(3, HearthRules.effectiveTier(HearthPool.REGEN, 9000, 6, 7, D));
        assertEquals(HearthRules.Lock.COMFORT, HearthRules.lock(HearthPool.REGEN, 9000, 6, 7, D));
        assertEquals(2, HearthRules.effectiveTier(HearthPool.HEARTY, 9000, 2, 12, D));
        assertEquals(HearthRules.Lock.CEILING, HearthRules.lock(HearthPool.HEARTY, 9000, 2, 12, D));
        assertEquals(1, HearthRules.effectiveTier(HearthPool.HEARTY, 300, 6, 12, D));
        assertEquals(HearthRules.Lock.NONE, HearthRules.lock(HearthPool.HEARTY, 300, 6, 12, D));
    }

    @Test
    void theCeilingLockNamesTheRealBlocker() {
        // Built to stage V with Character 10: Character allows III, so the ceiling is III and the
        // next tier waits on Character 14 (stage IV is already built).
        assertEquals(3, HearthRules.ceiling(5, 10, D));
        assertEquals(HearthRules.Lock.CHARACTER, HearthRules.lock(HearthPool.HEARTY, 9000, 5, 10, 12, D));
        assertEquals(14, HearthRules.need(HearthRules.Lock.CHARACTER, HearthPool.HEARTY, 3, 3, D));
        // Built to stage III with Character 30 (allows V): build stage IV.
        assertEquals(HearthRules.Lock.CEILING, HearthRules.lock(HearthPool.HEARTY, 9000, 3, 30, 12, D));
        assertEquals(4, HearthRules.need(HearthRules.Lock.CEILING, HearthPool.HEARTY, 3, 3, D));
        // Both short (stage III built, Character 8 allows exactly III): the offering for IV is
        // refused until Character 14, so Character comes first.
        assertEquals(HearthRules.Lock.CHARACTER, HearthRules.lock(HearthPool.HEARTY, 9000, 3, 8, 12, D));
        // Below stage III Character asks nothing until the next stage is III: stage I built, build II.
        assertEquals(HearthRules.Lock.CEILING, HearthRules.lock(HearthPool.HEARTY, 9000, 1, 0, 12, D));
        assertEquals(HearthRules.Lock.CHARACTER, HearthRules.lock(HearthPool.HEARTY, 9000, 2, 0, 12, D));
        assertEquals(8, HearthRules.need(HearthRules.Lock.CHARACTER, HearthPool.HEARTY, 2, 2, D));
        // Comfort and free pools are unchanged.
        assertEquals(HearthRules.Lock.COMFORT, HearthRules.lock(HearthPool.REGEN, 9000, 6, 50, 7, D));
        assertEquals(HearthRules.Lock.NONE, HearthRules.lock(HearthPool.HEARTY, 300, 6, 50, 12, D));
        assertEquals(0, HearthRules.need(HearthRules.Lock.NONE, HearthPool.HEARTY, 1, 6, D));

        // The state answers the same, and its need is what the panel row carries.
        HearthState state = new HearthState();
        state.setStage(5);
        state.setCharacter(10);
        state.setComfortKinds(java.util.Set.of("a", "b", "c", "d", "e", "f", "g", "h", "i", "j", "k", "l"));
        state.setCharge(HearthPool.HEARTY, 9000);
        assertEquals(HearthRules.Lock.CHARACTER, state.lock(HearthPool.HEARTY, D));
        assertEquals(14, state.need(HearthPool.HEARTY, D));
        state.setCharacter(40);
        assertEquals(HearthRules.Lock.CEILING, state.lock(HearthPool.HEARTY, D));
        assertEquals(6, state.need(HearthPool.HEARTY, D));
    }

    @Test
    void boonValuesComeFromTheData() {
        assertEquals(0.0, HearthRules.boonValue(HearthPool.HEARTY, 0, D));
        assertEquals(0.08, HearthRules.boonValue(HearthPool.HEARTY, 1, D), 1e-12);
        assertEquals(4.0, HearthRules.boonValue(HearthPool.REGEN, 6, D), 1e-12);
        assertEquals(0.20, HearthRules.boonValue(HearthPool.FORTIFIED, 6, D), 1e-12);
        assertEquals(0.08, D.pool(HearthPool.WORKSHOP).refundAt(6), 1e-12);
    }

    @Test
    void readinessShortensWarmthAndStretchesProvisioned() {
        assertEquals(60, HearthRules.warmthRiseSeconds(0, D), 1e-12);
        assertEquals(55, HearthRules.warmthRiseSeconds(1, D), 1e-12);
        assertEquals(30, HearthRules.warmthRiseSeconds(6, D), 1e-12);
        assertEquals(15, HearthRules.provisionedMinutes(0, D), 1e-12);
        assertEquals(30, HearthRules.provisionedMinutes(6, D), 1e-12);
    }

    /** The old by-stage formula, deprecated since the block's light follows {@code chargeLight}; kept in step. */
    @Test
    @SuppressWarnings("deprecation")
    void theBlockGlowsWithTheStage() {
        assertEquals(6, HearthRules.light(1, false));
        assertEquals(15, HearthRules.light(6, false));
        assertEquals(3, HearthRules.light(6, true));
        for (int stage = 1; stage < 6; stage++) {
            assertEquals(true, HearthRules.light(stage + 1, false) > HearthRules.light(stage, false));
        }
        assertEquals("VI", HearthRules.roman(6));
        assertEquals("", HearthRules.roman(0));
    }

    @Test
    void theBlockGlowsWithChargeSixToFifteen() {
        assertEquals(6, HearthRules.chargeLight(0, false), "no pool at a tier");
        assertEquals(15, HearthRules.chargeLight(6, false), "any pool at tier VI");
        assertEquals(3, HearthRules.chargeLight(6, true), "smouldering");
        for (int tier = 0; tier < 6; tier++) {
            assertEquals(true, HearthRules.chargeLight(tier + 1, false) > HearthRules.chargeLight(tier, false));
        }
        assertEquals(15, HearthRules.chargeLight(9, false), "clamped");
    }

    @Test
    void theParticlesTakeThePoolFedMostThisHour() {
        HearthState state = new HearthState();
        assertEquals(null, HearthRules.feedingMost(state, 0L), "nothing fed");
        long now = 10 * HourlyCap.BUCKET_TICKS;
        state.addCapped(HearthPool.HEARTY, 30, now, D);
        state.addCapped(HearthPool.WORKSHOP, 45, now, D);
        state.addUncapped(HearthPool.FORTIFIED, 900);
        assertEquals(HearthPool.WORKSHOP, HearthRules.feedingMost(state, now), "a burst is not feeding");
        state.addCapped(HearthPool.HEARTY, 20, now + 1200, D);
        assertEquals(HearthPool.HEARTY, HearthRules.feedingMost(state, now + 1200));
        // An hour on, nothing is left in the window.
        assertEquals(null, HearthRules.feedingMost(state, now + 1200 + 72_000L));
    }
}
