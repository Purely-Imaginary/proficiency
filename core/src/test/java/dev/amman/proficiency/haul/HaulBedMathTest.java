package dev.amman.proficiency.haul;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The bed button never covers "Leave Bed" or the chat input, at any GUI scale. */
class HaulBedMathTest {

    @Test
    void neverCoversLeaveBedOrTheChatInput() {
        int[] windows = {854, 1280, 1920, 2560, 3840};
        int[] heights = {480, 720, 1080, 1440, 2160};
        for (int i = 0; i < windows.length; i++) {
            for (int scale = 1; scale <= 6; scale++) {
                int w = windows[i] / scale;
                int h = heights[i] / scale;
                for (int label : new int[] {40, 70, 150, 400}) {
                    int[] b = HaulBedMath.place(w, h, label);
                    if (b == null) {
                        assertTrue(h < 80, "left out only on a very short screen: " + w + "x" + h);
                        continue;
                    }
                    int[] leave = {w / 2 - 100, h - 40, 200, 20};
                    int[] input = {2, h - 12, w - 4, 12};
                    assertFalse(HaulBedMath.overlaps(b, leave), "Leave Bed at " + w + "x" + h);
                    assertFalse(HaulBedMath.overlaps(b, input), "chat input at " + w + "x" + h);
                    assertTrue(b[0] >= 0 && b[0] + b[2] <= w && b[1] >= 0, "on screen at " + w + "x" + h);
                }
            }
        }
    }

    @Test
    void pulseStaysInRange() {
        for (long ms = 0; ms < 5000; ms += 37) {
            float p = HaulBedMath.pulse(ms);
            assertTrue(p >= 0.25f - 1e-4 && p <= 1.0f + 1e-4);
        }
        assertNotNull(HaulBedMath.place(427, 240, 80));
    }
}
