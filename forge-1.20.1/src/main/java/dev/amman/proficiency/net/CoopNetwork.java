package dev.amman.proficiency.net;

import dev.amman.proficiency.Proficiency;
import dev.amman.proficiency.coop.CoopBatch;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkDirection;
import net.minecraftforge.network.NetworkEvent;
import net.minecraftforge.network.NetworkRegistry;
import net.minecraftforge.network.PacketDistributor;
import net.minecraftforge.network.simple.SimpleChannel;

import java.util.Optional;

/**
 * Co-op XP streams (1.45.0) on their own Forge channel, {@code proficiency:coop}, like the haul's: an index on the
 * main channel that an older client does not know would break it, so a new payload there would need a protocol
 * bump. Optional both ways ({@code acceptMissingOr}): a client without it joins and is never sent a batch.
 */
public final class CoopNetwork {

    public static final String VERSION = "1";

    public static final SimpleChannel CHANNEL = NetworkRegistry.newSimpleChannel(
            Proficiency.id("coop"), () -> VERSION,
            NetworkRegistry.acceptMissingOr(VERSION), NetworkRegistry.acceptMissingOr(VERSION));

    private CoopNetwork() {
    }

    public static void register() {
        CHANNEL.registerMessage(0, CoopPayload.class, (payload, buf) -> CoopPayload.STREAM_CODEC.encode(buf, payload),
                CoopPayload.STREAM_CODEC::decode, (payload, context) -> {
                    NetworkEvent.Context ctx = context.get();
                    // Client-only class, touched only inside the lambda: a dedicated server never loads it.
                    ctx.enqueueWork(() -> dev.amman.proficiency.client.CoopStreams.accept(payload.batch()));
                    ctx.setPacketHandled(true);
                }, Optional.of(NetworkDirection.PLAY_TO_CLIENT));
    }

    /** True when this client has the coop channel (an older client, or a GameTest mock player, does not). */
    public static boolean canReceive(ServerPlayer player) {
        return player.connection != null && player.connection.connection.channel() != null
                && CHANNEL.isRemotePresent(player.connection.connection);
    }

    public static void send(ServerPlayer player, CoopBatch batch) {
        if (batch != null && canReceive(player)) {
            CHANNEL.send(PacketDistributor.PLAYER.with(() -> player), new CoopPayload(batch));
        }
    }
}
