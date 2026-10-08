package dev.amman.proficiency.platform;

import net.minecraft.server.MinecraftServer;
import org.jetbrains.annotations.Nullable;

/** The running server, as the loader tracks it. */
public final class ServerLifecycleHooks {

    private ServerLifecycleHooks() {
    }

    @Nullable
    public static MinecraftServer getCurrentServer() {
        return Services.platform().currentServer();
    }
}
