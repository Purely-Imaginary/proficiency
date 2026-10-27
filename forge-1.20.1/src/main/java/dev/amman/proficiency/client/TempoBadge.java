package dev.amman.proficiency.client;

import com.mojang.blaze3d.systems.RenderSystem;
import dev.amman.proficiency.Proficiency;
import dev.amman.proficiency.net.TempoPayload;
import dev.amman.proficiency.skill.Skill;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import org.lwjgl.opengl.GL11;

import java.util.List;

/**
 * The Tempo fire on the HUD skill bar: pixel flames rise from the top edge of the bar's fill, a few embers
 * at the first heat and tall yellow-white flames at the cap; a step up flares, and when the window lapses
 * the fire sinks to smoke wisps and goes. The multiplier sits small at the bar's right end, in a 3x5
 * pixel font, so the label row above stays the name, the level and the badges.
 *
 * <p>One animated strip ({@code textures/gui/tempo_flames.png}, made by {@code tools/gen_tempo_flames.py})
 * holds every heat, the flare, the smoke and the digits. A frame is at most three strip quads and the
 * digits with their shadow; the frame comes from the clock ({@link TempoFlames#frame}). While the fire burns
 * the label rises {@link TempoFlames#LIFT} rows ({@link #lift}) so no flame reaches it.
 *
 * <p>The server sends a skill's chain only when it changes, so the client keeps one small record per skill
 * ({@link TempoState}, pure and unit tested, with the rules in {@link TempoFlames}); the HUD line asks for
 * the skill it names.
 */
public final class TempoBadge {

    private static final ResourceLocation TEXTURE =
            new ResourceLocation(Proficiency.MOD_ID, "textures/gui/tempo_flames.png");
    // Keep in step with tools/gen_tempo_flames.py.
    private static final int TEX_W = 512;
    private static final int TEX_H = 136;
    private static final int STRIP_W = 70;
    private static final int BLOCK_W = 72;
    private static final int FRAME_H = 8;
    private static final int ROW_FLARE = 5;
    private static final int ROW_SMOKE = 6;
    private static final int GLYPH_Y = 128;
    private static final int GLYPH_W = 3;
    private static final int GLYPH_H = 5;
    /** The fire is never narrower than this, so a bar just emptied by a level-up still shows its chain. */
    private static final int MIN_SPAN = 8;
    /** Gap between the bar's right end and the multiplier. */
    private static final int NUMBER_GAP = 3;
    /** The multiplier's colour per heat (1 to 5): the flames' body colour, paler at the cap. */
    private static final int[] NUMBER_RGB = {0xE8743A, 0xF08A34, 0xFFA236, 0xFFBE48, 0xFFE07A};

    private static final TempoState STATE = new TempoState(Skill.VALUES.length);

    private static float lift;
    private static long liftAt = Long.MIN_VALUE;

    private TempoBadge() {
    }

    private static long now() {
        return net.minecraft.Util.getMillis();
    }

    public static void accept(TempoPayload payload) {
        int ordinal = Math.floorMod(payload.skillOrdinal(), Skill.VALUES.length);
        STATE.accept(ordinal, payload.steps(), payload.maxSteps(), payload.permille(),
                Math.max(1, payload.windowTicks()) * 50L, now());
    }

    public static void reset() {
        STATE.clear();
        lift = 0f;
        liftAt = Long.MIN_VALUE;
    }

    private static boolean enabled() {
        return dev.amman.proficiency.config.ProficiencyClientConfig.hudShowTempo();
    }

    /** True while {@code forSkill} has fire or smoke to draw (and the setting is on). */
    public static boolean visible(Skill forSkill) {
        return forSkill != null && enabled() && STATE.visible(forSkill.ordinal(), now());
    }

    /** 1 while the chain of {@code forSkill} lives, else 0; 0 when the setting is off. */
    public static float alpha(Skill forSkill) {
        return enabled() ? STATE.alpha(forSkill.ordinal(), now()) : 0f;
    }

    /**
     * Rows the label rises this frame for the line of {@code forSkill}: eased up to {@link TempoFlames#LIFT}
     * while its fire burns, back down after. Call once a frame, from the line.
     */
    public static int lift(Skill forSkill) {
        long now = now();
        float target = visible(forSkill) ? TempoFlames.LIFT : 0f;
        lift = liftAt == Long.MIN_VALUE ? target : TempoFlames.ease(lift, target, now - liftAt);
        liftAt = now;
        return Math.round(lift);
    }

    /** The rise drawn on the last frame, for what sits above the label (the frenzy line). */
    public static int currentLift() {
        return Math.round(lift);
    }

    private static String text(Skill forSkill) {
        return TempoFlames.text(STATE.permille(forSkill.ordinal()));
    }

