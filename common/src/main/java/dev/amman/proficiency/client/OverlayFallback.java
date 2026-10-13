package dev.amman.proficiency.client;

import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.contents.TranslatableContents;

/**
 * The action bar draws one line and cuts nothing, so a long translation ran off the screen. A
 * message of this mod's that is wider than the window goes to the chat instead, which wraps.
 * The loaders call {@link #reroute} from their system-message event.
 */
public final class OverlayFallback {

    private OverlayFallback() {
    }

    /**
     * For an action-bar message: when it is one of ours and too wide, put it in the chat and
     * answer true, and the loader drops the action-bar copy. Also answers true for the Hearth's
     * tier-up chat line while the Hearth tracker may show the same moment ({@code HearthChat} holds it
     * and lets it through a little later if the tracker does not).
     */
    public static boolean reroute(Component message, boolean overlay) {
        if (dev.amman.proficiency.hearth.client.HearthChat.hold(message, overlay)) {
            return true;
        }
        if (overlay && tooWide(message)) {
            Minecraft.getInstance().gui.getChat().addMessage(message);
            return true;
        }
        return false;
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
