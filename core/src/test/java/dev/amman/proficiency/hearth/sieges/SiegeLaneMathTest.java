package dev.amman.proficiency.hearth.sieges;

import dev.amman.proficiency.hearth.HearthData;
import dev.amman.proficiency.hearth.HearthDataLoader;
import dev.amman.proficiency.hearth.SiegeRules;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.SplittableRandom;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The Sieges lane's pure helpers: clock, douse meter, waves, horn reasons, data keys. */
class SiegeLaneMathTest {

    private static final HearthData.Siege S = HearthData.DEFAULTS.siege();
    private static final long DAY = HearthData.TICKS_PER_DAY;

    // ---- Clock --------------------------------------------------------------------------------

    @Test
    void theExpectedStartIsTheFirstDuskAfterTheDueTime() {
        // Noon now, due a day from now: the dusk after that is 1000 ticks later.
        long game = 100_000;
        long day = 12_000;
        long due = game + DAY;
        long start = SiegeClock.expectedStart(due, game, day);
        assertEquals(due + 1_000, start);
        // Every candidate is a dusk on the day clock.
        assertEquals(SiegeRules.DUSK, Math.floorMod(day + (start - game), DAY));
        // Due in the middle of the night waits for the next dusk.
        long midnight = SiegeClock.expectedStart(game + 6_000, game, 12_000);
        assertEquals(game + 6_000 + 19_000, midnight);
        assertTrue(SiegeRules.startsNow(start, due, true, false), "the start is a nightfall at or after the due time");
    }

    @Test
    void theExpectedStartAfterAPostponementIsTheNextNight() {
        // Postponed right at dusk: the next start is a whole day later, never the same dusk.
        long game = 500_000;
        long start = SiegeClock.expectedStart(game - 30_000, game, 13_000);
        assertEquals(game + DAY, start);
        long late = SiegeClock.expectedStart(game - 30_000, game, 13_015);
        assertEquals(game + DAY - 15, late);
    }

    @Test
    void anUnloadedHearthCountsTheDusksItMissedAfterTheDueTime() {
        long due = 10_000;
        // Two days unloaded from noon: dusks at day 13000 and 37000 both after the due time.
        assertEquals(2, SiegeClock.missedNightfalls(0, 12_000, 2 * DAY, 12_000 + 2 * DAY, 0, 10));
        // Only dusks at or after the due time count.
        assertEquals(1, SiegeClock.missedNightfalls(0, 12_000, 2 * DAY, 12_000 + 2 * DAY, 20_000, 10));
        assertEquals(0, SiegeClock.missedNightfalls(0, 12_000, 2 * DAY, 12_000 + 2 * DAY, 3 * DAY, 10));
        // At most max.
        assertEquals(1, SiegeClock.missedNightfalls(0, 12_000, 5 * DAY, 12_000 + 5 * DAY, due, 1));
        // A dusk inside the last second is the live check's.
        assertEquals(0, SiegeClock.missedNightfalls(0, 12_000, 1_010, 13_010, 0, 10));
        assertEquals(1, SiegeClock.missedNightfalls(0, 12_000, 1_100, 13_100, 0, 10));
        // Time set backwards counts nothing.
        assertEquals(0, SiegeClock.missedNightfalls(0, 20_000, 100, 10_000, 0, 10));
        assertTrue(SiegeClock.nightBeforeLiveStep(13_100));
        assertFalse(SiegeClock.nightBeforeLiveStep(13_010));
    }

    @Test
    void aSkippedNightIsADayJumpAndALiveSecondIsNot() {
        // A live second: both clocks move 20.
        assertFalse(SiegeClock.dayJumped(1_000, 12_600, 1_020, 12_620));
        // Slept at 12 600: the day clock jumps to the next morning, game time moves one second.
        assertTrue(SiegeClock.dayJumped(1_000, 12_600, 1_020, DAY));
        // Unloaded for a while with the sun moving normally is a gap, not a jump.
        assertFalse(SiegeClock.dayJumped(1_000, 12_600, 9_000, 20_600));
        // Time set backwards is no jump.
        assertFalse(SiegeClock.dayJumped(1_000, 12_600, 1_020, 1_000));
        // The dusk the bed jumped over is caught up like an unloaded one, when the siege was due by then.
        assertEquals(1, SiegeClock.missedNightfalls(1_000, 12_600, 1_020, DAY, 900, 3));
        assertEquals(0, SiegeClock.missedNightfalls(1_000, 12_600, 1_020, DAY, 5_000, 3));
        // A jump that lands at night: that night began at its dusk, which the live check takes.
        assertEquals(13_000, SiegeClock.nightFell(14_000));
        assertEquals(DAY + 13_000, SiegeClock.nightFell(DAY + 22_999));
        assertEquals(DAY + 13_000, SiegeClock.nightFell(DAY + 13_000));
    }

