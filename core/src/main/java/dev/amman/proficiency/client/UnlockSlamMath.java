package dev.amman.proficiency.client;

/**
 * The timeline of the Skillstone's unlock slam (1.x, {@code ui.unlockSlam}), with no Minecraft in it.
 *
 * <p>Filling a tree's root wakes the skill's signature and, past its level, the ability. The root's
 * socket flares, lifts off the tree and grows ({@link #rise}); the paid materials fly in from the screen
 * edges and each one that lands charges the morph from socket to Face card ({@link #morph}); under it
 * the slab turns to the Face ({@link #TURN_AT}); then the card drops into its slot ({@link #drop}),
 * squashes and settles ({@link #impactScale}), with a dust burst, a slab jolt and an ember edge glow.
 * A second card (both unlocked at once) follows {@link #SECOND_DELAY_MS} behind the first.
 *
 * <p>Every function takes the time since the slam began, ms. Nothing allocates.
 */
public final class UnlockSlamMath {

    /** ClickFx's success flare on the socket plays first. */
    public static final long RISE_AT = 150;
    public static final long RISE_MS = 330;
    public static final long CHARGE_AT = RISE_AT + RISE_MS;
    /** Every material lands inside this window, however many there are. */
    public static final long CHARGE_MS = 650;
    /** The slab turns from the Tree to the Face under the flying socket. */
    public static final long TURN_AT = CHARGE_AT + 20;
    public static final long FLIGHT_MS = 360;
    /** First arrival this long into the charge; the last one lands at its end. */
    public static final long FIRST_LAND_MS = 90;
    public static final long STEP_MS = 90;
    public static final long PULSE_MS = 170;
    public static final long FLASH_MS = 280;
    /** A beat after the last material lands, the card drops. */
    public static final long SLAM_AT = CHARGE_AT + CHARGE_MS + STEP_MS;
    public static final long DROP_MS = 160;
    public static final long SECOND_DELAY_MS = 150;
    /** From impact to the card at rest. */
    public static final long SETTLE_MS = 300;
    /** The ember edge glow and the "Lit!" mark after impact. */
    public static final long GLOW_MS = 700;
    public static final long JOLT_MS = 200;
    public static final long DUST_MS = 520;
    public static final int DUST = 28;
    public static final int MAX_ICONS = 12;
    /** How big the socket grows above the tree. */
    public static final float RISE_SCALE = 2.4f;
    /** The card starts its drop this much larger than its slot. */
    public static final float DROP_SCALE = 1.22f;
    public static final float DIM = 0.6f;
    public static final float SHAKE_PX = 4f;
    public static final float JOLT_PX = 3f;

    private UnlockSlamMath() {
    }

    public static float clamp01(float v) {
        return v < 0f ? 0f : v > 1f ? 1f : v;
    }

    public static float easeOutCubic(float k) {
        float u = 1f - clamp01(k);
        return 1f - u * u * u;
    }

    public static float easeInQuad(float k) {
        float c = clamp01(k);
        return c * c;
    }

    public static float smooth(float k) {
        float c = clamp01(k);
        return c * c * (3f - 2f * c);
    }

    public static float lerp(float a, float b, float k) {
        return a + (b - a) * k;
    }

    // ---- Phases ----------------------------------------------------------------------------------

    public enum Phase { FLARE, RISE, CHARGE, SLAM, SETTLE, DONE }

    public static Phase phase(long t, int cards) {
        if (t < RISE_AT) {
            return Phase.FLARE;
        }
        if (t < CHARGE_AT) {
            return Phase.RISE;
        }
        if (t < SLAM_AT) {
            return Phase.CHARGE;
        }
        if (t < impactAt(cards - 1)) {
            return Phase.SLAM;
        }
        return t < endAt(cards) ? Phase.SETTLE : Phase.DONE;
    }

    /** When card {@code card} (0 first, 1 second) hits its slot. */
    public static long impactAt(int card) {
        return SLAM_AT + DROP_MS + Math.max(0, card) * SECOND_DELAY_MS;
    }

    /** The whole run, glow included. */
    public static long endAt(int cards) {
        return impactAt(Math.max(1, cards) - 1) + GLOW_MS;
    }

    // ---- Rise ------------------------------------------------------------------------------------

    /** 0 on the tree, 1 hovering above it at {@link #RISE_SCALE}. */
    public static float rise(long t) {
        return easeOutCubic((t - RISE_AT) / (float) RISE_MS);
    }

    /** The dark laid over the slab while the socket flies: in with the rise, out as the card lands. */
    public static float dim(long t, int cards) {
        if (t < SLAM_AT) {
            return DIM * rise(t);
        }
        return DIM * (1f - clamp01((t - SLAM_AT) / (float) (impactAt(cards - 1) - SLAM_AT)));
    }

    // ---- Charge: the materials -------------------------------------------------------------------

