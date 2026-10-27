package dev.amman.proficiency.client;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Set;
import java.util.function.Predicate;
import org.junit.jupiter.api.Test;

class WaywheelMathTest {

    private static final Predicate<String> KNOWN = Set.of("mining", "swords", "running", "farming")::contains;

    @Test
    void flickPicksTheRuneItPointsAt() {
        assertEquals(0, WaywheelMath.rune(0, -50, 9));
        // Gathering is the second rune, 40 degrees clockwise from the top.
        double a = WaywheelMath.runeAngle(1, 9);
        assertEquals(1, WaywheelMath.rune(Math.cos(a), Math.sin(a), 9));
        assertEquals(8, WaywheelMath.rune(-30, -40, 9));
        // Just past half way between two runes goes to the nearer one, and the last wraps to 0.
        double b = WaywheelMath.runeAngle(8, 9) + Math.toRadians(25);
        assertEquals(0, WaywheelMath.rune(Math.cos(b), Math.sin(b), 9));
    }

    @Test
    void smallCategoriesUseOneArcCentredOnTheirRune() {
        double angle = WaywheelMath.runeAngle(1, 9);
        double[][] pts = WaywheelMath.tokens(5, angle, 91, 22, true);
        double r1 = WaywheelMath.ring1(91, 22);
        for (double[] p : pts) {
            assertEquals(r1, Math.hypot(p[0], p[1]), 1e-6);
        }
        // The middle token sits on the rune's own angle.
        assertEquals(angle, Math.atan2(pts[2][1], pts[2][0]), 1e-9);
        // Neighbours never overlap: a token plus a gap apart along the ring.
        for (int i = 1; i < pts.length; i++) {
            assertTrue(Math.hypot(pts[i][0] - pts[i - 1][0], pts[i][1] - pts[i - 1][1]) >= 22);
        }
    }

    @Test
    void combatsThirteenSplitIntoTwoStaggeredRings() {
        assertEquals(7, WaywheelMath.innerCount(13));
        assertEquals(7, WaywheelMath.innerCount(7));
        double[][] pts = WaywheelMath.tokens(13, WaywheelMath.runeAngle(0, 9), 91, 22, true);
        double r1 = WaywheelMath.ring1(91, 22);
        double r2 = WaywheelMath.ring2(91, 22);
        int inner = 0;
        int outer = 0;
        for (double[] p : pts) {
            double r = Math.hypot(p[0], p[1]);
            if (Math.abs(r - r1) < 1e-6) {
                inner++;
            } else if (Math.abs(r - r2) < 1e-6) {
                outer++;
            }
        }
        assertEquals(7, inner);
        assertEquals(6, outer);
        // No two tokens of the 13 overlap.
        for (int i = 0; i < pts.length; i++) {
            for (int j = i + 1; j < pts.length; j++) {
                assertTrue(Math.hypot(pts[i][0] - pts[j][0], pts[i][1] - pts[j][1]) >= 22, i + " vs " + j);
            }
        }
        // One ring when there is no room for two.
        double[][] flat = WaywheelMath.tokens(13, 0, 91, 22, false);
        for (double[] p : flat) {
            assertEquals(r1, Math.hypot(p[0], p[1]), 1e-6);
        }
    }

    @Test
    void fitPicksTheLargestDiscWhoseOuterRingFits() {
        // 1920x1080 at GUI 2: the large disc with the large tokens, the D mockup's size on screen.
        assertArrayEquals(new int[] {0, 30}, WaywheelMath.fit(960, 540));
        assertEquals(128, WaywheelMath.WHEELS[0]);
        // 1920x1080 at GUI 3 (640x360): the 91 disc, never the 30 px token on a smaller disc.
        assertArrayEquals(new int[] {1, 22}, WaywheelMath.fit(640, 360));
        // 1920x1080 at GUI 4.
        int[] gui4 = WaywheelMath.fit(480, 270);
        assertEquals(61, WaywheelMath.WHEELS[gui4[0]]);
        // 1280x720 at GUI 3: the small disc.
        int[] gui3 = WaywheelMath.fit(427, 240);
        assertEquals(45, WaywheelMath.WHEELS[gui3[0]]);
        assertTrue(WaywheelMath.ring2(45, gui3[1]) + gui3[1] / 2 <= 118);
        // Too small for any: the smallest disc and token.
        assertArrayEquals(new int[] {3, 16}, WaywheelMath.fit(320, 180));
        double[][] top = WaywheelMath.tokens(13, WaywheelMath.runeAngle(0, 9), 91, 22, true);
        assertTrue(WaywheelMath.inside(top, 480, 270, 22, 960, 540));
        assertFalse(WaywheelMath.inside(top, 480, 100, 22, 960, 540));
    }

