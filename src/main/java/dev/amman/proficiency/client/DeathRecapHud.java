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

    private static final int WIDTH = 146;
    private static final int ROW = 12;
    private static final int NAME_WIDTH = 62;
    private static final int BAR_WIDTH = 34;
    private static final int RIGHT_MARGIN = 4;
    /** With icons on, the bar gives the name column this much back, so fewer names need a cut. */
    private static final int ICON_BAR_TAKE = 6;
    private static final int RED = 0xE0584B;

    /** One row, with its strings already built so a frame formats nothing. */
    private record Entry(Skill skill, int accent, float before, float after, String name, String iconName,
            String lost) {
    }

    private static List<Entry> entries = List.of();
    private static String title = "";
    private static String streakText = "";
    private static String moreText = "";
    private static int streakPercent;
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
        for (DeathRecapPayload.Row row : payload.rows()) {
            Skill skill = Skill.VALUES[Math.floorMod(row.skillOrdinal(), Skill.VALUES.length)];
            String full = Component.translatable(skill.translationKey()).getString();
            int lostPercent = Math.max(1, Math.round((row.before() - row.after()) * 100f));
            // Two cuts of the name: the whole column, and the column less the icon, so the
            // setting can change mid-panel and the row still never grows.
            built.add(new Entry(skill, SkillPalette.accent(skill.category()), row.before(), row.after(),
                    fit(font, full, NAME_WIDTH), fit(font, full, NAME_WIDTH + ICON_BAR_TAKE - SkillIcons.advance(SkillIcons.SMALL)),
                    "-" + lostPercent + "%"));
        }
        entries = built;
        title = Component.translatable("proficiency.recap.title").getString();
        moreText = payload.more() > 0
                ? Component.translatable("proficiency.recap.more", payload.more()).getString() : "";
        hasStreak = payload.streakStacks() > 0;
        streakPercent = payload.streakPercent();
        streakText = hasStreak
                ? Component.translatable("proficiency.recap.streak", payload.streakPercent()).getString() : "";
        pending = true;
        start = Long.MIN_VALUE / 2;
    }

    /** The name cut to the column with an ellipsis, so a long one never reads as a different word. */
    static String fit(Font font, String name) {
        return fit(font, name, NAME_WIDTH);
    }

    static String fit(Font font, String name, int room) {
        if (font.width(name) <= room) {
            return name;
        }
        return font.plainSubstrByWidth(name, room - font.width("\u2026")) + "\u2026";
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
        int rows = entries.size();
        boolean more = !moreText.isEmpty();
        int height = 18 + rows * ROW + (more ? ROW : 0) + (hasStreak ? ROW + 3 : 0) + 4;
        int x = graphics.guiWidth() - WIDTH - RIGHT_MARGIN;
        int y = Math.max(8, Math.round(graphics.guiHeight() * 0.30f));

        graphics.fill(x, y, x + WIDTH, y + height, ((int) (alpha * 0.72f) << 24) | 0x0E1014);
        int border = (alpha << 24) | (RED & 0x00FFFFFF);
        graphics.fill(x, y, x + WIDTH, y + 1, border);
        graphics.fill(x, y + height - 1, x + WIDTH, y + height, (alpha / 3 << 24) | 0x2C333D);
        graphics.drawString(font, title, x + 6, y + 5, (alpha << 24) | 0xE8A39B, true);

        int rowY = y + 18;
        boolean icons = SkillIcons.enabled();
        for (int i = 0; i < rows; i++) {
            Entry entry = entries.get(i);
            float drained = drain(i, elapsed);
            float current = entry.before() + (entry.after() - entry.before()) * drained;
            int accent = entry.accent() & 0x00FFFFFF;
            if (icons) {
                // The icon takes the front of the name column; the name was cut shorter for it.
                SkillIcons.draw(graphics, entry.skill(), x + 6, SkillIcons.smallTop(rowY), SkillIcons.SMALL, alpha);
                graphics.drawString(font, entry.iconName(), x + 6 + SkillIcons.advance(SkillIcons.SMALL), rowY,
                        (alpha << 24) | accent, true);
            } else {
                graphics.drawString(font, entry.name(), x + 6, rowY, (alpha << 24) | accent, true);
            }

            int take = icons ? ICON_BAR_TAKE : 0;
            int barX = x + 6 + NAME_WIDTH + 3 + take;
            int barWidth = BAR_WIDTH - take;
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
            graphics.drawString(font, lost, x + WIDTH - 5 - font.width(lost), rowY,
                    (alpha << 24) | RED, true);
            rowY += ROW;
        }

        if (more) {
            graphics.drawString(font, moreText, x + 6, rowY, (alpha << 24) | 0xA0A6AE, true);
            rowY += ROW;
        }
        if (hasStreak) {
            rowY += 3;
            graphics.fill(x + 6, rowY - 2, x + WIDTH - 6, rowY - 1, (alpha / 5 << 24) | 0xFFFFFF);
            if (ProficiencyClientConfig.hudStreakFx()) {
                StreakBadge.drawDead(graphics, font, x + 6, rowY, alpha, now);
            } else {
                graphics.drawString(font, StreakBadge.GLYPH, x + 6, rowY, (alpha << 24) | StreakBadge.GOLD, true);
            }
            graphics.drawString(font, streakText, x + 6 + StreakBadge.WIDTH + 4, rowY,
                    (alpha << 24) | StreakBadge.GOLD, true);
        }
    }
}
