package dev.amman.proficiency.platform.hooks;

import dev.amman.proficiency.platform.bus.NeoForge;
import dev.amman.proficiency.platform.client.event.ClientTickEvent;
import dev.amman.proficiency.platform.client.event.RenderGuiEvent;
import dev.amman.proficiency.platform.client.event.RenderLevelEvent;
import dev.amman.proficiency.platform.client.event.ScreenInitEvent;
import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents;
import net.fabricmc.fabric.api.client.screen.v1.Screens;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderEvents;

public final class FabricClientHooks {

    private FabricClientHooks() {
    }

    public static void init() {
        ClientTickEvents.END_CLIENT_TICK.register(client -> NeoForge.EVENT_BUS.post(new ClientTickEvent.Post()));
        ScreenEvents.AFTER_INIT.register((client, screen, width, height) ->
                NeoForge.EVENT_BUS.post(new ScreenInitEvent.Post(screen, widget -> Screens.getButtons(screen).add(widget))));
        HudRenderCallback.EVENT.register((graphics, delta) ->
                NeoForge.EVENT_BUS.post(new RenderGuiEvent.Post(graphics, delta)));
        WorldRenderEvents.AFTER_TRANSLUCENT.register(context -> {
            if (context.matrixStack() != null) {
                NeoForge.EVENT_BUS.post(new RenderLevelEvent.Post(context.matrixStack(), context.camera()));
            }
        });
    }
}