    /** When icon {@code i} of {@code n} lands in the socket. */
    public static long arrivalAt(int i, int n) {
        if (n <= 0) {
            return CHARGE_AT + CHARGE_MS;
        }
        return CHARGE_AT + FIRST_LAND_MS + (CHARGE_MS - FIRST_LAND_MS) * (i + 1L) / n;
    }

    public static long launchAt(int i, int n) {
        return arrivalAt(i, n) - FLIGHT_MS;
    }

    /** Icon {@code i}'s flight, 0 at the edge to 1 in the socket; below 0 not launched, above 1 landed. */
    public static float flight(long t, int i, int n) {
        return (t - launchAt(i, n)) / (float) FLIGHT_MS;
    }

    /** How many icons have landed by {@code t}. */
    public static int landed(long t, int n) {
        int out = 0;
        for (int i = 0; i < n; i++) {
            if (t >= arrivalAt(i, n)) {
                out++;
            }
        }
        return out;
    }

    /** The part of the morph that runs evenly over the charge; the landings drive the rest. */
    public static final float MORPH_BASE = 0.3f;

    /**
     * The socket-to-card morph, 0..1. {@link #MORPH_BASE} of it runs evenly over the charge, and each landing
     * advances the rest one step (eased over {@link #STEP_MS}); with no materials all of it runs evenly.
     */
    public static float morph(long t, int n) {
        float even = smooth((t - CHARGE_AT) / (float) CHARGE_MS);
        if (n <= 0) {
            return even;
        }
        float sum = 0f;
        for (int i = 0; i < n; i++) {
            sum += easeOutCubic((t - arrivalAt(i, n)) / (float) STEP_MS);
        }
        return MORPH_BASE * even + (1f - MORPH_BASE) * sum / n;
    }

    /** The flare of the latest landing, 1 at the hit, fading over {@link #PULSE_MS}. */
    public static float pulse(long t, int n) {
        float out = 0f;
        for (int i = 0; i < n; i++) {
            long dt = t - arrivalAt(i, n);
            if (dt >= 0 && dt < PULSE_MS) {
                out = Math.max(out, 1f - dt / (float) PULSE_MS);
            }
        }
        return out;
    }

    /** The bigger flash when the charge completes (the last icon, or the end of an empty charge). */
    public static float fullFlash(long t, int n) {
        long dt = t - (n <= 0 ? CHARGE_AT + CHARGE_MS : arrivalAt(n - 1, n));
        if (dt < 0 || dt >= FLASH_MS) {
            return 0f;
        }
        return 1f - dt / (float) FLASH_MS;
    }

    /** The pitch of arrival {@code i}'s tick: rising with each one. */
    public static float tickPitch(int i, int n) {
        float k = n <= 1 ? 1f : i / (float) (n - 1);
        return 0.8f + 0.9f * k;
    }

    /**
     * Where icon {@code i} starts, off the screen: one of the four edges (spread round them), 18 px beyond
     * it. {@code x} picks the coordinate. Screen size {@code w} x {@code h}.
     */
    public static float startX(int i, int w) {
        int edge = edge(i);
        if (edge == 0) {
            return -18f;
        }
        if (edge == 1) {
            return w + 18f;
        }
        return w * (0.1f + 0.8f * ClickFxMath.hash(i, 31));
    }

    public static float startY(int i, int h) {
        int edge = edge(i);
        if (edge == 2) {
            return -18f;
        }
        if (edge == 3) {
            return h + 18f;
        }
        return h * (0.1f + 0.8f * ClickFxMath.hash(i, 37));
    }

    /** Left, right, top, bottom, in an order that does not repeat a side twice running. */
    static int edge(int i) {
        return EDGES[Math.floorMod(i, EDGES.length)];
    }

    private static final int[] EDGES = {0, 1, 2, 1, 0, 3};

    /**
     * Point on icon {@code i}'s curve at flight {@code k} (0..1) from {@code (sx, sy)} to {@code (ex, ey)}: a
     * quadratic arc whose control point sits off the straight line by 25-45% of its length, one side or the
     * other per icon, like DotFlight's dots. {@code wantY} picks y.
     */
    public static float curve(int i, float k, float sx, float sy, float ex, float ey, boolean wantY) {
        float e = smooth(k * 0.85f + 0.15f * k * k);
        float mx = (sx + ex) / 2f;
        float my = (sy + ey) / 2f;
        float dx = ex - sx;
        float dy = ey - sy;
        float bend = (0.25f + 0.2f * ClickFxMath.hash(i, 41)) * (i % 2 == 0 ? 1f : -1f);
        float cx = mx - dy * bend;
        float cy = my + dx * bend;
        float u = 1f - e;
        return wantY ? u * u * sy + 2f * u * e * cy + e * e * ey : u * u * sx + 2f * u * e * cx + e * e * ex;
    }

