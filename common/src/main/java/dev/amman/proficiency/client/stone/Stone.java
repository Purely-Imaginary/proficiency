package dev.amman.proficiency.client.stone;

import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.systems.RenderSystem;
import dev.amman.proficiency.Proficiency;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.resources.ResourceLocation;

/**
 * The Skillstone material ({@code ui.style = modern}): carved brick slabs with a 1 px light bevel
 * on top and a dark one below, sunk slates, sockets, plates, molten grooves and the additive ember
 * light. The numbers are the design kit's ({@code kit.py},
 * {@code dir_a.py}); the textures are baked by {@code tools/gen_skillstone.py}.
 *
 * <p>Everything is drawn at whole GUI pixels with nearest sampling. Shared verbatim by the
 * 1.21.1 lines and Forge 1.20.1 (copied by {@code copySharedHearth}).
 */
public final class Stone {

    public static final int STONE = 0x4C403C;
    public static final int INK = 0xEEE2D2;
    public static final int DIM = 0xA8988C;
    public static final int LOCK = 0x8C7E76;
    public static final int HEAD = 0xDEA06E;
    public static final int GOLD = 0xFFD696;
    public static final int WARM = 0x964614;
    public static final int OUTLINE = 0x181212;
    public static final int RED = 0xE0503C;
    public static final int GREEN = 0xA0FFAA;
    private static final int[] EMBER = {0x3A1004, 0x6E1E08, 0xAA340A, 0xE25C16, 0xFF8C28, 0xFFBE50, 0xFFE8A0};

    public static final ResourceLocation OUTER = tex("stone_outer");
    public static final ResourceLocation INNER = tex("stone_inner");
    public static final ResourceLocation WELL = tex("well");
    public static final ResourceLocation LIGHT = tex("light");

    private Stone() {
    }

    public static ResourceLocation tex(String name) {
        return Proficiency.id("textures/gui/skillstone/" + name + ".png");
    }

    // ---- Colour ---------------------------------------------------------------------------------

    public static int shade(int rgb, float k) {
        int r = Math.min(255, Math.max(0, Math.round(((rgb >> 16) & 0xFF) * k)));
        int g = Math.min(255, Math.max(0, Math.round(((rgb >> 8) & 0xFF) * k)));
        int b = Math.min(255, Math.max(0, Math.round((rgb & 0xFF) * k)));
        return (r << 16) | (g << 8) | b;
    }

    public static int ember(float t) {
        t = Math.max(0f, Math.min(0.999f, t));
        return EMBER[(int) (t * EMBER.length)];
    }

    public static int argb(int alpha, int rgb) {
        return (Math.max(0, Math.min(255, alpha)) << 24) | (rgb & 0xFFFFFF);
    }

    public static int mix(int a, int b, float t) {
        t = Math.max(0f, Math.min(1f, t));
        int r = Math.round(((a >> 16) & 0xFF) + (((b >> 16) & 0xFF) - ((a >> 16) & 0xFF)) * t);
        int g = Math.round(((a >> 8) & 0xFF) + (((b >> 8) & 0xFF) - ((a >> 8) & 0xFF)) * t);
        int bl = Math.round((a & 0xFF) + ((b & 0xFF) - (a & 0xFF)) * t);
        return (r << 16) | (g << 8) | bl;
    }

    // ---- Shapes ---------------------------------------------------------------------------------

    /** How far row {@code j} (0 = the edge row) of a rounded corner of radius {@code r} is cut in. */
    static int inset(int r, int j) {
        if (r <= 0 || j >= r) {
            return 0;
        }
        double dy = r - j - 0.5;
        return (int) Math.round(r - Math.sqrt(Math.max(0, (double) r * r - dy * dy)));
    }

    public static void fillRound(GuiGraphics g, int x, int y, int w, int h, int r, int argb) {
        r = Math.min(r, Math.min(w, h) / 2);
        for (int j = 0; j < r; j++) {
            int in = inset(r, j);
            g.fill(x + in, y + j, x + w - in, y + j + 1, argb);
            g.fill(x + in, y + h - 1 - j, x + w - in, y + h - j, argb);
        }
        g.fill(x, y + r, x + w, y + h - r, argb);
    }

