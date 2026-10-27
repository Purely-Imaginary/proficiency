package dev.amman.proficiency.net;

import dev.amman.proficiency.net.codec.StreamCodec;
import net.minecraft.network.FriendlyByteBuf;

/**
 * The optional Jade plugin asks what the block at {@code pos} would pay the held tool (Forge 1.20.1 only:
 * Jade 11 carries server data for block entities alone, and an ore has none). The server answers with a
 * {@link LookReplyPayload}. Never sent without Jade.
 */
public record LookRequestPayload(long pos) {

    public static final StreamCodec<FriendlyByteBuf, LookRequestPayload> STREAM_CODEC = StreamCodec.of(
            (buf, payload) -> buf.writeLong(payload.pos()), buf -> new LookRequestPayload(buf.readLong()));
}
