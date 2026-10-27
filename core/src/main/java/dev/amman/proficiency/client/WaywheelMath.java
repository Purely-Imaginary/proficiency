package dev.amman.proficiency.client;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * The Waywheel's rules (ui.style = modern, 1.33.0), free of Minecraft so they are unit tested: which
 * category rune a flick points at, where a category's skill tokens spring out (one arc, or two staggered
 * rings for a category too big for one, Combat's 13), which disc fits the window, the opening's spin and
 * overshoot, the tokens' spring, the pinned skills (a comma list in the client config) and how many pinned
 * tokens fit beside the hotbar.
 */
public final class WaywheelMath {

    /**
     * The baked disc radii, largest first (tools/gen_skillstone.py WHEELS). 128 (1.34.0) fills 960x540 (1080p
     * at GUI 2) to the D mockup's on-screen size; every disc is drawn 1:1 so it stays pixel-crisp.
     */
    public static final int[] WHEELS = {128, 91, 61, 45};
    /** The baked round token sides, largest first (gen_skillstone.py TOKENS); the largest only on the largest disc. */
    public static final int[] TOKEN_SIZES = {30, 22, 18, 16};
    /** Each token side's sunk face radius, inside the rim and the ember groove (gen_skillstone.py TOKENS). */
    public static final int[] TOKEN_FACE = {9, 6, 5, 5};
    /** Frames of the baked ember level ring past the empty one: level 100 lights all of them. */
    public static final int RING_STEPS = 20;
    /** The cast: the disc folds into the hub over this long while the token flies to its belt socket. */
    public static final long FOLD_MS = 160;
    public static final long FLY_MS = 300;
    /** A cast of a skill that is not on the belt: the token lifts off and burns away over this long. */
    public static final long POP_MS = 240;
    /** The opening: the wheel spins open from the cursor, overshoots 5% and settles. */
    public static final long OPEN_MS = 180;

    /** A press of the Waywheel's own key shorter than this is a tap: the wheel stays open for clicks. */
    public static final long TAP_MS = 250;

    /**
     * What a release of the key does to an open wheel that is not yet sticky: true when the wheel should stay
     * open in its sticky (click to cast) mode instead of closing. Only for the Waywheel's own key ({@code tapKey}):
     * a tap opens the wheel on the press and lets go a tick or two later, so without this a real tap (about
     * 100 ms) opened and closed the wheel at once. A release over a token still casts (a fast flick), and the
     * ability key's hold (the wheel opens only after a quarter second) keeps its cancel on an empty release.
     */
    public static boolean stickOnRelease(boolean tapKey, long sinceOpenMs, boolean overToken) {
        return tapKey && !overToken && sinceOpenMs >= 0 && sinceOpenMs < TAP_MS;
    }
    public static final float OVERSHOOT = 0.05f;
    /** The opening's spin, degrees, wound back at t = 0. */
    public static final float SPIN_DEG = 40f;
    /** One token's spring out, and the step between two tokens. */
    public static final long SPRING_MS = 140;
    public static final long STAGGER_MS = 22;
    /** A category with more skills than this gets a second ring. */
    public static final int ONE_RING = 7;
    /** At most this many skills sit beside the hotbar. */
    public static final int MAX_PINS = 3;
    /** The belt's slot side (a round token, the hotbar's height) and the gap after it (the Tempo multiplier sits in that gap). */
    public static final int BELT_SLOT = 22;
    public static final int BELT_GAP = 18;
    /** Room kept between the belt and the hotbar: the last slot's multiplier, the offhand slot and margins. */
    public static final int BELT_CLEAR = 50;

    private WaywheelMath() {
    }

    /** The rune a flick at (dx, dy) from the hub points at: 0 straight up, clockwise, of {@code n}. */
    public static int rune(double dx, double dy, int n) {
        double a = Math.atan2(dy, dx) + Math.PI / 2;
        if (a < 0) {
            a += 2 * Math.PI;
        }
        return (int) Math.round(a / (2 * Math.PI / n)) % n;
    }

    /** Angle of rune {@code i} of {@code n}, radians, 0 = right, clockwise on screen (y down). */
    public static double runeAngle(int i, int n) {
        return -Math.PI / 2 + i * 2 * Math.PI / n;
    }

    /** How many of {@code n} tokens go on the inner ring; the rest go on the outer one. */
    public static int innerCount(int n) {
        return n > ONE_RING ? (n + 1) / 2 : n;
    }

    /** Inner ring radius for a disc of rune radius {@code dial} and token side {@code tok}. */
    public static int ring1(int dial, int tok) {
        return dial + 22 + tok / 2;
    }

    /** Outer ring radius: one token further, a little closer since the rings are staggered. */
    public static int ring2(int dial, int tok) {
        return ring1(dial, tok) + tok + 4;
    }

