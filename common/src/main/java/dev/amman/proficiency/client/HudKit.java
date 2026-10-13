package dev.amman.proficiency.client;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;

import java.util.List;

/**
 * The client package's own drawing helpers ({@link TextFit}, {@link HudMath}, {@link StreakBadge}'s
 * blend, the icon blit of {@link SkillIcons}) opened to code in other packages, chiefly the Hearth's
 * client lane ({@code dev.amman.proficiency.hearth.client}). Each method only forwards, so the Hearth's
 * screens fit, wrap, ring and fade text exactly as the rest of the mod does, and the layout audit
 * counts their clips under their own site names.
 */
public final class HudKit {

    private HudKit() {
    }

    // ---- Text ---------------------------------------------------------------------------------

    /** {@link TextFit#clip}: cut to {@code width} pixels with an ellipsis, or whole when it fits. */
    public static String clip(Font font, String text, int width) {
        return TextFit.clip(font, text, width);
    }

    /** {@link TextFit#draw}: draws {@code text} cut to {@code width}; true when it had to cut. */
    public static boolean draw(GuiGraphics graphics, Font font, String site, String text, int x, int y, int width,
            int colour, boolean shadow) {
        return TextFit.draw(graphics, font, site, text, x, y, width, colour, shadow);
    }

    /** {@link TextFit#wrapPlain}: plain text broken into lines of at most {@code width}. */
    public static List<String> wrap(Font font, String text, int width) {
        return TextFit.wrapPlain(font, text, width);
    }

    /** {@link TextFit#wrapLimited}: at most {@code maxLines}, the last one ending in an ellipsis. */
    public static List<String> wrap(Font font, String text, int width, int maxLines, boolean[] overflow) {
        return TextFit.wrapLimited(font, text, width, maxLines, overflow);
    }

    /** {@link TextFit#tooltip}: a tooltip that wraps, and shrinks when still taller than the screen. */
    public static void tooltip(GuiGraphics graphics, Font font, List<Component> lines, int mouseX, int mouseY,
            int maxWidth) {
        TextFit.tooltip(graphics, font, lines, mouseX, mouseY, maxWidth);
    }

    /** Counts a clip or wrap at {@code site} for the layout audit (docs/LAYOUT-AUDIT.md). */
    public static void note(String site) {
        TextFit.note(site);
    }

    /** Developer demos only: pretend Shift is down (true) or up (false); null reads the real key. */
    public static void forceDetail(Boolean state) {
        TooltipDetail.force(state);
    }

    // ---- Rings and fades -----------------------------------------------------------------------

    /** {@link HudMath#ring}: the ellipse every HUD effect of the mod draws with. */
    public static void ring(GuiGraphics graphics, int cx, int cy, float rx, float ry, int argb, int size, int step,
            int count) {
        HudMath.ring(graphics, cx, cy, rx, ry, argb, size, step, count);
    }

    /** Point {@code index} (0..63) of the HUD's sine table, clockwise from the top. */
    public static float ringX(int index) {
        return HudMath.X64[Math.floorMod(index, HudMath.X64.length)];
    }

    public static float ringY(int index) {
        return HudMath.Y64[Math.floorMod(index, HudMath.Y64.length)];
    }

    /** {@link HudMath#smooth}: the smoothstep every fade of the mod uses. */
    public static float smooth(float t) {
        return HudMath.smooth(t);
    }

    /** {@link StreakBadge#lerp}: RGB blend. */
    public static int lerp(int from, int to, float t) {
        return StreakBadge.lerp(from, to, t);
    }

    // ---- Icons -----------------------------------------------------------------------------------

    /**
     * {@link SkillIcons#drawTexture}: a 16x16 GUI texture at {@code size} GUI pixels with an alpha of 0 to
     * 255, drawn as a skill icon is (nearest sampling, the blend switch and shader colour left as they were
     * found); nothing for a null texture.
     */
    public static void icon(GuiGraphics graphics, ResourceLocation texture, int x, int y, int size, int alpha) {
        SkillIcons.drawTexture(graphics, texture, x, y, size, alpha);
    }

    /** Whether the mod draws icons at all ({@code ui.skillIcons}). */
    public static boolean iconsOn() {
        return SkillIcons.enabled();
    }

    // ---- Banner -------------------------------------------------------------------------------------

    /** The discovery banner on screen: its top in GUI pixels, 0 with none (for HUD panels above it). */
    public static int bannerTop() {
        return DiscoveryBanner.top();
    }

    /** The discovery banner on screen: its bottom in GUI pixels, 0 with none. */
    public static int bannerBottom() {
        return DiscoveryBanner.bottom();
    }
}
