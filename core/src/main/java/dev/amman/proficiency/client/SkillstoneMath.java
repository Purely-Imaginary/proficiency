package dev.amman.proficiency.client;

/**
 * The numbers of the Skillstone UI ({@code ui.style = modern}): the slab's size at each screen size,
 * the dial radius, the 1.05 s opening, the rune heat, the hover slide and the "overload" shakes.
 * Pure functions of elapsed milliseconds, so every motion is frame-rate independent.
 *
 * <p>The opening (storyboard-intro, approved 2026-10-26): the slab rises from below (0-220 ms),
 * lands with a 3 px squash (220-320), sits cold, a flint strike lights the well at 450, the runes
 * ignite clockwise to full heat (450-760), cool to their real progress (760-900) while the
 * category slate slides in (760-1050).
 */
public final class SkillstoneMath {

    public static final long INTRO_MS = 1050;
    public static final long RISE_MS = 220;
    public static final long LAND_MS = 320;
    public static final long STRIKE_MS = 450;
    public static final long ROAR_MS = 600;
    public static final long IGNITED_MS = 760;
    public static final long COOLED_MS = 900;
    /** A second opening within this long skips straight to the settled slab. */
    public static final long SKIP_WINDOW_MS = 30_000;
    public static final long HOVER_MS = 120;
    /** Pixels a hovered rune slides out of the dial. */
    public static final int HOVER_OUT = 5;

    /** The full slab, the mockup's size at GUI scale 2 on 1920x1080. */
    public static final int FULL_W = 620;
    public static final int FULL_H = 360;
    public static final int RIM = 7;
    /** Dial radii the textures are baked for, largest first (tools/gen_skillstone.py). */
    public static final int[] DIALS = {149, 91, 61};

    private SkillstoneMath() {
    }

    public static float clamp01(float v) {
        return v < 0f ? 0f : v > 1f ? 1f : v;
    }

    static float easeOut(float t) {
        t = clamp01(t);
        return 1f - (1f - t) * (1f - t) * (1f - t);
    }

    /** The slab's width for a screen {@code w} logical px wide: the mockup's 620, or all but 4 px a side. */
    public static int slabWidth(int screenW) {
        return Math.max(160, Math.min(FULL_W, screenW - 8));
    }

    public static int slabHeight(int screenH) {
        return Math.max(120, Math.min(FULL_H, screenH - 8));
    }

    /** Whether the slab has the room of the full mockup (the compact layout trims gaps). */
    public static boolean compact(int slabH) {
        return slabH < 300;
    }

    /** The dial radius that fits a slab {@code slabH} tall: the biggest baked one not above the room. */
    public static int dialRadius(int slabH) {
        int inner = slabH - 2 * RIM;
        int room = inner / 2 - (compact(slabH) ? 18 : 20);
        for (int r : DIALS) {
            if (r <= room) {
                return r;
            }
        }
        return DIALS[DIALS.length - 1];
    }

    /** The fire well's radius on a dial of radius {@code dial}. */
    public static int pitRadius(int dial) {
        return (int) (dial * 0.44);
    }

    /** How far a category's rune glows, 0..1, from the average level of its skills. Full heat at 50. */
    public static float heat(double averageLevel) {
        return clamp01((float) (averageLevel / 50.0));
    }

    // ---- Opening -------------------------------------------------------------------------------

    /** 0 below the screen, 1 landed. */
    public static float rise(long t) {
        return easeOut(t / (float) RISE_MS);
    }

    /** Pixels the landed slab sinks: a 3 px squash between landing and settling. */
    public static int squash(long t) {
        if (t < RISE_MS || t >= LAND_MS) {
            return 0;
        }
        float f = (t - RISE_MS) / (float) (LAND_MS - RISE_MS);
        return Math.round(3f * (float) Math.sin(Math.PI * f));
    }

    /** Dust kicked up by the landing: 1 at the hit, gone at the strike. */
    public static float dust(long t) {
        if (t < RISE_MS - 40 || t >= STRIKE_MS) {
            return 0f;
        }
        return 1f - clamp01((t - (RISE_MS - 40)) / (float) (STRIKE_MS - RISE_MS + 40));
    }

    /** The fire in the well, 0 cold to 1 roaring: catches at the strike, roars by 600 ms. */
    public static float fire(long t) {
        if (t < STRIKE_MS) {
            return 0f;
        }
        return 0.25f + 0.75f * clamp01((t - STRIKE_MS) / (float) (ROAR_MS - STRIKE_MS));
    }

    /** The white flash of the flint strike, 1 at the strike, gone in 120 ms. */
    public static float strike(long t) {
        if (t < STRIKE_MS || t >= STRIKE_MS + 120) {
            return 0f;
        }
        return 1f - (t - STRIKE_MS) / 120f;
    }

    /** When rune {@code i} of {@code n} catches, clockwise from the top. */
    public static long igniteAt(int i, int n) {
        return STRIKE_MS + (IGNITED_MS - STRIKE_MS - 60) * i / Math.max(1, n - 1);
    }