    /**
     * Icons per material pile: each pile its count, at most {@link #MAX_ICONS} in all, every pile at least one
     * while there is room (piles past the cap get none). Proportional when the counts do not fit.
     */
    public static int[] icons(int[] counts) {
        int[] out = new int[counts.length];
        long total = 0;
        for (int c : counts) {
            total += Math.max(0, c);
        }
        if (total <= MAX_ICONS) {
            for (int i = 0; i < counts.length; i++) {
                out[i] = Math.max(0, counts[i]);
            }
            return out;
        }
        int left = MAX_ICONS;
        for (int i = 0; i < counts.length && left > 0; i++) {
            if (counts[i] > 0) {
                out[i] = 1;
                left--;
            }
        }
        // The rest by share, largest remainders first, never above a pile's own count.
        for (int round = 0; left > 0 && round < MAX_ICONS; round++) {
            int best = -1;
            double bestNeed = 0;
            for (int i = 0; i < counts.length; i++) {
                if (counts[i] <= out[i]) {
                    continue;
                }
                double need = counts[i] * (double) MAX_ICONS / total - out[i];
                if (best < 0 || need > bestNeed) {
                    best = i;
                    bestNeed = need;
                }
            }
            if (best < 0) {
                break;
            }
            out[best]++;
            left--;
        }
        return out;
    }

    public static final long PEEL_MS = 140;

    /** The second card sliding out from under the first once the charge completes, 0..1. */
    public static float peel(long t) {
        return easeOutCubic((t - (CHARGE_AT + CHARGE_MS)) / (float) PEEL_MS);
    }

    // ---- Slam ------------------------------------------------------------------------------------

    /** Card {@code card}'s drop from its hover rect to its slot, 0..1, accelerating. */
    public static float drop(long t, int card) {
        return easeInQuad((t - SLAM_AT - Math.max(0, card) * SECOND_DELAY_MS) / (float) DROP_MS);
    }

    /**
     * The card's scale: {@link #DROP_SCALE} at the top of the drop down to 1 at impact, then a squash to 0.93
     * and a small overshoot that settles by {@link #SETTLE_MS}.
     */
    public static float impactScale(long t, int card) {
        long dt = t - impactAt(card);
        if (dt < 0) {
            return lerp(DROP_SCALE, 1f, drop(t, card));
        }
        if (dt >= SETTLE_MS) {
            return 1f;
        }
        return 1f - 0.07f * (float) Math.cos(dt * 2 * Math.PI / 180.0) * (float) Math.exp(-dt / 90.0);
    }

    /** The slab's downward jolt at impact, px (before the shake): sharp, then back. */
    public static float jolt(long t, int card) {
        long dt = t - impactAt(card);
        if (dt < 0 || dt >= JOLT_MS) {
            return 0f;
        }
        return dt < 20 ? JOLT_PX * dt / 20f : JOLT_PX * (float) Math.exp(-(dt - 20) / 45.0);
    }

    /** The ember edge glow round the landed card, 1 at impact, gone at {@link #GLOW_MS}. */
    public static float glow(long t, int card) {
        long dt = t - impactAt(card);
        if (dt < 0 || dt >= GLOW_MS) {
            return 0f;
        }
        float k = 1f - dt / (float) GLOW_MS;
        return k * k;
    }

    /** The "Lit!" mark over the landed card: alpha. */
    public static float litAlpha(long t, int card) {
        long dt = t - impactAt(card) - 60;
        if (dt < 0 || dt >= GLOW_MS) {
            return 0f;
        }
        return Math.min(1f, dt / 80f) * Math.min(1f, (GLOW_MS - dt) / 220f);
    }

    /** How far the mark has risen, px. */
    public static float litRise(long t, int card) {
        return 6f * easeOutCubic((t - impactAt(card) - 60) / (float) GLOW_MS);
    }

    /**
     * Dust grain {@code k}'s offset from the slot's bottom centre at {@code dt} ms after impact, for a slot
     * {@code halfW} wide each side: thrown out along the slot's bottom edge and up, falling back.
     */
    public static float dustX(int k, long dt, float halfW) {
        float side = k % 2 == 0 ? -1f : 1f;
        float start = halfW * (0.2f + 0.8f * ClickFxMath.hash(k, 53));
        float speed = 0.05f + 0.09f * ClickFxMath.hash(k, 59);
        return side * (start + speed * dt);
    }

    public static float dustY(int k, long dt) {
        float up = 0.05f + 0.08f * ClickFxMath.hash(k, 61);
        float s = dt / 1000f;
        return -up * dt + 260f * s * s;
    }

    public static float dustAlpha(int k, long dt) {
        long life = (long) (DUST_MS * (0.6f + 0.4f * ClickFxMath.hash(k, 67)));
        if (dt < 0 || dt >= life) {
            return 0f;
        }
        return 1f - dt / (float) life;
    }
}
