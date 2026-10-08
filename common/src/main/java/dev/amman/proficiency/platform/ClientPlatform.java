package dev.amman.proficiency.platform;

import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/**
 * The client-only half of {@link Platform}, kept apart so a dedicated server never loads a
 * loader's client networking. Found only from client code.
 */
public interface ClientPlatform {

    void sendToServer(CustomPacketPayload payload);
}