    @Test
    void nearestTokenWithinReach() {
        double[][] pts = {{0, 0}, {30, 0}, {60, 0}};
        assertEquals(1, WaywheelMath.nearest(33, 4, pts, 14));
        assertEquals(-1, WaywheelMath.nearest(45, 30, pts, 14));
    }

    @Test
    void openingOvershootsThenSettles() {
        assertEquals(0.25f, WaywheelMath.open(0), 1e-6);
        float peak = 0f;
        for (long t = 0; t <= WaywheelMath.OPEN_MS; t++) {
            peak = Math.max(peak, WaywheelMath.open(t));
        }
        assertEquals(1f + WaywheelMath.OVERSHOOT, peak, 0.01f);
        assertEquals(1f, WaywheelMath.open(WaywheelMath.OPEN_MS), 1e-6);
        assertEquals(-WaywheelMath.SPIN_DEG, WaywheelMath.spin(0), 1e-6);
        assertEquals(0f, WaywheelMath.spin(WaywheelMath.OPEN_MS), 1e-6);
        // Frame-rate independent: the same t gives the same scale whatever came before.
        assertEquals(WaywheelMath.open(97), WaywheelMath.open(97));
    }

    @Test
    void tokensSpringOneAfterAnother() {
        assertEquals(0f, WaywheelMath.spring(0, 0));
        assertEquals(0f, WaywheelMath.spring(WaywheelMath.STAGGER_MS, 1));
        assertTrue(WaywheelMath.spring(WaywheelMath.SPRING_MS * 6 / 10, 0) > 1.1f);
        assertEquals(1f, WaywheelMath.spring(WaywheelMath.SPRING_MS, 0));
        assertEquals(1f, WaywheelMath.spring(5000, 12));
    }

    @Test
    void pinsAreCleanedAndCapped() {
        assertEquals(List.of("mining", "swords"), WaywheelMath.pins(" Mining,swords,,mining,bogus", KNOWN));
        assertEquals(List.of(), WaywheelMath.pins(null, KNOWN));
        assertEquals(List.of("mining", "swords", "running"),
                WaywheelMath.pins("mining,swords,running,farming", KNOWN));
        assertEquals("mining", WaywheelMath.toggle("", "mining", KNOWN));
        assertEquals("swords", WaywheelMath.toggle("mining,swords", "mining", KNOWN));
        // A full belt drops its oldest pin.
        assertEquals("swords,running,farming", WaywheelMath.toggle("mining,swords,running", "farming", KNOWN));
    }

    @Test
    void beltSitsLeftOfTheHotbarAndDropsWhatDoesNotFit() {
        int[] wide = WaywheelMath.belt(960, 3);
        assertEquals(3, wide.length);
        int right = 960 / 2 - 91 - WaywheelMath.BELT_CLEAR;
        assertEquals(right - WaywheelMath.BELT_SLOT, wide[2]);
        assertEquals(WaywheelMath.BELT_SLOT + WaywheelMath.BELT_GAP, wide[1] - wide[0]);
        for (int x : WaywheelMath.belt(427, 3)) {
            assertTrue(x >= 2);
        }
        assertEquals(0, WaywheelMath.belt(320, 3).length);
        assertEquals(0, WaywheelMath.belt(960, 0).length);
    }

