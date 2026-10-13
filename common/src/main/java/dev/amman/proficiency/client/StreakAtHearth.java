package dev.amman.proficiency.client;

import dev.amman.proficiency.hearth.client.HearthHud;
import dev.amman.proficiency.hearth.client.WarmthGlyph;
import dev.amman.proficiency.hearth.net.HearthClients;
import net.minecraft.ChatFormatting;
import net.minecraft.Util;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * The survival streak block of the skills panel at a Hearth (docs/HEARTH-SPEC.md, "The survival
 * streak does not grow at home"). The server's {@code STREAK_SAFE} HUD flag while home says the
 * streak is held ({@link HearthClients#streakSafe}, the HUD's own rule); the block then shows the
 * HUD's Warmth glyph, reads "next +n%: at the Hearth" where the progress was, draws the held step
 * bar in ember colours, and its tooltip says "Stacks safe at the Hearth" (the reason behind Shift).
 * Not "paused": home should not feel like a penalty box.
 */
public final class StreakAtHearth {

    /** The step bar while held: the Hearth's ember gold, not the growing green. */
    public static final int HELD_BAR = 0xFFE39A3B;
    /** Glyph advance in GUI pixels (7 drawn + 1), the HUD's. */
    public static final int GLYPH_WIDTH = WarmthGlyph.WIDTH;

    private StreakAtHearth() {
    }

    /** Whether the streak block should read as held at a Hearth. */
    public static boolean held(boolean off, boolean capped) {
        return !off && !capped && HearthClients.streakSafe();
    }

    /** The caption where the step progress was: "next +13%: at the Hearth". */
    public static Component caption(int nextPercent) {
        return Component.translatable("proficiency.hearth.effects.streak.next", nextPercent);
    }

    /**
     * The Warmth glyph exactly as the HUD shows it now ({@link HearthHud#glyph}): Warmth moved on by its
     * rate since the last payload, so it fills while the panel is open; embers when cold, gilded at full.
     */
    public static String glyph() {
        return HearthHud.glyph(Util.getMillis());
    }

    /** Draws the glyph in white (the glyph carries its own colours) at the start of the caption line. */
    public static void drawGlyph(GuiGraphics graphics, Font font, int x, int y) {
        graphics.drawString(font, glyph(), x, y - 1, 0xFFFFFFFF, true);
    }

    /** The block's tooltip: the short line, and the reason behind Shift. */
    public static List<Component> tooltip() {
        List<Component> lines = new ArrayList<>();
        lines.add(Component.translatable("proficiency.hearth.effects.streak.safe").withStyle(ChatFormatting.GOLD));
        if (TooltipDetail.detailed()) {
            lines.add(Component.translatable("proficiency.hearth.effects.streak.safe.detail")
                    .withStyle(ChatFormatting.GRAY));
        }
        return TooltipDetail.withHint(lines, true);
    }
}
