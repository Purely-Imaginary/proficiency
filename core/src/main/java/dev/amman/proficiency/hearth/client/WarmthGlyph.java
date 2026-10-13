package dev.amman.proficiency.hearth.client;

/**
 * The HUD's hearth glyph, without any Minecraft in it (docs/HEARTH-SPEC.md, "What the player sees":
 * a glyph next to the survival badge that fills with Warmth, embers to a full flame, a soft crackle
 * and glow at 100%). The font has nine frames, U+E010 (cold embers) to U+E018 (the gilded full
 * flame); the art lane drew them all 7 px wide, so the line never shifts while Warmth fills.
 *
 * <p>The server sends Warmth with the rate it is changing at, on a change of state only, so the
 * client moves it on by itself between packets ({@link #at}).
 */
public final class WarmthGlyph {

    /** First Warmth frame: cold embers. */
    public static final char FIRST = '';
    /** The full, gilded flame, shown only at 100%. */
    public static final int FULL_FRAME = 8;
    public static final char SIEGE = '';
    public static final char TALES = '';
    public static final char PROVISIONED = '';
    public static final char LOCK = '';
    public static final char BELL = '';
    public static final char HOUSE = '';
    /** Every glyph's advance in GUI pixels (7 drawn plus 1). */
    public static final int WIDTH = 8;
    /** The glow at 100%, as the streak badge's flare. */
    public static final long CUE_MS = 900;

    private WarmthGlyph() {
    }

    /** Frame 0..8 for a Warmth of 0..1: eight steps to the full flame, the gilded one at 100%. */
    public static int frame(double warmth) {
        if (!(warmth > 0)) {
            return 0;
        }
        if (isFull(warmth)) {
            return FULL_FRAME;
        }
        return Math.max(0, Math.min(FULL_FRAME - 1, (int) Math.floor(warmth * FULL_FRAME)));
    }

    /** The glyph for a Warmth. */
    public static String glyph(double warmth) {
        return String.valueOf((char) (FIRST + frame(warmth)));
    }

    /** 100%, with the float slop of a sum of per-second steps. */
    public static boolean isFull(double warmth) {
        return warmth >= 0.999;
    }

    /**
     * Warmth now: the packet's value moved on by its rate since it arrived, clamped to 0..1.
     * {@code elapsedMs} below 0 (a clock step) counts as 0.
     */
    public static double at(double warmth, double perSecond, long elapsedMs) {
        double value = warmth + (Double.isFinite(perSecond) ? perSecond : 0) * Math.max(0, elapsedMs) / 1000.0;
        if (!Double.isFinite(value)) {
            return 0;
        }
        return Math.max(0, Math.min(1, value));
    }

    /** True when Warmth reached 100% between two looks: the moment of the crackle and glow. */
    public static boolean filled(double before, double after) {
        return !isFull(before) && isFull(after);
    }

    /** 1 at the cue's start falling to 0, squared, like the streak badge's flare. */
    public static float glow(long startedAt, long now) {
        long t = now - startedAt;
        if (t < 0 || t >= CUE_MS) {
            return 0f;
        }
        float left = 1f - t / (float) CUE_MS;
        return left * left;
    }

    /**
     * How much of Provisioned is left, 0..1: the badge drains linearly from {@code from} to
     * {@code until} (game ticks), the same line {@code WarmthMath.provisioned} fades the boon along;
     * 0 with no strength or outside the window.
     */
    public static double provisionedLeft(double strength, long from, long until, long now) {
        if (!(strength > 0) || until <= from || now >= until) {
            return 0;
        }
        if (now <= from) {
            return 1;
        }
        return (until - now) / (double) (until - from);
    }

    /**
     * The Tales count beside the scroll: the number up to 999, then thousands with one decimal ("1.2k" ..
     * "9.9k"), then whole thousands ("10k"). Every step cuts down, never rounds up, so 9,999 reads "9.9k"
     * and the next one is "10k" (rounding showed "10.0k" first and then "10k").
     */
    public static String talesCount(int tales) {
        if (tales < 1000) {
            return Integer.toString(Math.max(0, tales));
        }
        if (tales < 10_000) {
            return (tales / 1000) + "." + (tales / 100 % 10) + "k";
        }
        return (tales / 1000) + "k";
    }

    /**
     * The room the skill line keeps for the glyph group, gap included, eased with the group's fades (each
     * 0..1, already smoothed): the line slides aside while the glyph fades in and closes up while it fades
     * out, instead of jumping by the whole width on the first frame of the fade. The group being at home
     * ({@code homeW}) or away ({@code awayW}) takes the larger of the two while they cross-fade.
     */
    public static int lineRoom(int gap, int homeW, float homeEase, int awayW, float awayEase) {
        float home = homeW > 0 ? (gap + homeW) * clamp01(homeEase) : 0f;
        float away = awayW > 0 ? (gap + awayW) * clamp01(awayEase) : 0f;
        return Math.round(Math.max(home, away));
    }

    private static float clamp01(float value) {
        return value > 0f ? Math.min(1f, value) : 0f;
    }
}
