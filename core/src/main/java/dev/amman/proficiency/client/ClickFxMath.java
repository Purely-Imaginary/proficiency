package dev.amman.proficiency.client;

/**
 * The curves behind the click feedback kit, with no Minecraft in them, so unit tests can drive them
 * with a fake clock. Everything is a closed-form function of the milliseconds since the effect
 * started: nothing is integrated per frame, so the result is the same at 20 fps and at 240 fps and
 * nothing allocates. {@code ClickFx} (in the game code) calls these and draws what they answer.
 *
 * <ul>
 *   <li>cannot: {@link #wiggle}, a damped left/right "no" of {@link #NO_MS};</li>
 *   <li>charging: {@link #shakeX} and {@link #shakeY} with an intensity that rises, and
 *       {@link #chargePitch} for the sound;</li>
 *   <li>success: {@link #pop} (scale overshoot), {@link #burstX} and {@link #burstY} (pixel sparks),
 *       {@link #ringRadius}, {@link #flash} and {@link #edge}.</li>
 * </ul>
 */
public final class ClickFxMath {

    /** The "no" wiggle. */
    public static final long NO_MS = 250;
    /** How long a red frame flash lasts. */
    public static final long RED_MS = 320;
    /** Scale overshoot. */
    public static final long POP_MS = 420;
    /** The longest a spark lives. */
    public static final long BURST_MS = 720;
    public static final long RING_MS = 440;
    public static final long FLASH_MS = 200;
    /** The screen-edge glow for a capstone or a milestone. */
    public static final long EDGE_MS = 1100;
    /** A click that no answer follows within this long is treated as refused. */
    public static final long PENDING_MS = 600;
    /** One charge tick of sound every this long while a click is pending. */
    public static final long TICK_MS = 70;
    /**
     * A charge shows at least this long before its success pops, so a local server's one-tick answer
     * still gets a visible shake and two rising ticks.
     */
    public static final long MIN_CHARGE_MS = 150;
    /** The shake reaches full intensity this long after the click. */
    public static final long RAMP_MS = 260;
    /** Most sparks one burst may spawn, and the most that may be alive at once. */
    public static final int MAX_BURST = 80;
    public static final int MAX_LIVE_SPARKS = 320;

    private ClickFxMath() {
    }

    /** Pixels left (negative) and right (positive) of home, {@code amp} at the first swing, damped to nothing. */
    public static float wiggle(long t, float amp) {
        if (t < 0 || t >= NO_MS) {
            return 0f;
        }
        return (float) (amp * Math.exp(-t / 80.0) * Math.sin(t * 2 * Math.PI / 100.0));
    }

    /** How far through the "no" this is, 0..1, or -1 when it is not running. */
    public static float progress(long t, long length) {
        return t < 0 || t >= length ? -1f : t / (float) length;
    }

    /** Charge level 0..1 after {@code t} ms of waiting: full after {@link #RAMP_MS}. */
    public static float chargeLevel(long t) {
        return clamp01(t / (float) RAMP_MS);
    }

    /** Horizontal shake in pixels; the amplitude rises from 0.5 to 2.6 with {@code level}. */
    public static float shakeX(long t, float level) {
        float amp = 0.5f + 2.1f * clamp01(level);
        return amp * (float) (0.6 * Math.sin(t * 0.11) + 0.4 * Math.sin(t * 0.29 + 1.3));
    }

    public static float shakeY(long t, float level) {
        float amp = 0.3f + 1.2f * clamp01(level);
        return amp * (float) (0.6 * Math.sin(t * 0.13 + 0.7) + 0.4 * Math.sin(t * 0.31 + 2.1));
    }

    /** The pitch of the n-th charge tick: rises from 0.7 to 1.4 over a full wait. */
    public static float chargePitch(float level) {
        return 0.7f + 0.7f * clamp01(level);
    }

    /** The highest value of exp(-t/95) * sin(2 pi t/210): dividing by it makes the first peak exactly {@code 1 + strength}. */
    private static final float POP_PEAK = 0.6114f;

    /**
     * Scale of a popping icon: 1 before and after, {@code 1 + strength} at the first peak, a small
     * undershoot, then settled by {@link #POP_MS}.
     */
    public static float pop(long t, float strength) {
        if (t < 0 || t >= POP_MS) {
            return 1f;
        }
        return 1f + (float) (strength * Math.exp(-t / 95.0) * Math.sin(t * 2 * Math.PI / 210.0)) / POP_PEAK;
    }