    /** Rune {@code i}'s glow during the opening: dark, catches, full, then cools to {@code real}. */
    public static float runeHeat(int i, int n, long t, float real) {
        if (t >= INTRO_MS) {
            return real;
        }
        long at = igniteAt(i, n);
        if (t < at) {
            return 0f;
        }
        if (t < IGNITED_MS) {
            return clamp01((t - at) / 60f);
        }
        float cool = clamp01((t - IGNITED_MS) / (float) (COOLED_MS - IGNITED_MS));
        return 1f + (real - 1f) * cool;
    }

    /** The category slate and the texts: slide in after the runes flare. */
    public static float slate(long t) {
        return easeOut((t - IGNITED_MS) / (float) (INTRO_MS - IGNITED_MS));
    }

    /** Hover slide of a rune, 0..1, {@code t} ms after the cursor arrived (Embers' raise). */
    public static float hover(long t) {
        return easeOut(t / (float) HOVER_MS);
    }

    /** Frame of an {@code frames}-frame fire loop at 10 frames a second. */
    public static int fireFrame(long t, int frames) {
        return (int) ((Math.max(0L, t) / 100L) % frames);
    }

    // ---- Overload click feedback -----------------------------------------------------------

    public static final long SHAKE_MS = 260;
    public static final long REFUSE_MS = 320;
    public static final long RISE_SHAKE_MS = 420;

    /** Whole-slab shake after a buy: decaying, two axes out of phase. {@code amp} px at the start. */
    public static float shakeX(long t, float amp) {
        if (t < 0 || t >= SHAKE_MS) {
            return 0f;
        }
        float k = 1f - t / (float) SHAKE_MS;
        return amp * k * k * (float) Math.sin(t * 0.11);
    }

    public static float shakeY(long t, float amp) {
        if (t < 0 || t >= SHAKE_MS) {
            return 0f;
        }
        float k = 1f - t / (float) SHAKE_MS;
        return amp * 0.7f * k * k * (float) Math.cos(t * 0.13);
    }

    /** Refusal: a hard left/right wiggle, square-ish, three swings, {@code amp} px. */
    public static float refuseX(long t, float amp) {
        if (t < 0 || t >= REFUSE_MS) {
            return 0f;
        }
        float k = 1f - t / (float) REFUSE_MS;
        double s = Math.sin(t * Math.PI * 6.0 / REFUSE_MS);
        return amp * k * (float) Math.signum(s) * (float) Math.min(1.0, Math.abs(s) * 2.5);
    }

    /** Unlocking: a shake that grows and climbs, then stops dead. */
    public static float riseShake(long t, float amp) {
        if (t < 0 || t >= RISE_SHAKE_MS) {
            return 0f;
        }
        float k = t / (float) RISE_SHAKE_MS;
        return amp * k * (float) Math.sin(t * (0.08 + 0.12 * k));
    }

    /** The red refusal flash: full at the click, gone with the wiggle. */
    public static float refuseFlash(long t) {
        if (t < 0 || t >= REFUSE_MS) {
            return 0f;
        }
        return 1f - t / (float) REFUSE_MS;
    }

    // ---- Talent tree (stage 2) -------------------------------------------------------------

    /** The tree slab: the mockup's 640 x 410, cut to the screen. */
    public static final int TREE_W = 640;
    public static final int TREE_H = 410;
    /** The Hearth face's slab height (the mockup's 380). */
    public static final int HEARTH_H = 380;
    /** Rows of the talent grid (levels 0, 10, 30, 60, 90, 100) and the columns (0..4). */
    public static final int TREE_ROWS = 6;
    public static final int TREE_COLS = 5;
    /** How long a rank's molten fill takes to pour in, and the "+1 rank" mark to rise. */
    public static final long FILL_MS = 300;
    public static final long RANK_MARK_MS = 900;
    /** How long a refused node keeps its reason next to it. */
    public static final long REASON_MS = 1600;

    public static int treeWidth(int screenW) {
        return Math.max(160, Math.min(TREE_W, screenW - 8));
    }

    public static int treeHeight(int screenH) {
        return Math.max(120, Math.min(TREE_H, screenH - 8));
    }

    /**
     * The step between tree rows: the room split over five gaps, never under {@code min} (the
     * socket, its pips and its name). Below that the tree scrolls.
     */
    public static int treeRowStep(int room, int min) {
        return Math.max(min, room / (TREE_ROWS - 1));
    }

    /** How far the tree can scroll: zero when the rows fit the view. */
    public static int treeScrollMax(int rowStep, int nodeSpan, int viewH) {
        return Math.max(0, (TREE_ROWS - 1) * rowStep + nodeSpan - viewH);
    }

    /**
     * How far a tree row reaches below its centre: a big socket's rank pips, or a small socket's pips and the
     * name label under them (pips at half+3..half+6, the 9 px label at half+8), plus a 2 px margin.
     */
    public static int treeNodeBelow(int smallHalf, int bigHalf) {
        return Math.max(bigHalf + 6, smallHalf + 17) + 2;
    }

