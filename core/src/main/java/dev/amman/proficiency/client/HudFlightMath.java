package dev.amman.proficiency.client;

import java.util.ArrayList;
import java.util.List;

/**
 * The pure numbers behind the three HUD flights of 1.38.0, with no Minecraft in them: bonus loot
 * flying from the world to the hotbar (projection with an edge clamp, flight cap), the talent point coin
 * flying from the skill bar to the brazier (path, count shown), and the rested pool pouring into the
 * XP fill (dot count). {@code SkillHud}'s {@code HudFlights} draws them; the unit tests drive them with a
 * fake clock. Everything is in milliseconds and GUI pixels, so it is the same at any frame rate.
 */
public final class HudFlightMath {

    /** Most bonus icons in the air at once. A drop that finds the sky full is not flown. */
    public static final int MAX_BONUS = 8;
    /** Time one bonus icon takes from the block to its slot. */
    public static final long BONUS_FLIGHT_MS = 520;
    /** Gap between two launches of one batch, so a pile reads as a stream. */
    public static final long BONUS_STAGGER_MS = 55;
    /** The glint at the slot after landing. */
    public static final long GLINT_MS = 260;

    /** Time the coin takes from the skill bar to the brazier, and how long the brazier flares after. */
    public static final long COIN_FLIGHT_MS = 780;
    public static final long COIN_FLARE_MS = 420;
    /** Coins in the air at once (a burst of level-ups); the rest land silently in the count. */
    public static final int MAX_COINS = 4;
    /** The brazier socket is this many GUI pixels square. */
    public static final int SOCKET = 18;
    /** Room the socket needs right of the hotbar: the 91 px half hotbar, the offhand slot and the attack indicator. */
    public static final int SOCKET_CLEAR = 36;
    /** The count is shown as "99+" past this. */
    public static final int COUNT_CAP = 99;

    /** Most rested dots one pay launches, and in the air at once. */
    public static final int MAX_POUR_PER_PAY = 8;
    public static final int MAX_POUR_LIVE = 14;
    public static final long POUR_FLIGHT_MS = 340;
    public static final long POUR_STAGGER_MS = 38;
    /** The pool spent per dot: a quarter of the bar is a dot, so a full bar of pool is the 8-dot cap. */
    private static final float POUR_REACH_PER_DOT = 0.125f;

    private HudFlightMath() {
    }

    /** A point on the screen in GUI pixels, and whether the world point it stands for is actually in view. */
    public record Screen(float x, float y, boolean onScreen) {
    }

    /**
     * Projects the world offset ({@code dx, dy, dz}, from the camera to the point) onto a {@code width} x
     * {@code height} GUI. {@code fwd}, {@code up} and {@code left} are the camera's unit vectors;
     * {@code fovDeg} is the vertical field of view. A point in view lands where it is; one off to the side, above,
     * below or behind starts from the screen edge in its direction (kept {@code margin} px inside), so an icon
     * from a block you are not looking at still comes from the right side.
     */
    public static Screen project(double dx, double dy, double dz, double[] fwd, double[] up, double[] left,
            double fovDeg, int width, int height, int margin) {
        double z = dx * fwd[0] + dy * fwd[1] + dz * fwd[2];
        double px = dx * left[0] + dy * left[1] + dz * left[2];
        double py = dx * up[0] + dy * up[1] + dz * up[2];
        double th = Math.tan(Math.toRadians(Math.max(1.0, Math.min(170.0, fovDeg))) / 2.0);
        double aspect = height > 0 ? width / (double) height : 1.0;
        double nx;
        double ny;
        boolean inView;
        if (z > 0.05) {
            nx = -px / (z * th * aspect);
            ny = py / (z * th);
            inView = Math.abs(nx) <= 1 && Math.abs(ny) <= 1;
        } else {
            // Behind or level with the camera: only the direction on the screen plane is known.
            nx = -px;
            ny = py;
            inView = false;
            if (Math.abs(nx) < 1e-6 && Math.abs(ny) < 1e-6) {
                ny = -1;
            }
        }
        if (!inView) {
            double big = Math.max(Math.abs(nx), Math.abs(ny));
            if (big > 1e-9) {
                nx /= big;
                ny /= big;
            }
        }
        float x = (float) ((nx + 1) / 2 * width);
        float y = (float) ((1 - ny) / 2 * height);
        x = Math.max(margin, Math.min(width - margin, x));
        y = Math.max(margin, Math.min(height - margin, y));
        return new Screen(x, y, inView);
    }

    /** Quadratic Bezier component: {@code e} 0..1 along the curve from {@code a} through control {@code c} to {@code b}. */
    public static float bezier(float a, float c, float b, double e) {
        double u = 1 - e;
        return (float) (u * u * a + 2 * u * e * c + e * e * b);
    }

    /** Smooth in and out, 0..1. */
    public static double ease(double t) {
        return XpGainDots.ease(t);
    }

    /** Progress 0..1 of a flight that began at {@code launch} and lasts {@code ms}; negative before it launches. */
    public static double progress(long launch, long ms, long now) {
        if (now < launch) {
            return -1;
        }
        return Math.min(1.0, (now - launch) / (double) ms);
    }