    @Test
    void aBedMayNotSleepThroughTheNightfallThatStartsTheSiege() {
        long game = 50_000;
        // Due this evening: a bed at 12 600 (or a thunderstorm noon) would skip tonight's dusk.
        assertTrue(SiegeClock.bedSkipsStart(game, game, 12_600, false));
        assertTrue(SiegeClock.bedSkipsStart(game + 300, game, 12_600, false), "due at dusk itself");
        assertTrue(SiegeClock.bedSkipsStart(game, game, 6_000, false), "a thunderstorm by day");
        // Due tomorrow: tonight's dusk would not start it, so a bed is fine.
        assertFalse(SiegeClock.bedSkipsStart(game + 1_000, game, 12_600, false));
        // Night already fell and the live check saw it (start or postponement done): sleep is fine.
        assertFalse(SiegeClock.bedSkipsStart(game - 5_000, game, 15_000, true));
        // Night fell this second and the live check has not run yet: the start is pending.
        assertTrue(SiegeClock.bedSkipsStart(game - 5_000, game, 13_005, false));
        // The hour before dawn skips no dusk.
        assertFalse(SiegeClock.bedSkipsStart(game - 5_000, game, 23_300, false));
    }

    @Test
    void onlyKillsByPlayersOrPetsDefendTheHearth() {
        assertEquals(SiegeTally.Fall.KILL, SiegeTally.fall(true, false));
        assertEquals(SiegeTally.Fall.KILL, SiegeTally.fall(true, true), "a player's hit within the credit window wins");
        assertEquals(SiegeTally.Fall.FRIENDLY, SiegeTally.fall(false, true));
        assertEquals(SiegeTally.Fall.UNEARNED, SiegeTally.fall(false, false));
        assertEquals(SiegeRules.Outcome.DEFENDED, SiegeTally.cleared(0));
        assertEquals(SiegeRules.Outcome.UNEARNED, SiegeTally.cleared(1));
        assertEquals(1, SiegeRules.streakAfter(1, SiegeRules.Outcome.UNEARNED), "unearned keeps the streak");
    }

    @Test
    void ticksToSecondsRoundUp() {
        assertEquals(0, SiegeClock.seconds(0));
        assertEquals(1, SiegeClock.seconds(1));
        assertEquals(1, SiegeClock.seconds(20));
        assertEquals(2, SiegeClock.seconds(21));
    }

    // ---- Douse --------------------------------------------------------------------------------

    @Test
    void theDouseMeterFillsPerMobAndStopsAtTheTop() {
        assertEquals(0, DouseMeter.step(0, 0, 1, S), 1e-12);
        assertEquals(4, DouseMeter.step(0, 4, 1, S), 1e-12);
        assertEquals(S.douseMax(), DouseMeter.step(99, 12, 1, S), 1e-12);
        assertTrue(DouseMeter.full(S.douseMax(), S));
        assertFalse(DouseMeter.full(S.douseMax() - 0.01, S));
        assertEquals(0, DouseMeter.step(Double.NaN, 0, 1, S), 1e-12);
        assertEquals(50, DouseMeter.percent(50, S));
        assertEquals(50, DouseMeter.crossed(49, 51, S));
        assertEquals(80, DouseMeter.crossed(10, 85, S));
        assertEquals(0, DouseMeter.crossed(51, 60, S));
        assertEquals(0, DouseMeter.crossed(70, 100, S), "full is the outcome, not a warning");
    }

    // ---- Waves --------------------------------------------------------------------------------

    @Test
    void aWaveFieldsTheCeilingsMixAndOneEvokerAtMost() {
        SplittableRandom random = new SplittableRandom(7);
        List<String> three = SiegeWaves.compose(4, 3, S, random);
        assertEquals(4, three.size());
        assertTrue(List.of("minecraft:zombie", "minecraft:skeleton", "minecraft:spider").containsAll(three), three.toString());
        for (int i = 0; i < 50; i++) {
            List<String> six = SiegeWaves.compose(12, 6, S, random);
            assertEquals(12, six.size());
            assertEquals(1, six.stream().filter("minecraft:evoker"::equals).count(), six.toString());
        }
        // A wave of one never spends its only mob on the leader.
        assertFalse(SiegeWaves.compose(1, 6, S, random).contains("minecraft:evoker"));
        assertTrue(SiegeWaves.compose(0, 6, S, random).isEmpty());
        assertTrue(SiegeWaves.compose(5, 2, S, random).isEmpty(), "no mix below ceiling III");
    }

