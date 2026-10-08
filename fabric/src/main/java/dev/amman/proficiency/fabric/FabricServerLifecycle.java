package dev.amman.proficiency.fabric;

import dev.amman.proficiency.platform.bus.NeoForge;
import dev.amman.proficiency.platform.event.server.ServerStartedEvent;
import dev.amman.proficiency.platform.event.server.ServerStoppingEvent;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.minecraft.server.MinecraftServer;
import org.jetbrains.annotations.Nullable;

/** The running server, tracked from Fabric's lifecycle events, and NeoForge's two server events. */
public final class FabricServerLifecycle {

    @Nullable
    private static volatile MinecraftServer current;

    private FabricServerLifecycle() {
    }

    public static void init() {
        ServerLifecycleEvents.SERVER_STARTING.register(server -> current = server);
        ServerLifecycleEvents.SERVER_STOPPING.register(server ->
                NeoForge.EVENT_BUS.post(new ServerStoppingEvent(server)));
        ServerLifecycleEvents.SERVER_STARTED.register(server ->
                NeoForge.EVENT_BUS.post(new ServerStartedEvent(server)));
        ServerLifecycleEvents.SERVER_STOPPED.register(server -> current = null);
    }

    @Nullable
    static MinecraftServer currentServer() {
        return current;
    }
}
