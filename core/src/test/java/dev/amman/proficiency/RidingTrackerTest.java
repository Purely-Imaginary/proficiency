package dev.amman.proficiency;

import dev.amman.proficiency.skill.RidingTracker;
import dev.amman.proficiency.skill.RidingTracker.Reason;
import dev.amman.proficiency.skill.RidingTracker.Result;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The anti-farm rules of Riding: input, teleports, loops and repeated ground. */
class RidingTrackerTest {

    private static final String CTX = "overworld:horse-1";

    /** A ride along +x at {@code speed} blocks per tick from tick 0. Returns the metres paid. */
    private static double ride(RidingTracker t, long from, long ticks, double x0, double speed, boolean input) {
        double paid = 0.0;
        for (long i = 0; i <= ticks; i++) {
            Result r = t.step(from + i, CTX, x0 + speed * i, 64, 0, true, input);
            paid += r.metres();
        }
        return paid;
    }

    @Test
    void aStraightSteeredRideIsPaidMetreForMetre() {
        RidingTracker t = new RidingTracker();
        double paid = ride(t, 0, 200, 0, 0.3, true);
        // 200 ticks at 0.3: the first tick only seeds the ride.
        assertEquals(0.3 * 200, paid, 0.31);
    }

    @Test
    void theFirstTickAndANewVehicleOnlySeed() {
        RidingTracker t = new RidingTracker();
        assertEquals(Reason.SKIPPED, t.step(0, CTX, 0, 64, 0, true, true).reason());
        assertEquals(Reason.PAID, t.step(1, CTX, 0.3, 64, 0, true, true).reason());
        assertEquals(Reason.SKIPPED, t.step(2, "overworld:boat-2", 0.6, 64, 0, true, true).reason());
        assertEquals(Reason.SKIPPED, t.step(3, "nether:boat-2", 0.9, 64, 0, true, true).reason(), "a new dimension");
    }

    @Test
    void aStepOverTenBlocksIsAPortalOrATeleportAndPaysNothing() {
        RidingTracker t = new RidingTracker();
        ride(t, 0, 20, 0, 0.3, true);
        Result jump = t.step(21, CTX, 5000, 64, 0, true, true);
        assertEquals(Reason.SKIPPED, jump.reason());
        assertEquals(0.0, jump.metres());
        assertEquals(Reason.PAID, t.step(22, CTX, 5000.3, 64, 0, true, true).reason(), "riding on from there pays again");
        assertEquals(Reason.SKIPPED, t.step(23, CTX, 5000.6, 200, 0, true, true).reason(), "ten blocks straight up");
    }

    @Test
    void aTickGapIsANewRide() {
        RidingTracker t = new RidingTracker();
        ride(t, 0, 20, 0, 0.3, true);
        assertEquals(Reason.SKIPPED, t.step(500, CTX, 6.3, 64, 0, true, true).reason());
    }

    @Test
    void aBoatWithNoSteeringPaysNothing() {
        RidingTracker t = new RidingTracker();
        double paid = ride(t, 0, 400, 0, 0.3, false);
        assertEquals(0.0, paid, 1e-9, "a boat in a current or a leash-dragged mount has no input");
        assertEquals(Reason.AFK, t.step(401, CTX, 120.6, 64, 0, true, false).reason());
    }

    @Test
    void steeringForABriefMomentPaysForTwoSeconds() {
        RidingTracker t = new RidingTracker();
        t.step(0, CTX, 0, 64, 0, true, true);
        for (int i = 1; i <= 40; i++) {
            assertEquals(Reason.PAID, t.step(i, CTX, i * 0.05, 64, 0, true, false).reason(), "tick " + i);
        }
        assertEquals(Reason.AFK, t.step(41, CTX, 2.05, 64, 0, true, false).reason());
        assertEquals(Reason.PAID, t.step(42, CTX, 2.1, 64, 0, true, true).reason(), "steering again");
    }

    @Test
    void aMinecartIsNotAskedToSteer() {
        RidingTracker t = new RidingTracker();
        double paid = 0;
        for (int i = 0; i < 100; i++) {
            paid += t.step(i, "overworld:cart", i * 0.4, 64, 0, false, false).metres();
        }
        assertTrue(paid > 35.0, "paid " + paid);
    }

