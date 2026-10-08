package dev.amman.proficiency.fabric.client;

import dev.amman.proficiency.platform.ClientPlatform;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/** {@link ClientPlatform} on Fabric. */
public final class FabricClientPlatform implements ClientPlatform {

    @Override
    public void sendToServer(CustomPacketPayload payload) {
        if (ClientPlayNetworking.canSend(payload.type())) {
            ClientPlayNetworking.send(payload);
        }
    }
}
