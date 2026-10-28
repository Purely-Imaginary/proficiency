package dev.amman.proficiency.client.stone;

import com.mojang.math.Axis;
import dev.amman.proficiency.ProficiencyAttachments;
import dev.amman.proficiency.client.AbilityGlint;
import dev.amman.proficiency.client.ClickFx;
import dev.amman.proficiency.client.ClickFxMath;
import dev.amman.proficiency.client.ReadyGlint;
import dev.amman.proficiency.client.SkillstoneMath;
import dev.amman.proficiency.client.TempoBadge;
import dev.amman.proficiency.client.WaywheelMath;
import dev.amman.proficiency.client.WaywheelPins;
import dev.amman.proficiency.config.ProficiencyClientConfig;
import dev.amman.proficiency.skill.PlayerSkills;
import dev.amman.proficiency.skill.Skill;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * The belt (ui.style = modern, 1.33.0): up to three skills pinned from the Waywheel sit as round stone tokens
 * left of the hotbar, each with its level ring and its ability's state (lit and a green pip when ready,
 * shaded while it cools, dark until unlocked), and the skill's Tempo flames rising off the token's top edge
 * ({@link TempoBadge}, the HUD line's fire).
 *
 * <p>The cast (1.34.0): the wheel screen closes at once (input is never held), and this HUD pass plays what
 * the D storyboard shows after it: the disc folds into the hub while the token arcs to its belt socket, which
 * flares and bursts as it lands. A skill that is not on the belt has no socket to fly to, so its token lifts
 * off its place on the wheel and burns away in an ember burst. All of it is timed in milliseconds (frame-rate
 * independent), a few textured quads a frame, and skipped when ui.juicyFeedback is off or Distortion Effects
 * is at 0% ({@link ClickFx#motion()}): then the socket just flares. Drawn from each line's HUD pass
 * ({@code SkillHud}).
 */
public final class WaywheelBelt {

    private static final ClickFx FX = new ClickFx();
    private static final WaywheelPins PINS = new WaywheelPins(id -> Skill.byId(id) != null);
    @Nullable
    private static Skill flashed;
    private static long flashedAt = Long.MIN_VALUE / 2;
    private static boolean flashPlayed = true;

    /** The cast in flight: the skill, when, from where on the wheel, the token side, the disc and its hub. */
    @Nullable
    private static Skill flying;
    private static long castAt;
    private static boolean toBelt;
    private static int fromX;
    private static int fromY;
    private static int fromTok;
    private static int disc;
    private static int hubX;
    private static int hubY;
    private static boolean popped;

    private WaywheelBelt() {
    }

    /** The pinned skill ids, parsed once per change of ui.waywheelPins. */
    public static List<String> pins() {
        return PINS.get(ProficiencyClientConfig.uiWaywheelPins());
    }

    /**
     * A cast from the wheel at token centre (x, y), side {@code tok}, on disc {@code dial} around (cx, cy): the
     * fold and the flight start now; the socket's flare waits for the token to land.
     */
    public static void cast(Skill s, int x, int y, int tok, int dial, int cx, int cy) {
        long now = WaywheelScreen.clock();
        flashPlayed = false;
        Minecraft mc = Minecraft.getInstance();
        boolean pinned = slotOf(s, mc.getWindow().getGuiScaledWidth()) >= 0;
        if (!ClickFx.motion()) {
            // Reduced motion: no fold and no flight, the socket answers at once.
            flying = null;
            flashed = pinned ? s : null;
            flashedAt = now;
            return;
        }
        flying = s;
        castAt = now;
        toBelt = pinned;
        fromX = x;
        fromY = y;
        fromTok = tok;
        disc = dial;
        hubX = cx;
        hubY = cy;
        popped = false;
        flashed = pinned ? s : null;
        flashedAt = now + (pinned ? WaywheelMath.FLY_MS : 0);
    }

    /** Index of {@code s}'s belt slot in a GUI {@code width} wide (as drawn), or -1 when it has none. */
    private static int slotOf(Skill s, int width) {
        List<String> ids = pins();
        int at = ids.indexOf(s.id());
        if (at < 0) {
            return -1;
        }
        int fit = WaywheelMath.belt(width, ids.size()).length;
        int first = ids.size() - fit;
        return at >= first ? at - first : -1;
    }

    /** Draws the belt and a cast in flight; nothing under the classic style or with the GUI hidden. */
    public static void render(GuiGraphics g, Minecraft mc) {
        if (mc.player == null || mc.options.hideGui || !SkillstoneScreen.modern()) {
            return;
        }
        long now = WaywheelScreen.clock();
        PlayerSkills skills = ProficiencyAttachments.of(mc.player);
        long game = mc.player.level().getGameTime();
        int n = WaywheelMath.BELT_SLOT;
        int y = g.guiHeight() - n;
        drawFold(g, now);
        List<String> ids = pins();
        int[] xs = WaywheelMath.belt(g.guiWidth(), ids.size());
        // Too narrow for all of them: the newest pins (the right end) stay.
        int first = ids.size() - xs.length;
        for (int i = 0; i < xs.length; i++) {
            Skill s = Skill.byId(ids.get(first + i));
            int x = xs[i];
            boolean landing = s == flying && toBelt && now - castAt < WaywheelMath.FLY_MS;
            long since = now - flashedAt;
            boolean flaring = s == flashed && since >= 0 && since < ClickFxMath.BURST_MS;
            if (flaring) {
                float f = 1f - since / (float) ClickFxMath.BURST_MS;
                Stone.light(g, x + n / 2, y + n / 2, Math.round(n * (1.2f + f)), Math.round(n * (1.2f + f)), Stone.WARM, 0.6f + f);
                if (!flashPlayed) {
                    flashPlayed = true;
                    FX.success(s, x, y, n, n, 0xFFFFB050, 1, 1f, now);
                }
            }
            long glintAge = landing ? Long.MAX_VALUE / 2 : AbilityGlint.age(s);
            boolean glinting = ReadyGlint.running(glintAge);
            if (glinting) {
                // The ability came ready: the token pops about its centre and a warm glow flares behind it.
                float fl = ReadyGlint.flash(glintAge);
                int rad = Math.round(n * (1.1f + 0.7f * fl));
                Stone.light(g, x + n / 2, y + n / 2, rad, rad, Stone.WARM, Math.min(1f, 0.9f * fl + 0.1f));
                g.pose().pushPose();
                if (ClickFx.motion()) {
                    float pop = ClickFxMath.pop(glintAge, 0.32f);
                    g.pose().translate(x + n / 2f, y + n / 2f, 0);
                    g.pose().scale(pop, pop, 1f);
                    g.pose().translate(-(x + n / 2f), -(y + n / 2f), 0);
                }
            }
            FX.begin(g, s, x, y, n, n, now);
            if (landing) {
                // The socket waits empty for its token: the stone shows, the icon is in the air.
                WaywheelToken.base(g, x, y, n, WaywheelToken.DARK, 1f);
            } else {
                WaywheelToken.skill(g, s, x, y, n, flaring, false, skills, game, 1f);
            }
            FX.end(g, s, x, y, n, n, now);
            // The Tempo fire comes off the token's top edge; its multiplier sits in the gap to the right.
            TempoBadge.draw(g, x + 2, y, n - 4, n - 4, 255, s);
            if (glinting) {
                if (ClickFx.motion()) {
                    AbilityGlint.sweep(g, x, y, n, n, glintAge, WaywheelMath.face(n));
                }
                g.pose().popPose();
            }
        }
        drawFlight(g, mc, skills, game, xs, first, y, now);
        FX.render(g, now, 1f);
    }

    /** The disc folding into its hub after a cast: one blit, scaled and turned, fading as it closes. */
    private static void drawFold(GuiGraphics g, long now) {
        if (flying == null) {
            return;
        }
        long t = now - castAt;
        float k = WaywheelMath.fold(t);
        if (k <= 0.02f || t < 0) {
            return;
        }
        int dn = 2 * (disc + 22);
        g.pose().pushPose();
        g.pose().translate(hubX, hubY, 0);
        g.pose().scale(k, k, 1f);
        g.pose().mulPose(Axis.ZP.rotationDegrees(40f * (1f - k)));
        g.pose().translate(-hubX, -hubY, 0);
        Stone.blitAlpha(g, Stone.tex("wheel_" + disc), hubX - dn / 2, hubY - dn / 2, dn, dn, 0f, 0f, dn, dn, 0.35f + 0.65f * k);
        int pit = (int) (disc * .44);
        int pn = 2 * (pit + 5);
        Stone.blitAlpha(g, Stone.tex("pit_" + pit), hubX - pn / 2, hubY - pn / 2, pn, pn, 0f, 0f, pn, pn, 0.35f + 0.65f * k);
        // The fire goes down with the wheel.
        int frame = SkillstoneMath.fireFrame(t, 8);
        Stone.blitAlpha(g, Stone.tex("fire_" + pit), hubX - pn / 2, hubY - pn / 2, pn, pn, 0f, frame * pn, pn, pn * 8, k);
        g.pose().popPose();
    }

    /** The cast token: arcing to its socket (a short ember trail behind it), or lifting off and burning away. */
    private static void drawFlight(GuiGraphics g, Minecraft mc, PlayerSkills skills, long game, int[] xs, int first,
                                   int beltY, long now) {
        Skill s = flying;
        if (s == null) {
            return;
        }
        long t = now - castAt;
        if (t < 0) {
            return;
        }
        int n = WaywheelMath.BELT_SLOT;
        if (toBelt) {
            int slot = slotOf(s, g.guiWidth());
            if (t >= WaywheelMath.FLY_MS || slot < 0 || slot >= xs.length) {
                flying = null;
                return;
            }
            double tx = xs[slot] + n / 2.0;
            double ty = beltY + n / 2.0;
            // The ember trail: the light the token left a few frames' worth of time ago.
            for (int i = 3; i >= 1; i--) {
                double[] q = WaywheelMath.fly(t - 28L * i, fromX, fromY, tx, ty);
                int r = Math.max(3, Math.round(fromTok * (0.5f - 0.1f * i)));
                Stone.light(g, (int) Math.round(q[0]), (int) Math.round(q[1]), r, r, Stone.WARM, 0.55f - 0.14f * i);
            }
            double[] p = WaywheelMath.fly(t, fromX, fromY, tx, ty);
            float scale = (float) ((fromTok + (n - fromTok) * p[2]) / fromTok);
            drawToken(g, s, p[0], p[1], scale, 1f, skills, game);
            return;
        }
        if (t >= WaywheelMath.POP_MS) {
            flying = null;
            return;
        }
        if (!popped) {
            popped = true;
            FX.burst(s, fromX, fromY, fromTok, 0xFFFFB050, 1, now);
        }
        float[] pop = WaywheelMath.pop(t);
        drawToken(g, s, fromX, fromY - pop[0], pop[1], pop[2], skills, game);
    }

    private static void drawToken(GuiGraphics g, Skill s, double cx, double cy, float scale, float alpha,
                                  PlayerSkills skills, long game) {
        int tok = fromTok;
        g.pose().pushPose();
        g.pose().translate((float) cx, (float) cy, 0);
        g.pose().scale(scale, scale, 1f);
        WaywheelToken.skill(g, s, -tok / 2, -tok / 2, tok, true, false, skills, game, alpha);
        g.pose().popPose();
    }
}