    @Test
    void tokenFacesAndRingFrames() {
        assertEquals(9, WaywheelMath.face(30));
        assertEquals(6, WaywheelMath.face(22));
        assertEquals(5, WaywheelMath.face(16));
        assertEquals(0, WaywheelMath.ringFrame(0, 100));
        // Level 1 already shows a step of ember; only level 100 closes the ring.
        assertEquals(1, WaywheelMath.ringFrame(1, 100));
        assertEquals(10, WaywheelMath.ringFrame(50, 100));
        assertEquals(19, WaywheelMath.ringFrame(99, 100));
        assertEquals(WaywheelMath.RING_STEPS, WaywheelMath.ringFrame(100, 100));
        assertEquals(WaywheelMath.RING_STEPS, WaywheelMath.ringFrame(140, 100));
        // A disc's chords: widest in the middle rows, symmetric, inside the radius.
        assertEquals(9, WaywheelMath.chord(9, 8));
        assertEquals(WaywheelMath.chord(9, 0), WaywheelMath.chord(9, 17));
        assertTrue(WaywheelMath.chord(9, 0) < 5);
    }

    @Test
    void castFoldsTheDiscAndFliesTheTokenInAnArc() {
        assertEquals(1f, WaywheelMath.fold(0));
        assertEquals(0f, WaywheelMath.fold(WaywheelMath.FOLD_MS));
        float last = 1f;
        for (long t = 0; t <= WaywheelMath.FOLD_MS; t += 10) {
            float f = WaywheelMath.fold(t);
            assertTrue(f <= last);
            last = f;
        }
        double[] start = WaywheelMath.fly(0, 400, 200, 120, 510);
        assertEquals(400, start[0], 1e-9);
        assertEquals(200, start[1], 1e-9);
        double[] end = WaywheelMath.fly(WaywheelMath.FLY_MS, 400, 200, 120, 510);
        assertEquals(120, end[0], 1e-9);
        assertEquals(510, end[1], 1e-9);
        assertEquals(1.0, end[2], 1e-9);
        // Past the end it stays in the socket (a slow frame cannot overshoot it).
        assertArrayEquals(end, WaywheelMath.fly(WaywheelMath.FLY_MS * 3, 400, 200, 120, 510), 1e-9);
        // It arcs: early on it rises above the start before dropping to the belt.
        assertTrue(WaywheelMath.fly(WaywheelMath.FLY_MS / 5, 400, 200, 120, 510)[1] < 200);
        // Frame-rate independent: the same time gives the same point however it was reached.
        assertArrayEquals(WaywheelMath.fly(137, 400, 200, 120, 510), WaywheelMath.fly(137, 400, 200, 120, 510), 0);
        float[] p0 = WaywheelMath.pop(0);
        assertEquals(1f, p0[2]);
        assertEquals(0f, WaywheelMath.pop(WaywheelMath.POP_MS)[2]);
        assertTrue(WaywheelMath.pop(WaywheelMath.POP_MS / 2)[0] > 0);
    }

    @Test
    void pinsAreParsedOncePerChange() {
        int[] checks = {0};
        WaywheelPins cache = new WaywheelPins(id -> {
            checks[0]++;
            return KNOWN.test(id);
        });
        List<String> first = cache.get("mining, swords");
        int after = checks[0];
        assertEquals(List.of("mining", "swords"), first);
        for (int i = 0; i < 100; i++) {
            assertTrue(cache.pinned("mining, swords", "swords"));
        }
        // A hundred asks with the same text: no new parse.
        assertEquals(after, checks[0]);
        assertTrue(first == cache.get("mining, swords"));
        // A change is parsed again.
        assertEquals(List.of("running"), cache.get("running"));
        assertTrue(checks[0] > after);
        assertEquals(List.of(), cache.get(null));
    }

    @org.junit.jupiter.api.Test
    void aTapOfTheWaywheelKeyKeepsTheWheelOpen() {
        // A real tap: the press opens the wheel, the release comes ~100 ms later over nothing.
        assertTrue(WaywheelMath.stickOnRelease(true, 100, false));
        assertTrue(WaywheelMath.stickOnRelease(true, 0, false));
        // Held past the tap time and let go over nothing: the wheel closes (cancel).
        assertFalse(WaywheelMath.stickOnRelease(true, WaywheelMath.TAP_MS, false));
        // A fast flick onto a token still casts.
        assertFalse(WaywheelMath.stickOnRelease(true, 100, true));
        // The ability key's hold never turns sticky.
        assertFalse(WaywheelMath.stickOnRelease(false, 100, false));
    }
}
