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
 * <p>A support skill (Courage, Charger, Guardian, Tactician, Blocking) owns no chain since 1.49.0: its line
 * shows the fire of the weapon chain it rides ({@link TempoState#source}); Endurance's line shows none.
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

    /** A vanilla jump crit lengthened {@code skillOrdinal}'s chain: flare it white (ui.tempoCritFlare). */
    public static void acceptCrit(int skillOrdinal) {
        if (dev.amman.proficiency.config.ProficiencyClientConfig.uiTempoCritFlare()) {
            STATE.acceptCrit(Math.floorMod(skillOrdinal, Skill.VALUES.length), now());
        }
    }

    public static void reset() {
        STATE.clear();
        lift = 0f;
        liftAt = Long.MIN_VALUE;
    }

    private static boolean enabled() {
        return dev.amman.proficiency.config.ProficiencyClientConfig.hudShowTempo();
    }

    /**
     * The record whose fire the line of {@code forSkill} shows: its own, the weapon chain a support skill rides,
     * or -1 (Endurance and the lump-paid skills have no Tempo).
     */
    private static int source(Skill forSkill, long now) {
        return forSkill == null ? -1 : STATE.source(forSkill.ordinal(), now);
    }

    /** True while {@code forSkill} has fire or smoke to draw (and the setting is on). */
    public static boolean visible(Skill forSkill) {
        long now = now();
        int s = source(forSkill, now);
        return s >= 0 && enabled() && STATE.visible(s, now);
    }

    /** 1 while the chain {@code forSkill} shows lives, else 0; 0 when the setting is off. */
    public static float alpha(Skill forSkill) {
        long now = now();
        int s = source(forSkill, now);
        return s >= 0 && enabled() ? STATE.alpha(s, now) : 0f;
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

    private static String text(int source) {
        return TempoFlames.text(STATE.permille(source));
    }

    /**
     * Draws the fire on the bar at {@code (barLeft, barTop)}, {@code barWidth} long with {@code filled} of it
     * filled, and the multiplier past its right end. {@code alpha} is the line's, 0 to 255.
     */
    public static void draw(GuiGraphics graphics, int barLeft, int barTop, int barWidth, int filled, int alpha,
            Skill forSkill) {
        drawFire(graphics, barLeft, barTop, barWidth, filled, alpha, forSkill, true);
    }

    /**
     * The fire at half size on a recent-skills stack row's bar ({@link HudStackRows}): the same strip and frames,
     * four pixels tall, no multiplier (its 3x5 digits would not read at half size). Does not move the main line's
     * label lift.
     */
    public static void drawSmall(GuiGraphics graphics, int barLeft, int barTop, int barWidth, int filled, int alpha,
            Skill forSkill) {
        if (forSkill == null || alpha <= 8 || !visible(forSkill)) {
            return;
        }
        graphics.pose().pushPose();
        graphics.pose().translate(barLeft, barTop + 1, 0);
        graphics.pose().scale(0.5f, 0.5f, 1f);
        drawFire(graphics, 0, 0, barWidth * 2, filled * 2, alpha, forSkill, false);
        graphics.pose().popPose();
    }

    private static void drawFire(GuiGraphics graphics, int barLeft, int barTop, int barWidth, int filled, int alpha,
            Skill forSkill, boolean number) {
        if (forSkill == null || alpha <= 8 || !visible(forSkill)) {
            return;
        }
        long now = now();
        int s = source(forSkill, now);
        if (s < 0) {
            return;
        }
        float lapse = STATE.lapse(s, now);
        int heat = TempoFlames.shownHeat(STATE.heat(s), lapse);
        float flare = lapse > 0f ? 0f : STATE.flare(s, now);
        float smoke = TempoFlames.smoke(lapse);
        float crit = STATE.critFlare(s, now);
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
        if (crit > 0.02f) {
            // A jump crit: the brightest flare row added on top (additive, so it whitens what burns), two quads on
            // offset frames so it reads as a burst; it fades over CRIT_FLARE_MS. Brightness only: it stays at
            // Distortion Effects 0%.
            RenderSystem.blendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE);
            strip(graphics, ROW_FLARE, frame, barLeft, top, span, r0, g0, b0, a * crit);
            strip(graphics, ROW_FLARE, (frame + TempoFlames.FRAMES / 3) % TempoFlames.FRAMES, barLeft, top, span,
                    r0, g0, b0, a * crit * 0.7f);
            RenderSystem.defaultBlendFunc();
        }
        float numberA = number ? a * TempoFlames.numberFade(lapse) : 0f;
        if (numberA > 0.03f) {
            int rgb = NUMBER_RGB[Math.max(0, Math.min(NUMBER_RGB.length, STATE.heat(s)) - 1)];
            float white = Math.max(flare, crit);
            if (white > 0f) {
                rgb = StreakBadge.lerp(rgb, 0xFFFFFF, white);
            }
            String text = text(s);
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

    /**
     * The skills panel's hover line for {@code forSkill}: empty unless the chain it shows is alive. The seconds are
     * that chain's own window as the server sent it (7 s for a weapon, 3 s for Mining, doubled by In Step); a
     * support skill names the weapon chain it rides.
     */
    public static List<Component> tooltip(Skill forSkill) {
        if (alpha(forSkill) <= 0f) {
            return List.of();
        }
        int s = source(forSkill, now());
        float seconds = Math.round(STATE.windowMs(s) / 100f) / 10f;
        if (s != forSkill.ordinal()) {
            return List.of(Component.translatable("proficiency.tooltip.tempo_ride", text(s),
                    Component.translatable(Skill.VALUES[s].translationKey()), seconds).withStyle(ChatFormatting.GOLD));
        }
        return List.of(Component.translatable("proficiency.tooltip.tempo", text(s), seconds)
                .withStyle(ChatFormatting.GOLD));
    }
}
