package dev.amman.proficiency.client;

import java.util.List;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;

/** The dim hint line under a tree, and the tooltip that explains its chat command. Shared by both tree screens. */
final class FooterHint {
    private FooterHint() {
    }

    /** Draws the packed lines from y; true when the pointer is on the block. */
    static boolean draw(GuiGraphics graphics, Font font, List<String> lines, int x, int y, int width, int lineHeight,
            int blockHeight, int mouseX, int mouseY) {
        boolean hovered = mouseX >= x && mouseX < x + width && mouseY >= y && mouseY < y + blockHeight;
        for (String line : lines) {
            graphics.drawString(font, line, x, y, SkillPalette.TEXT_DIM, false);
            y += lineHeight;
        }
        return hovered;
    }

    static List<Component> tip(String titleKey, int accent, Component body) {
        return List.of(Component.translatable(titleKey).withStyle(style -> style.withColor(accent)),
                body.copy().withStyle(ChatFormatting.GRAY));
    }
}