    @Test
    void aTightCircleIsALoopAndStopsPayingByNetDisplacement() {
        RidingTracker t = new RidingTracker();
        // Radius 40 blocks, 0.4 blocks per tick: one lap every ~630 ticks, so each lap is ~250 m.
        double radius = 40.0;
        double paidFirstHundred = 0;
        double paidLater = 0;
        long tick = 0;
        for (int i = 0; i < 2000; i++, tick++) {
            double angle = i * 0.4 / radius;
            Result r = t.step(tick, "overworld:cart", radius * Math.cos(angle), 64, radius * Math.sin(angle), false, false);
            if (tick < 300) {
                paidFirstHundred += r.metres();
            } else if (tick > 1300) {
                paidLater += r.metres();
            }
        }
        assertTrue(paidFirstHundred > 80.0, "the first stretch is real riding: " + paidFirstHundred);
        assertEquals(0.0, paidLater, 1e-9, "going round the same loop again pays nothing");
    }

    @Test
    void aShuttleBetweenTwoStationsPaysTheFirstTripOnly() {
        RidingTracker t = new RidingTracker();
        double first = 0;
        double rest = 0;
        long tick = 0;
        for (int trip = 0; trip < 8; trip++) {
            int dir = trip % 2 == 0 ? 1 : -1;
            for (int i = 0; i < 250; i++, tick++) {
                double x = dir == 1 ? i * 0.4 : 100 - i * 0.4;
                double paid = t.step(tick, "overworld:cart", x, 64, 0, false, false).metres();
                if (trip == 0) {
                    first += paid;
                } else {
                    rest += paid;
                }
            }
        }
        assertTrue(first > 80.0, "first trip " + first);
        // Turning round inside the last cell of the first trip is still that cell's stay: at most one cell (8 blocks).
        assertTrue(rest < 8.0, "the shuttle returns over spent ground, but paid " + rest);
    }

    @Test
    void theGroundIsFreshAgainAfterTenMinutes() {
        RidingTracker t = new RidingTracker();
        double first = ride(t, 0, 100, 0, 0.4, true);
        assertTrue(first > 30.0);
        t.endRide();
        double soon = ride(t, 300, 100, 0, 0.4, true);
        assertEquals(0.0, soon, 1e-9, "the same road again, five seconds later");
        t.endRide();
        double later = ride(t, 13000, 100, 0, 0.4, true);
        assertTrue(later > 30.0, "after ten minutes the road is fresh: " + later);
    }

    @Test
    void aLongStraightRideIsNotAffectedByTheLoopRule() {
        RidingTracker t = new RidingTracker();
        double paid = ride(t, 0, 3000, 0, 0.5, true);
        assertEquals(0.5 * 3000, paid, 1.0, "a 1500 block ride pays all of it");
    }

    @Test
    void oneTickNeverPaysMoreThanThreeBlocks() {
        RidingTracker t = new RidingTracker();
        t.step(0, "x", 0, 64, 0, false, false);
        Result r = t.step(1, "x", 8.0, 64, 0, false, false);
        assertEquals(Reason.PAID, r.reason());
        assertEquals(3.0, r.metres(), 1e-9);
    }

    @Test
    void xpIsPaidInSmallLumpsAndProcsRollOncePerPoint() {
        RidingTracker t = new RidingTracker();
        assertEquals(0.0, t.add(0.1), 1e-9);
        assertEquals(0.0, t.add(0.1), 1e-9);
        assertEquals(0.35, t.add(0.15), 1e-9, "the whole pile is paid, fraction included");
        assertEquals(0.0, t.pending(), 1e-9);
        assertEquals(0, t.procRolls(0.6));
        assertEquals(1, t.procRolls(0.6));
        assertEquals(2, t.procRolls(2.2));
        assertEquals(0.0, t.add(Double.NaN), 1e-9);
        assertEquals(0.0, t.add(-3), 1e-9);
    }

    @Test
    void standingStillPaysNothingAndIsNotALoop() {
        RidingTracker t = new RidingTracker();
        t.step(0, CTX, 0, 64, 0, true, true);
        for (int i = 1; i < 2000; i++) {
            assertEquals(Reason.STILL, t.step(i, CTX, 0, 64, 0, true, true).reason());
        }
        assertEquals(Reason.PAID, t.step(2000, CTX, 0.3, 64, 0, true, true).reason());
    }
}
