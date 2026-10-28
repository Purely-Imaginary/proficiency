package dev.amman.proficiency.client;

/**
 * The timeline of the synergy discovery slam (1.45.0), with no Minecraft in it so unit tests can drive it
 * with a fake clock. Everything is a closed-form function of the milliseconds {@code t} since the slam began
 * (already scaled by the queue's speed): nothing is integrated per frame, so it plays the same at 20 and at
 * 240 fps.
 *
 * <pre>
 *   0 ........ 450  the skill icons fly in from the screen sides (accelerating)
 *   450             IMPACT: white flash, tier-2 spark burst, ring, 3 px shake for 240 ms
 *   450 .. 750      the synergy chip forms from the impact (overshoot pop)
 *   520 .. 760      the plate slides up and fades in
 *   1350 .. 1800    the chip flies to the bottom-right corner, the plate fades out 1400..1600
 *   1800            landed: the "+1 synergy" tally ticks
 * </pre>
 */
public final class SynergySlamMath {

    public static final long IMPACT_MS = 450;
    public static final long TOTAL_MS = 1800;
    public static final long CHIP_IN_MS = 300;
    public static final long PLATE_IN_START = 520;
    public static final long PLATE_IN_MS = 240;
    public static final long PLATE_OUT_START = 1400;
    public static final long PLATE_OUT_MS = 200;
    public static final long FLY_START = 1350;
    public static final long SHAKE_MS = 240;
    public static final long FLASH_MS = 180;
    public static final int SHAKE_PX = 3;
    /** The most skill icons that fly in; a synergy with more needs shows the first few. */
    public static final int MAX_ICONS = 3;

    private SynergySlamMath() {
    }

    public static float clamp01(float v) {
        return v < 0f ? 0f : Math.min(v, 1f);
    }

    /** How far the icons have come, 0 (off screen) to 1 (touching at the centre): an ease-in (quadratic), so they hit hard. */
    public static float approach(long t) {
        float u = clamp01(t / (float) IMPACT_MS);
        return u * u;
    }

    /** Which way icon {@code index} of {@code count} comes from: -1 left, +1 right (x), and the vertical: -1 above, +1 below. */
    public static int fromX(int index, int count) {
        if (count <= 1) {
            return -1;
        }
        return switch (index % 4) {
            case 0 -> -1;
            case 1 -> 1;
            default -> 0;
        };
    }

    public static int fromY(int index, int count) {
        if (count <= 2 || index % 4 < 2) {
            return 0;
        }
        return index % 4 == 2 ? -1 : 1;
    }

    /** The icons' opacity: full until just after the impact, gone by 200 ms past it (the chip takes over). */
    public static float iconAlpha(long t) {
        if (t < IMPACT_MS + 50) {
            return 1f;
        }
        return 1f - clamp01((t - IMPACT_MS - 50) / 150f);
    }

    /** The chip's scale: nothing before the impact, an overshoot pop to 1.3 and back to 1, then smaller as it flies off. */
    public static float chipScale(long t) {
        if (t < IMPACT_MS) {
            return 0f;
        }
        float scale;
        long since = t - IMPACT_MS;
        if (since >= CHIP_IN_MS) {
            scale = 1f;
        } else {
            float u = since / (float) CHIP_IN_MS;
            // 0 -> 1.3 by 60% of the way, then settles to 1.
            scale = u < 0.6f ? 1.3f * (u / 0.6f) : 1.3f - 0.3f * ((u - 0.6f) / 0.4f);
        }
        return scale * (1f - 0.45f * flight(t));
    }

    /** 0 until the chip leaves, then an ease-in-out to 1 at the corner. */
    public static float flight(long t) {
        float u = clamp01((t - FLY_START) / (float) (TOTAL_MS - FLY_START));
        return u * u * (3f - 2f * u);
    }

    /** The white flash: 1 at the impact, down to 0 over {@link #FLASH_MS}. */
    public static float flash(long t) {
        long since = t - IMPACT_MS;
        if (since < 0 || since >= FLASH_MS) {
            return 0f;
        }
        float u = 1f - since / (float) FLASH_MS;
        return u * u;
    }

    /** Whole-pixel shake for {@code px} amplitude: damped, closed-form, nothing before the impact or after {@link #SHAKE_MS}. */
    public static int shakeX(long t, int px) {
        return shake(t, px, 0.11, 0.0);
    }

    public static int shakeY(long t, int px) {
        return shake(t, px, 0.13, 1.7);
    }

    private static int shake(long t, int px, double freq, double phase) {
        long since = t - IMPACT_MS;
        if (since < 0 || since >= SHAKE_MS) {
            return 0;
        }
        double decay = 1.0 - since / (double) SHAKE_MS;
        return (int) Math.round(px * decay * Math.sin(since * freq * 2.4 + phase));
    }

    /** Plate opacity: in over {@link #PLATE_IN_MS}, held, out over {@link #PLATE_OUT_MS}. */
    public static float plateAlpha(long t) {
        float in = clamp01((t - PLATE_IN_START) / (float) PLATE_IN_MS);
        float out = 1f - clamp01((t - PLATE_OUT_START) / (float) PLATE_OUT_MS);
        return in * out;
    }

    /** 0..1 how far the plate has slid up into place (an ease-out); 1 once settled. */
    public static float plateSettle(long t) {
        float u = clamp01((t - PLATE_IN_START) / (float) PLATE_IN_MS);
        return 1f - (1f - u) * (1f - u);
    }

    /** Whether the slam is over. */
    public static boolean done(long t) {
        return t >= TOTAL_MS;
    }

    /**
     * How quickly a queue plays: normal alone, quicker with others waiting so a burst of five discoveries
     * does not take nine seconds. The timeline's own length stays {@link #TOTAL_MS}; the queue scales the clock.
     */
    public static float speed(int waiting) {
        if (waiting <= 0) {
            return 1f;
        }
        return waiting == 1 ? 1.35f : 1.8f;
    }
}
