package dev.amman.proficiency.neoforge.client;

import dev.amman.proficiency.platform.ClientPlatform;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.neoforged.neoforge.network.PacketDistributor;

/** {@link ClientPlatform} on NeoForge. */
public final class NeoForgeClientPlatform implements ClientPlatform {

    @Override
    public void sendToServer(CustomPacketPayload payload) {
        PacketDistributor.sendToServer(payload);
    }
}
