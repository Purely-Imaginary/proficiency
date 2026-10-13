package dev.amman.proficiency.client;

import dev.amman.proficiency.Proficiency;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.ClientChatReceivedEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * The action bar draws one line and cuts nothing, so a long translation ran off the screen. A
 * message of this mod's that is wider than the window goes to the chat instead, which wraps.
 */
@Mod.EventBusSubscriber(modid = Proficiency.MOD_ID, value = Dist.CLIENT)
public final class OverlayFallback {

    private OverlayFallback() {
    }

    /**
     * For an action-bar message: when it is one of ours and too wide, it goes to the chat and the
     * action-bar copy is dropped. Also drops the Hearth's tier-up chat line while the Hearth tracker may
     * show the same moment ({@code HearthChat} holds it and lets it through a little later if the
     * tracker does not).
     */
    @SubscribeEvent
    public static void onSystemMessage(ClientChatReceivedEvent.System event) {
        if (dev.amman.proficiency.hearth.client.HearthChat.hold(event.getMessage(), event.isOverlay())) {
            event.setCanceled(true);
            return;
        }
        if (event.isOverlay() && tooWide(event.getMessage())) {
            event.setCanceled(true);
            Minecraft.getInstance().gui.getChat().addMessage(event.getMessage());
        }
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
