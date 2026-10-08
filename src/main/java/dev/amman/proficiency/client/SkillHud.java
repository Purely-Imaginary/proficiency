package dev.amman.proficiency.client;

import dev.amman.proficiency.Proficiency;
import dev.amman.proficiency.ProficiencyAttachments;
import dev.amman.proficiency.config.ProficiencyClientConfig;
import dev.amman.proficiency.skill.ActiveService;
import dev.amman.proficiency.skill.PlayerSkills;
import dev.amman.proficiency.skill.Skill;
import dev.amman.proficiency.skill.SkillMath;
import dev.amman.proficiency.skill.SurvivalStreak;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import dev.amman.proficiency.platform.bus.Dist;
import dev.amman.proficiency.platform.bus.SubscribeEvent;
import dev.amman.proficiency.platform.bus.EventBusSubscriber;
import dev.amman.proficiency.platform.client.event.RenderGuiEvent;

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
    /** The skill and displayed level seen last frame, to catch the number turning over. */
    private static final int[] SEEN = newSeen();

    private static final int GOLD_FLASH = 0xFFD24A;
    /** Gap between the level and the badge. */
    private static final int GAP_WIDTH = 6;

    // The label's pieces, rebuilt only when the skill, the level, the percent or the language moves.
    private static Skill cSkill;
    private static String cLanguage;
    private static String cPrefix = "";
    private static String cSuffix = "";
    private static int cPrefixW;
    private static int cSuffixW;
    private static int cLevel = -1;
    private static String cNum = "";
    private static int cNumW;
    private static int cPercent = -1;
    private static String cPct = "";
    private static int cPctW;
    private static String cPctLanguage;
    private static int cOldLevel = -1;
    private static String cOld = "";
    private static int cOldW;

    /** The XP-gain dots and the animated fill; see {@link XpGainDots}. */
    private static final XpGainDots DOTS = new XpGainDots();

    private SkillHud() {
    }

    private static int[] newSeen() {
        int[] seen = new int[Skill.VALUES.length];
        java.util.Arrays.fill(seen, -1);
        return seen;
    }

    /** Forgets everything the HUD remembers. Called from the loader's logout hook. */
    static void reset() {
        lastSkill = null;
        lastProgress = -1f;
        lastLevel = -1;
        java.util.Arrays.fill(SEEN, -1);
        cSkill = null;
        cLevel = -1;
        cPercent = -1;
        cOldLevel = -1;
        DOTS.snap(0, 0);
    }

    @SubscribeEvent
    public static void onRenderGui(RenderGuiEvent.Post event) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player == null) {
            // Left the world: nothing seen there may leak into the next one. The loader's logout
            // hook does this too; this catches a frame that slips in between.
            HudState.reset();
            return;
        }
        // The skills panel's activity glow and sparklines, fed whether or not the HUD is shown.
        long now = System.currentTimeMillis();
        SkillActivity.observe(ProficiencyAttachments.of(minecraft.player), net.minecraft.Util.getMillis());
        if (minecraft.options.hideGui) {
            // Nothing is drawn, so nothing may be queued up to play when the GUI comes back: a
            // level gained now would otherwise replay as a borrowed line long after it happened.
            LevelUpFx.takeQueuedSkill();
            lastSkill = null;
            PlayerSkills hidden = ProficiencyAttachments.of(minecraft.player);
            for (Skill skill : Skill.VALUES) {
                SEEN[skill.ordinal()] = hidden.level(skill);
            }
            return;
        }

        GuiGraphics graphics = event.getGuiGraphics();
        PlayerSkills skills = ProficiencyAttachments.of(minecraft.player);
        Skill held = AbilityContext.current(minecraft.player);

        // Every skill but the one the line shows keeps its last-seen level equal to the real one,
        // so a level gained while it was not on show (a borrowed line, another world) is never
        // replayed when it is picked up again. The shown one is compared against the number on
        // screen below, which is what makes the roll land with the bar.
        for (Skill other : Skill.VALUES) {
            if (other != lastSkill || SEEN[other.ordinal()] < 0) {
                SEEN[other.ordinal()] = skills.level(other);
            }
        }
        StreakBadge.observe(SurvivalStreak.percent(SurvivalStreak.stacks(skills)), now);
        AbilityFx.render(graphics, minecraft.player, skills, held, event.getPartialTick().getGameTimeDeltaPartialTick(false), now);
        DeathRecapHud.render(graphics, minecraft, now);

        // A level gained on a skill the HUD is not showing borrows the line for a moment.
        Skill queued = LevelUpFx.takeQueuedSkill();
        if (queued != null && ProficiencyClientConfig.hudLevelUpFx() && queued != held) {
            int level = LevelUpFx.queuedLevel();
            LevelUpFx.start(queued, level - 1, level, now, true);
        }
        if (ProficiencyClientConfig.hudLevelUpFx() && LevelUpFx.lineUp(now)) {
            Skill skill = LevelUpFx.skill();
            int level = Math.max(skills.level(skill), LevelUpFx.newLevel());
            long elapsed = now - LevelUpFx.startedAt();
            long remaining = LevelUpFx.lineMs() - elapsed;
            int alpha = (int) (255 * Math.min(1.0, remaining / (double) FADE_MS));
            drawLine(graphics, minecraft, skills, skill, level, value(level, skills.progress(skill)),
                    alpha, now, false);
            return;
        }

        Skill skill = held;
        if (skill == null) {
            // Forget it, so gains made while nothing showed are not flown in later.
            lastSkill = null;
            return;
        }

        // Show it when something changed, then let it fade rather than sitting there forever.
        float progress = skills.progress(skill);
        int level = skills.level(skill);
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

        // The number turns over when the bar does, not when the packet arrives.
        int shownLevel = Math.min(level, (int) Math.floor(shown + 1e-6));
        int seen = SEEN[skill.ordinal()];
        if (shownLevel > seen && ProficiencyClientConfig.hudLevelUpFx()) {
            LevelUpFx.start(skill, seen, shownLevel, now, false);
        }
        SEEN[skill.ordinal()] = shownLevel;
        // The effect and the line it lives on outlast the bar's own timer.
        if (ProficiencyClientConfig.hudLevelUpFx() && LevelUpFx.skill() == skill && LevelUpFx.active(now)) {
            shownAt = Math.max(shownAt, now);
        }
        // So does a streak that just broke, so the badge is seen going.
        if (ProficiencyClientConfig.hudStreakFx() && StreakBadge.holding(now)) {
            shownAt = now;
        }

        long elapsed = now - shownAt;
        if (elapsed > VISIBLE_MS) {
            return;
        }
        long remaining = VISIBLE_MS - elapsed;
        int alpha = (int) (255 * Math.min(1.0, remaining / (double) FADE_MS));
        drawLine(graphics, minecraft, skills, skill, shownLevel, shown, alpha, now, true);

        // Above the label, not below the bar: below is the item-name and action-bar zone.
        if (ActiveService.isFrenzied(minecraft.player, skill)) {
            String frenzy = TextFit.clip(minecraft.font, Component.translatable(skill.activeKey()).getString(),
                    graphics.guiWidth() - 16);
            int width = minecraft.font.width(frenzy);
            graphics.drawString(minecraft.font, frenzy,
                    graphics.guiWidth() / 2 - width / 2, graphics.guiHeight() - LINE_FROM_BOTTOM - 11,
                    0xFFF2D98A, true);
        }
    }

    /**
     * The line itself: the skill name and level, the streak badge and its percent, the bar. The
     * level-up effect (flash, rolling number, ring) and the streak effects hang off it, each behind
     * its own switch. {@code alpha} is 0 to 255; {@code dots} says whether the XP dots belong to
     * this skill (they do not on a borrowed line).
     */
    private static void drawLine(GuiGraphics graphics, Minecraft minecraft, PlayerSkills skills, Skill skill,
            int shownLevel, double shown, int alpha, long now, boolean dots) {
        Font font = minecraft.font;
        int centreX = graphics.guiWidth() / 2;
        int y = graphics.guiHeight() - LINE_FROM_BOTTOM;
        int accent = SkillPalette.accent(skill.category());
        int rgb = accent & 0x00FFFFFF;
        int a24 = alpha << 24;

        boolean levelFx = ProficiencyClientConfig.hudLevelUpFx() && LevelUpFx.skill() == skill
                && LevelUpFx.active(now);
        boolean gold = levelFx && LevelUpFx.gold();
        float flash = levelFx ? LevelUpFx.flash(now) : 0f;
        boolean rolling = levelFx && LevelUpFx.roll(now) < 1f;
        boolean streakFx = ProficiencyClientConfig.hudStreakFx();

        labelCache(minecraft, skill, shownLevel);
        int stacks = SurvivalStreak.stacks(skills);
        boolean dead = streakFx && StreakBadge.holding(now);
        int percent = dead ? StreakBadge.breakPercent() : (stacks > 0 ? SurvivalStreak.percent(stacks) : 0);
        boolean badge = dead || stacks > 0;
        if (badge) {
            pctCache(font, percent);
        }
        int total = cPrefixW + cNumW + cSuffixW;
        if (badge) {
            total += GAP_WIDTH + StreakBadge.WIDTH + cPctW;
        }
        // A name too long for the screen is cut so the level and the badge stay whole.
        String prefix = cPrefix;
        int prefixWidth = cPrefixW;
        int available = graphics.guiWidth() - 16;
        if (total > available) {
            prefix = TextFit.clip(font, cPrefix, Math.max(font.width("\u2026"), cPrefixW - (total - available)));
            prefixWidth = font.width(prefix);
            total = total - cPrefixW + prefixWidth;
            TextFit.note("hud.line");
        }
        int x = centreX - total / 2;
        // The skill's icon hangs left of the text, so the text stays centred over the bar. It goes
        // rather than run off a narrow screen.
        int icon = SkillIcons.enabled()
                ? SkillIcons.fit(graphics.guiWidth() - 8 - SkillIcons.advance(SkillIcons.SMALL), total,
                        SkillIcons.SMALL) : 0;
        if (icon > 0) {
            SkillIcons.draw(graphics, skill, x - SkillIcons.advance(icon), SkillIcons.smallTop(y), icon, alpha);
        }

        int base = rgb;
        if (flash > 0f) {
            base = StreakBadge.lerp(rgb, gold ? GOLD_FLASH : 0xFFFFFF, Math.min(1f, flash * 0.85f));
        }
        int colour = a24 | base;
        graphics.drawString(font, prefix, x, y, colour, true);
        int numX = x + prefixWidth;
        if (rolling && alpha > 8) {
            float roll = LevelUpFx.roll(now);
            int slide = Math.round(roll * 9);
            int oldAlpha = Math.round(alpha * (1f - roll));
            int newAlpha = Math.round(alpha * roll);
            if (LevelUpFx.oldLevel() != cOldLevel) {
                cOld = Integer.toString(LevelUpFx.oldLevel());
                cOldW = font.width(cOld);
                cOldLevel = LevelUpFx.oldLevel();
            }
            graphics.enableScissor(numX - 1, y - 1, numX + Math.max(cNumW, cOldW) + 1, y + 9);
            if (oldAlpha > 8) {
                graphics.drawString(font, cOld, numX, y - slide, (oldAlpha << 24) | base, true);
            }
            if (newAlpha > 8) {
                graphics.drawString(font, cNum, numX, y + 9 - slide, (newAlpha << 24) | base, true);
            }
            graphics.disableScissor();
        } else {
            graphics.drawString(font, cNum, numX, y, colour, true);
        }
        int cursor = numX + cNumW;
        if (!cSuffix.isEmpty()) {
            graphics.drawString(font, cSuffix, cursor, y, colour, true);
            cursor += cSuffixW;
        }
        if (badge) {
            int badgeX = cursor + GAP_WIDTH;
            int pctColour = StreakBadge.GOLD;
            // The percent fades with the breaking badge; the bar and the burst below keep their own.
            int badgeAlpha = alpha;
            if (!streakFx) {
                graphics.drawString(font, StreakBadge.GLYPH, badgeX, y, a24 | StreakBadge.GOLD, true);
            } else if (dead) {
                StreakBadge.drawDead(graphics, font, badgeX, y, alpha, now);
                float t = StreakBadge.breakT(now);
                if (t > 0f) {
                    badgeAlpha = Math.round(alpha * (1f - t));
                }
            } else {
                StreakBadge.draw(graphics, font, badgeX, y, SurvivalStreak.stepProgress(skills), alpha, now);
                pctColour = StreakBadge.lerp(StreakBadge.GOLD, 0xFFFFFF, StreakBadge.flare(now));
            }
            if (badgeAlpha > 8) {
                graphics.drawString(font, cPct, badgeX + StreakBadge.WIDTH, y, (badgeAlpha << 24) | pctColour,
                        true);
            }
        }

        int barLeft = centreX - BAR_WIDTH / 2;
        int barTop = y + 11;
        graphics.fill(barLeft, barTop, barLeft + BAR_WIDTH, barTop + 2,
                (SkillPalette.TRACK & 0x00FFFFFF) | a24);
        int filled = (int) Math.round(BAR_WIDTH * fill(shown));
        if (filled > 0) {
            graphics.fill(barLeft, barTop, barLeft + filled, barTop + 2, a24 | rgb);
        }
        if (flash > 0f) {
            int glow = Math.round(alpha * flash * 0.85f);
            if (glow > 8) {
                graphics.fill(barLeft - 1, barTop - 1, barLeft + BAR_WIDTH + 1, barTop + 3,
                        (glow << 24) | (gold ? GOLD_FLASH : 0xFFFFFF));
            }
        }
        if (levelFx) {
            levelUpBurst(graphics, centreX, barTop + 1, accent, gold, alpha, now);
        }
        if (dots) {
            drawDots(graphics, barLeft + filled, barTop + 1, accent, a24, now);
        }
    }

    /**
     * The ring that leaves the bar: an ellipse of 64 pixels growing out from the bar and fading in
     * about 0.6 s. The tenth-level version is bigger, runs 1.4 s, has a second ring a beat behind
     * and ten gold sparkles drifting up and twinkling. Pixels only, a table for the angles.
     */
    private static void levelUpBurst(GuiGraphics graphics, int cx, int cy, int accent, boolean gold,
            int alpha, long now) {
        float t = LevelUpFx.t(now);
        if (t < 0f || t >= 1f) {
            return;
        }
        int ringRgb = gold ? GOLD_FLASH : brighten(accent) & 0x00FFFFFF;
        float e = 1f - (1f - t) * (1f - t) * (1f - t);
        float reach = gold ? 64f : 44f;
        float lift = gold ? 26f : 15f;
        ringFx(graphics, cx, cy, 8f + e * reach, 2f + e * lift, (1f - t), alpha, ringRgb, 1);
        if (gold) {
            float t2 = t - 0.18f;
            if (t2 > 0f) {
                float e2 = 1f - (1f - t2) * (1f - t2) * (1f - t2);
                ringFx(graphics, cx, cy, 6f + e2 * 48f, 2f + e2 * 19f, (1f - t2), alpha, 0xFFFFFF, 2);
            }
            for (int k = 0; k < 10; k++) {
                if (((now / 70) + k) % 3 == 0) {
                    continue;
                }
                int index = (k * 23) % 64;
                float spread = 0.5f + 0.5f * ((k % 5) / 4f);
                int px = cx + Math.round(HudMath.X64[index] * (8f + e * reach) * spread);
                int py = cy + Math.round(HudMath.Y64[index] * (4f + e * lift) * 1.5f * spread - e * 7f);
                int a = Math.round(alpha * (1f - t) * 0.95f);
                if (a <= 8) {
                    continue;
                }
                int c = (a << 24) | (k % 2 == 0 ? GOLD_FLASH : 0xFFFFFF);
                graphics.fill(px, py, px + 2, py + 2, c);
                if (k % 3 == 1) {
                    graphics.fill(px - 1, py, px, py + 1, (a / 2 << 24) | GOLD_FLASH);
                    graphics.fill(px + 2, py, px + 3, py + 1, (a / 2 << 24) | GOLD_FLASH);
                    graphics.fill(px, py - 1, px + 1, py, (a / 2 << 24) | GOLD_FLASH);
                    graphics.fill(px, py + 2, px + 1, py + 3, (a / 2 << 24) | GOLD_FLASH);
                }
            }
        }
    }

    /**
     * The level-up ring: {@link HudMath#ring} faded by {@code fade}, two pixels square while young
     * and bright, one as it thins. {@code step} 2 draws every second pixel, a lighter trailing ring.
     */
    private static void ringFx(GuiGraphics graphics, int cx, int cy, float rx, float ry, float fade, int alpha,
            int rgb, int step) {
        int a = Math.round(alpha * fade * 0.9f);
        if (a > 8) {
            HudMath.ring(graphics, cx, cy, rx, ry, (a << 24) | rgb, fade > 0.5f ? 2 : 1, step, 64);
        }
    }

    /** Rebuilds the cached pieces of the label when the skill, the level or the language changed. */
    private static void labelCache(Minecraft minecraft, Skill skill, int level) {
        Font font = minecraft.font;
        String language = minecraft.getLanguageManager().getSelected();
        if (skill != cSkill || !language.equals(cLanguage)) {
            // The line is "%s %s" or something else in another language; find where the number goes
            // by formatting it with a marker, so the number can move on its own.
            String full = Component.translatable("proficiency.hud.line",
                    Component.translatable(skill.translationKey()), "\u0001").getString();
            int at = full.indexOf('\u0001');
            cPrefix = at < 0 ? full : full.substring(0, at);
            cSuffix = at < 0 ? "" : full.substring(at + 1);
            cPrefixW = font.width(cPrefix);
            cSuffixW = font.width(cSuffix);
            cSkill = skill;
            cLanguage = language;
        }
        if (level != cLevel) {
            cNum = Integer.toString(level);
            cNumW = font.width(cNum);
            cLevel = level;
        }
    }

    private static void pctCache(Font font, int percent) {
        String language = Minecraft.getInstance().getLanguageManager().getSelected();
        if (percent != cPercent || !language.equals(cPctLanguage)) {
            cPctLanguage = language;
            cPct = Component.translatable("proficiency.hud.streak", percent).getString();
            cPctW = font.width(cPct);
            cPercent = percent;
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