    /**
     * Offsets from the hub of {@code n} tokens around the rune at {@code angle}: centred on it, one token
     * plus a gap apart on the inner ring; past {@link #ONE_RING} the rest sit on the outer ring in the gaps
     * between the inner ones. {@code twoRings} false puts all on the inner ring (a window too small for the
     * outer one). Each row is {x, y}.
     */
    public static double[][] tokens(int n, double angle, int dial, int tok, boolean twoRings) {
        double[][] out = new double[n][2];
        int inner = twoRings ? innerCount(n) : n;
        double r1 = ring1(dial, tok);
        double step = (tok + 4) / r1;
        for (int i = 0; i < inner; i++) {
            double a = angle + (i - (inner - 1) / 2.0) * step;
            out[i][0] = r1 * Math.cos(a);
            out[i][1] = r1 * Math.sin(a);
        }
        double r2 = ring2(dial, tok);
        int outer = n - inner;
        for (int j = 0; j < outer; j++) {
            // The same angular step as the inner ring, so each outer token sits over a gap below it.
            double a = angle + (j - (outer - 1) / 2.0) * step;
            out[inner + j][0] = r2 * Math.cos(a);
            out[inner + j][1] = r2 * Math.sin(a);
        }
        return out;
    }

    /** True when every token of {@code pts} (offsets from a hub at cx, cy) lies inside the window, side {@code tok}. */
    public static boolean inside(double[][] pts, int cx, int cy, int tok, int width, int height) {
        int half = tok / 2 + 2;
        for (double[] p : pts) {
            double x = cx + p[0];
            double y = cy + p[1];
            if (x - half < 0 || y - half < 0 || x + half > width || y + half > height) {
                return false;
            }
        }
        return true;
    }

    /**
     * The disc (index into {@link #WHEELS}) and token side for a window: the largest disc whose outer ring
     * still fits on every side, with the largest token that allows (30 px only around the largest disc, else
     * 22, 18, 16); the smallest disc when none fits. Returns {disc index, token side}.
     */
    public static int[] fit(int width, int height) {
        int room = Math.min(width, height) / 2 - 2;
        for (int d = 0; d < WHEELS.length; d++) {
            for (int k = d == 0 ? 0 : 1; k < TOKEN_SIZES.length; k++) {
                int tok = TOKEN_SIZES[k];
                if (ring2(WHEELS[d], tok) + tok / 2 <= room) {
                    return new int[] {d, tok};
                }
            }
        }
        return new int[] {WHEELS.length - 1, 16};
    }

    /** The sunk face radius of a round token of side {@code tok} (the nearest baked size at or below it). */
    public static int face(int tok) {
        for (int k = 0; k < TOKEN_SIZES.length; k++) {
            if (tok >= TOKEN_SIZES[k]) {
                return TOKEN_FACE[k];
            }
        }
        return TOKEN_FACE[TOKEN_FACE.length - 1];
    }

    /** The ember ring frame for a skill level: 0 (dark) at level 0, a lit step from level 1, all at 100. */
    public static int ringFrame(int level, int maxLevel) {
        if (level <= 0 || maxLevel <= 0) {
            return 0;
        }
        if (level >= maxLevel) {
            return RING_STEPS;
        }
        return Math.max(1, Math.min(RING_STEPS - 1, (int) Math.floor(level * RING_STEPS / (double) maxLevel)));
    }

    /** Half the width of a disc of radius {@code r} at row {@code j} (0 = the top row, 2r rows), pixel centred. */
    public static int chord(int r, int j) {
        double dy = r - j - 0.5;
        double h = (double) r * r - dy * dy;
        return h <= 0 ? 0 : (int) Math.round(Math.sqrt(h));
    }

    // ---- The cast (1.34.0): the disc folds, the token flies to the belt -----------------------------

    /** The disc's scale {@code t} ms after a cast: 1 down to 0 over {@link #FOLD_MS}, slow first, then snapping shut. */
    public static float fold(long t) {
        if (t <= 0) {
            return 1f;
        }
        if (t >= FOLD_MS) {
            return 0f;
        }
        float p = t / (float) FOLD_MS;
        return 1f - p * p;
    }

    /** Ease in and out (smoothstep) of {@code t} over {@code total} ms, 0..1. */
    public static float ease(long t, long total) {
        if (t <= 0) {
            return 0f;
        }
        if (t >= total) {
            return 1f;
        }
        float p = t / (float) total;
        return p * p * (3f - 2f * p);
    }

    /**
     * The flying token's centre {@code t} ms after a cast, from (x0, y0) to (x1, y1) over {@link #FLY_MS}: a
     * quadratic curve whose control point is the midpoint lifted by a third of the distance (at most 80 px),
     * so the token arcs up over the world and drops into its socket. Returns {x, y, progress 0..1}.
     */
    public static double[] fly(long t, double x0, double y0, double x1, double y1) {
        double p = ease(t, FLY_MS);
        double dist = Math.hypot(x1 - x0, y1 - y0);
        double lift = Math.min(80.0, dist / 3.0);
        double mx = (x0 + x1) / 2.0;
        double my = Math.min(y0, y1) - lift;
        double q = 1 - p;
        double x = q * q * x0 + 2 * q * p * mx + p * p * x1;
        double y = q * q * y0 + 2 * q * p * my + p * p * y1;
        return new double[] {x, y, p};
    }

