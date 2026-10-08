package dev.amman.proficiency.client;

import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.fabricmc.fabric.api.client.message.v1.ClientReceiveMessageEvents;

/**
 * The action bar draws one line and cuts nothing, so a long translation ran off the screen. A
 * message of this mod's that is wider than the window goes to the chat instead, which wraps.
 */
public final class OverlayFallback {

    private OverlayFallback() {
    }

    /** Called once from the client entry point. */
    public static void register() {
        ClientReceiveMessageEvents.ALLOW_GAME.register((message, overlay) -> {
            if (overlay && tooWide(message)) {
                Minecraft.getInstance().gui.getChat().addMessage(message);
                return false;
            }
            return true;
        });
    }

    /** Whether this is one of our messages and the action bar cannot show it whole. */
    static boolean tooWide(Component message) {
        Minecraft minecraft = Minecraft.getInstance();
        return mentionsMod(message)
                && minecraft.font.width(message) > minecraft.getWindow().getGuiScaledWidth() - 16;
    }

    private static boolean mentionsMod(Component message) {
        if (message.getContents() instanceof TranslatableContents translatable
                && translatable.getKey().startsWith("proficiency.")) {
            return true;
        }
        for (Component sibling : message.getSiblings()) {
            if (mentionsMod(sibling)) {
                return true;
            }
        }
        return false;
    }
}
