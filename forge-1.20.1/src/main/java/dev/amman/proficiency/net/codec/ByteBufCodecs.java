package dev.amman.proficiency.net.codec;

import net.minecraft.network.FriendlyByteBuf;

/** The two {@code ByteBufCodecs} entries master's payloads use, for {@link StreamCodec}. */
public final class ByteBufCodecs {

    public static final StreamCodec<FriendlyByteBuf, Integer> VAR_INT =
            StreamCodec.of(FriendlyByteBuf::writeVarInt, FriendlyByteBuf::readVarInt);

    public static final StreamCodec<FriendlyByteBuf, String> STRING_UTF8 =
            StreamCodec.of((buf, value) -> buf.writeUtf(value), buf -> buf.readUtf());

    private ByteBufCodecs() {
    }
}