    /**
     * Where a bonus icon is {@code t} (0..1) of the way from ({@code x0}, {@code y0}) to ({@code x1}, {@code y1}):
     * a lifted arc, ease in and out. Returns {x, y, scale}; the icon shrinks a little as it drops into the slot.
     */
    public static float[] bonusPoint(double t, float x0, float y0, float x1, float y1) {
        double e = ease(t);
        float lift = 26 + Math.abs(x1 - x0) * 0.08f;
        float cx = x0 + (x1 - x0) * 0.35f;
        float cy = Math.min(y0, y1) - lift;
        float scale = (float) (0.9 - 0.35 * e);
        return new float[] {bezier(x0, cx, x1, e), bezier(y0, cy, y1, e), scale};
    }

    /** Where the coin is {@code t} of the way: rises off the bar, arcs wide over the hotbar and drops in. {x, y, scale}. */
    public static float[] coinPoint(double t, float x0, float y0, float x1, float y1) {
        double e = ease(t);
        float cx = x0 + (x1 - x0) * 0.2f;
        float cy = Math.min(y0, y1) - 46;
        float scale = (float) (1.0 + 0.25 * Math.sin(Math.PI * e) - 0.2 * e);
        return new float[] {bezier(x0, cx, x1, e), bezier(y0, cy, y1, e), scale};
    }

    /** The coin turns edge-on and back as it flies: its apparent width 0.15..1 of the sprite, {@code spins} half turns. */
    public static float coinWidth(double t, double spins) {
        return (float) (0.15 + 0.85 * Math.abs(Math.cos(Math.PI * spins * t)));
    }

    /**
     * Top-left of the brazier socket for a GUI size, or {@code null} when there is no room: right of the hotbar
     * ({@link #SOCKET_CLEAR}), level with it, kept clear of the offhand slot, the attack indicator and the
     * Waywheel belt on the other side.
     */
    public static int[] socket(int guiWidth, int guiHeight) {
        int x = guiWidth / 2 + 91 + SOCKET_CLEAR;
        if (x + SOCKET + 2 > guiWidth) {
            return null;
        }
        return new int[] {x, guiHeight - SOCKET - 2};
    }

    /** What the socket counts: the real unspent points less the coins still in the air (each lands as one). */
    public static int shownPoints(int real, int coinsInFlight) {
        return Math.max(0, real - Math.max(0, coinsInFlight));
    }

    public static String pointsLabel(int points) {
        return points > COUNT_CAP ? COUNT_CAP + "+" : Integer.toString(points);
    }

    /** How the brazier glows: 0 at no points, up to 1 from 5 points, never fully dark while any is unspent. */
    public static float glow(int points) {
        if (points <= 0) {
            return 0f;
        }
        return Math.min(1f, 0.45f + 0.11f * points);
    }

    /** Dots for a rested pool that paid {@code reachPaid} (a fraction of the bar): 0 for none, 1..8 otherwise. */
    public static int pourDots(float reachPaid) {
        if (!(reachPaid > 0f)) {
            return 0;
        }
        return Math.max(1, Math.min(MAX_POUR_PER_PAY, Math.round(reachPaid / POUR_REACH_PER_DOT + 0.499f)));
    }

    /**
     * Dots for the change of one frame of the held skill's line: the pool paid only when the same skill at the same
     * level gained XP ({@code value} rose) while the pool's reach shrank. A death or a respec that empties the pool
     * moves the bar down, and a new skill, a level turning over or a first look only remember the numbers.
     */
    public static int pourFor(boolean sameBar, float prevReach, float reach, double prevValue, double value) {
        if (!sameBar || !(prevReach >= 0f) || !(reach < prevReach - 0.0005f) || !(value > prevValue)) {
            return 0;
        }
        return pourDots(prevReach - reach);
    }

    /**
     * One flying thing: launched at {@code launch}, from ({@code x}, {@code y}) on screen, carrying {@code tag}
     * and {@code count}.
     */
    public record Flight<T>(long launch, float x, float y, T tag, int count) {
        public long lands() {
            return launch + BONUS_FLIGHT_MS;
        }
    }

    /** The bonus icons in the air: bounded by {@link #MAX_BONUS}, staggered, and pruned when they land. */
    public static final class Flights<T> {
        private final List<Flight<T>> live = new ArrayList<>();
        private long lastLaunch;

        public List<Flight<T>> live() {
            return live;
        }

        /** Adds a flight; false when the sky is full. Launches never closer together than {@link #BONUS_STAGGER_MS}. */
        public boolean add(float x, float y, T tag, int count, long now) {
            if (live.size() >= MAX_BONUS || count <= 0) {
                return false;
            }
            long launch = Math.max(now, lastLaunch + BONUS_STAGGER_MS);
            lastLaunch = launch;
            live.add(new Flight<>(launch, x, y, tag, count));
            return true;
        }

        /** Removes and returns the ones that have landed by {@code now}. */
        public List<Flight<T>> land(long now) {
            List<Flight<T>> done = null;
            for (int i = live.size() - 1; i >= 0; i--) {
                Flight<T> f = live.get(i);
                if (now >= f.lands()) {
                    if (done == null) {
                        done = new ArrayList<>();
                    }
                    done.add(0, live.remove(i));
                }
            }
            return done == null ? List.of() : done;
        }

        public void clear() {
            live.clear();
            lastLaunch = 0;
        }
    }
}