    public static void outlineRound(GuiGraphics g, int x, int y, int w, int h, int r, int argb) {
        r = Math.min(r, Math.min(w, h) / 2);
        g.fill(x + r, y, x + w - r, y + 1, argb);
        g.fill(x + r, y + h - 1, x + w - r, y + h, argb);
        g.fill(x, y + r, x + 1, y + h - r, argb);
        g.fill(x + w - 1, y + r, x + w, y + h - r, argb);
        for (int j = 0; j < r; j++) {
            int in = inset(r, j);
            int next = j + 1 < r ? inset(r, j + 1) : 0;
            int run = Math.max(1, in - next);
            g.fill(x + next, y + j, x + next + run, y + j + 1, argb);
            g.fill(x + w - next - run, y + j, x + w - next, y + j + 1, argb);
            g.fill(x + next, y + h - 1 - j, x + next + run, y + h - j, argb);
            g.fill(x + w - next - run, y + h - 1 - j, x + w - next, y + h - j, argb);
        }
    }

    /** Tiles {@code tex} over the rect, the texture's origin at {@code (ox, oy)}. */
    public static void tile(GuiGraphics g, ResourceLocation tex, int tw, int th, int ox, int oy,
                            int x, int y, int w, int h) {
        if (w <= 0 || h <= 0) {
            return;
        }
        int py = y;
        while (py < y + h) {
            int v = Math.floorMod(py - oy, th);
            int rh = Math.min(th - v, y + h - py);
            int px = x;
            while (px < x + w) {
                int u = Math.floorMod(px - ox, tw);
                int rw = Math.min(tw - u, x + w - px);
                g.blit(tex, px, py, (float) u, (float) v, rw, rh, tw, th);
                px += rw;
            }
            py += rh;
        }
    }

    /** A rounded rect filled with a tiled texture. */
    public static void texRound(GuiGraphics g, ResourceLocation tex, int tw, int th, int x, int y, int w, int h, int r) {
        r = Math.min(r, Math.min(w, h) / 2);
        for (int j = 0; j < r; j++) {
            int in = inset(r, j);
            tile(g, tex, tw, th, x, y, x + in, y + j, w - 2 * in, 1);
            tile(g, tex, tw, th, x, y, x + in, y + h - 1 - j, w - 2 * in, 1);
        }
        tile(g, tex, tw, th, x, y, x, y + r, w, h - 2 * r);
    }

    /** The kit's bevel bands: light top/left and dark bottom/right (swapped when sunk). */
    public static void bevel(GuiGraphics g, int x, int y, int w, int h, int r, int width, boolean inset) {
        for (int i = 0; i < width; i++) {
            int light = argb(90 - i * 30, 0xFFF0DC);
            int dark = argb(110 - i * 35, 0x000000);
            int top = inset ? dark : light;
            int bot = inset ? light : dark;
            g.fill(x + r + i, y + 1 + i, x + w - 1 - r - i + 1, y + 2 + i, top);
            g.fill(x + 1 + i, y + r + i, x + 2 + i, y + h - 1 - r - i + 1, top);
            g.fill(x + r + i, y + h - 2 - i, x + w - 1 - r - i + 1, y + h - 1 - i, bot);
            g.fill(x + w - 2 - i, y + r + i, x + w - 1 - i, y + h - 1 - r - i + 1, bot);
        }
        outlineRound(g, x, y, w, h, r, argb(255, OUTLINE));
    }

    /** A bevelled block of flat colour. */
    public static void block(GuiGraphics g, int x, int y, int w, int h, int r, int width, boolean inset, int rgb) {
        fillRound(g, x, y, w, h, r, argb(255, rgb));
        bevel(g, x, y, w, h, r, width, inset);
    }

    /** A bevelled block of a tiled texture. */
    public static void block(GuiGraphics g, ResourceLocation tex, int tw, int th, int x, int y, int w, int h, int r,
                             int width, boolean inset) {
        texRound(g, tex, tw, th, x, y, w, h, r);
        bevel(g, x, y, w, h, r, width, inset);
    }

