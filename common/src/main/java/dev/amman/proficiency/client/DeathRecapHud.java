package dev.amman.proficiency.client;

import dev.amman.proficiency.config.ProficiencyClientConfig;
import dev.amman.proficiency.net.DeathRecapPayload;
import dev.amman.proficiency.skill.Skill;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * The panel after a respawn that lists the XP bars the death wiped. Each bar starts where it
 * stood and drains to what it kept (empty, unless a ward held some back), then the whole panel
 * fades after about five seconds. The streak that went sits at the bottom with its badge breaking
 * apart.
 *
 * <p>It sits at the right edge, narrow enough to stay clear of the default chat width (the chat
 * draws over the HUD and the death lines fill it at exactly this moment).
 *
 * <p>The packet arrives from the server's respawn handler, a moment before the client has its new
 * body, so it only waits here; the clock starts on the first frame with no screen up.
 */
public final class DeathRecapHud {

    public static final long TOTAL_MS = 5000;
    public static final long FADE_IN_MS = 250;
    public static final long FADE_OUT_MS = 700;
    public static final long DRAIN_DELAY_MS = 450;
    public static final long DRAIN_MS = 1500;
    public static final long ROW_STAGGER_MS = 90;

    /** The panel's least width; it grows to the longest skill name and the longest percent. */
    private static final int MIN_WIDTH = 146;
    /** Most the name column may take, so one long name never makes a very wide panel. */
    private static final int NAME_MAX = 120;
    private static final int ROW = 12;
    private static final int NAME_MIN = 62;
    private static final int BAR_WIDTH = 34;
    private static final int RIGHT_MARGIN = 4;
    /** With icons on, the bar gives the name column this much back, so fewer names need a cut. */
    private static final int ICON_BAR_TAKE = 6;
    private static final int RED = 0xE0584B;

    /** One row, with its strings already built so a frame formats nothing. */
    private record Entry(Skill skill, int accent, float before, float after, String full, String lost) {
    }

    private static List<Entry> entries = List.of();
    private static String title = "";
    private static String streakText = "";
    private static String moreText = "";
    /** How many more bars the payload said were lost than it lists. */
    private static int moreCount;
    /** Rows that fit between the top margin and the hotbar on this window; the rest fold into "+N more". */
    private static int rowsShown;
    private static int laidOutHeight = -1;
    private static int panelTop = 8;
    private static int streakPercent;
    /** Widest skill name and widest loss figure, measured when the panel arrives. */
    private static int nameWidest;
    private static int lostWidest;
    /** The wrapped text lines and the width they were wrapped for; rebuilt when the window changes. */
    private static int laidOutFor = -1;
    private static int panelWidth = MIN_WIDTH;
    private static int nameColumn = NAME_MIN;
    private static List<String> titleLines = List.of();
    private static List<String> moreLines = List.of();
    private static List<String> streakLines = List.of();
    private static boolean hasStreak;
    private static boolean pending;
    private static long start = Long.MIN_VALUE / 2;

    private DeathRecapHud() {
    }

    /** Client thread, from the packet. */
    public static void accept(DeathRecapPayload payload) {
        if (payload.isEmpty()) {
            return;
        }
        Minecraft minecraft = Minecraft.getInstance();
        Font font = minecraft.font;
        List<Entry> built = new ArrayList<>(payload.rows().size());
        nameWidest = 0;
        lostWidest = 0;
        for (DeathRecapPayload.Row row : payload.rows()) {
            Skill skill = Skill.VALUES[Math.floorMod(row.skillOrdinal(), Skill.VALUES.length)];
            String full = Component.translatable(skill.translationKey()).getString();
            int lostPercent = Math.max(1, Math.round((row.before() - row.after()) * 100f));
            String lost = "-" + lostPercent + "%";
            nameWidest = Math.max(nameWidest, font.width(full));
            lostWidest = Math.max(lostWidest, font.width(lost));
            built.add(new Entry(skill, SkillPalette.accent(skill.category()), row.before(), row.after(), full, lost));
        }
        entries = built;
        title = Component.translatable("proficiency.recap.title").getString();
        moreCount = payload.more();
        hasStreak = payload.streakStacks() > 0;
        streakPercent = payload.streakPercent();
        streakText = hasStreak
                ? Component.translatable("proficiency.recap.streak", payload.streakPercent()).getString() : "";
        laidOutFor = -1;
        laidOutHeight = -1;
        pending = true;
        start = Long.MIN_VALUE / 2;
    }

    /** The name cut to the column with an ellipsis, so a long one never reads as a different word. */
    static String fit(Font font, String name) {
        return fit(font, name, NAME_MIN);
    }

