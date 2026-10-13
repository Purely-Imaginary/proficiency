package dev.amman.proficiency.hearth;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The hourly cap, lazy decay, Warmth, Provisioned and temp HP. */
class HearthClockTest {

    private static final HearthData D = HearthData.DEFAULTS;
    private static final long HOUR = HearthData.TICKS_PER_HOUR;
    private static final long MINUTE = 1200L;

    // ---- Hourly cap -----------------------------------------------------------------------------

    @Test
    void theCapAllowsThreeHundredInAnyHour() {
        HourlyCap cap = new HourlyCap();
        assertEquals(200, cap.take(0, 200, 300), 1e-9);
        assertEquals(100, cap.take(10 * MINUTE, 200, 300), 1e-9);
        assertEquals(0, cap.take(30 * MINUTE, 50, 300), 1e-9);
        // The first 200 leave the window an hour after they came in.
        assertEquals(200, cap.take(HOUR, 250, 300), 1e-9);
        assertEquals(300, cap.used(HOUR + 5 * MINUTE), 1e-9);
        assertEquals(0, cap.take(0, Double.NaN, 300), 1e-9);
    }

    @Test
    void theCapSurvivesASave() {
        HourlyCap cap = new HourlyCap();
        cap.take(5 * MINUTE, 120, 300);
        cap.take(20 * MINUTE, 80, 300);
        HourlyCap loaded = new HourlyCap();
        loaded.decode(cap.encode());
        assertEquals(200, loaded.used(30 * MINUTE), 1e-9);
        assertEquals(100, loaded.room(30 * MINUTE, 300), 1e-9);
    }

    // ---- Decay ----------------------------------------------------------------------------------

    @Test
    void decayHoldsTheTierFloorForTheGraceDay() {
        // 1500 is tier III (floor 1400). Twenty hours away: 2%/h would take it to ~1001, the floor holds 1400.
        double after = HearthDecay.apply(1500, 20 * HOUR, 0, 0, D);
        assertEquals(1400, after, 1e-9);
        // Two hours: above the floor, plain compounding.
        assertEquals(1500 * Math.pow(0.98, 2), HearthDecay.apply(1500, 2 * HOUR, 0, 0, D), 1e-9);
    }

    @Test
    void afterTheGraceDecayRunsFree() {
        double after = HearthDecay.apply(1500, 34 * HOUR, 0, 0, D);
        // Floor-clamped to 1400 by hour 24, then ten free hours.
        assertEquals(1400 * Math.pow(0.98, 10), after, 1e-6);
        assertTrue(after < 1400);
    }

    @Test
    void manySmallStepsEqualOneBigOne() {
        double charge = 5000;
        long last = 30 * HOUR;
        for (long t = 30 * HOUR + 600; t <= 40 * HOUR; t += 600) {
            charge = HearthDecay.apply(charge, t, 0, last, D);
            last = t;
        }
        assertEquals(HearthDecay.apply(5000, 40 * HOUR, 0, 30 * HOUR, D), charge, 1e-6);
    }

    @Test
    void stateDecayStopsWhileSomeoneIsHome() {
        HearthState state = new HearthState();
        state.setCharge(HearthPool.HEARTY, 1000);
        state.tickDecay(0, true, D);
        for (long t = 20; t <= 2 * HOUR; t += 20) {
            state.tickDecay(t, true, D);
        }
        assertEquals(1000, state.charge(HearthPool.HEARTY), 1e-9);
        // An unloaded Hearth catches up on its first tick (abuse check 14).
        state.tickDecay(2 * HOUR + 30 * HOUR, false, D);
        assertTrue(state.charge(HearthPool.HEARTY) < 1000);
    }

    // ---- Warmth ---------------------------------------------------------------------------------

    @Test
    void warmthFillsInAMinuteAndDrainsInHalf() {
        double w = 0;
        for (int s = 0; s < 30; s++) {
            w = WarmthMath.step(w, true, false, 1, 60, 30);
        }
        assertEquals(0.5, w, 1e-9);
        for (int s = 0; s < 30; s++) {
            w = WarmthMath.step(w, true, false, 1, 60, 30);
        }
        assertTrue(WarmthMath.isFull(w));
        // Abuse check 11: ten seconds outside cost twenty back.
        for (int s = 0; s < 10; s++) {
            w = WarmthMath.step(w, false, false, 1, 60, 30);
        }
        assertEquals(1.0 - 10.0 / 30.0, w, 1e-9);
        double back = w;
        int seconds = 0;
        while (!WarmthMath.isFull(back)) {
            back = WarmthMath.step(back, true, false, 1, 60, 30);
            seconds++;
        }
        assertEquals(20, seconds);
    }

