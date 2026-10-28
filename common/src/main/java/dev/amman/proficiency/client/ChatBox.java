package dev.amman.proficiency.client;

import dev.amman.proficiency.mixin.client.ChatComponentAccessor;
import net.minecraft.client.GuiMessage;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.ChatComponent;
import net.minecraft.client.gui.screens.ChatScreen;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * Where the chat box is on screen this frame, in GUI pixels, worked out the way vanilla's
 * {@code ChatComponent.render} lays it out: lines stack up from 40 px above the bottom, a line is
 * {@code 9 * (spacing + 1)} high, the box is {@code width + 8} wide at the chat scale from x = 4 (the left margin),
 * and unless the chat is open only lines younger than 200 GUI ticks show. Used by the recent-skills stack
 * ({@link HudStackRows}) to stay clear of it. Compiled unchanged for Forge 1.20.1.
 */
final class ChatBox {

    private ChatBox() {
    }

    /** {right, top, bottom} of the visible chat box, or {@code null} when no chat line shows. */
    @Nullable
    static int[] of(Minecraft minecraft, int guiHeight) {
        if (minecraft.options.chatVisibility().get() == net.minecraft.world.entity.player.ChatVisiblity.HIDDEN) {
            return null;
        }
        ChatComponent chat = minecraft.gui.getChat();
        List<GuiMessage.Line> lines;
        try {
            lines = ((ChatComponentAccessor) chat).proficiency$trimmedMessages();
        } catch (ClassCastException e) {
            // The accessor did not apply (another mod replaced the chat): assume a full box.
            return new int[] {chat.getWidth() + 12, guiHeight - 40 - chat.getHeight(), guiHeight - 2};
        }
        boolean open = minecraft.screen instanceof ChatScreen;
        int max = chat.getLinesPerPage();
        int ticks = minecraft.gui.getGuiTicks();
        int shown = 0;
        for (int i = 0; i < lines.size() && shown < max; i++) {
            if (open || ticks - lines.get(i).addedTime() < 200) {
                shown++;
            }
        }
        if (shown == 0 && !open) {
            return null;
        }
        double scale = chat.getScale();
        double lineHeight = 9.0 * (minecraft.options.chatLineSpacing().get() + 1.0);
        int bottom = guiHeight - 40;
        int top = (int) Math.floor(bottom - shown * lineHeight * scale) - 2;
        int right = (int) Math.ceil(4 + (chat.getWidth() + 8) * scale);
        // The open chat's input box runs the whole width at the bottom; the stack is far above it.
        return new int[] {right, top, open ? guiHeight : bottom + 2};
    }
}