    /** A cast off the belt: {rise in px, scale, alpha} {@code t} ms in; the token lifts, swells and burns away. */
    public static float[] pop(long t) {
        if (t >= POP_MS) {
            return new float[] {12f, 1.35f, 0f};
        }
        float p = Math.max(0f, t / (float) POP_MS);
        float e = 1f - (1f - p) * (1f - p);
        return new float[] {12f * e, 1f + 0.35f * e, 1f - p * p};
    }

    /** Index of the point of {@code pts} nearest (mx, my) within {@code reach}, or -1. */
    public static int nearest(double mx, double my, double[][] pts, double reach) {
        int best = -1;
        double bestD = reach * reach;
        for (int i = 0; i < pts.length; i++) {
            double dx = mx - pts[i][0];
            double dy = my - pts[i][1];
            double d = dx * dx + dy * dy;
            if (d <= bestD) {
                bestD = d;
                best = i;
            }
        }
        return best;
    }

    /** The wheel's scale {@code t} ms into the opening: 0.25 to 1.05, then back to 1 (an ease-out-back). */
    public static float open(long t) {
        if (t >= OPEN_MS) {
            return 1f;
        }
        if (t <= 0) {
            return 0.25f;
        }
        float p = t / (float) OPEN_MS;
        if (p < 0.7f) {
            float q = p / 0.7f;
            float e = 1f - (1f - q) * (1f - q);
            return 0.25f + (1f + OVERSHOOT - 0.25f) * e;
        }
        float q = (p - 0.7f) / 0.3f;
        return 1f + OVERSHOOT * (1f - q * q * (3f - 2f * q));
    }

    /** The wheel's spin, degrees, {@code t} ms into the opening: wound back {@link #SPIN_DEG}, then 0. */
    public static float spin(long t) {
        if (t >= OPEN_MS) {
            return 0f;
        }
        float p = Math.max(0f, t / (float) OPEN_MS);
        float e = 1f - (1f - p) * (1f - p) * (1f - p);
        return -SPIN_DEG * (1f - e);
    }

    /** Token {@code i}'s scale {@code t} ms after its category was chosen: springs from 0 past 1.15 back to 1. */
    public static float spring(long t, int i) {
        long u = t - i * STAGGER_MS;
        if (u <= 0) {
            return 0f;
        }
        if (u >= SPRING_MS) {
            return 1f;
        }
        float p = u / (float) SPRING_MS;
        if (p < 0.6f) {
            float q = p / 0.6f;
            return 1.15f * (1f - (1f - q) * (1f - q));
        }
        float q = (p - 0.6f) / 0.4f;
        return 1.15f - 0.15f * q;
    }

    /** The pinned skill ids in the config's comma list: trimmed, lower case, known ones only, no repeats, at most {@link #MAX_PINS}. */
    public static List<String> pins(String csv, java.util.function.Predicate<String> known) {
        List<String> out = new ArrayList<>();
        if (csv == null) {
            return out;
        }
        for (String part : csv.split(",")) {
            String id = part.trim().toLowerCase(Locale.ROOT);
            if (!id.isEmpty() && known.test(id) && !out.contains(id) && out.size() < MAX_PINS) {
                out.add(id);
            }
        }
        return out;
    }

    /**
     * The list after a pin click on {@code id}: a pinned skill comes off, another goes on the right end;
     * a full belt drops its oldest (left) pin to make room.
     */
    public static String toggle(String csv, String id, java.util.function.Predicate<String> known) {
        List<String> list = pins(csv, known);
        if (!list.remove(id)) {
            if (list.size() >= MAX_PINS) {
                list.remove(0);
            }
            list.add(id);
        }
        return String.join(",", list);
    }

    /**
     * Left x of each of {@code n} belt slots for a GUI {@code width} wide: right-aligned against the
     * hotbar (182 px, centred) less {@link #BELT_CLEAR}; slots that would leave the window are dropped
     * from the left. Length = how many fit.
     */
    public static int[] belt(int width, int n) {
        int right = width / 2 - 91 - BELT_CLEAR;
        int pitch = BELT_SLOT + BELT_GAP;
        int fit = Math.max(0, Math.min(n, (right - 2 + BELT_GAP) / pitch));
        int[] xs = new int[fit];
        for (int i = 0; i < fit; i++) {
            xs[i] = right - BELT_SLOT - (fit - 1 - i) * pitch;
        }
        return xs;
    }
}
