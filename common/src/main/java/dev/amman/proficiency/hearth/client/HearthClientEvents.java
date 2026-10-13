package dev.amman.proficiency.hearth.client;

import dev.amman.proficiency.platform.bus.SubscribeEvent;
import dev.amman.proficiency.platform.client.event.ClientTickEvent;
import dev.amman.proficiency.platform.client.event.RenderGuiEvent;
import dev.amman.proficiency.platform.client.event.RenderLevelEvent;
import net.minecraft.Util;
import net.minecraft.client.Minecraft;

/**
 * The client lane's two listeners on the shared bus: the HUD pass (the glyph when the skill line is
 * not up, the tracker, the siege panel and the bell's arrow, the glyph's tooltip with the chat open)
 * the world pass (the build guide's ghosts) and the client tick (the range ring, the ghosts, the demo). Registered by {@link HearthClientInit}.
 */
public final class HearthClientEvents {

    private HearthClientEvents() {
    }

    @SubscribeEvent
    public static void onRenderGui(RenderGuiEvent.Post event) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player == null) {
            return;
        }
        long now = Util.getMillis();
        HearthHud.frame(now);
        if (minecraft.options.hideGui) {
            // The tracker's numbers and its home clock still move on, so nothing replays when the GUI
            // comes back; a held tier-up line whose numeral turned over unseen goes to the chat.
            TrackerHud.hidden(now);
            return;
        }
        var graphics = event.getGuiGraphics();
        HearthHud.drawAlone(graphics, minecraft.font, now);
        TrackerHud.render(graphics, minecraft, now);
        SiegePanelHud.render(graphics, minecraft, now);
        HearthHud.tooltip(graphics, minecraft, now);
    }

    /** The panel's build guide: ghost blocks in the world ({@link HearthGhost}). */
    @SubscribeEvent
    public static void onRenderLevel(RenderLevelEvent.Post event) {
        HearthGhost.render(event.getPoseStack(), event.getCamera());
    }

    @SubscribeEvent
    public static void onClientTick(ClientTickEvent.Post event) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player == null || minecraft.level == null) {
            return;
        }
        long now = Util.getMillis();
        // A held tier-up line still reaches the chat on time when no HUD frame is drawn.
        HearthChat.settle(now, 0, HearthView.TRACKER);
        HearthRangeRing.tick(minecraft, now);
        HearthGhost.tick(minecraft);
        HearthDemo.tick(minecraft, now);
    }
}
