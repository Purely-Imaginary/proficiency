package dev.amman.proficiency.client;

import dev.amman.proficiency.item.ItemData;
import dev.amman.proficiency.item.ProficiencyItems;
import net.minecraft.client.renderer.item.CompassItemPropertyFunction;
import net.minecraft.client.renderer.item.ItemProperties;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.client.ConfigScreenHandler;
import net.minecraftforge.client.event.ClientPlayerNetworkEvent;
import net.minecraftforge.client.event.RegisterKeyMappingsEvent;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.fml.ModLoadingContext;
import net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent;

/** Client-only wiring. Only ever loaded from the mod constructor's client branch. */
public final class ProficiencyClientSetup {

    private ProficiencyClientSetup() {
    }

    public static void init(IEventBus modBus, ModLoadingContext container) {
        // Mods > Proficiency > Config opens the client file. Forge 1.20.1 has no generic config
        // screen (NeoForge's ConfigurationScreen), so ClientConfigScreen draws one entry per key.
        container.registerExtensionPoint(ConfigScreenHandler.ConfigScreenFactory.class,
                () -> new ConfigScreenHandler.ConfigScreenFactory(
                        (minecraft, parent) -> new ClientConfigScreen(parent)));
        modBus.addListener(ProficiencyClientSetup::onRegisterKeyMappings);
        modBus.addListener(ProficiencyClientSetup::onClientSetup);
        // The recent-XP list is per session on the server; leaving a world ends it here too, or
        // the next server would open on the last one's lines.
        MinecraftForge.EVENT_BUS.addListener((ClientPlayerNetworkEvent.LoggingOut event) -> {
            ClientXpLog.clear();
            XpFeedHud.clear();
            TempoBadge.reset();
            SynergySlam.reset();
            HudFlights.reset();
            DiscoveryBanner.clear();
            MilestoneCeremony.clear();
            ClientVisited.clear();
            HudState.reset();
            // The Hearth's client copies (the HUD packet, then the client lane's views) and Homebuilder's.
            HomebuilderHud.reset();
            // A tale panel still inside its five seconds must not greet the next world.
            TalesPayInHud.reset();
            dev.amman.proficiency.hearth.net.HearthClients.leaveWorld();
        });
        // The Hearth's client lane installs its payload handler and HUD listeners.
        dev.amman.proficiency.hearth.client.HearthClientInit.init();
        // Homebuilder's channel ring, a friend's House Proud glow and the wheel's two slots.
        HomebuilderHud.install();
    }

    /** Vanilla's needle maths, fed from the stack's stored target instead of a lodestone. */
    private static void onClientSetup(FMLClientSetupEvent event) {
        event.enqueueWork(() -> {
            ItemProperties.register(ProficiencyItems.FORESTERS_COMPASS.get(),
                    new ResourceLocation("angle"),
                    new CompassItemPropertyFunction((level, stack, entity) -> ItemData.target(stack)));
            // The friend's live position when this client can see them (inside entity-tracking
            // range), else the server's coarse copy in the stack. See FriendCompassItem.
            ItemProperties.register(ProficiencyItems.FRIEND_COMPASS.get(),
                    new ResourceLocation("angle"),
                    new CompassItemPropertyFunction((level, stack, entity) -> {
                        java.util.UUID id = dev.amman.proficiency.item.FriendCompassItem.friend(stack);
                        if (id != null && level != null) {
                            net.minecraft.world.entity.player.Player friend = level.getPlayerByUUID(id);
                            if (friend != null) {
                                return net.minecraft.core.GlobalPos.of(level.dimension(), friend.blockPosition());
                            }
                        }
                        return ItemData.target(stack);
                    }));
        });
    }

    private static void onRegisterKeyMappings(RegisterKeyMappingsEvent event) {
        event.register(ProficiencyClient.OPEN_SKILLS);
        event.register(ProficiencyClient.USE_ABILITY);
        event.register(ProficiencyClient.WAYWHEEL);
        event.register(ProficiencyClient.HEARTH_MAP);
    }
}
