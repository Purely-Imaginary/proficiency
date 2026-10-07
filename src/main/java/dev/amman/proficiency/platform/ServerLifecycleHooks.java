package dev.amman.proficiency.platform;

import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.minecraft.server.MinecraftServer;
import org.jetbrains.annotations.Nullable;

/** The running server, tracked from Fabric's lifecycle events. */
public final class ServerLifecycleHooks {

    @Nullable
    private static volatile MinecraftServer current;

    private ServerLifecycleHooks() {
    }

    public static void init() {
        ServerLifecycleEvents.SERVER_STARTING.register(server -> current = server);
        // NeoForge's ServerStoppingEvent: closes any XP recording so the last lines reach the file.
        ServerLifecycleEvents.SERVER_STOPPING.register(server -> {
            dev.amman.proficiency.skill.XpFeedRecorder.stopAll();
            dev.amman.proficiency.platform.bus.NeoForge.EVENT_BUS.post(
                    new dev.amman.proficiency.platform.event.server.ServerStoppingEvent(server));
        });
        ServerLifecycleEvents.SERVER_STARTED.register(server ->
                dev.amman.proficiency.platform.bus.NeoForge.EVENT_BUS.post(
                        new dev.amman.proficiency.platform.event.server.ServerStartedEvent(server)));
        ServerLifecycleEvents.SERVER_STOPPED.register(server -> current = null);
    }

    @Nullable
    public static MinecraftServer getCurrentServer() {
        return current;
    }
}
