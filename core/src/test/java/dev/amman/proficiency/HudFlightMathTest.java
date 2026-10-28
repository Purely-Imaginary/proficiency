package dev.amman.proficiency;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.amman.proficiency.client.HudFlightMath;
import dev.amman.proficiency.client.HudFlightMath.Screen;
import org.junit.jupiter.api.Test;

/** The numbers of the three 1.38.0 HUD flights: projection clamp, coin path, socket room, dot counts. */
class HudFlightMathTest {

    // Camera looking down +z, +y up, left is +x (as Minecraft's camera does for a yaw of 0).
    private static final double[] FWD = {0, 0, 1};
    private static final double[] UP = {0, 1, 0};
    private static final double[] LEFT = {1, 0, 0};

    @Test
    void aPointStraightAheadProjectsToTheCentre() {
        Screen s = HudFlightMath.project(0, 0, 5, FWD, UP, LEFT, 70, 400, 240, 4);
        assertTrue(s.onScreen());
        assertEquals(200, s.x(), 0.01);
        assertEquals(120, s.y(), 0.01);
    }

    @Test
    void leftOfTheViewIsLeftOnScreenAndAbovePointsUp() {
        Screen left = HudFlightMath.project(1, 0, 5, FWD, UP, LEFT, 70, 400, 240, 4);
        assertTrue(left.x() < 200);
        Screen up = HudFlightMath.project(0, 1, 5, FWD, UP, LEFT, 70, 400, 240, 4);
        assertTrue(up.y() < 120);
    }

    @Test
    void aPointOutOfViewIsClampedToTheEdgeInItsDirection() {
        Screen far = HudFlightMath.project(-40, 0, 5, FWD, UP, LEFT, 70, 400, 240, 4);
        assertFalse(far.onScreen());
        assertEquals(396, far.x(), 0.01);
        assertEquals(120, far.y(), 0.01);
        Screen below = HudFlightMath.project(0, -50, 5, FWD, UP, LEFT, 70, 400, 240, 4);
        assertFalse(below.onScreen());
        assertEquals(236, below.y(), 0.01);
    }

    @Test
    void aPointBehindTheCameraStartsFromTheEdgeOnItsSide() {
        Screen behind = HudFlightMath.project(3, 0, -5, FWD, UP, LEFT, 70, 400, 240, 4);
        assertFalse(behind.onScreen());
        assertTrue(behind.x() < 5 || behind.x() > 395);
        // Dead behind: no side to pick, so the bottom edge.
        Screen dead = HudFlightMath.project(0, 0, -5, FWD, UP, LEFT, 70, 400, 240, 4);
        assertEquals(236, dead.y(), 0.01);
        assertEquals(200, dead.x(), 0.01);
    }

    @Test
    void theClampNeverLeavesTheMargin() {
        for (int i = 0; i < 200; i++) {
            double a = i * 0.37;
            Screen s = HudFlightMath.project(Math.cos(a) * 30, Math.sin(a * 1.3) * 30, Math.sin(a) * 30,
                    FWD, UP, LEFT, 90, 480, 270, 6);
            assertTrue(s.x() >= 6 && s.x() <= 474 && s.y() >= 6 && s.y() <= 264, "i=" + i);
        }
    }

    @Test
    void theFlightPathsStartAndEndWhereTheyAreTold() {
        float[] a = HudFlightMath.bonusPoint(0, 10, 20, 200, 230);
        assertEquals(10, a[0], 0.01);
        assertEquals(20, a[1], 0.01);
        float[] b = HudFlightMath.bonusPoint(1, 10, 20, 200, 230);
        assertEquals(200, b[0], 0.01);
        assertEquals(230, b[1], 0.01);
        float[] c = HudFlightMath.coinPoint(0, 100, 200, 300, 230);
        assertEquals(100, c[0], 0.01);
        float[] d = HudFlightMath.coinPoint(1, 100, 200, 300, 230);
        assertEquals(300, d[0], 0.01);
        assertEquals(230, d[1], 0.01);
        // The coin arcs up over the straight line half way.
        float[] mid = HudFlightMath.coinPoint(0.5, 100, 200, 300, 230);
        assertTrue(mid[1] < 200);
    }

    @Test
    void theCoinTurnsEdgeOnButNeverVanishes() {
        for (int i = 0; i <= 100; i++) {
            float w = HudFlightMath.coinWidth(i / 100.0, 3);
            assertTrue(w >= 0.15f && w <= 1.0001f);
        }
        assertEquals(1f, HudFlightMath.coinWidth(0, 3), 1e-4);
    }