    static String fit(Font font, String name, int room) {
        if (font.width(name) <= room) {
            return name;
        }
        return font.plainSubstrByWidth(name, room - font.width("\u2026")) + "\u2026";
    }

    /**
     * Sizes the panel for this window: wide enough for the longest name and the longest loss, never
     * wider than half the screen, with the title, the "+N more" line and the streak line wrapped
     * to what is left. Cached until the window or the data changes.
     */
    private static void layout(Font font, int guiWidth, int guiHeight) {
        if (laidOutFor == guiWidth && laidOutHeight == guiHeight) {
            return;
        }
        laidOutFor = guiWidth;
        laidOutHeight = guiHeight;
        int icon = SkillIcons.advance(SkillIcons.SMALL);
        int limit = Math.max(MIN_WIDTH, Math.min(guiWidth - RIGHT_MARGIN * 2, guiWidth * 6 / 10));
        int chrome = 6 + 3 + BAR_WIDTH + 4 + lostWidest + 5;
        nameColumn = Math.max(NAME_MIN, Math.min(NAME_MAX, nameWidest + icon));
        panelWidth = Math.max(MIN_WIDTH, Math.min(limit, chrome + nameColumn));
        nameColumn = panelWidth - chrome;
        int text = panelWidth - 12;
        boolean[] cut = new boolean[1];
        titleLines = TextFit.wrapLimited(font, title, text, 2, cut);
        streakLines = streakText.isEmpty() ? List.of()
                : TextFit.wrapLimited(font, streakText, text - StreakBadge.WIDTH - 4, 3, cut);
        // The panel stays above the hotbar and the hearts (about 44 pixels from the bottom). A
        // window too short for every row shows fewer and counts the rest into "+N more".
        int titleHeight = 5 + titleLines.size() * 9 + 4;
        int streakHeight = hasStreak ? Math.max(ROW, streakLines.size() * 9) + 6 : 0;
        panelTop = topFor(guiHeight);
        int top = panelTop;
        int room = guiHeight - 44 - top - titleHeight - streakHeight - 4;
        int rowsFit = Math.max(1, room / ROW);
        rowsShown = Math.min(entries.size(), rowsFit);
        if (rowsShown < entries.size() && room - rowsShown * ROW < 12) {
            rowsShown = Math.max(1, rowsShown - 1);
        }
        int hidden = entries.size() - rowsShown;
        if (hidden > 0) {
            TextFit.note("recap.rows_folded");
        }
        int more = moreCount + hidden;
        moreText = more > 0 ? Component.translatable("proficiency.recap.more", more).getString() : "";
        moreLines = moreText.isEmpty() ? List.of() : TextFit.wrapLimited(font, moreText, text, 2, cut);
        if (cut[0]) {
            TextFit.note("recap.text");
        }
        for (Entry entry : entries) {
            if (font.width(entry.full()) > nameColumn - icon) {
                TextFit.note("recap.name");
                break;
            }
        }
    }

    /**
     * Where the panel starts: 30% down, or below the banner that is on screen when the panel is laid
     * out (a respawn into a new place shows both), so the banner's XP line is not covered.
     */
    private static int topFor(int guiHeight) {
        int banner = DiscoveryBanner.bottom();
        return Math.max(8, Math.max(Math.round(guiHeight * 0.30f), banner > 0 ? banner + 4 : 0));
    }

    public static void reset() {
        pending = false;
        start = Long.MIN_VALUE / 2;
        entries = List.of();
    }

    /** How far a row's bar has drained, 0 to 1 (eased), for row {@code index} at {@code elapsed} ms. */
    public static float drain(int index, long elapsed) {
        float t = (elapsed - DRAIN_DELAY_MS - index * ROW_STAGGER_MS) / (float) DRAIN_MS;
        return (float) XpGainDots.ease(Math.max(0f, Math.min(1f, t)));
    }

    /** Panel opacity, 0 to 1: a quick fade in, steady, then a slower fade out. */
    public static float opacity(long elapsed) {
        if (elapsed < 0 || elapsed >= TOTAL_MS) {
            return 0f;
        }
        float in = Math.min(1f, elapsed / (float) FADE_IN_MS);
        float out = Math.min(1f, (TOTAL_MS - elapsed) / (float) FADE_OUT_MS);
        return Math.min(in, out);
    }

