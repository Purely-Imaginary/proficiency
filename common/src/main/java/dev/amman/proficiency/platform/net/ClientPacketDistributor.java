package dev.amman.proficiency.platform.net;

import dev.amman.proficiency.platform.Services;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/** Client-to-server half; its own class so a dedicated server never resolves client networking. */
public final class ClientPacketDistributor {

    private ClientPacketDistributor() {
    }

    public static void sendToServer(CustomPacketPayload payload) {
        Services.client().sendToServer(payload);
    }
}
