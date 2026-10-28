package dev.amman.proficiency.net;

import dev.amman.proficiency.net.codec.StreamCodec;
import net.minecraft.network.FriendlyByteBuf;

/**
 * A vanilla jump crit just gave a weapon skill's Tempo chain the long window (1.49.0): the client flares that
 * skill's flames white. Appended last, no protocol bump: the {@link TempoPayload} sent just before carries the
 * longer window itself.
 */
public record TempoCritPayload(int skillOrdinal) {

    public static final String ID = "tempo_crit";

    public static final StreamCodec<FriendlyByteBuf, TempoCritPayload> STREAM_CODEC = StreamCodec.of(
            (buf, payload) -> buf.writeVarInt(payload.skillOrdinal()),
            buf -> new TempoCritPayload(buf.readVarInt()));
}
