package dev.amman.proficiency.client;

import dev.amman.proficiency.config.ProficiencyClientConfig;
import dev.amman.proficiency.skill.PlayerSkills;
import dev.amman.proficiency.skill.Skill;
import dev.amman.proficiency.skill.SkillMath;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import org.jetbrains.annotations.Nullable;

/**
 * The recent-skills stack above the HUD skill line (ui.hudStack, 1.54.0). The rules live in {@link HudStack};
 * this senses which skills earned XP this frame, hands the gains to the rules and draws the rows.
 *
 * <p>A row is one text line: the skill's icon and "Name level" right-aligned to a fixed axis, and a thin bar left
 * aligned on it, so the bars of all rows line up in one column. The row's own {@link XpGainDots} fly into its bar
 * through the same {@link DotFlight} as the main line, its Tempo flames burn at half size on the bar
 * ({@link TempoBadge#drawSmall}), and a level or a Mastery star pops on the row itself.
 *
 * <p>Compiled unchanged for Forge 1.20.1 (forge-1.20.1/build.gradle, sharedHearthJava).
 */
public final class HudStackRows {

    /** Height of one row in GUI pixels: a font line and a 3 px gap. */
    static final int ROW_H = 11;
    /** Length of a row's bar. */
    static final int BAR = 40;
    /** The bars' left end sits this far right of the screen centre; the labels end 4 px left of it. */
    static final int AXIS = 14;
    /** Longest label before it is cut. */
    static final int MAX_LABEL = 96;
    /** Gap from the main line's label (or its frenzy line) to the lowest row. */
    static final int BASE_GAP = 4;

    private static final int GOLD_FLASH = 0xFFD24A;

    private static final HudStack STACK = new HudStack();
    /** Each skill's bar value last frame ({@link BarValue}), -1 before the first look. */
    private static final double[] PREV = newPrev();
    /** When each skill last earned XP, on {@code System.currentTimeMillis}. */
    private static final long[] GAIN_AT = newGainAt();
    /** The skills that earned XP this frame and the value they came from. */
    private static final boolean[] GAINED = new boolean[Skill.VALUES.length];
    private static final double[] FROM = new double[Skill.VALUES.length];

    // The main line's skill as the stack sees it (after the movement hysteresis) and since when.
    @Nullable
    private static Skill main;
    private static long mainSince;

    private static long lastFrame;
    private static float base = Float.NaN;
    private static float shift;
    private static float hide;

    // Label pieces per skill, rebuilt when the language changes.
    private static final String[] PREFIX = new String[Skill.VALUES.length];
    private static final String[] SUFFIX = new String[Skill.VALUES.length];
    private static String language;

    /** The layout demo's film (LayoutDemo.hsFilm), called by SkillHud after every other HUD listener; null off. */
    static java.util.function.Consumer<Minecraft> filmSink;

    /** Last frame's column, for the layout audit: left, top, right, bottom in GUI pixels; empty when nothing drew. */
    static int[] lastRect = new int[0];

    private HudStackRows() {
    }

    private static double[] newPrev() {
        double[] prev = new double[Skill.VALUES.length];
        java.util.Arrays.fill(prev, -1);
        return prev;
    }

    private static long[] newGainAt() {
        long[] at = new long[Skill.VALUES.length];
        java.util.Arrays.fill(at, Long.MIN_VALUE / 2);
        return at;
    }

    public static boolean enabled() {
        return ProficiencyClientConfig.uiHudStack();
    }

    static void reset() {
        STACK.clear();
        java.util.Arrays.fill(PREV, -1);
        java.util.Arrays.fill(GAIN_AT, Long.MIN_VALUE / 2);
        java.util.Arrays.fill(GAINED, false);
        main = null;
        base = Float.NaN;
        shift = 0f;
        hide = 0f;
        lastRect = new int[0];
    }

    /** The GUI is hidden (F1): forget the rows, keep the values so nothing is flown in when it returns. */
    static void hidden(PlayerSkills skills) {
        STACK.clear();
        for (Skill skill : Skill.VALUES) {
            PREV[skill.ordinal()] = BarValue.value(skills, skill);
        }
        base = Float.NaN;
        lastRect = new int[0];
    }

    /** Step 1 of a frame: which skills earned XP since the last frame. */
    static void sense(PlayerSkills skills, long now) {
        for (Skill skill : Skill.VALUES) {
            int i = skill.ordinal();
            double value = BarValue.value(skills, skill);
            double prev = PREV[i];
            GAINED[i] = prev >= 0 && value > prev + 1e-9;
            FROM[i] = prev;
            if (GAINED[i]) {
                GAIN_AT[i] = now;
            } else if (prev >= 0 && value < prev - 1e-9) {
                // A loss (death wipes the bar): the row, if any, shows it as it is.
                HudStack.Row row = STACK.row(skill);
                if (row != null) {
                    row.dots.snap(value, now);
                    row.shownWhole = (int) Math.floor(value + 1e-6);
                }
            }
            PREV[i] = value;
        }
    }

