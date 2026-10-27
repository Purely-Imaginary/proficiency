package dev.amman.proficiency.client.stone;

import dev.amman.proficiency.client.SkillIcons;
import dev.amman.proficiency.client.WaywheelMath;
import dev.amman.proficiency.skill.ActiveService;
import dev.amman.proficiency.skill.PlayerSkills;
import dev.amman.proficiency.skill.Skill;
import dev.amman.proficiency.skill.SkillMath;
import net.minecraft.client.gui.GuiGraphics;

/**
 * A Waywheel skill token (1.34.0), the D mockup's round socket in carved stone: a baked token (rest, raised or
 * dark), the ember level ring in its groove, the skill icon, a cooling shade over the sunk face, and the green
 * "ready" pip or the gold pin nail on the rim. Four textured quads and a few fills; the wheel and the belt
 * both draw it. Written once against APIs both Minecraft versions share (forge copies it, copySharedHearth).
 */
public final class WaywheelToken {

    public static final int REST = 0;
    public static final int RAISED = 1;
    public static final int DARK = 2;

    private WaywheelToken() {
    }

    /** The nearest baked token side at or below {@code tok}. */
    public static int baked(int tok) {
        for (int n : WaywheelMath.TOKEN_SIZES) {
            if (tok >= n) {
                return n;
            }
        }
        return WaywheelMath.TOKEN_SIZES[WaywheelMath.TOKEN_SIZES.length - 1];
    }

    /** The icon side for a token: 16 (1:1) from 20 px up, else 12. */
    public static int iconSide(int tok) {
        return tok >= 20 ? 16 : 12;
    }

    /** The bare token: {@code state} REST, RAISED or DARK, at {@code alpha}. */
    public static void base(GuiGraphics g, int x0, int y0, int n, int state, float alpha) {
        Stone.blitAlpha(g, Stone.tex("token_" + n), x0, y0, n, n, 0f, state * n, n, n * 3, alpha);
    }

    /** The ember level ring for {@code level}. */
    public static void ring(GuiGraphics g, int x0, int y0, int n, int level, float alpha) {
        int f = WaywheelMath.ringFrame(level, SkillMath.MAX_LEVEL);
        if (f > 0) {
            Stone.blitAlpha(g, Stone.tex("ring_" + n), x0, y0, n, n, 0f, f * n, n, n * (WaywheelMath.RING_STEPS + 1), alpha);
        }
    }

    /** The green ready pip ({@code nail} false, top right) or the gold pin nail (top left). */
    public static void pip(GuiGraphics g, int x0, int y0, int n, boolean nail) {
        // Set into the rim on the diagonal (its centre 2.5 px inside the edge), like a stud, not floating off it.
        int off = (int) Math.round(n / 2.0 - (n / 2.0 - 2.5) / Math.sqrt(2) - 2.5);
        int x = nail ? x0 + off : x0 + n - 5 - off;
        Stone.blitAlpha(g, Stone.tex("pip"), x, y0 + off, 5, 5, 0f, nail ? 5 : 0, 5, 10, 1f);
    }

    /**
     * A whole skill token at (x0, y0), side {@code n} (a baked size): the glow when ready or hovered, the
     * token, the level ring, the icon, the cooling shade, the ready pip and (when {@code pinned}) the nail.
     */
    public static void skill(GuiGraphics g, Skill s, int x0, int y0, int n, boolean hov, boolean pinned,
                             PlayerSkills skills, long game, float alpha) {
        int level = skills.level(s);
        boolean unlocked = ActiveService.unlocked(skills, s);
        long cooldown = unlocked ? skills.cooldownRemaining(s, game) : 0;
        float lit = !unlocked ? 0f : cooldown > 0 ? 0.15f : 0.85f;
        if (hov) {
            lit = Math.max(lit, 1f);
        }
        if (lit > 0.05f && alpha > 0.5f) {
            int rad = Math.round(n * (0.8f + 0.4f * Math.min(1f, lit)));
            Stone.light(g, x0 + n / 2, y0 + n / 2, rad, rad, Stone.WARM, 0.6f * (0.35f + 0.65f * Math.min(1f, lit)) * alpha);
        }
        base(g, x0, y0, n, hov ? RAISED : level == 0 ? DARK : REST, alpha);
        ring(g, x0, y0, n, level, alpha);
        int is = iconSide(n);
        int ix = x0 + (n - is) / 2;
        int iy = y0 + (n - is) / 2;
        if (level > 0) {
            Stone.icon(g, SkillIcons.texture(s), ix, iy, is, alpha);
        } else {
            Stone.greyIcon(g, SkillIcons.texture(s), ix, iy, is, 0.45f * alpha);
        }
        if (alpha < 0.5f) {
            return;
        }
        if (cooldown > 0) {
            double left = cooldown / (double) Math.max(1L, ActiveService.cooldownTicks(skills, s));
            shade(g, x0 + n / 2, y0 + n / 2, WaywheelMath.face(n), (float) Math.min(1.0, left));
        } else if (unlocked) {
            pip(g, x0, y0, n, false);
        }
        if (pinned) {
            pip(g, x0, y0, n, true);
        }
    }

    /**
     * A cooling ability: the sunk face is shaded from the bottom, {@code part} of its height, and the shade
     * drains down as it recovers. One fill per row of the round face.
     */
    public static void shade(GuiGraphics g, int cx, int cy, int r, float part) {
        int rows = 2 * r;
        int shaded = Math.round(rows * part);
        for (int j = rows - shaded; j < rows; j++) {
            int half = WaywheelMath.chord(r, j);
            if (half > 0) {
                g.fill(cx - half, cy - r + j, cx + half, cy - r + j + 1, 0x90000000);
            }
        }
    }
}
