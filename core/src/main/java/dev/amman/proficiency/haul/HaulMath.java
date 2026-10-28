package dev.amman.proficiency.haul;

import dev.amman.proficiency.client.XpGainDots;

import java.util.List;

/**
 * The Haul card's numbers without Minecraft: a skill's bar position in level units, and the card's timeline. The
 * rows fill one after another, each by a few bursts of {@link XpGainDots} (the dots that fly into the HUD bar),
 * the next row starting while the last dots of the one before are still in the air; the totals count up under
 * them; the rating stamp lands last. All in milliseconds from the card opening, closed-form, so it runs the same at
 * any frame rate and a key press can jump straight to {@link #end}.
 */
public final class HaulMath {

    /** The slab lands before the first row moves. */
    public static final long INTRO_MS = 260;
    /** Between one row's last launch and the next row's first. */
    public static final long ROW_STEP_MS = 200;
    /** Between two bursts of one row. */
    public static final long BURST_SPACING_MS = 120;
    /** Most bursts a row fires. A row that crosses many levels still fills in at most this many. */
    public static final int MAX_BURSTS = 5;
    /** From a row's last launch until its bar has settled: a dot's flight, a full stagger and the fill. */
    public static final long SETTLE_MS = XpGainDots.FLIGHT_MS + 60 + 7 * XpGainDots.STAGGER_MS + 140;
    /** The totals count up over this long, starting with the last row. */
    public static final long TOTALS_MS = 1100;
    /** After everything settled, the stamp comes down. */
    public static final long STAMP_DELAY_MS = 160;
    /** The stamp's fall and bounce. */
    public static final long STAMP_MS = 420;
    /** The stamp's scale as it starts to fall. */
    public static final float STAMP_FROM = 2.6f;
    /** Gap between two count-up ticks, so a long count is a run of clicks, not a buzz. */
    public static final long TICK_GAP_MS = 65;

    private HaulMath() {
    }

    // ---- Units -----------------------------------------------------------------------------------

    /**
     * A skill's bar position in level units: below 100, the level plus its progress (never a whole next level);
     * at 100, 100 plus the stars plus the bar toward the next star, so a star reads like a level crossing. A
     * skill holding every star the cap allows sits at {@code 100 + maxStars}.
     */
    public static double units(int level, float progress, int stars, int maxStars, float starProgress) {
        if (level < 100) {
            return Math.max(0, level) + clampBar(progress);
        }
        int cap = Math.max(0, maxStars);
        int held = Math.max(0, Math.min(cap, stars));
        return 100 + held + (held >= cap ? 0 : clampBar(starProgress));
    }

    private static double clampBar(float bar) {
        return Float.isFinite(bar) ? Math.max(0.0, Math.min(0.9999, bar)) : 0.0;
    }

    /** Whole levels (and stars) the bar crosses between {@code from} and {@code to}. */
    public static int crossings(double from, double to) {
        if (!(to > from)) {
            return 0;
        }
        return Math.max(0, (int) Math.floor(to) - (int) Math.floor(from));
    }

    /** The level label for a bar position: 37.6 reads 37; 102.3 reads 100 (the stars are drawn on their own). */
    public static int levelAt(double units) {
        return (int) Math.min(100, Math.floor(Math.max(0, units)));
    }

    /** The stars a bar position past 100 holds. */
    public static int starsAt(double units) {
        return units < 100 ? 0 : (int) Math.floor(units - 100);
    }

    // ---- Timeline --------------------------------------------------------------------------------

    /** How many bursts a row fires: one, plus two per level it crosses, at most {@link #MAX_BURSTS}. */
    public static int bursts(double from, double to) {
        return Math.max(1, Math.min(MAX_BURSTS, 1 + 2 * crossings(from, to)));
    }

    /**
     * The card's timeline for rows filling from {@code from[i]} to {@code to[i]} (level units). {@code totals} is
     * false when there is nothing to count up (the stamp then follows the rows).
     */
    public static Timeline timeline(double[] from, double[] to, boolean totals) {
        int n = Math.min(from.length, to.length);
        long[] start = new long[n];
        int[] bursts = new int[n];
        long t = INTRO_MS;
        long settled = INTRO_MS;
        for (int i = 0; i < n; i++) {
            start[i] = t;
            bursts[i] = bursts(from[i], to[i]);
            long lastLaunch = t + (bursts[i] - 1) * BURST_SPACING_MS;
            settled = Math.max(settled, lastLaunch + SETTLE_MS);
            t = lastLaunch + ROW_STEP_MS;
        }
        long totalsAt = n == 0 ? INTRO_MS : start[n - 1] + 120;
        long totalsEnd = totals ? totalsAt + TOTALS_MS : totalsAt;
        long stampAt = Math.max(settled, totalsEnd) + STAMP_DELAY_MS;
        return new Timeline(start, bursts, totalsAt, stampAt);
    }