    @Test
    void theSocketSitsRightOfTheHotbarAndHidesWhenThereIsNoRoom() {
        for (int scale = 1; scale <= 6; scale++) {
            int w = 1920 / scale;
            int h = 1080 / scale;
            int[] s = HudFlightMath.socket(w, h);
            if (w / 2 + 91 + HudFlightMath.SOCKET_CLEAR + HudFlightMath.SOCKET + 2 > w) {
                assertNull(s, "scale " + scale);
                continue;
            }
            assertNotNull(s, "scale " + scale);
            // Right of the hotbar and its offhand slot (91 + 29 from the centre), inside the screen, on the hotbar row.
            assertTrue(s[0] >= w / 2 + 91 + 29);
            assertTrue(s[0] + HudFlightMath.SOCKET <= w);
            assertTrue(s[1] >= h - 22 && s[1] + HudFlightMath.SOCKET <= h);
        }
        // The Waywheel belt keeps its place on the other side of the hotbar.
        int[] belt = dev.amman.proficiency.client.WaywheelMath.belt(480, 3);
        int[] socket = HudFlightMath.socket(480, 270);
        assertTrue(belt.length == 0 || belt[belt.length - 1] + 22 < 480 / 2);
        assertTrue(socket[0] > 480 / 2);
        assertNull(HudFlightMath.socket(250, 135));
    }

    @Test
    void theCountShownWaitsForTheCoinsInTheAir() {
        assertEquals(5, HudFlightMath.shownPoints(7, 2));
        assertEquals(0, HudFlightMath.shownPoints(1, 3));
        assertEquals(7, HudFlightMath.shownPoints(7, -1));
        assertEquals("12", HudFlightMath.pointsLabel(12));
        assertEquals("99", HudFlightMath.pointsLabel(99));
        assertEquals("99+", HudFlightMath.pointsLabel(100));
        assertEquals(0f, HudFlightMath.glow(0));
        assertTrue(HudFlightMath.glow(1) > 0.4f);
        assertEquals(1f, HudFlightMath.glow(40));
    }

    @Test
    void pourDotsScaleWithThePoolPaidAndAreBounded() {
        assertEquals(0, HudFlightMath.pourDots(0));
        assertEquals(0, HudFlightMath.pourDots(-0.2f));
        assertEquals(0, HudFlightMath.pourDots(Float.NaN));
        assertEquals(1, HudFlightMath.pourDots(0.002f));
        assertEquals(2, HudFlightMath.pourDots(0.2f));
        assertEquals(HudFlightMath.MAX_POUR_PER_PAY, HudFlightMath.pourDots(1f));
        assertEquals(HudFlightMath.MAX_POUR_PER_PAY, HudFlightMath.pourDots(50f));
        int last = 0;
        for (int i = 0; i <= 100; i++) {
            int d = HudFlightMath.pourDots(i / 100f);
            assertTrue(d >= last);
            last = d;
        }
    }

    @Test
    void thePoolPaysOnlyWhenTheSameBarGainedAndTheReachShrank() {
        assertTrue(HudFlightMath.pourFor(true, 0.30f, 0.20f, 5.1, 5.3) > 0);
        // A death empties the pool and the bar goes down: no dots.
        assertEquals(0, HudFlightMath.pourFor(true, 0.30f, 0f, 5.1, 5.0));
        // Another skill, a level turning over, a first look.
        assertEquals(0, HudFlightMath.pourFor(false, 0.30f, 0.20f, 5.1, 5.3));
        assertEquals(0, HudFlightMath.pourFor(true, -1f, 0.20f, 5.1, 5.3));
        // The pool filling while you rest is not a payment.
        assertEquals(0, HudFlightMath.pourFor(true, 0.20f, 0.30f, 5.1, 5.3));
        assertEquals(0, HudFlightMath.pourFor(true, 0.20f, 0.20f, 5.1, 5.3));
        // The whole bar's worth is the cap.
        assertEquals(HudFlightMath.MAX_POUR_PER_PAY, HudFlightMath.pourFor(true, 1.5f, 0f, 5.1, 5.9));
    }

    @Test
    void bonusFlightsAreBoundedStaggeredAndPruned() {
        HudFlightMath.Flights<String> flights = new HudFlightMath.Flights<>();
        for (int i = 0; i < 20; i++) {
            boolean added = flights.add(10, 10, "x", 1, 1000);
            assertEquals(i < HudFlightMath.MAX_BONUS, added);
        }
        assertEquals(HudFlightMath.MAX_BONUS, flights.live().size());
        assertEquals(1000, flights.live().get(0).launch());
        assertEquals(1000 + HudFlightMath.BONUS_STAGGER_MS, flights.live().get(1).launch());
        assertFalse(flights.add(0, 0, "x", 0, 1000));
        assertTrue(flights.land(1100).isEmpty());
        int landed = flights.land(1000 + HudFlightMath.BONUS_FLIGHT_MS + 10 * HudFlightMath.BONUS_STAGGER_MS).size();
        assertEquals(HudFlightMath.MAX_BONUS, landed);
        assertTrue(flights.live().isEmpty());
        assertTrue(HudFlightMath.progress(500, 100, 400) < 0);
        assertEquals(1.0, HudFlightMath.progress(500, 100, 900));
    }
}
