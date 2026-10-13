package dev.amman.proficiency.hearth.net;

import dev.amman.proficiency.Proficiency;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

import java.util.List;

/**
 * The tracker's rows alone, for a change that needs no full state (a siege burst, decay caught up on
 * load). Server: Charge, Sieges. Client: {@code HearthClient.onTracker}.
 */
public record HearthTrackerPayload(long pos, List<PoolRow> rows) implements CustomPacketPayload {

    public static final CustomPacketPayload.Type<HearthTrackerPayload> TYPE =
            new CustomPacketPayload.Type<>(Proficiency.id("hearth_tracker"));

    public static final StreamCodec<FriendlyByteBuf, HearthTrackerPayload> STREAM_CODEC = StreamCodec.of(
            (buf, p) -> {
                buf.writeLong(p.pos());
                PoolRow.writeList(buf, p.rows());
            },
            buf -> new HearthTrackerPayload(buf.readLong(), PoolRow.readList(buf)));

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
