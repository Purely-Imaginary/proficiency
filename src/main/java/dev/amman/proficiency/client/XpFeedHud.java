package dev.amman.proficiency.client;

import dev.amman.proficiency.Proficiency;
import dev.amman.proficiency.config.ProficiencyClientConfig;
import dev.amman.proficiency.net.XpFeedPayload;
import dev.amman.proficiency.skill.Skill;
import dev.amman.proficiency.skill.SkillService;
import dev.amman.proficiency.skill.XpFactors;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RenderGuiEvent;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * The opt-in debug feed, down the left of the screen: every XP gain as it lands, with the base
 * the call site asked for next to what the player actually got, and under it, when any
 * multiplier applied, a small line naming each one. Only fed when the server has the
 * feed on for this player ({@code /skills xpfeed}), so for everyone else it never draws.
 *
 * <p>The same skill from the same source inside {@link #MERGE_MS} counts up on one line, so a
 * vein of ore is "×7" and not seven lines; every gain is still in the total.
 */
@EventBusSubscriber(modid = Proficiency.MOD_ID, value = Dist.CLIENT)
public final class XpFeedHud {

    private static final int HARD_CAP = 200;
    private static final long MERGE_MS = 2000;
    private static final long FADE_MS = 1500;
    private static final float DETAIL_SCALE = 0.75f;
    /** Height of the small line: the font's nine pixels scaled, plus a pixel of air. */
    private static final int DETAIL_HEIGHT = 7;

    private static final class Line {
        final Skill skill;
        final String source;
        float amount;
        float base;
        int count;
        long at;
        /** The newest gain's factors, as the text of the small second line; empty for none. */
        String factors;
        /** The factor text wrapped for the width it was last drawn at, and that width. */
        List<String> detail = List.of();
        int detailFor = -1;

        Line(Skill skill, String source, float amount, float base, String factors, long at) {
            this.skill = skill;
            this.source = source;
            this.amount = amount;
            this.base = base;
            this.count = 1;
            this.factors = factors;
            this.at = at;
        }
    }

    /** Oldest first, newest at the bottom, like chat. */
    private static final List<Line> LINES = new ArrayList<>();

    /** Gains that scrolled off the top: their total and count, and when the newest of them landed. */
    private static float overflowAmount;
    private static int overflowCount;
    private static long overflowAt;

    private XpFeedHud() {
    }

    public static void accept(XpFeedPayload payload) {
        long now = System.currentTimeMillis();
        for (SkillService.XpFeedGain gain : payload.gains()) {
            if (gain.skill() < 0 || gain.skill() >= Skill.VALUES.length) {
                continue;
            }
            Skill skill = Skill.VALUES[gain.skill()];
            Line last = LINES.isEmpty() ? null : LINES.get(LINES.size() - 1);
            if (last != null && last.skill == skill && last.source.equals(gain.source())
                    && now - last.at <= MERGE_MS) {
                last.amount += gain.amount();
                last.base += gain.base();
                last.count++;
                last.factors = factorText(gain.factors());
                last.at = now;
                continue;
            }
            LINES.add(new Line(skill, gain.source(), gain.amount(), gain.base(),
                    factorText(gain.factors()), now));
            // Only a runaway cap here; the player's maxLines is applied when drawing.
            trim(HARD_CAP);
        }
    }

    /**
     * Drops the oldest lines past {@code max} into one running total, drawn as a single grey
     * line. It sums across skills and sources on purpose: it is only a count of what scrolled
     * off, and never joins a normal line, which would put XP under the wrong skill.
     */
    private static void trim(int max) {
        while (LINES.size() > max) {
            Line oldest = LINES.remove(0);
            overflowAmount += oldest.amount;
            overflowCount += oldest.count;
            overflowAt = Math.max(overflowAt, oldest.at);
        }
    }

    /** {@code tempo x1.30 · streak x1.12}, or an empty string when nothing but 1.0 applied. */
    private static String factorText(java.util.List<XpFactors.Factor> factors) {
        StringBuilder out = new StringBuilder();
        for (XpFactors.Factor factor : factors) {
            if (out.length() > 0) {
                out.append(" · ");
            }
            out.append(Component.translatable("proficiency.xpfeed.factor." + factor.id()).getString())
                    .append(" x").append(String.format(Locale.ROOT, "%.2f", factor.value()));
        }
        return out.toString();
    }

    public static void clear() {
        LINES.clear();
        overflowAmount = 0;
        overflowCount = 0;
    }

    @SubscribeEvent
    public static void onRenderGui(RenderGuiEvent.Post event) {
        if (LINES.isEmpty()) {
            overflowAmount = 0;
            overflowCount = 0;
            return;
        }
        Minecraft minecraft = Minecraft.getInstance();
        long now = System.currentTimeMillis();
        long visibleMs = ProficiencyClientConfig.feedVisibleMs();
        LINES.removeIf(line -> now - line.at > visibleMs + FADE_MS);
        trim(ProficiencyClientConfig.feedMaxLines());
        if (minecraft.player == null || minecraft.options.hideGui || LINES.isEmpty()) {
            return;
        }
        GuiGraphics graphics = event.getGuiGraphics();
        Font font = minecraft.font;
        int x = ProficiencyClientConfig.feedX();
        boolean showFactors = ProficiencyClientConfig.feedShowFactors();
        // Stops above the hearts, the hotbar and the chat (about 44 pixels), not at the window edge.
        int screenBottom = graphics.guiHeight() - 48;
        int baseY = Math.max(8, Math.round(graphics.guiHeight() * ProficiencyClientConfig.feedYFraction()));
        // Clear of the discovery banner, which a new biome puts up at the same moment. If that
        // would push the feed off the bottom, draw at the set place instead: the banner is brief.
        int y = Math.max(baseY, DiscoveryBanner.bottom() + 6);
        if (y + 11 + 10 > screenBottom) {
            y = baseY;
        }
        // Keep the newest lines that fit between here and the bottom edge.
        boolean overflow = overflowCount > 0;
        int room = screenBottom - (y + 11) - (overflow ? 10 : 0);
        // Lines never run past the right edge: the source is cut, and the factor line wraps.
        int maxWidth = graphics.guiWidth() - x - 4;
        int first = LINES.size();
        while (first > 0) {
            Line candidate = LINES.get(first - 1);
            int step = showFactors && !candidate.factors.isEmpty()
                    ? 10 + DETAIL_HEIGHT * detail(font, candidate, maxWidth).size() : 10;
            if (step > room) {
                break;
            }
            room -= step;
            first--;
        }
        if (first == LINES.size()) {
            return;
        }
        TextFit.draw(graphics, font, "feed.header", Component.translatable("proficiency.xpfeed.header").getString(),
                x, y, maxWidth, SkillPalette.TEXT_DIM, true);
        y += 11;
        if (overflow) {
            long oage = now - overflowAt;
            float ofade = oage <= visibleMs ? 1f : 1f - (oage - visibleMs) / (float) FADE_MS;
            int oa = Math.round(Math.max(0f, ofade) * 255);
            if (oa >= 8) {
                TextFit.draw(graphics, font, "feed.overflow", Component.translatable("proficiency.xpfeed.overflow",
                        format(overflowAmount), overflowCount).getString(), x, y, maxWidth,
                        (Math.round(oa * 0.75f) << 24) | (SkillPalette.TEXT_DIM & 0xFFFFFF), true);
            }
            y += 10;
        }
        boolean iconOn = SkillIcons.enabled();
        for (Line line : LINES.subList(first, LINES.size())) {
            long age = now - line.at;
            float fade = age <= visibleMs ? 1f : 1f - (age - visibleMs) / (float) FADE_MS;
            int a = Math.round(Math.max(0f, fade) * 255);
            List<String> detailLines = showFactors && !line.factors.isEmpty()
                    ? detail(font, line, maxWidth) : List.of();
            boolean detail = !detailLines.isEmpty();
            int step = detail ? 10 + DETAIL_HEIGHT * detailLines.size() : 10;
            if (a < 8) {
                y += step;
                continue;
            }
            // Base in grey after it: the gap between the two is the multipliers.
            String base = " (" + format(line.base) + ")";
            int baseWidth = font.width(base);
            String head = "+" + format(line.amount) + " " + Component.translatable(line.skill.translationKey()).getString();
            String count = line.count > 1 ? " \u00D7" + line.count : "";
            int fixed = font.width(head) + font.width(count) + baseWidth;
            // The skill's icon leads the line, unless the line already runs to the right edge.
            int icon = iconOn ? SkillIcons.fit(maxWidth - 2, fixed, SkillIcons.SMALL) : 0;
            int sourceRoom = maxWidth - SkillIcons.advance(icon) - fixed;
            String source = line.source.isEmpty() ? "" : " \u00B7 " + TalentTreeScreen.sourceName(line.source);
            String shownSource = TextFit.clip(font, source, Math.max(0, sourceRoom));
            if (!shownSource.equals(source)) {
                TextFit.note("feed.line");
            }
            MutableComponent text = Component.literal(head + shownSource + count);
            int width = font.width(text);
            int tx = x + SkillIcons.advance(icon);
            graphics.fill(x - 2, y - 1, tx + width + baseWidth + 2, y + 9,
                    Math.round(a * 0.45f) << 24);
            SkillIcons.draw(graphics, line.skill, x, SkillIcons.smallTop(y), icon, a);
            graphics.drawString(font, text, tx, y,
                    (a << 24) | (SkillPalette.accent(line.skill.category()) & 0xFFFFFF), true);
            graphics.drawString(font, base, tx + width, y, (a << 24) | (SkillPalette.TEXT_DIM & 0xFFFFFF), true);
            if (detail) {
                // Smaller and dimmer: the why under the what.
                var pose = graphics.pose();
                int lineY = y + 10;
                for (String piece : detailLines) {
                    pose.pushPose();
                    pose.translate(x + 4 + SkillIcons.advance(icon), lineY, 0);
                    pose.scale(DETAIL_SCALE, DETAIL_SCALE, 1f);
                    graphics.drawString(font, piece, 0, 0,
                            (Math.round(a * 0.75f) << 24) | (SkillPalette.TEXT_DIM & 0xFFFFFF), true);
                    pose.popPose();
                    lineY += DETAIL_HEIGHT;
                }
            }
            y += step;
        }
    }

    /** The factor text wrapped (at most two lines) for the room under the line; cached per width. */
    private static List<String> detail(Font font, Line line, int maxWidth) {
        if (line.detailFor != maxWidth) {
            int room = Math.round((maxWidth - 4 - SkillIcons.advance(SkillIcons.SMALL)) / DETAIL_SCALE);
            boolean[] cut = new boolean[1];
            line.detail = TextFit.pack(font, line.factors, room, 2, cut, TextFit.DOT_RE, TextFit.DOT);
            line.detailFor = maxWidth;
            if (line.detail.size() > 1 || cut[0]) {
                TextFit.note("feed.detail");
            }
        }
        return line.detail;
    }

    private static String format(float value) {
        return value >= 100 ? String.valueOf(Math.round(value))
                : String.format(Locale.ROOT, value >= 10 ? "%.1f" : "%.2f", value);
    }
}
