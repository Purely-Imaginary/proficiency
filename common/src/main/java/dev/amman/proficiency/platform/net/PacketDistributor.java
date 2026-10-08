package dev.amman.proficiency.platform.net;

import dev.amman.proficiency.platform.Services;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.server.level.ServerPlayer;

/** Server-to-client sends, the shape NeoForge's {@code PacketDistributor} has. */
public final class PacketDistributor {

    private PacketDistributor() {
    }

    public static void sendToPlayer(ServerPlayer player, CustomPacketPayload payload) {
        Services.platform().sendToPlayer(player, payload);
    }
}