    @Test
    void aWaveComesFromOneSideOrMore() {
        assertEquals(1, SiegeWaves.gates(4));
        assertEquals(2, SiegeWaves.gates(9));
        assertEquals(3, SiegeWaves.gates(18));
        double[] gates = SiegeWaves.gateAngles(9, new SplittableRandom(1));
        assertEquals(2, gates.length);
        assertEquals(Math.PI, gates[1] - gates[0], 1e-9);
        double a = SiegeWaves.near(1.0, new SplittableRandom(3));
        assertTrue(Math.abs(a - 1.0) <= SiegeWaves.GATE_SPREAD);
    }

    @Test
    void compassPointsFollowMinecraftsAxes() {
        assertEquals(0, SiegeWaves.direction(0, -10), "north is -Z");
        assertEquals(2, SiegeWaves.direction(10, 0), "east is +X");
        assertEquals(4, SiegeWaves.direction(0, 10));
        assertEquals(6, SiegeWaves.direction(-10, 0));
        assertEquals(1, SiegeWaves.direction(7, -7));
        double angle = Math.PI / 2;
        assertEquals(10, SiegeWaves.dx(angle, 10), 1e-9);
        assertEquals(0, SiegeWaves.dz(angle, 10), 1e-9);
        assertEquals(2, SiegeWaves.direction(SiegeWaves.dx(angle, 10), SiegeWaves.dz(angle, 10)));
    }

    // ---- Horn ---------------------------------------------------------------------------------

    @Test
    void theHornSaysWhyItIsRefused() {
        assertEquals(HornCheck.Result.OK, HornCheck.check(true, false, true, false, 3, false, 1.0, S));
        assertEquals(HornCheck.Result.OFF, HornCheck.check(false, false, true, false, 6, false, 9, S));
        assertEquals(HornCheck.Result.PEACEFUL, HornCheck.check(true, true, true, false, 6, false, 9, S));
        assertEquals(HornCheck.Result.NO_HEARTH, HornCheck.check(true, false, false, false, 0, false, 9, S));
        assertEquals(HornCheck.Result.SMOULDERING, HornCheck.check(true, false, true, true, 6, false, 9, S));
        assertEquals(HornCheck.Result.CEILING, HornCheck.check(true, false, true, false, 2, false, 9, S));
        assertEquals(HornCheck.Result.BUSY, HornCheck.check(true, false, true, false, 6, true, 9, S));
        assertEquals(HornCheck.Result.COOLDOWN, HornCheck.check(true, false, true, false, 6, false, 0.5, S));
        assertEquals(30, HornCheck.minutesLeft(0.5, S));
        assertEquals(0, HornCheck.minutesLeft(2, S));
        assertEquals(0, HornCheck.minutesLeft(Double.POSITIVE_INFINITY, S));
    }

    // ---- Data ---------------------------------------------------------------------------------

    @Test
    void theLanesSiegeKeysLoadAndDefault() {
        assertEquals(3.0, S.douseRadius(), 1e-12);
        assertEquals(1.0, S.dousePerMobSecond(), 1e-12);
        assertEquals(100, S.douseMax(), 1e-12);
        assertEquals(10, S.abandonGraceSeconds(), 1e-12);
        assertEquals(6, S.spawnClearance(), 1e-12);
        assertEquals(List.of("minecraft:evoker", "minecraft:ravager"), S.onePerWave());
        HearthDataLoader.Result r = HearthDataLoader.parse("t",
                "{\"siege\": {\"douseMax\": 40, \"onePerWave\": [], \"spawnClearance\": 0}}");
        assertTrue(r.messages().isEmpty(), r.messages().toString());
        assertEquals(40, r.data().siege().douseMax(), 1e-12);
        assertTrue(r.data().siege().onePerWave().isEmpty());
        assertEquals(0, r.data().siege().spawnClearance(), 1e-12);
        HearthDataLoader.Result bad = HearthDataLoader.parse("t", "{\"siege\": {\"douseMax\": 0}}");
        assertFalse(bad.messages().isEmpty());
        assertEquals(100, bad.data().siege().douseMax(), 1e-12);
    }
}
