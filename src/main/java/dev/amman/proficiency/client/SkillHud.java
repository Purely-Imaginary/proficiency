package dev.amman.proficiency.client;

import dev.amman.proficiency.Proficiency;
import dev.amman.proficiency.ProficiencyAttachments;
import dev.amman.proficiency.config.ProficiencyClientConfig;
import dev.amman.proficiency.skill.ActiveService;
import dev.amman.proficiency.skill.PlayerSkills;
import dev.amman.proficiency.skill.Skill;
import dev.amman.proficiency.skill.SkillMath;
import dev.amman.proficiency.skill.SurvivalStreak;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RenderGuiEvent;

/**
 * What you are training, where you are actually looking.
 *
 * <p>A player with twenty-seven skills has no idea which one the swing in their hand feeds. The
 * panel answers that, but only if you stop and open it. This says it above the hotbar, mid-swing,
 * and fades out when you stop.
 *
 * <p>Reads {@link AbilityContext}, the same resolver the ability key uses, so the line always names
 * the skill the key would actually fire.
 */
@EventBusSubscriber(modid = Proficiency.MOD_ID, value = Dist.CLIENT)
public final class SkillHud {

    /**
     * Real milliseconds, not frames. This counted down once per rendered frame, so the line lasted
     * about a quarter of a second at 240 fps and about four at 15, which made it feel broken on
     * exactly the machines where it mattered.
     */
    private static final long VISIBLE_MS = 3000;

    private static final long FADE_MS = 500;
    private static final int BAR_WIDTH = 70;

    /**
     * Label baseline, measured up from the bottom of the screen. It sat at 60, exactly on vanilla's
     * held-item name (drawn at height - 59) and just under the action bar (height - 72 to - 63), so
     * picking up a sword printed the skill line straight over the sword's name. At 88 the label and
     * its bar end at height - 75, clear of both.
     */
    private static final int LINE_FROM_BOTTOM = 88;

    private static Skill lastSkill;
    private static int lastLevel = -1;
    private static float lastProgress = -1f;
    private static long shownAt;

    /** The XP-gain dots and the animated fill; see {@link XpGainDots}. */
    private static final XpGainDots DOTS = new XpGainDots();

    private SkillHud() {
    }