    /**
     * Step 2: the main line's skill with the movement hysteresis ({@link HudStack#holdMain}). {@code picked} is
     * {@link dev.amman.proficiency.skill.HudSkillPick}'s answer.
     */
    @Nullable
    static Skill hold(@Nullable Skill picked, long now) {
        long sinceGain = main == null ? Long.MAX_VALUE : now - GAIN_AT[main.ordinal()];
        Skill next = HudStack.holdMain(picked, main, now - mainSince, sinceGain, now - GAIN_AT[Skill.RUNNING.ordinal()]);
        if (next != main) {
            main = next;
            mainSince = now;
        }
        return next;
    }

    /** Step 3: hands this frame's gains to the rules and draws the rows above the line at {@code lineY}. */
    static void render(GuiGraphics graphics, Minecraft minecraft, PlayerSkills skills, @Nullable Skill mainSkill,
            int lineY, boolean frenzy, long now) {
        long dt = lastFrame == 0 ? 0 : Math.max(0, now - lastFrame);
        lastFrame = now;
        boolean motion = ClickFx.motion();
        boolean dotsOn = ProficiencyClientConfig.hudXpDots();
        for (Skill skill : Skill.VALUES) {
            int i = skill.ordinal();
            if (!GAINED[i]) {
                continue;
            }
            boolean fresh = STACK.row(skill) == null;
            HudStack.Row row = STACK.gain(skill, mainSkill, now);
            if (row == null) {
                continue;
            }
            double to = PREV[i];
            if (fresh) {
                // A new row starts at the value before this gain, so the gain flies in and a level crossed pops.
                row.dots.snap(FROM[i], now);
                row.shownWhole = (int) Math.floor(FROM[i] + 1e-6);
            }
            if (dotsOn) {
                row.dots.gain(FROM[i], to, now, Math.max(0, HudStack.ROW_DOTS - row.dots.inAir()));
            } else {
                row.dots.snap(to, now);
            }
        }
        STACK.tick(mainSkill, now, dt, ProficiencyClientConfig.uiHudStackLingerMs(), motion);
        if (STACK.rows().isEmpty()) {
            lastRect = new int[0];
            base = Float.NaN;
            return;
        }

        // The lowest row sits just above the main line's label, rising with its Tempo lift and its frenzy line.
        float target = lineY - BASE_GAP - TempoBadge.currentLift() - (frenzy ? 11 : 0);
        if (Float.isNaN(base) || !motion) {
            base = target;
        } else {
            base += (float) ((target - base) * (1 - Math.exp(-dt / 70.0)));
        }

        Font font = minecraft.font;
        labels(minecraft);
        int width = graphics.guiWidth();
        int axis = width / 2 + AXIS;

        // The column, for the chat check: the widest label and the bar.
        int widest = 0;
        for (HudStack.Row row : STACK.rows()) {
            widest = Math.max(widest, labelWidth(font, row.skill(), skills.level(row.skill())));
        }
        int icon = SkillIcons.enabled() ? SkillIcons.advance(SkillIcons.SMALL) : 0;
        int left = axis - 4 - widest - icon;
        int right = axis + BAR + 2;
        int top = Math.round(base - STACK.height() * ROW_H);
        int bottom = Math.round(base);
        int[] chat = ChatBox.of(minecraft, graphics.guiHeight());
        int dodge = chat == null ? 0
                : HudStack.dodgeChat(left, right - left, top, bottom, chat[0], chat[1], chat[2], width);
        float hideTarget = dodge == Integer.MIN_VALUE ? 1f : 0f;
        float shiftTarget = dodge == Integer.MIN_VALUE ? shift : dodge;
        float k = motion ? (float) (1 - Math.exp(-dt / 90.0)) : 1f;
        shift += (shiftTarget - shift) * k;
        hide += (hideTarget - hide) * (float) (1 - Math.exp(-dt / 90.0));
        if (Math.abs(shift - shiftTarget) < 0.5f) {
            shift = shiftTarget;
        }
        int dx = Math.round(shift);
        float shown = 1f - hide;
        if (shown < 0.03f) {
            lastRect = new int[0];
            return;
        }
        lastRect = new int[] {left + dx, top, right + dx, bottom};

        for (HudStack.Row row : STACK.rows()) {
            int alpha = Math.round(255 * row.alpha(now) * shown);
            if (alpha <= 8) {
                continue;
            }
            // A new row rises 3 px into place as it fades in.
            float rise = motion ? (1f - Math.min(1f, (now - row.born()) / (float) HudStack.FADE_IN_MS)) * 3f : 0f;
            int y = Math.round(base - (row.slot() + 1) * ROW_H + rise);
            drawRow(graphics, font, skills, row, axis + dx, y, alpha, now, motion, dotsOn);
        }
    }

