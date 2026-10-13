package dev.amman.proficiency.hearth;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Sieges: the condition, the roll, the horn, the clock, waves and rewards (abuse check 13). */
class SiegeRulesTest {

    private static final HearthData D = HearthData.DEFAULTS;
    private static final HearthData.Siege S = D.siege();
    private static final long DAY = HearthData.TICKS_PER_DAY;

    private static SiegeRules.Facts facts(boolean horn, int active, int warm, int ceiling, double hours, long ticks) {
        return new SiegeRules.Facts(horn, active, warm, ceiling, 1, hours, ticks, true, false, false);
    }

    @Test
    void neverBeforeTheFirstHorn() {
        assertFalse(SiegeRules.conditionHolds(facts(false, 2, 2, 6, 100, 100 * DAY), S));
        assertTrue(SiegeRules.conditionHolds(facts(true, 2, 2, 6, 100, 100 * DAY), S));
        HearthData noHorn = HearthDataLoader.parse("t", "{\"siege\": {\"requiresFirstHorn\": false}}").data();
        assertTrue(SiegeRules.conditionHolds(facts(false, 2, 2, 6, 100, 100 * DAY), noHorn.siege()));
    }

    @Test
    void theWholeGroupMustBeHomeAndWarm() {
        assertFalse(SiegeRules.conditionHolds(facts(true, 3, 2, 6, 100, 100 * DAY), S));
        assertFalse(SiegeRules.conditionHolds(facts(true, 0, 0, 6, 100, 100 * DAY), S));
    }

    @Test
    void neverAtCeilingOneOrTwoNorWithEmptyPools() {
        assertFalse(SiegeRules.conditionHolds(facts(true, 1, 1, 2, 100, 100 * DAY), S));
        assertFalse(SiegeRules.conditionHolds(
                new SiegeRules.Facts(true, 1, 1, 6, 0, 100, 100 * DAY, true, false, false), S));
    }

    @Test
    void theCooldownNeedsPlayHoursAndDays() {
        assertFalse(SiegeRules.conditionHolds(facts(true, 1, 1, 3, 3.9, 100 * DAY), S));
        assertFalse(SiegeRules.conditionHolds(facts(true, 1, 1, 3, 100, DAY), S));
        assertTrue(SiegeRules.conditionHolds(facts(true, 1, 1, 3, 4, 2 * DAY), S));
    }

    @Test
    void siegesOffPeacefulOrBusyBlockIt() {
        assertFalse(SiegeRules.conditionHolds(new SiegeRules.Facts(true, 1, 1, 6, 1, 100, 100 * DAY, false, false, false), S));
        assertFalse(SiegeRules.conditionHolds(new SiegeRules.Facts(true, 1, 1, 6, 1, 100, 100 * DAY, true, true, false), S));
        assertFalse(SiegeRules.conditionHolds(new SiegeRules.Facts(true, 1, 1, 6, 1, 100, 100 * DAY, true, false, true), S));
    }

    @Test
    void theChanceRampsToPMaxAndPityIsSure() {
        assertEquals(0, SiegeRules.chance(3.99, S), 1e-12);
        assertEquals(0, SiegeRules.chance(4, S), 1e-12);
        assertEquals(0.01, SiegeRules.chance(7, S), 1e-12);
        assertEquals(0.02, SiegeRules.chance(10, S), 1e-12);
        assertEquals(0.02, SiegeRules.chance(15.9, S), 1e-12);
        assertEquals(1.0, SiegeRules.chance(16, S), 1e-12);
        assertTrue(SiegeRules.roll(16, 0.999, S));
        assertFalse(SiegeRules.roll(10, 0.5, S));
        assertTrue(SiegeRules.roll(10, 0.01, S));
    }

    @Test
    void theHornNeedsCeilingThreeAndAnHour() {
        assertTrue(SiegeRules.hornAllowed(3, 1.0, false, S));
        assertFalse(SiegeRules.hornAllowed(3, 0.9, false, S));
        assertFalse(SiegeRules.hornAllowed(2, 5, false, S));
        // Two sieges never overlap.
        assertFalse(SiegeRules.hornAllowed(6, 5, true, S));
    }

    @Test
    void aHitIsDueADayLaterAndStartsAtDusk() {
        assertEquals(1000 + DAY, SiegeRules.rolledDue(1000, S));
        assertEquals(1000 + 2400, SiegeRules.hornDue(1000, S));
        assertTrue(SiegeRules.isNight(13_000));
        assertFalse(SiegeRules.isNight(12_999));
        assertFalse(SiegeRules.isNight(23_000));
        assertTrue(SiegeRules.isNight(DAY * 5 + 18_000));
        // Due, and the night just began: start.
        assertTrue(SiegeRules.startsNow(30_000, 25_000, true, false));
        // Due mid-night: wait for the next dusk.
        assertFalse(SiegeRules.startsNow(30_000, 25_000, true, true));
        assertFalse(SiegeRules.startsNow(20_000, 25_000, true, false));
    }

