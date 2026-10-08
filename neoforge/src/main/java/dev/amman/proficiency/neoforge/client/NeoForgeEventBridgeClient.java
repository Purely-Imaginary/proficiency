package dev.amman.proficiency.neoforge.client;

import dev.amman.proficiency.neoforge.NeoForgeEventBridge;
import dev.amman.proficiency.platform.client.event.ClientTickEvent;
import dev.amman.proficiency.platform.client.event.RenderGuiEvent;

/** The client events, relayed like {@link NeoForgeEventBridge}'s. Client dist only. */
public final class NeoForgeEventBridgeClient {

    private NeoForgeEventBridgeClient() {
    }

    /** After {@code Subscribers.registerClient()}, so the client listeners are counted. */
    public static void register() {
        NeoForgeEventBridge.relay(net.neoforged.neoforge.client.event.ClientTickEvent.Post.class,
                ClientTickEvent.Post.class, e -> new ClientTickEvent.Post());
        NeoForgeEventBridge.relay(net.neoforged.neoforge.client.event.RenderGuiEvent.Post.class,
                RenderGuiEvent.Post.class, e -> new RenderGuiEvent.Post(e.getGuiGraphics(), e.getPartialTick()));
    }
}