    @Test
    void warmthDoesNotRiseInCombatButKeepsWhatItHas() {
        assertEquals(0.4, WarmthMath.step(0.4, true, true, 1, 60, 30), 1e-12);
        assertTrue(WarmthMath.inCombat(100, 150, 5));
        assertFalse(WarmthMath.inCombat(100, 200, 5));
        assertFalse(WarmthMath.inCombat(PlayerHearth.NEVER, 200, 5));
        assertEquals(60 / 1.3, WarmthMath.withSpeedBonus(60, 0.3), 1e-9);
        assertEquals(1.0 / 60, WarmthMath.ratePerSecond(0.2, true, false, 60, 30), 1e-12);
        assertEquals(-1.0 / 30, WarmthMath.ratePerSecond(0.2, false, false, 60, 30), 1e-12);
    }

    @Test
    void provisionedFadesLinearly() {
        long until = WarmthMath.provisionedUntil(1000, 15);
        assertEquals(1000 + 15 * MINUTE, until);
        assertEquals(0.28, WarmthMath.provisioned(0.28, 1000, until, 1000), 1e-12);
        assertEquals(0.14, WarmthMath.provisioned(0.28, 1000, until, 1000 + (until - 1000) / 2), 1e-12);
        assertEquals(0.0, WarmthMath.provisioned(0.28, 1000, until, until), 1e-12);
    }

    // ---- Temp HP --------------------------------------------------------------------------------

    @Test
    void tempHpIsTenPercentOfMaxHealthPerTier() {
        assertEquals(12, TempHp.cap(20, 6, 0.10), 1e-9);
        assertEquals(24, TempHp.cap(40, 6, 0.10), 1e-9);
        assertEquals(6, TempHp.cap(20, 3, 0.10), 1e-9);
        assertEquals(0, TempHp.cap(20, 0, 0.10), 1e-9);
    }

    @Test
    void tempHpOnlyGrowsAndNeverStacks() {
        assertEquals(2, TempHp.next(0, 12), 1e-9);
        assertEquals(12, TempHp.next(11, 12), 1e-9);
        // Charger's 16 points are above the Hearth's 12: untouched, not 16 + 12.
        assertEquals(16, TempHp.next(16, 12), 1e-9);
    }

    @Test
    void theAbsorptionModifierNeverShrinksWhileItLasts() {
        assertEquals(24, TempHp.modifier(0, 5, 24), 1e-9);
        // Max health fell back to 20: the cap is 12 now, the modifier stays 24 while absorption lasts.
        assertEquals(24, TempHp.modifier(24, 10, 12), 1e-9);
        assertEquals(0, TempHp.modifier(24, 0, 12), 1e-9);
    }

    // ---- Hold queue -----------------------------------------------------------------------------

    @Test
    void shufflingOneBlockChargesNothing() {
        HoldQueue queue = new HoldQueue();
        java.util.UUID p = java.util.UUID.randomUUID();
        long a = HearthPos.pack(1, 64, 1);
        long b = HearthPos.pack(2, 64, 1);
        queue.add(a, new HoldQueue.Held(p, "P", HearthPool.FORTIFIED, "masonry", "minecraft:stone", 1.0, 600 * 20));
        assertEquals(1, queue.drop(a).size());
        queue.add(b, new HoldQueue.Held(p, "P", HearthPool.FORTIFIED, "masonry", "minecraft:stone", 1.0, 700 * 20));
        assertTrue(queue.takeDue(650 * 20).isEmpty());
        List<HoldQueue.Due> due = queue.takeDue(700 * 20);
        assertEquals(1, due.size());
        assertEquals(b, due.get(0).pos());
        assertTrue(queue.takeDue(10_000 * 20).isEmpty());
        assertTrue(queue.isEmpty());
    }
}