    /**
     * Draws the fire on the bar at {@code (barLeft, barTop)}, {@code barWidth} long with {@code filled} of it
     * filled, and the multiplier past its right end. {@code alpha} is the line's, 0 to 255.
     */
    public static void draw(GuiGraphics graphics, int barLeft, int barTop, int barWidth, int filled, int alpha,
            Skill forSkill) {
        if (forSkill == null || alpha <= 8 || !visible(forSkill)) {
            return;
        }
        long now = now();
        int s = forSkill.ordinal();
        float lapse = STATE.lapse(s, now);
        int heat = TempoFlames.shownHeat(STATE.heat(s), lapse);
        float flare = lapse > 0f ? 0f : STATE.flare(s, now);
        float smoke = TempoFlames.smoke(lapse);
        int span = Math.min(Math.min(barWidth, STRIP_W), Math.max(MIN_SPAN, filled));
        int frame = TempoFlames.frame(now);
        // The strip's bottom row lies on the bar's top row: the fire comes out of the bar.
        int top = barTop - (FRAME_H - 1);

        boolean blendWasOn = GL11.glIsEnabled(GL11.GL_BLEND);
        if (!blendWasOn) {
            RenderSystem.enableBlend();
            RenderSystem.defaultBlendFunc();
        }
        float[] colour = RenderSystem.getShaderColor();
        float r0 = colour[0];
        float g0 = colour[1];
        float b0 = colour[2];
        float a0 = colour[3];
        float a = a0 * (alpha / 255f);
        if (heat > 0) {
            strip(graphics, heat - 1, frame, barLeft, top, span, r0, g0, b0, a);
        }
        if (flare > 0.02f && heat > 0) {
            // A step up flares one heat hotter for a moment (the cap has its own, brighter flare), so a chain
            // worked fast still reads as its own heat. Half a loop ahead, so it does not sit on the flames below.
            int row = heat >= TempoFlames.HEATS ? ROW_FLARE : heat;
            strip(graphics, row, (frame + TempoFlames.FRAMES / 2) % TempoFlames.FRAMES, barLeft, top, span,
                    r0, g0, b0, a * flare);
        }
        if (smoke > 0.02f) {
            strip(graphics, ROW_SMOKE, frame, barLeft, top, span, r0, g0, b0, a * smoke);
        }
        float numberA = a * TempoFlames.numberFade(lapse);
        if (numberA > 0.03f) {
            int rgb = NUMBER_RGB[Math.max(0, Math.min(NUMBER_RGB.length, STATE.heat(s)) - 1)];
            if (flare > 0f) {
                rgb = StreakBadge.lerp(rgb, 0xFFFFFF, flare);
            }
            String text = text(forSkill);
            int x = barLeft + barWidth + NUMBER_GAP;
            // Bottom of the digits on the bar's bottom row.
            int y = barTop + 2 - GLYPH_H;
            // A shadow like the vanilla font's: the colour at a quarter, one pixel down and right.
            digits(graphics, text, x + 1, y + 1, (rgb >> 2) & 0x3F3F3F, numberA * 0.9f, r0, g0, b0);
            digits(graphics, text, x, y, rgb, numberA * 0.92f, r0, g0, b0);
        }
        RenderSystem.setShaderColor(r0, g0, b0, a0);
        if (!blendWasOn) {
            RenderSystem.disableBlend();
        }
    }

    /** The right end of strip row {@code row}, {@code span} wide: the strip's tapered end sits on the fill edge. */
    private static void strip(GuiGraphics graphics, int row, int frame, int x, int y, int span, float r, float g,
            float b, float a) {
        if (a <= 0.03f || span <= 0) {
            return;
        }
        RenderSystem.setShaderColor(r, g, b, a);
        graphics.blit(TEXTURE, x, y, row * BLOCK_W + STRIP_W - span, frame * FRAME_H, span, FRAME_H, TEX_W, TEX_H);
    }

    private static void digits(GuiGraphics graphics, String text, int x, int y, int rgb, float a, float r, float g,
            float b) {
        RenderSystem.setShaderColor(r * ((rgb >> 16) & 0xFF) / 255f, g * ((rgb >> 8) & 0xFF) / 255f,
                b * (rgb & 0xFF) / 255f, a);
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            int glyph = TempoFlames.glyph(c);
            if (glyph >= 0) {
                graphics.blit(TEXTURE, x, y, glyph * 4, GLYPH_Y, GLYPH_W, GLYPH_H, TEX_W, TEX_H);
            }
            x += TempoFlames.advance(c);
        }
    }

    /** The skills panel's hover line for {@code forSkill}: empty unless its chain is alive. */
    public static List<Component> tooltip(Skill forSkill) {
        if (alpha(forSkill) <= 0f) {
            return List.of();
        }
        return List.of(Component.translatable("proficiency.tooltip.tempo", text(forSkill),
                Math.round(STATE.windowMs(forSkill.ordinal()) / 100f) / 10f).withStyle(ChatFormatting.GOLD));
    }
}