    // ---- Components -----------------------------------------------------------------------------

    /**
     * The slab: drop shadow, the outer rim (12x6 bricks, r 12) and the sunk face (10x5 bricks).
     * Returns nothing; the face is {@code rim} px inside.
     */
    public static void slab(GuiGraphics g, int x, int y, int w, int h, int rim) {
        fillRound(g, x + 4, y + 6, w, h, 10, argb(150, 0));
        block(g, OUTER, 96, 48, x, y, w, h, 12, 3, false);
        block(g, INNER, 100, 40, x + rim, y + rim, w - 2 * rim, h - 2 * rim, 7, 2, true);
    }

    /** A sunk dark slate (the category list, the cards). */
    public static void well(GuiGraphics g, int x, int y, int w, int h) {
        block(g, WELL, 64, 32, x, y, w, h, 4, 2, true);
    }

    /** A rune socket. {@code lit} 0..1+ is its ember glow. */
    public static void socket(GuiGraphics g, int x, int y, int w, int h, float lit, boolean sel, boolean hover) {
        if (lit > 0.05f) {
            float l = Math.min(1f, lit);
            int rad = Math.round(w * (0.8f + 0.4f * l));
            light(g, x + w / 2, y + h / 2, rad, rad, WARM, 0.6f * (0.35f + 0.65f * l));
        }
        block(g, x, y, w, h, 3, 2, false, shade(STONE, sel || hover ? 1.22f : 1.08f));
        block(g, x + 3, y + 3, w - 6, h - 6, 2, 1, true, shade(STONE, 0.5f));
        if (sel) {
            outlineRound(g, x - 2, y - 2, w + 4, h + 4, 4, argb(255, ember(0.85f)));
        }
    }

    /** A raised label plate with centred text. */
    public static void plate(GuiGraphics g, Font font, int x, int y, int w, int h, String text, int colour, boolean on) {
        block(g, x, y, w, h, 3, 2, false, shade(STONE, on ? 1.3f : 1.12f));
        int tw = font.width(text);
        g.drawString(font, text, x + (w - tw) / 2, y + (h - 8) / 2, argb(255, colour), true);
    }

    /** A horizontal molten groove from {@code x0} to {@code x1}, filled to {@code t}. */
    public static void groove(GuiGraphics g, int x0, int x1, int y, float t, int w, boolean glow) {
        int top = y - w / 2;
        g.fill(x0 + 1, top + 1, x1 + 1, top + w + 1, argb(120, 0x968278));
        g.fill(x0, top, x1, top + w, argb(255, 0x161110));
        int fx = x0 + Math.round((x1 - x0) * Math.max(0f, Math.min(1f, t)));
        if (fx > x0) {
            if (glow) {
                g.fill(x0, top - 2, fx, top + w + 2, argb(70, ember(0.5f)));
            }
            int iw = Math.max(1, w - 2);
            int it = y - iw / 2;
            g.fill(x0, it, fx, it + iw, argb(255, ember(0.62f)));
            if (w >= 3) {
                g.fill(x0, y, fx, y + 1, argb(255, ember(0.86f)));
            }
        }
    }

    /**
     * A scissor that follows the pose (the slab's shake and edge-on turn). 1.21 transforms the rectangle by the
     * pose and 1.20.1 does not, so the corners are transformed here and handed over under an identity pose.
     */
    public static void scissor(GuiGraphics g, int x0, int y0, int x1, int y1) {
        org.joml.Matrix4f m = g.pose().last().pose();
        org.joml.Vector3f a = m.transformPosition(new org.joml.Vector3f(x0, y0, 0));
        org.joml.Vector3f b = m.transformPosition(new org.joml.Vector3f(x1, y1, 0));
        int sx0 = (int) Math.floor(Math.min(a.x, b.x));
        int sy0 = (int) Math.floor(Math.min(a.y, b.y));
        int sx1 = (int) Math.ceil(Math.max(a.x, b.x));
        int sy1 = (int) Math.ceil(Math.max(a.y, b.y));
        g.pose().pushPose();
        g.pose().setIdentity();
        g.enableScissor(sx0, sy0, Math.max(sx0, sx1), Math.max(sy0, sy1));
        g.pose().popPose();
    }

