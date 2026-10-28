package dev.amman.proficiency.net;

import dev.amman.proficiency.Proficiency;
import dev.amman.proficiency.haul.HaulSummary;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkDirection;
import net.minecraftforge.network.NetworkEvent;
import net.minecraftforge.network.NetworkRegistry;
import net.minecraftforge.network.PacketDistributor;
import net.minecraftforge.network.simple.SimpleChannel;

import java.util.Optional;

/**
 * Today's Haul (1.44.0) on its own Forge channel, {@code proficiency:haul}. The main channel multiplexes by index and
 * an index it does not know breaks an older client, so a new payload there would need a protocol bump. This
 * channel is optional both ways ({@code acceptMissingOr}): a client without it joins, is never sent the card
 * ({@link #canReceive}), and gets the totals in chat from {@code /proficiency haul}; a server without it never says
 * hello, so the client never asks.
 */
public final class HaulNetwork {

    public static final String VERSION = "1";

    public static final SimpleChannel CHANNEL = NetworkRegistry.newSimpleChannel(
            Proficiency.id("haul"), () -> VERSION,
            NetworkRegistry.acceptMissingOr(VERSION), NetworkRegistry.acceptMissingOr(VERSION));

    private HaulNetwork() {
    }

    public static void register() {
        CHANNEL.registerMessage(0, HaulPayload.class, (payload, buf) -> HaulPayload.STREAM_CODEC.encode(buf, payload),
                HaulPayload.STREAM_CODEC::decode, (payload, context) -> {
                    NetworkEvent.Context ctx = context.get();
                    // Client-only class, touched only inside the lambda: a dedicated server never loads it.
                    ctx.enqueueWork(() -> dev.amman.proficiency.client.HaulClient.accept(payload.summary()));
                    ctx.setPacketHandled(true);
                }, Optional.of(NetworkDirection.PLAY_TO_CLIENT));
        CHANNEL.registerMessage(1, HaulRequestPayload.class,
                (payload, buf) -> HaulRequestPayload.STREAM_CODEC.encode(buf, payload),
                HaulRequestPayload.STREAM_CODEC::decode, (payload, context) -> {
                    NetworkEvent.Context ctx = context.get();
                    ServerPlayer player = ctx.getSender();
                    ctx.enqueueWork(() -> {
                        if (player != null) {
                            dev.amman.proficiency.haul.HaulService.onRequest(player, payload.reason());
                        }
                    });
                    ctx.setPacketHandled(true);
                }, Optional.of(NetworkDirection.PLAY_TO_SERVER));
    }

    /** True when this client has the haul channel (an older client, or a GameTest mock player, does not). */
    public static boolean canReceive(ServerPlayer player) {
        return player.connection != null && player.connection.connection.channel() != null
                && CHANNEL.isRemotePresent(player.connection.connection);
    }

    public static void send(ServerPlayer player, HaulSummary summary) {
        if (summary != null && canReceive(player)) {
            CHANNEL.send(PacketDistributor.PLAYER.with(() -> player), new HaulPayload(summary));
        }
    }

    public static void request(int reason) {
        CHANNEL.sendToServer(new HaulRequestPayload(reason));
    }
}