    public static int clampScroll(int scroll, int max) {
        return Math.max(0, Math.min(scroll, max));
    }

    /** The molten fill of a new rank: 0 at the click's answer, 1 when the socket holds it. */
    public static float fill(long t) {
        if (t <= 0) {
            return 0f;
        }
        return t >= FILL_MS ? 1f : easeOut(t / (float) FILL_MS);
    }

    /** The "+1 rank" mark: how high it has risen (px) and how opaque it is, for {@code t} ms. */
    public static float markRise(long t) {
        return t <= 0 ? 0f : 14f * easeOut(Math.min(1f, t / (float) RANK_MARK_MS));
    }

    public static float markAlpha(long t) {
        if (t < 0 || t >= RANK_MARK_MS) {
            return 0f;
        }
        float k = t / (float) RANK_MARK_MS;
        return k < 0.6f ? 1f : 1f - (k - 0.6f) / 0.4f;
    }

    /** The molten groove pours from a full parent to its child: lit share of the path at {@code t} ms. */
    public static float pour(long t, long ms) {
        if (t <= 0) {
            return 0f;
        }
        return t >= ms ? 1f : t / (float) ms;
    }

    // ---- One-column Face -----------------------------------------------------------------------

    /** A Face at most this many logical px wide (GUI 4 on 1080p, 960x720 at GUI 3, 1280x720 at GUI 4) goes one column. */
    public static final int FACE_COLUMN_MAX_W = 480;
    /** Width of the Face column's scroll track. */
    public static final int FACE_TRACK_W = 4;

    /** Whether the Face stacks its cards in one scrolling column on a screen {@code screenW} logical px wide. */
    public static boolean faceOneColumn(int screenW) {
        return screenW <= FACE_COLUMN_MAX_W;
    }

    /** The height of a Face card holding {@code textLines} wrapped lines of {@code line} px under its header. */
    public static int faceCardHeight(int textLines, int line) {
        return 5 + line + 1 + Math.max(0, textLines) * line + 4;
    }

    /** How many sockets of {@code size} fit a row {@code avail} px wide with a 4 px gap (at least one). */
    public static int socketsPerRow(int avail, int size, int count) {
        return Math.max(1, Math.min(count, (avail + 4) / (size + 4)));
    }

    public static int maxScroll(int contentH, int viewH) {
        return Math.max(0, contentH - viewH);
    }

    public static int clampScroll(int scroll, int contentH, int viewH) {
        return Math.max(0, Math.min(scroll, maxScroll(contentH, viewH)));
    }

    /** A wheel notch moves the column by this many px (a little over two text lines). */
    public static int wheelStep(double delta, int line) {
        int steps = (int) Math.round(delta);
        if (steps == 0 && delta != 0) {
            steps = delta > 0 ? 1 : -1;
        }
        return -steps * line * 3;
    }

    /** The scroll thumb: {y offset, height} inside a track {@code trackH} tall; null when nothing scrolls. */
    public static int[] scrollThumb(int scroll, int contentH, int viewH, int trackH) {
        int max = maxScroll(contentH, viewH);
        if (max <= 0) {
            return null;
        }
        int h = Math.max(10, Math.min(trackH, trackH * viewH / contentH));
        int y = (trackH - h) * clampScroll(scroll, contentH, viewH) / max;
        return new int[] {y, h};
    }

    /**
     * Word-wraps {@code text} to {@code max} px. {@code width} measures a string. A word wider than the line
     * (or text with no spaces, like Chinese) breaks between characters; spaces inside a run are kept.
     */
    public static java.util.List<String> wrap(String text, java.util.function.ToIntFunction<String> width, int max) {
        java.util.List<String> out = new java.util.ArrayList<>();
        if (text == null || text.isEmpty()) {
            return out;
        }
        StringBuilder cur = new StringBuilder();
        for (String word : text.split(" ", -1)) {
            if (word.isEmpty() && cur.length() == 0) {
                continue;
            }
            String cand = cur.length() == 0 ? word : cur + " " + word;
            if (width.applyAsInt(cand) <= max) {
                cur.setLength(0);
                cur.append(cand);
                continue;
            }
            if (cur.length() > 0) {
                out.add(cur.toString());
                cur.setLength(0);
            }
            if (width.applyAsInt(word) <= max) {
                cur.append(word);
                continue;
            }
            for (int k = 0; k < word.length(); ) {
                int cp = word.codePointAt(k);
                String ch = new String(Character.toChars(cp));
                if (cur.length() > 0 && width.applyAsInt(cur + ch) > max) {
                    out.add(cur.toString());
                    cur.setLength(0);
                }
                cur.append(ch);
                k += Character.charCount(cp);
            }
        }
        if (cur.length() > 0) {
            out.add(cur.toString());
        }
        return out;
    }
}