    /**
     * @param start    each row's first launch
     * @param bursts   each row's burst count
     * @param totalsAt when the totals start to count
     * @param stampAt  when the stamp starts to fall
     */
    public record Timeline(long[] start, int[] bursts, long totalsAt, long stampAt) {

        /** Everything has landed: the stamp is down. */
        public long end() {
            return stampAt + STAMP_MS;
        }

        /** When burst {@code k} of row {@code i} leaves. */
        public long launch(int i, int k) {
            return start[i] + k * BURST_SPACING_MS;
        }

        /** The bar position burst {@code k} of row {@code i} carries the bar to (the last one reaches {@code to}). */
        public static double burstTo(double from, double to, int k, int count) {
            if (k >= count - 1) {
                return to;
            }
            return from + (to - from) * (k + 1) / count;
        }

        /** 0..1 of the totals counted at {@code t}: a quick start that eases into the final number. */
        public double totals(long t) {
            double x = (t - totalsAt) / (double) TOTALS_MS;
            if (x <= 0) {
                return 0;
            }
            if (x >= 1) {
                return 1;
            }
            return 1 - Math.pow(1 - x, 3);
        }

        /** The row a click on the card's rows should treat as current at {@code t}, -1 before the first. */
        public int activeRow(long t) {
            int row = -1;
            for (int i = 0; i < start.length; i++) {
                if (t >= start[i]) {
                    row = i;
                }
            }
            return row;
        }
    }

    // ---- The stamp -------------------------------------------------------------------------------

    /**
     * The stamp's scale {@code ms} after it starts to fall: {@link #STAMP_FROM} down to 1 by 55% of the time
     * (accelerating, a stamp hitting paper), a small 0.92 squash on impact, then back to 1. 0 before it starts.
     */
    public static float stampScale(long ms) {
        if (ms < 0) {
            return 0f;
        }
        double x = ms / (double) STAMP_MS;
        if (x >= 1) {
            return 1f;
        }
        double hit = 0.55;
        if (x < hit) {
            double k = x / hit;
            return (float) (STAMP_FROM + (1 - STAMP_FROM) * k * k);
        }
        double k = (x - hit) / (1 - hit);
        return (float) (1 - 0.08 * Math.sin(Math.PI * k) * (1 - k));
    }

    /** The stamp's opacity: fades in over its first 30%. */
    public static float stampAlpha(long ms) {
        if (ms < 0) {
            return 0f;
        }
        return (float) Math.min(1.0, ms / (STAMP_MS * 0.3));
    }

    /** True from the moment the stamp hits (the thud, the burst). */
    public static boolean stampHit(long ms) {
        return ms >= Math.round(STAMP_MS * 0.55);
    }

    // ---- Layout ----------------------------------------------------------------------------------

    /**
     * How many rows fit in {@code room} px at {@code rowHeight} each, leaving a line for "+N more" when some are
     * left out. At least one when there are rows, at most {@link HaulSummary#MAX_ROWS}.
     */
    public static int rowsThatFit(int rows, int room, int rowHeight, int moreLine) {
        if (rows <= 0) {
            return 0;
        }
        int cap = Math.min(rows, HaulSummary.MAX_ROWS);
        if (rowHeight <= 0) {
            return 1;
        }
        if (cap * rowHeight <= room && cap == rows) {
            return cap;
        }
        int fit = (room - moreLine) / rowHeight;
        return Math.max(1, Math.min(cap, fit));
    }

    /** The XP figure shown on a row whose bar has filled {@code fraction} of its way. */
    public static int countUp(double total, double fraction) {
        double f = Math.max(0, Math.min(1, fraction));
        return (int) Math.round(Math.max(0, total) * f);
    }

    /** The totals' order on the card, as keys of {@code proficiency.haul.total.*}; only the non-zero ones show. */
    public static final List<String> TOTALS = List.of("xp", "levels", "stars", "points", "tempo", "found",
            "synergies", "charge");
}
