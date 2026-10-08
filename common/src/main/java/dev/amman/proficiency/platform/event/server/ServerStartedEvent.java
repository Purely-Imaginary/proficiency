package dev.amman.proficiency.platform.event.server;

import dev.amman.proficiency.platform.bus.Event;
import net.minecraft.server.MinecraftServer;

/** The server finished starting (Fabric SERVER_STARTED). */
public class ServerStartedEvent extends Event {

    private final MinecraftServer server;

    public ServerStartedEvent(MinecraftServer server) {
        this.server = server;
    }

    public MinecraftServer getServer() {
        return server;
    }
}
