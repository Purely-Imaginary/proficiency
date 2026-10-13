package dev.amman.proficiency.neoforge.client;

import dev.amman.proficiency.neoforge.NeoForgeEventBridge;
import dev.amman.proficiency.platform.client.event.ClientTickEvent;
import dev.amman.proficiency.platform.client.event.RenderGuiEvent;
import dev.amman.proficiency.platform.client.event.RenderLevelEvent;

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
        // The world pass, once per frame, at the stage after the particles (the stage with a pose stack
        // that is also posted in both graphics modes). A direct listener: the relay cannot filter a stage.
        net.neoforged.neoforge.common.NeoForge.EVENT_BUS.addListener(
                net.neoforged.neoforge.client.event.RenderLevelStageEvent.class, e -> {
                    if (e.getStage() == net.neoforged.neoforge.client.event.RenderLevelStageEvent.Stage.AFTER_PARTICLES
                            && e.getPoseStack() != null) {
                        dev.amman.proficiency.platform.bus.NeoForge.EVENT_BUS
                                .post(new RenderLevelEvent.Post(e.getPoseStack(), e.getCamera()));
                    }
                });
    }
}
