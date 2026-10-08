package dev.amman.proficiency.client;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;

/**
 * The survival-streak badge, the two stacked chevrons of glyph U+E000. It fills from the bottom up
 * as the next +1% gets closer, flares when the bonus passes +10, +25 and +50%, and breaks in two
 * when a death takes the streak. State is static and tiny; {@link SkillHud} and
 * {@link DeathRecapHud} draw with it.
 *
 * <p>The glyph is a 7 by 8 bitmap: one chevron in rows 0 to 2, one in rows 4 to 6. The fill is
 * the glyph drawn twice, the second time clipped to its lower part with a scissor, so there is no
 * texture of its own and no per-frame allocation.
 */
public final class StreakBadge {

    public static final String GLYPH = "";
    /** Width of the glyph in GUI pixels, the 7 of the bitmap plus the 1 of spacing. */
    public static final int WIDTH = 8;
    private static final int ROWS = 7;

    public static final int GOLD = 0xFFAA00;
    private static final int DIM_GOLD = 0x6E5210;
    private static final int[] FLARE_AT = {10, 25, 50};
    public static final long FLARE_MS = 900;
    public static final long BREAK_MS = 800;

    private static int lastPercent = -1;
    private static long flareStart = Long.MIN_VALUE / 2;
    private static long holdStart = Long.MIN_VALUE / 2;
    private static long breakStart = Long.MIN_VALUE / 2;
    private static int breakPercent;
    /** The badge stays whole this long after the respawn, so you see it before it goes. */
    public static final long BREAK_DELAY_MS = 450;

    private StreakBadge() {
    }

    /**
     * True when the bonus stepped over one of the flare marks. A jump of more than a few percent or
     * one from zero is a respawn or a dimension change handing the client an empty sheet for a
     * moment, not a streak growing, so it does not count.
     */
    public static boolean crossesFlare(int from, int to) {
        if (from <= 0 || to <= from || to - from > 3) {
            return false;
        }
        for (int mark : FLARE_AT) {
            if (from < mark && to >= mark) {
                return true;
            }
        }
        return false;
    }

    /** Called every frame with the current bonus in whole percent. */
    public static void observe(int percent, long now) {
        if (lastPercent >= 0 && crossesFlare(lastPercent, percent)) {
            flareStart = now;
        }
        lastPercent = percent;
    }

    public static void reset() {
        lastPercent = -1;
        flareStart = Long.MIN_VALUE / 2;
        holdStart = Long.MIN_VALUE / 2;
        breakStart = Long.MIN_VALUE / 2;
    }

    /** A death took the streak: the badge breaks apart at {@code percent}, where it stood. */
    public static void startBreak(int percent, long now) {
        holdStart = now;
        breakStart = now + BREAK_DELAY_MS;
        breakPercent = percent;
    }

    /**
     * True from the respawn until the pieces have faded: the HUD draws the dead streak's badge
     * (whole at first, then breaking) in place of the real one, which is already empty.
     */
    public static boolean holding(long now) {
        return now >= holdStart && now < breakStart + BREAK_MS;
    }

    /** 0 to 1 through the break; negative while the badge is still whole. */
    public static float breakT(long now) {
        return (now - breakStart) / (float) BREAK_MS;
    }

    public static int breakPercent() {
        return breakPercent;
    }

    public static boolean flaring(long now) {
        return now >= flareStart && now - flareStart < FLARE_MS;
    }

    /** 1 at the flare's start falling to 0. */
    public static float flare(long now) {
        if (!flaring(now)) {
            return 0f;
        }
        float t = (now - flareStart) / (float) FLARE_MS;
        return (1f - t) * (1f - t);
    }

    /** Where the fill edge sits, from the bottom, in rows of the glyph: 0 to 7. */
    public static int fillRows(float fraction) {
        return Math.max(0, Math.min(ROWS, Math.round(fraction * ROWS)));
    }

    /** The held badge of a streak that just died: whole, then broken, as {@link #breakT} says. */
    public static void drawDead(GuiGraphics graphics, Font font, int x, int y, int alpha, long now) {
        float t = breakT(now);
        if (t < 0f) {
            draw(graphics, font, x, y, 1f, alpha, now);
        } else {
            drawBroken(graphics, font, x, y, t, alpha);
        }
    }

    /**
     * Draws the badge at {@code (x, y)}, the top left of the text cell. {@code fraction} is the
     * fill (0 to 1), {@code alpha} 0 to 255.
     */
    public static void draw(GuiGraphics graphics, Font font, int x, int y, float fraction, int alpha,
            long now) {
        if (alpha <= 8) {
            return;
        }
        float flare = flare(now);
        int a = alpha << 24;
        graphics.drawString(font, GLYPH, x, y, a | DIM_GOLD, true);
        int rows = fillRows(fraction);
        if (rows > 0) {
            int colour = lerp(GOLD, 0xFFFFFF, flare);
            graphics.enableScissor(x - 1, y + ROWS - rows, x + WIDTH, y + ROWS + 1);
            graphics.drawString(font, GLYPH, x, y, a | colour, true);
            graphics.disableScissor();
        }
        if (flare > 0f) {
            int ringAlpha = (int) (alpha * flare);
            if (ringAlpha > 8) {
                float radius = 4 + (1f - flare) * 9;
                HudMath.ring(graphics, x + 3, y + 3, radius, radius, (ringAlpha << 24) | 0xFFE9A0, 1, 5, 64);
            }
        }
    }

    /**
     * The two chevrons falling apart, {@code t} 0 to 1 since the break began. The upper one drifts
     * left and drops, the lower one drifts right and drops faster, both fade.
     */
    public static void drawBroken(GuiGraphics graphics, Font font, int x, int y, float t, int alpha) {
        t = Math.max(0f, Math.min(1f, t));
        int a = (int) (alpha * (1f - t * t));
        if (a <= 8) {
            return;
        }
        int colour = (a << 24) | GOLD;
        int upperDx = -Math.round(3 * t);
        int upperDy = Math.round(9 * t * t + 2 * t);
        int lowerDx = Math.round(4 * t);
        int lowerDy = Math.round(17 * t * t + 3 * t);
        graphics.enableScissor(x - 2 + upperDx, y + upperDy, x + WIDTH + 1 + upperDx, y + 3 + upperDy);
        graphics.drawString(font, GLYPH, x + upperDx, y + upperDy, colour, true);
        graphics.disableScissor();
        graphics.enableScissor(x - 2 + lowerDx, y + 3 + lowerDy, x + WIDTH + 1 + lowerDx, y + 9 + lowerDy);
        graphics.drawString(font, GLYPH, x + lowerDx, y + lowerDy, colour, true);
        graphics.disableScissor();
    }

    static int lerp(int from, int to, float t) {
        int r = Math.round(((from >> 16) & 0xFF) * (1 - t) + ((to >> 16) & 0xFF) * t);
        int g = Math.round(((from >> 8) & 0xFF) * (1 - t) + ((to >> 8) & 0xFF) * t);
        int b = Math.round((from & 0xFF) * (1 - t) + (to & 0xFF) * t);
        return (r << 16) | (g << 8) | b;
    }
}
