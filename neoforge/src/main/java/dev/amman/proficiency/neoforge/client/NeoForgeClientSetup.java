package dev.amman.proficiency.neoforge.client;

import dev.amman.proficiency.client.CalledShotMarks;
import dev.amman.proficiency.client.ClientWiring;
import dev.amman.proficiency.client.OverlayFallback;
import dev.amman.proficiency.client.SkillTooltip;
import dev.amman.proficiency.item.ProficiencyItems;
import dev.amman.proficiency.platform.Subscribers;
import net.minecraft.client.renderer.item.ItemProperties;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent;
import net.neoforged.neoforge.client.event.ClientChatReceivedEvent;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent;
import net.neoforged.neoforge.client.event.RenderLivingEvent;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.entity.player.ItemTooltipEvent;

/** Client-only wiring on NeoForge. Only ever loaded from the mod constructor's client branch. */
public final class NeoForgeClientSetup {

    private NeoForgeClientSetup() {
    }

    public static void init(IEventBus modBus, ModContainer container) {
        // registerConfig returns nothing, so the client ModConfig is taken from its load event.
        java.util.concurrent.atomic.AtomicReference<net.neoforged.fml.config.ModConfig> clientConfig =
                new java.util.concurrent.atomic.AtomicReference<>();
        modBus.addListener((net.neoforged.fml.event.config.ModConfigEvent.Loading event) -> {
            if (event.getConfig().getSpec() == dev.amman.proficiency.config.ProficiencyClientConfig.SPEC) {
                clientConfig.set(event.getConfig());
            }
        });
        // Mods > Proficiency > Config opens the client file's sections directly. The stock
        // ConfigurationScreen would also list the server config, whose entries have no lang keys
        // and show as raw names; the server config is edited in its toml, not here.
        container.registerExtensionPoint(net.neoforged.neoforge.client.gui.IConfigScreenFactory.class,
                (ModContainer mod, net.minecraft.client.gui.screens.Screen parent) ->
                        clientConfig.get() == null ? parent
                                : new net.neoforged.neoforge.client.gui.ConfigurationScreen.ConfigurationSectionScreen(
                                        parent, net.neoforged.fml.config.ModConfig.Type.CLIENT,
                                        clientConfig.get(), net.minecraft.network.chat.Component.translatable(
                                                "proficiency.configuration.section.proficiency.client.toml.title",
                                                mod.getModInfo().getDisplayName())));
        modBus.addListener(NeoForgeClientSetup::onRegisterKeyMappings);
        modBus.addListener(NeoForgeClientSetup::onClientSetup);

        Subscribers.registerClient();
        NeoForgeEventBridgeClient.register();
        NeoForge.EVENT_BUS.register(GameEvents.class);
    }

    /** The NeoForge game-bus events the shared client code is called from. */
    public static final class GameEvents {

        private GameEvents() {
        }

        @SubscribeEvent
        public static void onLoggingOut(ClientPlayerNetworkEvent.LoggingOut event) {
            ClientWiring.onLeaveWorld();
        }

        @SubscribeEvent
        public static void onTooltip(ItemTooltipEvent event) {
            SkillTooltip.append(event.getEntity(), event.getItemStack(), event.getToolTip());
        }

        @SubscribeEvent
        public static void onSystemMessage(ClientChatReceivedEvent.System event) {
            if (OverlayFallback.reroute(event.getMessage(), event.isOverlay())) {
                event.setCanceled(true);
            }
        }

        @SubscribeEvent
        public static void onRenderLiving(RenderLivingEvent.Post<?, ?> event) {
            CalledShotMarks.render(event.getEntity(), event.getPartialTick(), event.getPoseStack(),
                    event.getMultiBufferSource());
        }
    }

    private static void onClientSetup(FMLClientSetupEvent event) {
        event.enqueueWork(() -> {
            ItemProperties.register(ProficiencyItems.FORESTERS_COMPASS.get(), ClientWiring.ANGLE,
                    ClientWiring.forestersCompassNeedle());
            ItemProperties.register(ProficiencyItems.FRIEND_COMPASS.get(), ClientWiring.ANGLE,
                    ClientWiring.friendCompassNeedle());
        });
    }

    private static void onRegisterKeyMappings(RegisterKeyMappingsEvent event) {
        ClientWiring.keyMappings().forEach(event::register);
    }
}