    public static void render(GuiGraphics graphics, Minecraft minecraft, long now) {
        if (!pending && start < 0) {
            return;
        }
        if (pending) {
            // Wait for the death screen and any loading screen to go.
            if (minecraft.screen != null) {
                return;
            }
            pending = false;
            start = now;
            // The badge breaks on the HUD line whether or not the panel is on, and the other way
            // round: each effect answers to its own switch.
            if (hasStreak && ProficiencyClientConfig.hudStreakFx()) {
                StreakBadge.startBreak(streakPercent, now);
            }
        }
        if (!ProficiencyClientConfig.hudDeathRecap()) {
            if (now - start >= TOTAL_MS) {
                reset();
            }
            return;
        }
        long elapsed = now - start;
        float opacity = opacity(elapsed);
        if (elapsed >= TOTAL_MS) {
            reset();
            return;
        }
        int alpha = Math.round(255 * opacity);
        if (alpha <= 8) {
            return;
        }

        Font font = minecraft.font;
        layout(font, graphics.guiWidth(), graphics.guiHeight());
        int rows = rowsShown;
        boolean more = !moreLines.isEmpty();
        int titleHeight = 5 + titleLines.size() * 9 + 4;
        int height = titleHeight + rows * ROW + (more ? moreLines.size() * 9 + 3 : 0)
                + (hasStreak ? Math.max(ROW, streakLines.size() * 9) + 6 : 0) + 4;
        int width = panelWidth;
        int x = graphics.guiWidth() - width - RIGHT_MARGIN;
        int y = panelTop;

        graphics.fill(x, y, x + width, y + height, ((int) (alpha * 0.72f) << 24) | 0x0E1014);
        int border = (alpha << 24) | (RED & 0x00FFFFFF);
        graphics.fill(x, y, x + width, y + 1, border);
        graphics.fill(x, y + height - 1, x + width, y + height, (alpha / 3 << 24) | 0x2C333D);
        int titleY = y + 5;
        for (String line : titleLines) {
            graphics.drawString(font, line, x + 6, titleY, (alpha << 24) | 0xE8A39B, true);
            titleY += 9;
        }

        int rowY = y + titleHeight + 1;
        boolean icons = SkillIcons.enabled();
        int iconAdv = icons ? SkillIcons.advance(SkillIcons.SMALL) : 0;
        for (int i = 0; i < rows; i++) {
            Entry entry = entries.get(i);
            float drained = drain(i, elapsed);
            float current = entry.before() + (entry.after() - entry.before()) * drained;
            int accent = entry.accent() & 0x00FFFFFF;
            if (icons) {
                SkillIcons.draw(graphics, entry.skill(), x + 6, SkillIcons.smallTop(rowY), SkillIcons.SMALL, alpha);
            }
            graphics.drawString(font, fit(font, entry.full(), nameColumn - iconAdv), x + 6 + iconAdv, rowY,
                    (alpha << 24) | accent, true);

            int barX = x + 6 + nameColumn + 3;
            int barWidth = BAR_WIDTH;
            int barY = rowY + 3;
            graphics.fill(barX, barY, barX + barWidth, barY + 3, (alpha << 24) | (SkillPalette.TRACK & 0x00FFFFFF));
            int before = Math.round(barWidth * entry.before());
            int now1 = Math.round(barWidth * current);
            // The part already drained stays as a dim red ghost that fades as the drain ends.
            if (before > now1) {
                int ghost = Math.round(alpha * 0.45f * (1f - drained));
                if (ghost > 4) {
                    graphics.fill(barX + now1, barY, barX + before, barY + 3, (ghost << 24) | RED);
                }
            }
            if (now1 > 0) {
                graphics.fill(barX, barY, barX + now1, barY + 3, (alpha << 24) | accent);
            }
            String lost = entry.lost();
            graphics.drawString(font, lost, x + width - 5 - font.width(lost), rowY,
                    (alpha << 24) | RED, true);
            rowY += ROW;
        }

        if (more) {
            for (String line : moreLines) {
                graphics.drawString(font, line, x + 6, rowY, (alpha << 24) | 0xA0A6AE, true);
                rowY += 9;
            }
            rowY += 3;
        }
        if (hasStreak) {
            rowY += 3;
            graphics.fill(x + 6, rowY - 2, x + width - 6, rowY - 1, (alpha / 5 << 24) | 0xFFFFFF);
            if (ProficiencyClientConfig.hudStreakFx()) {
                StreakBadge.drawDead(graphics, font, x + 6, rowY, alpha, now);
            } else {
                graphics.drawString(font, StreakBadge.GLYPH, x + 6, rowY, (alpha << 24) | StreakBadge.GOLD, true);
            }
            int streakY = rowY;
            for (String line : streakLines) {
                graphics.drawString(font, line, x + 6 + StreakBadge.WIDTH + 4, streakY,
                        (alpha << 24) | StreakBadge.GOLD, true);
                streakY += 9;
            }
        }
    }
}