    private static void drawRow(GuiGraphics graphics, Font font, PlayerSkills skills, HudStack.Row row, int axis,
            int y, int alpha, long now, boolean motion, boolean dotsOn) {
        Skill skill = row.skill();
        int accent = SkillPalette.accent(skill.category());
        int rgb = accent & 0x00FFFFFF;
        int a24 = alpha << 24;

        double real = BarValue.value(skills, skill);
        double shown = row.dots.update(real, now);
        int level = skills.level(skill);
        int whole = (int) Math.floor(shown + 1e-6);
        // The number turns over when the bar does, like the main line.
        if (row.shownWhole >= 0 && whole > row.shownWhole && ProficiencyClientConfig.hudLevelUpFx()) {
            boolean star = whole > SkillMath.MAX_LEVEL;
            row.pop(row.shownWhole, whole, star, now);
            if (!star) {
                // The coin leaves from this row's bar (its centre), not the main line's.
                HudFlights.onLevelUp(skill, row.shownWhole, whole, axis + BAR / 2f, y + 5);
            }
        }
        row.shownWhole = whole;
        int shownLevel = Math.min(level, Math.min(SkillMath.MAX_LEVEL, whole));

        float flash = row.popFlash(now);
        boolean gold = row.popGold() || row.popStar();
        int base = flash > 0f ? StreakBadge.lerp(rgb, gold ? GOLD_FLASH : 0xFFFFFF, Math.min(1f, flash * 0.85f)) : rgb;
        int colour = a24 | base;

        // Label, right-aligned to the axis: "Name " + number (+ suffix in languages that put it after).
        String prefix = PREFIX[skill.ordinal()];
        String suffix = SUFFIX[skill.ordinal()];
        String num = Integer.toString(shownLevel);
        int numW = font.width(num);
        int suffixW = font.width(suffix);
        int prefixRoom = MAX_LABEL - numW - suffixW;
        if (font.width(prefix) > prefixRoom) {
            prefix = TextFit.clip(font, prefix, Math.max(font.width("…"), prefixRoom));
            TextFit.note("hud.stack");
        }
        int prefixW = font.width(prefix);
        int textRight = axis - 4;
        int x = textRight - (prefixW + numW + suffixW);
        if (SkillIcons.enabled()) {
            SkillIcons.draw(graphics, skill, x - SkillIcons.advance(SkillIcons.SMALL), SkillIcons.smallTop(y),
                    SkillIcons.SMALL, alpha);
        }
        graphics.drawString(font, prefix, x, y, colour, true);
        int numX = x + prefixW;
        float roll = motion ? row.popRoll(now) : 1f;
        if (roll < 1f && row.popOld() != row.popNew() && !row.popStar()) {
            int slide = Math.round(roll * 9);
            int oldAlpha = Math.round(alpha * (1f - roll));
            int newAlpha = Math.round(alpha * roll);
            String old = Integer.toString(Math.min(SkillMath.MAX_LEVEL, row.popOld()));
            graphics.enableScissor(numX - 1, y - 1, numX + Math.max(numW, font.width(old)) + 1, y + 9);
            if (oldAlpha > 8) {
                graphics.drawString(font, old, numX, y - slide, (oldAlpha << 24) | base, true);
            }
            if (newAlpha > 8) {
                graphics.drawString(font, num, numX, y + 9 - slide, (newAlpha << 24) | base, true);
            }
            graphics.disableScissor();
        } else {
            graphics.drawString(font, num, numX, y, colour, true);
        }
        if (!suffix.isEmpty()) {
            graphics.drawString(font, suffix, numX + numW, y, colour, true);
        }

        // The bar: 2 px on the text's lower half, so the half-size flames rise beside the label, not into the row above.
        int barTop = y + 4;
        graphics.fill(axis, barTop, axis + BAR, barTop + 2, (SkillPalette.TRACK & 0x00FFFFFF) | a24);
        int filled = (int) Math.round(BAR * BarValue.fill(shown));
        int barRgb = shownLevel >= SkillMath.MAX_LEVEL && dev.amman.proficiency.skill.Mastery.maxStars() > 0
                ? SkillPalette.STAR_BAR & 0x00FFFFFF : rgb;
        if (filled > 0) {
            graphics.fill(axis, barTop, axis + filled, barTop + 2, a24 | barRgb);
        }
        TempoBadge.drawSmall(graphics, axis, barTop, BAR, filled, alpha, skill);
        if (flash > 0f) {
            int glow = Math.round(alpha * flash * 0.85f);
            if (glow > 8) {
                graphics.fill(axis - 1, barTop - 1, axis + BAR + 1, barTop + 3,
                        (glow << 24) | (gold ? GOLD_FLASH : 0xFFFFFF));
            }
        }
        if (motion && row.popping(now)) {
            popRing(graphics, axis + BAR / 2, barTop + 1, accent, row, alpha, now);
        }
        if (dotsOn) {
            DotFlight.fromRightEdge(graphics.guiWidth(), barTop + 1).draw(graphics, row.dots, axis + filled, barTop + 1,
                    accent, brighten(accent), alpha, now);
        }
    }

