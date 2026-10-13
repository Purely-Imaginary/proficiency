package dev.amman.proficiency.client;

/**
 * The right edge of the HUD, shared by the mod's five-second panels (the death recap, the Hearth's Tales
 * panel) and the Hearth tracker. A panel claims the rows it covers every frame it draws ({@link #claim}),
 * with its own fade as the claim's strength; the tracker asks where it may go ({@link #place}). It moves
 * only for a claim it would really overlap: under the panel, or over it when that is the shorter move and
 * there is room, sliding there and back ({@link Follower}); where neither fits it fades out by the panel's
 * fade instead. A new right-edge panel only claims its rows here: the tracker needs no edit (code review
 * of polish-forge, 2026-10-10: the tracker had hidden for every Tales panel, even with nothing in the way,
 * and so missed the pay-in's own tier-up).
 *
 * <p>Pure: the clock is the caller's, one clock for every claim and every look (the client passes
 * {@code Util.getMillis()}). Client thread only.
 */
public final class RightEdge {

    /** The panels' top as a share of the screen height: below the toasts and a minimap in the top right corner. */
    public static final float PANEL_TOP = 0.30f;
    /** Room kept between a panel and the tracker that stepped clear of it. */
    public static final int GAP = 4;
    /** A claim not renewed for this long is gone: its panel stopped drawing (the GUI hidden, a world left). */
    public static final long STALE_MS = 250;
    /** Below this strength a claim moves and hides nothing (a panel's first and last frames). */
    static final float FAINT = 0.02f;

    public static final int DEATH_RECAP = 0;
    public static final int TALES = 1;
    /** Claim slots; a new right-edge panel takes the next one. */
    static final int SLOTS = 4;

    private static final int[] TOPS = new int[SLOTS];
    private static final int[] BOTTOMS = new int[SLOTS];
    private static final float[] STRENGTHS = new float[SLOTS];
    private static final long[] CLAIMED_AT = new long[SLOTS];
    // The live claims of one look, copied here so a frame allocates nothing.
    private static final int[] LIVE_TOPS = new int[SLOTS];
    private static final int[] LIVE_BOTTOMS = new int[SLOTS];
    private static final float[] LIVE_STRENGTHS = new float[SLOTS];

    private RightEdge() {
    }

    /**
     * Where a right-edge panel starts: {@link #PANEL_TOP} of the screen, or just under a discovery banner whose
     * bottom ({@code bannerBottom}, 0 with none) would otherwise cover it, never above 8.
     */
    public static int panelTop(int guiHeight, int bannerBottom) {
        return Math.max(8, Math.max(Math.round(guiHeight * PANEL_TOP), bannerBottom > 0 ? bannerBottom + GAP : 0));
    }

    /** Panel {@code slot} covers rows {@code top} to {@code bottom} this frame, as strongly as it shows (0..1). */
    public static void claim(int slot, int top, int bottom, float strength, long now) {
        TOPS[slot] = top;
        BOTTOMS[slot] = bottom;
        STRENGTHS[slot] = Math.max(0f, Math.min(1f, strength));
        CLAIMED_AT[slot] = now;
    }

    /** Panel {@code slot} is gone (its five seconds ended, or the player left the world). */
    public static void release(int slot) {
        STRENGTHS[slot] = 0f;
    }

    /** Forgets every claim (tests, a world left). */
    public static void clear() {
        java.util.Arrays.fill(STRENGTHS, 0f);
    }

    /** Where a box goes this frame: its top, and how much of it shows (1 = all; less while it gives way). */
    public record Place(int top, float alpha, boolean squeezed) {
    }

    /**
     * Where a box of {@code height} that wants its top at {@code top} goes this frame, kept between
     * {@code minTop} and {@code maxBottom}, given the live claims (the rule is {@link #place(int, int, int, int,
     * int[], int[], float[], int)}). Allocates nothing but the answer.
     */
    public static Place place(int top, int height, int minTop, int maxBottom, long now) {
        int live = 0;
        for (int slot = 0; slot < SLOTS; slot++) {
            if (STRENGTHS[slot] >= FAINT && now >= CLAIMED_AT[slot] && now - CLAIMED_AT[slot] <= STALE_MS) {
                LIVE_TOPS[live] = TOPS[slot];
                LIVE_BOTTOMS[live] = BOTTOMS[slot];
                LIVE_STRENGTHS[live] = STRENGTHS[slot];
                live++;
            }
        }
        return place(top, height, minTop, maxBottom, LIVE_TOPS, LIVE_BOTTOMS, LIVE_STRENGTHS, live);
    }

    /**
     * The rule, pure, over the first {@code count} claims. The box takes the claims it overlaps (with
     * {@link #GAP} round it), and any claim that band then touches, as one band. With none it stays. Otherwise it
     * goes under the band, or over it when that move is shorter, if it fits between {@code minTop} and
     * {@code maxBottom}: whole, at once (the caller slides it, {@link Follower}). If neither fits it stays and
     * fades by the strongest claim's strength ({@code squeezed}).
     */
    public static Place place(int top, int height, int minTop, int maxBottom, int[] tops, int[] bottoms,
            float[] strengths, int count) {
        int bandTop = Integer.MAX_VALUE;
        int bandBottom = Integer.MIN_VALUE;
        float strength = 0f;
        int taken = 0;
        boolean grew = true;
        while (grew) {
            grew = false;
            for (int i = 0; i < count; i++) {
                if ((taken & (1 << i)) != 0 || strengths[i] < FAINT || bottoms[i] <= tops[i]) {
                    continue;
                }
                boolean hitsBox = tops[i] < top + height + GAP && bottoms[i] + GAP > top;
                boolean hitsBand = bandTop != Integer.MAX_VALUE && tops[i] < bandBottom + GAP && bottoms[i] + GAP > bandTop;
                if (hitsBox || hitsBand) {
                    taken |= 1 << i;
                    grew = true;
                    bandTop = Math.min(bandTop, tops[i]);
                    bandBottom = Math.max(bandBottom, bottoms[i]);
                    strength = Math.max(strength, strengths[i]);
                }
            }
        }
        if (bandTop == Integer.MAX_VALUE) {
            return new Place(top, 1f, false);
        }
        int under = bandBottom + GAP;
        int over = bandTop - GAP - height;
        boolean underFits = under + height <= maxBottom;
        boolean overFits = over >= minTop;
        if (underFits && (!overFits || under - top <= top - over)) {
            return new Place(under, 1f, false);
        }
        if (overFits) {
            return new Place(over, 1f, false);
        }
        return new Place(top, 1f - strength, true);
    }

    /**
     * A drawn top that slides to its target instead of jumping (about 60 ms to two thirds of the way, as the
     * skill line's lift), and snaps after a pause in drawing (the box was hidden).
     */
    public static final class Follower {

        private double shown = Double.NaN;
        private long shownAt;

        public int follow(int target, long now) {
            if (Double.isNaN(shown) || now - shownAt > 500 || now < shownAt) {
                shown = target;
            } else {
                shown = LineLift.ease(shown, target, now - shownAt);
            }
            shownAt = now;
            return (int) Math.round(shown);
        }

        public void reset() {
            shown = Double.NaN;
        }
    }
}
