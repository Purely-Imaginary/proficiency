package dev.amman.proficiency.client;

import net.minecraft.client.gui.GuiGraphics;

/**
 * Draws the dots of an {@link XpGainDots} in flight from a start point to an end point. Each dot
 * flies a quadratic curve with a two-pixel trail; the end point glows when one lands. The skill HUD
 * launches from just past the right edge of the screen into its bar, the Hearth tracker from the skill
 * bar into a tracker row, both through this one drawing. A few {@code fill} calls per dot and none
 * at all when nothing is flying.
 *
 * @param startX      where every dot starts, x
 * @param startY      where the middle lane starts, y; each dot's lane moves it by {@code spread}
 * @param spread      start height spread across lanes (lane -1..1)
 * @param lift        how far the curve's control point sits above the higher end
 * @param liftSpread  the lift's spread across lanes
 */
public record DotFlight(float startX, float startY, float spread, float lift, float liftSpread) {

    /** The skill HUD's flight: from just past the right edge, a wide fan of lanes, into the bar. */
    public static DotFlight fromRightEdge(int guiWidth, int edgeY) {
        return new DotFlight(guiWidth + 3, edgeY - 34, 26, 22, 12);
    }

    /**
     * Draws {@code dots} flying to ({@code edgeX}, {@code edgeY}): head in {@code head}, trail in
     * {@code accent}, faded by {@code alpha} (0..255).
     */
    public void draw(GuiGraphics graphics, XpGainDots dots, int edgeX, int edgeY, int accent, int head, int alpha,
            long now) {
        float flash = dots.flash(now);
        if (flash > 0) {
            int a = (int) (alpha * flash);
            graphics.fill(edgeX - 1, edgeY - 2, edgeX + 1, edgeY + 2, (a << 24) | 0xFFFFFF);
        }
        if (dots.dots().isEmpty()) {
            return;
        }
        int headColour = (alpha << 24) | (head & 0x00FFFFFF);
        for (XpGainDots.Dot dot : dots.dots()) {
            double t = XpGainDots.progress(dot, now);
            if (t < 0) {
                continue;
            }
            float fromY = startY + dot.lane() * spread;
            // Control point above the straight line, so the dot arcs over and drops in.
            float controlX = startX + (edgeX - startX) * 0.45f;
            float controlY = Math.min(fromY, edgeY) - lift - dot.lane() * liftSpread;
            for (int step = 2; step >= 0; step--) {
                double e = XpGainDots.ease(t - step * 0.045);
                if (e <= 0) {
                    continue;
                }
                double u = 1 - e;
                int x = (int) Math.round(u * u * startX + 2 * u * e * controlX + e * e * edgeX);
                int y = (int) Math.round(u * u * fromY + 2 * u * e * controlY + e * e * edgeY);
                if (step == 0) {
                    graphics.fill(x - 1, y - 1, x + 1, y + 1, headColour);
                } else {
                    int a = (int) (alpha * (step == 1 ? 0.55f : 0.25f));
                    graphics.fill(x, y, x + 1, y + 1, (a << 24) | (accent & 0x00FFFFFF));
                }
            }
        }
    }
}
