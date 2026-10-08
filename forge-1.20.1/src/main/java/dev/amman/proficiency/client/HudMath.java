package dev.amman.proficiency.client;

import net.minecraft.client.gui.GuiGraphics;

/** Sine and cosine tables for the HUD rings, so drawing one allocates and computes nothing. */
final class HudMath {

    static final float[] X64 = table(64, true);
    static final float[] Y64 = table(64, false);

    private HudMath() {
    }

    /**
     * The one ring every HUD effect draws with: an ellipse of up to 64 pixels round {@code (cx,
     * cy)}, clockwise from the top, {@code size} pixels square, every {@code step}th point of the
     * first {@code count} (64 is the whole ring). {@code rx} and {@code ry} are the radii.
     */
    static void ring(GuiGraphics graphics, int cx, int cy, float rx, float ry, int argb, int size, int step,
            int count) {
        int end = Math.min(count, X64.length);
        for (int i = 0; i < end; i += step) {
            int px = cx + Math.round(X64[i] * rx);
            int py = cy + Math.round(Y64[i] * ry);
            graphics.fill(px, py, px + size, py + size, argb);
        }
    }

    static float clamp01(float t) {
        return t < 0f ? 0f : Math.min(t, 1f);
    }

    /** The smoothstep every fade here uses: it swells in and melts out rather than ramping. */
    static float smooth(float t) {
        t = clamp01(t);
        return t * t * (3f - 2f * t);
    }

    /** Angles run clockwise from straight up, which is where a draining ring should start. */
    private static float[] table(int steps, boolean xAxis) {
        float[] out = new float[steps];
        for (int i = 0; i < steps; i++) {
            double angle = 2 * Math.PI * i / steps;
            out[i] = (float) (xAxis ? Math.sin(angle) : -Math.cos(angle));
        }
        return out;
    }
}