    @SubscribeEvent
    public static void onRenderGui(RenderGuiEvent.Post event) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player == null || minecraft.options.hideGui) {
            return;
        }

        Skill skill = AbilityContext.current(minecraft.player);
        if (skill == null) {
            // Forget it, so gains made while nothing showed are not flown in later.
            lastSkill = null;
            return;
        }
        PlayerSkills skills = ProficiencyAttachments.of(minecraft.player);

        // Show it when something changed, then let it fade rather than sitting there forever.
        float progress = skills.progress(skill);
        int level = skills.level(skill);
        long now = System.currentTimeMillis();
        double real = value(level, progress);
        // Level is part of the key as well as progress: dying at level 0 leaves progress at exactly
        // 0.0 both sides of the respawn, and without this the line would never come back.
        if (skill != lastSkill || progress != lastProgress || level != lastLevel) {
            boolean animate = skill == lastSkill && ProficiencyClientConfig.hudXpDots();
            if (animate) {
                DOTS.gain(value(lastLevel, lastProgress), real, now);
            } else {
                DOTS.snap(real, now);
            }
            lastSkill = skill;
            lastProgress = progress;
            lastLevel = level;
            shownAt = now;
        }
        if (!ProficiencyClientConfig.hudXpDots()) {
            DOTS.snap(real, now);
        }
        double shown = DOTS.update(real, now);
        // Keep the line up while anything is still flying in; the fade starts when it has landed.
        if (DOTS.busy(real, now)) {
            shownAt = now;
        }

        long elapsed = now - shownAt;
        if (elapsed > VISIBLE_MS) {
            return;
        }

        GuiGraphics graphics = event.getGuiGraphics();
        int centreX = graphics.guiWidth() / 2;
        int y = graphics.guiHeight() - LINE_FROM_BOTTOM;
        int accent = SkillPalette.accent(skill.category());
        long remaining = VISIBLE_MS - elapsed;
        int alpha = (int) (255 * Math.min(1.0, remaining / (double) FADE_MS)) << 24;

        // The number turns over when the bar does, not when the packet arrives.
        int shownLevel = Math.min(level, (int) Math.floor(shown + 1e-6));
        Component label = Component.translatable("proficiency.hud.line",
                Component.translatable(skill.translationKey()), shownLevel);
        // The streak rides on the same line: it multiplies exactly the XP this line shows.
        int stacks = SurvivalStreak.stacks(skills);
        if (stacks > 0) {
            label = label.copy()
                    .append(Component.literal("  "))
                    // U+E000 is the two-chevron glyph this mod adds to the default font.
                    .append(Component.literal("\uE000").withStyle(ChatFormatting.GOLD))
                    .append(Component.translatable("proficiency.hud.streak", SurvivalStreak.percent(stacks))
                            .withStyle(ChatFormatting.GOLD));
        }
        int labelWidth = minecraft.font.width(label);
        graphics.drawString(minecraft.font, label,
                centreX - labelWidth / 2, y, (accent & 0x00FFFFFF) | alpha, true);

        int barLeft = centreX - BAR_WIDTH / 2;
        int barTop = y + 11;
        graphics.fill(barLeft, barTop, barLeft + BAR_WIDTH, barTop + 2,
                (SkillPalette.TRACK & 0x00FFFFFF) | alpha);
        int filled = (int) Math.round(BAR_WIDTH * fill(shown));
        if (filled > 0) {
            graphics.fill(barLeft, barTop, barLeft + filled, barTop + 2,
                    (accent & 0x00FFFFFF) | alpha);
        }
        drawDots(graphics, barLeft + filled, barTop + 1, accent, alpha, now);

        // Above the label, not below the bar: below is the item-name and action-bar zone.
        if (ActiveService.isFrenzied(minecraft.player, skill)) {
            Component frenzy = Component.translatable(skill.activeKey());
            int width = minecraft.font.width(frenzy);
            graphics.drawString(minecraft.font, frenzy,
                    centreX - width / 2, y - 11, 0xFFF2D98A, true);
        }
    }

    /** Level + progress; a maxed skill is exactly {@link SkillMath#MAX_LEVEL} (its progress reads 1). */
    private static double value(int level, float progress) {
        return level >= SkillMath.MAX_LEVEL ? SkillMath.MAX_LEVEL : level + progress;
    }

    /** The bar's fill for an animated value: its fraction, and a full bar at the top level. */
    private static double fill(double shown) {
        if (shown >= SkillMath.MAX_LEVEL) {
            return 1.0;
        }
        return Math.max(0.0, Math.min(1.0, shown - Math.floor(shown)));
    }

    /**
     * The dots: each flies on a quadratic curve from just past the right edge of the screen to
     * the fill edge ({@code edgeX}, {@code edgeY}), with a two-pixel trail, and the edge glows
     * when one lands. A few {@code fill} calls per dot and none at all when nothing is flying.
     */
    private static void drawDots(GuiGraphics graphics, int edgeX, int edgeY, int accent, int alpha,
            long now) {
        float flash = DOTS.flash(now);
        int fade = alpha >>> 24;
        if (flash > 0) {
            int a = (int) (fade * flash);
            graphics.fill(edgeX - 1, edgeY - 2, edgeX + 1, edgeY + 2, (a << 24) | 0xFFFFFF);
        }
        if (DOTS.dots().isEmpty()) {
            return;
        }
        int head = (fade << 24) | (brighten(accent) & 0x00FFFFFF);
        int startX = graphics.guiWidth() + 3;
        for (XpGainDots.Dot dot : DOTS.dots()) {
            double t = XpGainDots.progress(dot, now);
            if (t < 0) {
                continue;
            }
            float startY = edgeY - 34 + dot.lane() * 26;
            // Control point above the straight line, so the dot arcs over and drops in.
            float controlX = startX + (edgeX - startX) * 0.45f;
            float controlY = Math.min(startY, edgeY) - 22 - dot.lane() * 12;
            for (int step = 2; step >= 0; step--) {
                double e = XpGainDots.ease(t - step * 0.045);
                if (e <= 0) {
                    continue;
                }
                double u = 1 - e;
                int x = (int) Math.round(u * u * startX + 2 * u * e * controlX + e * e * edgeX);
                int y = (int) Math.round(u * u * startY + 2 * u * e * controlY + e * e * edgeY);
                if (step == 0) {
                    graphics.fill(x - 1, y - 1, x + 1, y + 1, head);
                } else {
                    int a = (int) (fade * (step == 1 ? 0.55f : 0.25f));
                    graphics.fill(x, y, x + 1, y + 1, (a << 24) | (accent & 0x00FFFFFF));
                }
            }
        }
    }

    /** Half way from the accent to white: the dot's head reads against both the world and the bar. */
    private static int brighten(int argb) {
        int r = (((argb >> 16) & 0xFF) + 255) / 2;
        int g = (((argb >> 8) & 0xFF) + 255) / 2;
        int b = ((argb & 0xFF) + 255) / 2;
        return (r << 16) | (g << 8) | b;
    }
}
