package dev.amman.proficiency.event;

import dev.amman.proficiency.Proficiency;
import dev.amman.proficiency.platform.bus.EventBusSubscriber;
import dev.amman.proficiency.platform.bus.SubscribeEvent;
import dev.amman.proficiency.platform.event.server.ServerStartedEvent;
import dev.amman.proficiency.platform.event.server.ServerStoppingEvent;
import dev.amman.proficiency.platform.event.tick.ServerTickEvent;
import dev.amman.proficiency.xp.XpReload;

/** Loads the XP sources when the server starts, and again whenever /reload swaps its datapacks. */
@EventBusSubscriber(modid = Proficiency.MOD_ID)
public final class XpReloadEvents {

    private XpReloadEvents() {
    }

    @SubscribeEvent
    public static void onServerStarted(ServerStartedEvent event) {
        XpReload.reload(event.getServer());
    }

    @SubscribeEvent
    public static void onServerTick(ServerTickEvent.Post event) {
        XpReload.check(event.getServer());
    }

    @SubscribeEvent
    public static void onServerStopping(ServerStoppingEvent event) {
        XpReload.forget();
    }
}
