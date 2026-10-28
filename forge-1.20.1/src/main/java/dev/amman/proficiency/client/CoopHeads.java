package dev.amman.proficiency.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.PlayerFaceRenderer;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.resources.ResourceLocation;

import java.util.UUID;

/**
 * A partner's face for the co-op pips (1.45.0). The one piece of {@link CoopStreams} that differs between
 * Minecraft versions (1.20.1 reads the skin from {@code PlayerInfo.getSkinLocation()}), so each line has its own copy.
 */
final class CoopHeads {

    private CoopHeads() {
    }

    /** Draws the face at ({@code x}, {@code y}), {@code size} square; false when the player's skin is unknown. */
    static boolean draw(GuiGraphics graphics, Minecraft mc, UUID id, int x, int y, int size, float alpha) {
        if (mc.getConnection() == null) {
            return false;
        }
        PlayerInfo info = mc.getConnection().getPlayerInfo(id);
        if (info == null) {
            return false;
        }
        ResourceLocation skin = info.getSkinLocation();
        com.mojang.blaze3d.systems.RenderSystem.enableBlend();
        graphics.setColor(1f, 1f, 1f, alpha);
        PlayerFaceRenderer.draw(graphics, skin, x, y, size);
        graphics.setColor(1f, 1f, 1f, 1f);
        com.mojang.blaze3d.systems.RenderSystem.disableBlend();
        return true;
    }
}