    /** A straight groove between two points, {@code t} of it molten (the dial's spoke). */
    public static void groove(GuiGraphics g, int x0, int y0, int x1, int y1, float t, int w) {
        int steps = Math.max(Math.abs(x1 - x0), Math.abs(y1 - y0));
        int lit = Math.round(steps * Math.max(0f, Math.min(1f, t)));
        for (int i = 0; i <= steps; i++) {
            int x = x0 + Math.round((x1 - x0) * i / (float) Math.max(1, steps));
            int y = y0 + Math.round((y1 - y0) * i / (float) Math.max(1, steps));
            g.fill(x - w / 2 + 1, y - w / 2 + 1, x - w / 2 + w + 1, y - w / 2 + w + 1, argb(60, 0x968278));
        }
        for (int i = 0; i <= steps; i++) {
            int x = x0 + Math.round((x1 - x0) * i / (float) Math.max(1, steps));
            int y = y0 + Math.round((y1 - y0) * i / (float) Math.max(1, steps));
            g.fill(x - w / 2, y - w / 2, x - w / 2 + w, y - w / 2 + w, argb(255, 0x161110));
        }
        for (int i = 0; i <= lit; i++) {
            int x = x0 + Math.round((x1 - x0) * i / (float) Math.max(1, steps));
            int y = y0 + Math.round((y1 - y0) * i / (float) Math.max(1, steps));
            g.fill(x, y, x + 1, y + 1, argb(255, ember(0.7f)));
        }
    }

    /** The brazier: unspent talent points as up to three glowing coals (more as a number). */
    public static void coals(GuiGraphics g, int x, int y, int n) {
        if (n > 0) {
            light(g, x + 18, y + 6, 22, 22, WARM, 0.8f);
        }
        block(g, x, y + 6, 36, 10, 2, 1, false, 0x382E2C);
        for (int i = 0; i < 3; i++) {
            int cx = x + 8 + i * 10;
            if (i < n) {
                g.fill(cx - 3, y + 3, cx + 4, y + 9, argb(255, ember(0.6f)));
                g.fill(cx - 1, y + 4, cx + 2, y + 7, argb(255, ember(0.95f)));
            } else {
                g.fill(cx - 3, y + 5, cx + 4, y + 9, argb(255, 0x241E1C));
            }
        }
    }

    // ---- Light ----------------------------------------------------------------------------------

    /**
     * Additive stepped light (the kit's {@code light}): brightens and tints what is under it, so
     * the stone stays visible. One textured quad, no particles.
     */
    public static void light(GuiGraphics g, int cx, int cy, int rx, int ry, int rgb, float strength) {
        if (strength <= 0.01f || rx <= 0 || ry <= 0) {
            return;
        }
        float s = Math.min(1.5f, strength);
        RenderSystem.enableBlend();
        RenderSystem.blendFunc(GlStateManager.SourceFactor.ONE, GlStateManager.DestFactor.ONE);
        RenderSystem.setShaderColor(((rgb >> 16) & 0xFF) / 255f * s, ((rgb >> 8) & 0xFF) / 255f * s,
                (rgb & 0xFF) / 255f * s, 1f);
        g.blit(LIGHT, cx - rx, cy - ry, 2 * rx, 2 * ry, 0f, 0f, 64, 64, 64, 64);
        RenderSystem.setShaderColor(1f, 1f, 1f, 1f);
        RenderSystem.defaultBlendFunc();
    }

