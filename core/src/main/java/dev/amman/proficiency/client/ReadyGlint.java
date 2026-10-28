package dev.amman.proficiency.client;

/**
 * The "ability ready" glint, with no Minecraft in it so unit tests can drive it. Two pieces:
 *
 * <ul>
 *   <li>{@link Detector}: tells a cooldown that just ended from an ability that has simply been
 *       ready all along. Only cooling to ready counts. The first reading of a skill after login
 *       (or after a reset) only sets the baseline, so nothing glints or chimes for what was ready
 *       when you joined; locked skills never count; a skill that unlocks while idle never counts.</li>
 *   <li>{@link Gate}: the chime's minimum gap, so three cooldowns ending together make one sound.</li>
 * </ul>
 *
 * Curves ({@link #sweep}, {@link #flash}) are closed-form in the milliseconds since the glint began,
 * so they look the same at any frame rate.
 */
public final class ReadyGlint {

    /** The whole glint, ms. The pop uses {@link ClickFxMath#pop}, which settles by {@link ClickFxMath#POP_MS}. */
    public static final long GLINT_MS = 640;
    /** The diagonal sweep starts this long after the pop and crosses the icon in {@link #SWEEP_MS}. */
    public static final long SWEEP_DELAY_MS = 70;
    public static final long SWEEP_MS = 420;
    /** No second chime within this long of the last one. */
    public static final long CHIME_GAP_MS = 450;

    private ReadyGlint() {
    }

    /** Where the bright diagonal band is, 0 (left of the icon) to 1 (right of it), or -1 when it is not running. */
    public static float sweep(long t) {
        long s = t - SWEEP_DELAY_MS;
        if (s < 0 || s >= SWEEP_MS) {
            return -1f;
        }
        float f = s / (float) SWEEP_MS;
        // Ease in and out: slow at the edges, quick over the middle, like a real glint.
        return f * f * (3f - 2f * f);
    }

    /** Brightness of the band: rises fast, falls away, 0..1. */
    public static float sweepAlpha(long t) {
        long s = t - SWEEP_DELAY_MS;
        if (s < 0 || s >= SWEEP_MS) {
            return 0f;
        }
        float f = s / (float) SWEEP_MS;
        return (float) Math.sin(Math.PI * f);
    }

    /** A soft glow behind the icon: peaks right away and fades over {@link #GLINT_MS}. */
    public static float flash(long t) {
        if (t < 0 || t >= GLINT_MS) {
            return 0f;
        }
        float f = 1f - t / (float) GLINT_MS;
        return f * f;
    }

    public static boolean running(long t) {
        return t >= 0 && t < GLINT_MS;
    }

    /** Per-slot cooling to ready detection. */
    public static final class Detector {

        private static final byte UNKNOWN = 0;
        private static final byte READY = 1;
        private static final byte COOLING = 2;
        private static final byte LOCKED = 3;

        private final byte[] state;

        public Detector(int slots) {
            state = new byte[slots];
        }

        /**
         * One reading. {@code unlocked}: the ability can be used at all. {@code cooling}: it has time left.
         * True exactly once, on the reading where a cooling, unlocked ability reads ready.
         */
        public boolean observe(int slot, boolean unlocked, boolean cooling) {
            byte before = state[slot];
            byte now = !unlocked ? LOCKED : cooling ? COOLING : READY;
            state[slot] = now;
            return before == COOLING && now == READY;
        }

        /** Forget every baseline: the next reading of each slot is a fresh start (login, world change). */
        public void clear() {
            java.util.Arrays.fill(state, UNKNOWN);
        }
    }

    /** The chime's minimum gap. */
    public static final class Gate {

        private long last = Long.MIN_VALUE / 2;

        /** True, and remembers {@code now}, when {@link #CHIME_GAP_MS} has passed since the last true. */
        public boolean allow(long now) {
            if (now - last < CHIME_GAP_MS && now >= last) {
                return false;
            }
            last = now;
            return true;
        }

        public void clear() {
            last = Long.MIN_VALUE / 2;
        }
    }
}
