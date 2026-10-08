package dev.amman.proficiency.platform.event.server;

import dev.amman.proficiency.platform.bus.Event;
import net.minecraft.server.MinecraftServer;

/** The server is about to stop (Fabric SERVER_STOPPING). */
public class ServerStoppingEvent extends Event {

    private final MinecraftServer server;

    public ServerStoppingEvent(MinecraftServer server) {
        this.server = server;
    }

    public MinecraftServer getServer() {
        return server;
    }
}
