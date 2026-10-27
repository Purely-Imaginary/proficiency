package dev.amman.proficiency.net;

import dev.amman.proficiency.net.codec.StreamCodec;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.FriendlyByteBuf;

/** The answer to a {@link LookRequestPayload}: the tag {@code XpLookData.block} wrote, empty when there is nothing to show. */
public record LookReplyPayload(long pos, CompoundTag data) {

    public static final StreamCodec<FriendlyByteBuf, LookReplyPayload> STREAM_CODEC = StreamCodec.of(
            (buf, payload) -> {
                buf.writeLong(payload.pos());
                buf.writeNbt(payload.data());
            },
            buf -> {
                long pos = buf.readLong();
                CompoundTag data = buf.readNbt();
                return new LookReplyPayload(pos, data == null ? new CompoundTag() : data);
            });
}
