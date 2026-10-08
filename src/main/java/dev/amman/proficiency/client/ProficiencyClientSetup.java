package dev.amman.proficiency.client;

import dev.amman.proficiency.item.ProficiencyItems;
import net.minecraft.client.renderer.item.CompassItemPropertyFunction;
import net.minecraft.client.renderer.item.ItemProperties;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.item.component.LodestoneTracker;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent;
import net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent;

/** Client-only wiring. Only ever loaded from the mod constructor's client branch. */
public final class ProficiencyClientSetup {

    private ProficiencyClientSetup() {
    }

    public static void init(IEventBus modBus, net.neoforged.fml.ModContainer container) {
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
                (net.neoforged.fml.ModContainer mod, net.minecraft.client.gui.screens.Screen parent) ->
                        clientConfig.get() == null ? parent
                                : new net.neoforged.neoforge.client.gui.ConfigurationScreen.ConfigurationSectionScreen(
                                        parent, net.neoforged.fml.config.ModConfig.Type.CLIENT,
                                        clientConfig.get(), net.minecraft.network.chat.Component.translatable(
                                                "proficiency.configuration.section.proficiency.client.toml.title",
                                                mod.getModInfo().getDisplayName())));
        modBus.addListener(ProficiencyClientSetup::onRegisterKeyMappings);
        modBus.addListener(ProficiencyClientSetup::onClientSetup);
        // The recent-XP list is per session on the server; leaving a world ends it here too, or
        // the next server would open on the last one's lines.
        net.neoforged.neoforge.common.NeoForge.EVENT_BUS.addListener(
                (net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent.LoggingOut event) ->
                {
                    ClientXpLog.clear();
                    XpFeedHud.clear();
                    DiscoveryBanner.clear();
                    ClientVisited.clear();
                    HudState.reset();
                });
    }

    /** Vanilla's needle maths, fed from the stack's lodestone tracker instead of a lodestone. */
    private static void onClientSetup(FMLClientSetupEvent event) {
        event.enqueueWork(() -> {
            ItemProperties.register(ProficiencyItems.FORESTERS_COMPASS.get(),
                    net.minecraft.resources.ResourceLocation.withDefaultNamespace("angle"),
                    new CompassItemPropertyFunction((level, stack, entity) -> {
                        LodestoneTracker tracker = stack.get(DataComponents.LODESTONE_TRACKER);
                        return tracker == null ? null : tracker.target().orElse(null);
                    }));
            // The friend's live position when this client can see them (inside entity-tracking
            // range), else the server's coarse copy in the lodestone tracker. See FriendCompassItem.
            ItemProperties.register(ProficiencyItems.FRIEND_COMPASS.get(),
                    net.minecraft.resources.ResourceLocation.withDefaultNamespace("angle"),
                    new CompassItemPropertyFunction((level, stack, entity) -> {
                        java.util.UUID id = dev.amman.proficiency.item.FriendCompassItem.friend(stack);
                        if (id != null && level != null) {
                            net.minecraft.world.entity.player.Player friend = level.getPlayerByUUID(id);
                            if (friend != null) {
                                return net.minecraft.core.GlobalPos.of(level.dimension(), friend.blockPosition());
                            }
                        }
                        LodestoneTracker tracker = stack.get(DataComponents.LODESTONE_TRACKER);
                        return tracker == null ? null : tracker.target().orElse(null);
                    }));
        });
    }

    private static void onRegisterKeyMappings(RegisterKeyMappingsEvent event) {
        event.register(ProficiencyClient.OPEN_SKILLS);
        event.register(ProficiencyClient.USE_ABILITY);
    }
}
