package dev.amman.proficiency.platform.hooks;

import dev.amman.proficiency.platform.bus.NeoForge;
import dev.amman.proficiency.platform.client.event.ClientTickEvent;
import dev.amman.proficiency.platform.client.event.RenderGuiEvent;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;

public final class FabricClientHooks {

    private FabricClientHooks() {
    }

    public static void init() {
        ClientTickEvents.END_CLIENT_TICK.register(client -> NeoForge.EVENT_BUS.post(new ClientTickEvent.Post()));
        HudRenderCallback.EVENT.register((graphics, delta) ->
                NeoForge.EVENT_BUS.post(new RenderGuiEvent.Post(graphics, delta)));
    }
}