    /** A rectangle of additive light with a stepped falloff, {@code pad} px past the rect. */
    public static void lightRect(GuiGraphics g, int x, int y, int w, int h, int rgb, int pad, float strength) {
        if (strength <= 0.01f) {
            return;
        }
        RenderSystem.enableBlend();
        RenderSystem.blendFunc(GlStateManager.SourceFactor.SRC_ALPHA, GlStateManager.DestFactor.ONE);
        for (int i = 3; i >= 1; i--) {
            int p = pad * i / 3;
            int a = Math.round(255 * Math.min(1f, strength) * (float) Math.pow((1 - i / 3f) + 1 / 3f, 1.3));
            fillRound(g, x - p, y - p, w + 2 * p, h + 2 * p, p + 1, argb(a, rgb));
        }
        RenderSystem.defaultBlendFunc();
    }

    /** Draws a texture with an alpha, restoring the shader colour. */
    public static void blitAlpha(GuiGraphics g, ResourceLocation tex, int x, int y, int w, int h, float u, float v,
                                 int texW, int texH, float alpha) {
        if (alpha <= 0.01f) {
            return;
        }
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.setShaderColor(1f, 1f, 1f, Math.min(1f, alpha));
        g.blit(tex, x, y, u, v, w, h, texW, texH);
        RenderSystem.setShaderColor(1f, 1f, 1f, 1f);
    }

    /** An icon tinted towards stone: an unlit rune. */
    public static void greyIcon(GuiGraphics g, ResourceLocation tex, int x, int y, int size, float dim) {
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.setShaderColor(0.43f * dim + 0.2f, 0.38f * dim + 0.18f, 0.35f * dim + 0.17f, 1f);
        g.blit(tex, x, y, size, size, 0f, 0f, 16, 16, 16, 16);
        RenderSystem.setShaderColor(1f, 1f, 1f, 1f);
    }

    public static void icon(GuiGraphics g, ResourceLocation tex, int x, int y, int size, float alpha) {
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.setShaderColor(1f, 1f, 1f, Math.min(1f, alpha));
        g.blit(tex, x, y, size, size, 0f, 0f, 16, 16, 16, 16);
        RenderSystem.setShaderColor(1f, 1f, 1f, 1f);
    }

    /** Text with the slab's dark shadow. */
    public static void text(GuiGraphics g, Font font, String s, int x, int y, int rgb) {
        g.drawString(font, s, x, y, argb(255, rgb), true);
    }

    public static void textCentred(GuiGraphics g, Font font, String s, int cx, int y, int rgb) {
        g.drawString(font, s, cx - font.width(s) / 2, y, argb(255, rgb), true);
    }

    public static void textRight(GuiGraphics g, Font font, String s, int rx, int y, int rgb) {
        g.drawString(font, s, rx - font.width(s), y, argb(255, rgb), true);
    }

    /** Cuts {@code s} to {@code room} px with an ellipsis. */
    /**
     * A page's tooltip, wrapped so it stays on the screen. NeoForge and Forge wrap a tooltip that does not fit
     * beside the cursor; vanilla (Fabric) does not, and a long line ran off the right edge. Lines that fit on
     * either side of the cursor are drawn as they are, so the loaders that wrap see no change.
     */
    public static void tooltip(GuiGraphics g, Font font, java.util.List<net.minecraft.network.chat.Component> lines,
                               int mx, int my, int screenW) {
        int widest = 0;
        for (net.minecraft.network.chat.Component line : lines) {
            widest = Math.max(widest, font.width(line));
        }
        int right = screenW - mx - 16;
        int left = mx - 16;
        if (widest <= right || widest <= left) {
            g.renderComponentTooltip(font, lines, mx, my);
            return;
        }
        int room = Math.max(60, Math.max(right, left));
        java.util.List<net.minecraft.util.FormattedCharSequence> wrapped = new java.util.ArrayList<>();
        for (net.minecraft.network.chat.Component line : lines) {
            wrapped.addAll(font.split(line, room));
        }
        g.renderTooltip(font, wrapped, mx, my);
    }

    public static String fit(Font font, String s, int room) {
        if (font.width(s) <= room) {
            return s;
        }
        String dots = "...";
        int keep = s.length();
        while (keep > 0 && font.width(s.substring(0, keep)) + font.width(dots) > room) {
            keep--;
        }
        return s.substring(0, keep) + dots;
    }
}
