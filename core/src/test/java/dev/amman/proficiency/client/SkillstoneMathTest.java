package dev.amman.proficiency.client;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class SkillstoneMathTest {

    @Test
    void slabFitsEveryScreenFromGui4At720pTo4k() {
        int[][] screens = {{320, 180}, {426, 240}, {480, 270}, {640, 360}, {960, 540}, {1920, 1080}};
        for (int[] s : screens) {
            int w = SkillstoneMath.slabWidth(s[0]);
            int h = SkillstoneMath.slabHeight(s[1]);
            assertTrue(w <= s[0] - 8 && h <= s[1] - 8, "slab inside " + s[0] + "x" + s[1]);
            int r = SkillstoneMath.dialRadius(h);
            // The dial and its ticks (R + 20) fit the slab's inner height.
            assertTrue(2 * (r + 20) <= h, "dial fits " + s[0] + "x" + s[1] + " r=" + r);
        }
        assertEquals(620, SkillstoneMath.slabWidth(960));
        assertEquals(149, SkillstoneMath.dialRadius(360));
        assertEquals(91, SkillstoneMath.dialRadius(232));
        assertEquals(61, SkillstoneMath.dialRadius(172));
    }

    @Test
    void pitRadiiMatchTheBakedTextures() {
        assertEquals(65, SkillstoneMath.pitRadius(149));
        assertEquals(40, SkillstoneMath.pitRadius(91));
        assertEquals(26, SkillstoneMath.pitRadius(61));
    }

    @Test
    void openingFollowsTheStoryboard() {
        assertEquals(0f, SkillstoneMath.rise(0), 1e-6);
        assertEquals(1f, SkillstoneMath.rise(SkillstoneMath.RISE_MS), 1e-6);
        assertEquals(3, SkillstoneMath.squash(270));
        assertEquals(0, SkillstoneMath.squash(400));
        assertEquals(0f, SkillstoneMath.fire(449), 1e-6);
        assertTrue(SkillstoneMath.fire(450) > 0f);
        assertEquals(1f, SkillstoneMath.fire(700), 1e-6);
        assertTrue(SkillstoneMath.strike(450) > 0.9f);
        assertEquals(0f, SkillstoneMath.slate(700), 1e-6);
        assertEquals(1f, SkillstoneMath.slate(SkillstoneMath.INTRO_MS), 1e-6);
    }

    @Test
    void runesIgniteClockwiseFlareThenCoolToProgress() {
        int n = 9;
        for (int i = 1; i < n; i++) {
            assertTrue(SkillstoneMath.igniteAt(i, n) > SkillstoneMath.igniteAt(i - 1, n));
        }
        assertTrue(SkillstoneMath.igniteAt(n - 1, n) + 60 <= SkillstoneMath.IGNITED_MS);
        assertEquals(0f, SkillstoneMath.runeHeat(0, n, 300, 0.2f), 1e-6);
        // Every rune is at full heat when all nine flare.
        for (int i = 0; i < n; i++) {
            assertEquals(1f, SkillstoneMath.runeHeat(i, n, SkillstoneMath.IGNITED_MS, 0.2f), 1e-6);
        }
        assertEquals(0.2f, SkillstoneMath.runeHeat(4, n, SkillstoneMath.COOLED_MS, 0.2f), 1e-6);
        assertEquals(0.2f, SkillstoneMath.runeHeat(4, n, 5000, 0.2f), 1e-6);
    }

    @Test
    void heatIsAverageLevelOverFifty() {
        assertEquals(0f, SkillstoneMath.heat(0), 1e-6);
        assertEquals(0.5f, SkillstoneMath.heat(25), 1e-6);
        assertEquals(1f, SkillstoneMath.heat(100), 1e-6);
    }

    @Test
    void shakesAreBoundedAndEnd() {
        for (long t = 0; t < 600; t += 3) {
            assertTrue(Math.abs(SkillstoneMath.shakeX(t, 3f)) <= 3f);
            assertTrue(Math.abs(SkillstoneMath.shakeY(t, 3f)) <= 3f);
            assertTrue(Math.abs(SkillstoneMath.refuseX(t, 4f)) <= 4f);
            assertTrue(Math.abs(SkillstoneMath.riseShake(t, 2f)) <= 2f);
        }
        assertEquals(0f, SkillstoneMath.shakeX(SkillstoneMath.SHAKE_MS, 3f), 1e-6);
        assertEquals(0f, SkillstoneMath.refuseX(SkillstoneMath.REFUSE_MS, 4f), 1e-6);
        assertEquals(0f, SkillstoneMath.riseShake(SkillstoneMath.RISE_SHAKE_MS, 2f), 1e-6);
        // The refusal swings both ways, hard.
        boolean left = false;
        boolean right = false;
        for (long t = 0; t < SkillstoneMath.REFUSE_MS; t++) {
            float v = SkillstoneMath.refuseX(t, 4f);
            left |= v < -2f;
            right |= v > 2f;
        }
        assertTrue(left && right);
    }

    @Test
    void fireLoopsAtTenFramesASecond() {
        assertEquals(0, SkillstoneMath.fireFrame(0, 8));
        assertEquals(1, SkillstoneMath.fireFrame(100, 8));
        assertEquals(0, SkillstoneMath.fireFrame(800, 8));
    }

    @Test
    void treeRowsFitOrScroll() {
        // Full size: five gaps of ~60 px, nothing to scroll.
        int step = SkillstoneMath.treeRowStep(304, 40);
        assertEquals(60, step);
        assertEquals(0, SkillstoneMath.treeScrollMax(step, 30, 5 * step + 30));
        // GUI 4 at 720p: the room is too short, the step stays at the minimum and the tree scrolls.
        int small = SkillstoneMath.treeRowStep(120, 32);
        assertEquals(32, small);
        assertTrue(SkillstoneMath.treeScrollMax(small, 22, 150) > 0);
        assertEquals(0, SkillstoneMath.clampScroll(-5, 10));
        assertEquals(10, SkillstoneMath.clampScroll(50, 10));
        int[][] screens = {{320, 180}, {426, 240}, {480, 270}, {960, 540}, {1920, 1080}};
        for (int[] s : screens) {
            assertTrue(SkillstoneMath.treeWidth(s[0]) <= s[0] - 8 || s[0] < 168);
            assertTrue(SkillstoneMath.treeHeight(s[1]) <= s[1] - 8);
        }
        assertEquals(640, SkillstoneMath.treeWidth(960));
        assertEquals(410, SkillstoneMath.treeHeight(540));
    }

    @Test
    void rankFillAndMarkRunOnTheClock() {
        assertEquals(0f, SkillstoneMath.fill(0), 1e-6);
        assertTrue(SkillstoneMath.fill(SkillstoneMath.FILL_MS / 2) > 0.5f);
        assertEquals(1f, SkillstoneMath.fill(SkillstoneMath.FILL_MS), 1e-6);
        assertEquals(1f, SkillstoneMath.markAlpha(100), 1e-6);
        assertEquals(0f, SkillstoneMath.markAlpha(SkillstoneMath.RANK_MARK_MS), 1e-6);
        assertTrue(SkillstoneMath.markRise(SkillstoneMath.RANK_MARK_MS) >= 13.9f);
        assertEquals(0.5f, SkillstoneMath.pour(100, 200), 1e-6);
        assertEquals(1f, SkillstoneMath.pour(500, 200), 1e-6);
    }

    @Test
    void treeScrollReachesTheLastRowsLabels() {
        // GUI 4 on 720p: small sockets (9) and big ones (11); the label under a small socket ends at 9 + 17.
        int below = SkillstoneMath.treeNodeBelow(9, 11);
        assertTrue(below >= 9 + 17);
        assertTrue(below >= 11 + 6);
        int step = 36;
        int view = 120;
        int max = SkillstoneMath.treeScrollMax(step, below + 20, view);
        // Scrolled to the end, the last row's label bottom (row centre + 26) is inside the view.
        int lastCentre = 20 + (SkillstoneMath.TREE_ROWS - 1) * step - max;
        assertTrue(lastCentre + 9 + 17 <= view);
    }

    @Test
    void faceGoesOneColumnAtGui4AndStaysTwoOnBigScreens() {
        assertTrue(SkillstoneMath.faceOneColumn(480));
        assertTrue(SkillstoneMath.faceOneColumn(426));
        assertTrue(SkillstoneMath.faceOneColumn(320));
        assertTrue(!SkillstoneMath.faceOneColumn(640));
        assertTrue(!SkillstoneMath.faceOneColumn(960));
        assertTrue(!SkillstoneMath.faceOneColumn(1920));
    }

    @Test
    void wrapKeepsEveryCharacterAndNeverExceedsTheLine() {
        java.util.function.ToIntFunction<String> w = x -> x.length() * 6;
        String text = "Your Mining speed rises with level  ·  +12% faster, never over the cap";
        for (int max : new int[] {60, 120, 292}) {
            java.util.List<String> lines = SkillstoneMath.wrap(text, w, max);
            for (String l : lines) {
                assertTrue(w.applyAsInt(l) <= max, "line fits " + max + ": " + l);
            }
            assertEquals(text.replace(" ", "").replace("\u00b7", ""),
                    String.join("", lines).replace(" ", "").replace("\u00b7", ""));
        }
        assertEquals(1, SkillstoneMath.wrap("short", w, 292).size());
        assertTrue(SkillstoneMath.wrap("", w, 292).isEmpty());
        // No spaces (Chinese): breaks between characters, still fits.
        java.util.List<String> cjk = SkillstoneMath.wrap("挖掘速度随等级提升而提升", x -> x.length() * 9, 36);
        assertEquals(3, cjk.size());
        // A word wider than the line is cut, and a double space never starts a line.
        java.util.List<String> wide = SkillstoneMath.wrap("abcdefghijkl  x", w, 36);
        for (String l : wide) {
            assertTrue(w.applyAsInt(l) <= 36 && !l.startsWith(" "));
        }
    }

    @Test
    void scrollClampsAndThumbTracksIt() {
        assertEquals(0, SkillstoneMath.maxScroll(100, 150));
        assertEquals(50, SkillstoneMath.maxScroll(200, 150));
        assertEquals(50, SkillstoneMath.clampScroll(999, 200, 150));
        assertEquals(0, SkillstoneMath.clampScroll(-5, 200, 150));
        assertEquals(null, SkillstoneMath.scrollThumb(0, 100, 150, 140));
        int[] top = SkillstoneMath.scrollThumb(0, 300, 150, 140);
        int[] bottom = SkillstoneMath.scrollThumb(150, 300, 150, 140);
        assertEquals(0, top[0]);
        assertEquals(140, bottom[0] + bottom[1]);
        assertTrue(SkillstoneMath.wheelStep(1, 10) < 0 && SkillstoneMath.wheelStep(-1, 10) > 0);
        assertEquals(1, SkillstoneMath.socketsPerRow(10, 18, 5));
        assertEquals(5, SkillstoneMath.socketsPerRow(400, 18, 5));
        assertEquals(5 + 10 + 1 + 20 + 4, SkillstoneMath.faceCardHeight(2, 10));
    }
}
