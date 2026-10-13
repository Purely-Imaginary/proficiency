package dev.amman.proficiency.hearth.client;

import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.client.event.RenderGuiEvent;
import net.minecraftforge.client.event.RenderLevelStageEvent;
import net.minecraft.Util;
import net.minecraft.client.Minecraft;
import net.minecraftforge.event.TickEvent;

/**
 * The client lane's two listeners on Forge's bus: the HUD pass (the glyph when the skill line is
 * not up, the tracker, the siege panel and the bell's arrow, the glyph's tooltip with the chat open)
 * and the client tick (the range ring, the demo). Registered by {@link HearthClientInit}.
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
        if (minecraft.options.hideGui) {
            // F1 with a screen open: Gui.render still runs under the screen (and posts this event).
            hiddenPass(now);
            return;
        }
        HearthHud.frame(now);
        var graphics = event.getGuiGraphics();
        HearthHud.drawAlone(graphics, minecraft.font, now);
        TrackerHud.render(graphics, minecraft, now);
        SiegePanelHud.render(graphics, minecraft, now);
        HearthHud.tooltip(graphics, minecraft, now);
    }

    /**
     * The HUD pass with the GUI hidden, from whichever listener runs under F1: the glyph's fades, the
     * tracker's numbers and its home clock move on, so nothing replays when the GUI comes back, and a
     * tier-up line whose numeral turned over unseen goes to the chat.
     */
    private static void hiddenPass(long now) {
        HearthHud.frame(now);
        TrackerHud.hidden(now);
    }

    /** The panel's build guide: ghost blocks in the world ({@link HearthGhost}), once a frame after the particles. */
    @SubscribeEvent
    public static void onRenderLevel(RenderLevelStageEvent event) {
        if (event.getStage() == RenderLevelStageEvent.Stage.AFTER_PARTICLES) {
            HearthGhost.render(event.getPoseStack(), event.getCamera());
        }
    }

    @SubscribeEvent
    public static void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) {
            return;
        }
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player == null || minecraft.level == null) {
            return;
        }
        long now = Util.getMillis();
        if (minecraft.options.hideGui && minecraft.screen == null) {
            // Forge 1.20.1 draws no GUI at all under F1 with no screen open (GameRenderer skips
            // Gui.render, so RenderGuiEvent never fires; NeoForge 1.21.1 still posts it). The HUD pass's
            // hidden branch runs here instead, the same one onRenderGui runs with a screen open.
            hiddenPass(now);
        }
        // A held tier-up line still reaches the chat on time when no HUD frame is drawn.
        HearthChat.settle(now, 0, HearthView.TRACKER);
        HearthRangeRing.tick(minecraft, now);
        HearthGhost.tick(minecraft);
        HearthDemo.tick(minecraft, now);
    }
}