    @Test
    void nobodyHomePostponesTwiceThenFizzles() {
        assertEquals(SiegeRules.Nightfall.START, SiegeRules.atNightfall(true, 2, S));
        assertEquals(SiegeRules.Nightfall.POSTPONE, SiegeRules.atNightfall(false, 0, S));
        assertEquals(SiegeRules.Nightfall.POSTPONE, SiegeRules.atNightfall(false, 1, S));
        assertEquals(SiegeRules.Nightfall.FIZZLE, SiegeRules.atNightfall(false, 2, S));
    }

    @Test
    void aFizzleCountsTheCooldownHalfServedOnBothClocksInTicks() {
        long playNow = 10 * HearthData.TICKS_PER_HOUR;
        long gameNow = 500_000L;
        SiegeRules.SiegeEnd end = SiegeRules.fizzledEnd(playNow, gameNow, S);
        // 4 h of play and 2 in-game days, half of each.
        assertEquals(playNow - 2 * HearthData.TICKS_PER_HOUR, end.playTicks());
        assertEquals(gameNow - HearthData.TICKS_PER_DAY, end.gameTicks());

        // Stored on a Hearth, the cooldown is half gone: 2 h of play since, and one day.
        HearthState state = new HearthState();
        state.setLastSiegeEnd(end);
        assertEquals(2.0, state.playHoursSinceSiege(playNow), 1e-9);
        assertEquals(HearthData.TICKS_PER_DAY, state.ticksSinceSiege(gameNow));
        assertFalse(SiegeRules.cooldownServed(state.playHoursSinceSiege(playNow), state.ticksSinceSiege(gameNow), S));
        // The other half later, both clocks: served.
        long playLater = playNow + 2 * HearthData.TICKS_PER_HOUR;
        long gameLater = gameNow + HearthData.TICKS_PER_DAY;
        assertTrue(SiegeRules.cooldownServed(state.playHoursSinceSiege(playLater), state.ticksSinceSiege(gameLater), S));
        // Only one clock served is not enough.
        assertFalse(SiegeRules.cooldownServed(state.playHoursSinceSiege(playLater), state.ticksSinceSiege(gameNow), S));
        // ...and the roll starts from zero again, not at the pity point.
        assertEquals(0.0, SiegeRules.chance(state.playHoursSinceSiege(playNow), S), 1e-12);
    }

    @Test
    void aSiegeThatEndsStartsBothClocksAtNow() {
        HearthState state = new HearthState();
        state.setLastSiegeEnd(SiegeRules.endedAt(7_200_000L, 900_000L));
        assertEquals(7_200_000L, state.lastSiegeEndPlay());
        assertEquals(900_000L, state.lastSiegeEndTick());
        assertEquals(0.0, state.playHoursSinceSiege(7_200_000L), 1e-12);
    }

    @Test
    void wavesGrowWithCeilingAndPlayers() {
        assertEquals(0, SiegeRules.waveSize(2, 1, S));
        assertEquals(4, SiegeRules.waveSize(3, 1, S));
        assertEquals(12, SiegeRules.waveSize(6, 1, S));
        assertEquals(18, SiegeRules.waveSize(6, 2, S));
        assertEquals(8, SiegeRules.waveSize(3, 3, S));
        assertFalse(SiegeRules.nextWaveDue(8, 5, S));
        assertTrue(SiegeRules.nextWaveDue(8, 4, S));
        assertEquals(List.of("minecraft:zombie", "minecraft:skeleton", "minecraft:spider"), SiegeRules.mobsFor(3, S));
        assertTrue(SiegeRules.mobsFor(6, S).contains("minecraft:evoker"));
        assertEquals(7, SiegeRules.mobsFor(6, S).size());
    }

    @Test
    void theBurstGrowsWithTheStreakUpToItsCap() {
        // Tier II pool (charge 700): the next threshold is 1400. 10% + 5% per streak.
        assertEquals(1400 * 0.15, SiegeRules.burst(700, 1, D), 1e-9);
        assertEquals(1400 * 0.35, SiegeRules.burst(700, 9, D), 1e-9);
        // At VI the burst is a share of VI's own threshold.
        assertEquals(8000 * 0.10, SiegeRules.burst(9000, 0, D), 1e-9);
        assertEquals(3, SiegeRules.streakAfter(2, SiegeRules.Outcome.DEFENDED));
        assertEquals(0, SiegeRules.streakAfter(2, SiegeRules.Outcome.DOUSED));
        assertEquals(2, SiegeRules.streakAfter(2, SiegeRules.Outcome.DAWN));
        assertEquals(900, SiegeRules.doused(1000, S), 1e-9);
    }
}