    /** A cheap stable hash of two ints, 0..1. */
    public static float hash(int a, int b) {
        int h = a * 0x9E3779B1 + b * 0x85EBCA6B + 0x165667B1;
        h ^= h >>> 15;
        h *= 0x2C1B3C6D;
        h ^= h >>> 12;
        h *= 0x297A2D39;
        h ^= h >>> 15;
        return (h & 0xFFFFFF) / (float) 0x1000000;
    }

    private static float angle(int i, int seed) {
        return (float) (i * 2.399963 + hash(i, seed) * 0.9);
    }

    /** How long spark {@code i} lives, ms. */
    public static long sparkLife(int i, int seed) {
        return (long) (BURST_MS * (0.55f + 0.45f * hash(i + 77, seed)));
    }

    private static float reach(int i, int seed, float radius) {
        return radius * (0.45f + 0.55f * hash(i + 13, seed));
    }

    /** Spark {@code i}'s offset from the burst's centre after {@code t} ms: it flies out fast and slows. */
    public static float burstX(int i, int seed, long t, float radius) {
        return (float) (Math.cos(angle(i, seed)) * reach(i, seed, radius) * (1 - Math.exp(-t / 140.0)));
    }

    /** As {@link #burstX} with a little fall, so the sparks arc down like embers. */
    public static float burstY(int i, int seed, long t, float radius) {
        float fall = 0.000045f * t * t * 0.35f * radius;
        return (float) (Math.sin(angle(i, seed)) * reach(i, seed, radius) * (1 - Math.exp(-t / 140.0))) + fall;
    }

    /** Spark alpha 0..1: full for the first half of its life, then fading out. */
    public static float sparkAlpha(int i, int seed, long t) {
        long life = sparkLife(i, seed);
        if (t < 0 || t >= life) {
            return 0f;
        }
        float f = t / (float) life;
        return f < 0.5f ? 1f : clamp01(2f * (1f - f));
    }

    /** A spark's side in pixels: mostly 1 and 2, a few 3 in a big burst. */
    public static int sparkSize(int i, int seed, boolean big) {
        float h = hash(i + 5, seed);
        return h > (big ? 0.6f : 0.82f) ? 3 : h > (big ? 0.25f : 0.4f) ? 2 : 1;
    }

    /** The ring's radius: {@code from} at the start to {@code to}, eased out. */
    public static float ringRadius(long t, float from, float to) {
        float f = clamp01(t / (float) RING_MS);
        float e = 1f - (1f - f) * (1f - f) * (1f - f);
        return from + (to - from) * e;
    }

    /** Ring alpha 0..1. */
    public static float ringAlpha(long t) {
        return t < 0 || t >= RING_MS ? 0f : 1f - t / (float) RING_MS;
    }

    /** The white flash: 1 at once, squared falloff. */
    public static float flash(long t) {
        if (t < 0 || t >= FLASH_MS) {
            return 0f;
        }
        float f = 1f - t / (float) FLASH_MS;
        return f * f;
    }

    /** The red frame flash: two beats, so it reads as a refusal and not a hover. */
    public static float red(long t) {
        if (t < 0 || t >= RED_MS) {
            return 0f;
        }
        float f = t / (float) RED_MS;
        return (float) (Math.pow(1f - f, 1.5) * (0.65 + 0.35 * Math.cos(f * 2 * Math.PI * 1.5)));
    }

    /** The screen-edge glow: quick attack, long decay. */
    public static float edge(long t) {
        if (t < 0 || t >= EDGE_MS) {
            return 0f;
        }
        if (t < 90) {
            return t / 90f;
        }
        float f = (t - 90) / (float) (EDGE_MS - 90);
        return (1f - f) * (1f - f);
    }

    /** How many sparks a burst of this tier spawns. */
    public static int sparkCount(int tier) {
        return tier >= 2 ? 72 : tier == 1 ? 36 : 14;
    }

    /** The delay before a neighbour {@code distance} pixels away lights up, ms, so the glow ripples out. */
    public static long rippleDelay(float distance) {
        return Math.min(420L, (long) (distance * 1.7f) + 60L);
    }

    /** The success chime's pitch: higher with more of the node filled, and for a bigger tier. */
    public static float chimePitch(float filled, int tier) {
        return 0.85f + 0.4f * clamp01(filled) + 0.1f * tier;
    }

    public static float clamp01(float v) {
        return v < 0f ? 0f : v > 1f ? 1f : v;
    }
}