    /** The row's pop: one small ring off its bar, a second and four sparks for a gold level or a star. */
    private static void popRing(GuiGraphics graphics, int cx, int cy, int accent, HudStack.Row row, int alpha,
            long now) {
        float t = row.popT(now);
        boolean gold = row.popGold() || row.popStar();
        float e = 1f - (1f - t) * (1f - t) * (1f - t);
        int ringRgb = gold ? GOLD_FLASH : brighten(accent) & 0x00FFFFFF;
        float reach = gold ? 30f : 22f;
        int a = Math.round(alpha * (1f - t) * 0.9f);
        if (a > 8) {
            HudMath.ring(graphics, cx, cy, 6f + e * reach, 1.5f + e * 6f, (a << 24) | ringRgb, t < 0.5f ? 2 : 1, 1, 64);
        }
        if (!gold) {
            return;
        }
        float t2 = t - 0.18f;
        if (t2 > 0f) {
            float e2 = 1f - (1f - t2) * (1f - t2) * (1f - t2);
            int a2 = Math.round(alpha * (1f - t2) * 0.8f);
            if (a2 > 8) {
                HudMath.ring(graphics, cx, cy, 4f + e2 * 24f, 1.5f + e2 * 5f, (a2 << 24) | 0xFFFFFF, 1, 2, 64);
            }
        }
        for (int k = 0; k < 4; k++) {
            if (((now / 70) + k) % 3 == 0) {
                continue;
            }
            int index = (k * 23 + 5) % 64;
            int px = cx + Math.round(HudMath.X64[index] * (8f + e * reach) * 0.8f);
            int py = cy + Math.round(HudMath.Y64[index] * (3f + e * 6f) * 1.5f - e * 5f);
            int sa = Math.round(alpha * (1f - t) * 0.95f);
            if (sa > 8) {
                graphics.fill(px, py, px + 2, py + 2, (sa << 24) | (k % 2 == 0 ? GOLD_FLASH : 0xFFFFFF));
            }
        }
    }

    private static int labelWidth(Font font, Skill skill, int level) {
        int w = font.width(PREFIX[skill.ordinal()]) + font.width(Integer.toString(Math.min(level, SkillMath.MAX_LEVEL)))
                + font.width(SUFFIX[skill.ordinal()]);
        return Math.min(MAX_LABEL, w);
    }

    /** "Name " and the suffix around the number, from the main line's own "%s %s" pattern, per language. */
    private static void labels(Minecraft minecraft) {
        String lang = minecraft.getLanguageManager().getSelected();
        if (lang.equals(language) && PREFIX[0] != null) {
            return;
        }
        for (Skill skill : Skill.VALUES) {
            String full = Component.translatable("proficiency.hud.line",
                    Component.translatable(skill.translationKey()), "\u0001").getString();
            int at = full.indexOf('\u0001');
            PREFIX[skill.ordinal()] = at < 0 ? full : full.substring(0, at);
            SUFFIX[skill.ordinal()] = at < 0 ? "" : full.substring(at + 1);
        }
        language = lang;
    }

    private static int brighten(int argb) {
        int r = (((argb >> 16) & 0xFF) + 255) / 2;
        int g = (((argb >> 8) & 0xFF) + 255) / 2;
        int b = ((argb & 0xFF) + 255) / 2;
        return (r << 16) | (g << 8) | b;
    }

    /** The skills the stack shows now, bottom to top (leaving ones too), for the demo log. */
    static String describe() {
        StringBuilder text = new StringBuilder();
        for (HudStack.Row row : STACK.rows()) {
            text.append(row.skill().id()).append(row.leaving() ? "(leaving)" : "").append(' ');
        }
        return text.toString().trim();
    }

    @Nullable
    static Skill mainSkill() {
        return main;
    }
}
