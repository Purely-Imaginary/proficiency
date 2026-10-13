package dev.amman.proficiency.client;

/**
 * The timing of the proc callout above the crosshair: it snaps in, holds long enough to read a
 * two-word name, then fades out and drifts up a little as it goes. Pure, so it can be tested.
 */
public final class CalloutFade {

    /** Fully opaque for this long. */
    public static final long HOLD_MS = 450L;
    /** Then fades to nothing over this long. */
    public static final long FADE_MS = 650L;
    public static final long TOTAL_MS = HOLD_MS + FADE_MS;
    /** How far it rises while fading, in GUI pixels. */
    public static final float RISE = 6f;
    /** The pop on arrival: starts this much bigger and settles to 1 in {@link #POP_MS}. */
    public static final float POP = 0.25f;
    public static final long POP_MS = 120L;

    private CalloutFade() {
    }

    public static boolean active(long elapsed) {
        return elapsed >= 0 && elapsed < TOTAL_MS;
    }

    /** 1 while holding, easing to 0 at the end. */
    public static float alpha(long elapsed) {
        if (elapsed < 0 || elapsed >= TOTAL_MS) {
            return 0f;
        }
        if (elapsed <= HOLD_MS) {
            return 1f;
        }
        float t = (elapsed - HOLD_MS) / (float) FADE_MS;
        return 1f - t * t;
    }

    /** Pixels above the resting line: 0 while holding, up to {@link #RISE} as it fades. */
    public static float rise(long elapsed) {
        if (elapsed <= HOLD_MS) {
            return 0f;
        }
        float t = Math.min(1f, (elapsed - HOLD_MS) / (float) FADE_MS);
        return RISE * t;
    }

    /** Text scale: a short pop on arrival, then 1. */
    public static float scale(long elapsed) {
        if (elapsed < 0 || elapsed >= POP_MS) {
            return 1f;
        }
        float t = elapsed / (float) POP_MS;
        return 1f + POP * (1f - t) * (1f - t);
    }

    /** ARGB with this alpha. Text drawn with an alpha under 4 shows as opaque, so it stops at 5. */
    public static int withAlpha(int rgb, float alpha) {
        int a = Math.max(5, Math.min(255, Math.round(alpha * 255f)));
        return (a << 24) | (rgb & 0xFFFFFF);
    }
}
