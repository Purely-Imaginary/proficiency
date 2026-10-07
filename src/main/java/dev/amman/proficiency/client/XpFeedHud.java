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
import dev.amman.proficiency.platform.bus.Dist;
import dev.amman.proficiency.platform.bus.SubscribeEvent;
import dev.amman.proficiency.platform.bus.EventBusSubscriber;
import dev.amman.proficiency.platform.client.event.RenderGuiEvent;

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
        int screenBottom = graphics.guiHeight() - 4;
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
        int first = LINES.size();
        while (first > 0) {
            Line candidate = LINES.get(first - 1);
            int step = showFactors && !candidate.factors.isEmpty() ? 10 + DETAIL_HEIGHT : 10;
            if (step > room) {
                break;
            }
            room -= step;
            first--;
        }
        if (first == LINES.size()) {
            return;
        }
        graphics.drawString(font, Component.translatable("proficiency.xpfeed.header"), x, y,
                SkillPalette.TEXT_DIM, true);
        y += 11;
        if (overflow) {
            long oage = now - overflowAt;
            float ofade = oage <= visibleMs ? 1f : 1f - (oage - visibleMs) / (float) FADE_MS;
            int oa = Math.round(Math.max(0f, ofade) * 255);
            if (oa >= 8) {
                graphics.drawString(font, Component.translatable("proficiency.xpfeed.overflow",
                        format(overflowAmount), overflowCount), x, y,
                        (Math.round(oa * 0.75f) << 24) | (SkillPalette.TEXT_DIM & 0xFFFFFF), true);
            }
            y += 10;
        }
        for (Line line : LINES.subList(first, LINES.size())) {
            long age = now - line.at;
            float fade = age <= visibleMs ? 1f : 1f - (age - visibleMs) / (float) FADE_MS;
            int a = Math.round(Math.max(0f, fade) * 255);
            boolean detail = showFactors && !line.factors.isEmpty();
            int step = detail ? 10 + DETAIL_HEIGHT : 10;
            if (a < 8) {
                y += step;
                continue;
            }
            MutableComponent text = Component.literal("+" + format(line.amount) + " ")
                    .append(Component.translatable(line.skill.translationKey()));
            if (!line.source.isEmpty()) {
                text.append(" · " + TalentTreeScreen.sourceName(line.source));
            }
            if (line.count > 1) {
                text.append(" ×" + line.count);
            }
            int width = font.width(text);
            // Base in grey after it: the gap between the two is the multipliers.
            String base = " (" + format(line.base) + ")";
            graphics.fill(x - 2, y - 1, x + width + font.width(base) + 2, y + 9,
                    Math.round(a * 0.45f) << 24);
            graphics.drawString(font, text, x, y,
                    (a << 24) | (SkillPalette.accent(line.skill.category()) & 0xFFFFFF), true);
            graphics.drawString(font, base, x + width, y, (a << 24) | (SkillPalette.TEXT_DIM & 0xFFFFFF), true);
            if (detail) {
                // Smaller and dimmer: the why under the what.
                var pose = graphics.pose();
                pose.pushPose();
                pose.translate(x + 4, y + 10, 0);
                pose.scale(DETAIL_SCALE, DETAIL_SCALE, 1f);
                graphics.drawString(font, line.factors, 0, 0,
                        (Math.round(a * 0.75f) << 24) | (SkillPalette.TEXT_DIM & 0xFFFFFF), true);
                pose.popPose();
            }
            y += step;
        }
    }

    private static String format(float value) {
        return value >= 100 ? String.valueOf(Math.round(value))
                : String.format(Locale.ROOT, value >= 10 ? "%.1f" : "%.2f", value);
    }
}
