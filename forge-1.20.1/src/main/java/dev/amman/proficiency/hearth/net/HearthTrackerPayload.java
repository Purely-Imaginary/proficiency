package dev.amman.proficiency.hearth.net;

import net.minecraft.network.FriendlyByteBuf;
import dev.amman.proficiency.net.codec.StreamCodec;

import java.util.List;

/**
 * The tracker's rows alone, for a change that needs no full state (a siege burst, decay caught up on
 * load). Server: Charge, Sieges. Client: {@code HearthClient.onTracker}.
 */
public record HearthTrackerPayload(long pos, List<PoolRow> rows) {

    /** The payload's name on the wire (its index in {@code ProficiencyNetwork} is what travels). */
    public static final String ID = "hearth_tracker";

    public static final StreamCodec<FriendlyByteBuf, HearthTrackerPayload> STREAM_CODEC = StreamCodec.of(
            (buf, p) -> {
                buf.writeLong(p.pos());
                PoolRow.writeList(buf, p.rows());
            },
            buf -> new HearthTrackerPayload(buf.readLong(), PoolRow.readList(buf)));
}
