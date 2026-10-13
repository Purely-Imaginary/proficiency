package dev.amman.proficiency.hearth.client;

import dev.amman.proficiency.client.SkillPalette;
import dev.amman.proficiency.hearth.HearthPool;
import dev.amman.proficiency.hearth.charge.PoolColors;

/**
 * The Hearth's colours. A pool's colour is the charge lane's {@link PoolColors} (the category that
 * feeds it, as its icon; Fortified a stone slate so the two Construction pools differ in a stacked
 * ledger bar), so the chat ledger, the panel, its keepers tab and the tracker all agree.
 */
public final class HearthPalette {

    /** The ember orange of the Hearth family (the stage emblems' rim, the Warmth glyph). */
    public static final int EMBER = 0xFFE89A3C;
    /** Tier VI and the gilded flame. */
    public static final int GOLD = 0xFFFFD24A;
    /** Siege red, a step warmer than the Combat accent so it reads on the dark panel. */
    public static final int SIEGE = 0xFFE0584B;
    /** A lock and what it waits on. */
    public static final int LOCKED = 0xFFB0A48C;

    private HearthPalette() {
    }

    /** One colour per pool, by {@link HearthPool} ordinal; grey for a pool this build does not know. */
    public static int pool(int ordinal) {
        HearthPool pool = HearthPool.byIndex(ordinal);
        if (pool == null) {
            return SkillPalette.TEXT_DIM;
        }
        return PoolColors.argb(pool);
    }

    /** The tier numeral's colour: gold at the top tier, the pool's colour below, dim at 0. */
    public static int tier(int ordinal, int tier, int maxTier) {
        if (tier <= 0) {
            return SkillPalette.TEXT_DIM;
        }
        return tier >= maxTier ? GOLD : pool(ordinal);
    }

    /** Half way from a colour to white: a dot's head reads on both the world and the bar. */
    public static int brighten(int argb) {
        int r = (((argb >> 16) & 0xFF) + 255) / 2;
        int g = (((argb >> 8) & 0xFF) + 255) / 2;
        int b = ((argb & 0xFF) + 255) / 2;
        return (argb & 0xFF000000) | (r << 16) | (g << 8) | b;
    }

    /** {@code rgb} (alpha ignored) with alpha {@code alpha} 0..255. */
    public static int withAlpha(int rgb, int alpha) {
        return (Math.max(0, Math.min(255, alpha)) << 24) | (rgb & 0x00FFFFFF);
    }

    /** Linear blend of two colours, alpha from the first. */
    public static int mix(int from, int to, float t) {
        float k = Math.max(0f, Math.min(1f, t));
        int r = Math.round(((from >> 16) & 0xFF) * (1 - k) + ((to >> 16) & 0xFF) * k);
        int g = Math.round(((from >> 8) & 0xFF) * (1 - k) + ((to >> 8) & 0xFF) * k);
        int b = Math.round((from & 0xFF) * (1 - k) + (to & 0xFF) * k);
        return (from & 0xFF000000) | (r << 16) | (g << 8) | b;
    }
}
