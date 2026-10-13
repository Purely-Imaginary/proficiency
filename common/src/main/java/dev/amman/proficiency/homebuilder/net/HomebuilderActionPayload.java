package dev.amman.proficiency.homebuilder.net;

import dev.amman.proficiency.Proficiency;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/**
 * Client to server: a Homebuilder slot of the ability wheel was picked. {@link #MAGNET} flips the
 * magnet switch (the same as {@code /skills magnet}); {@link #HOMEWARD} starts the capstone's channel
 * (the same as {@code /skills homeward}). The server checks everything again.
 */
public record HomebuilderActionPayload(int action) implements CustomPacketPayload {

    public static final int MAGNET = 0;
    public static final int HOMEWARD = 1;

    public static final CustomPacketPayload.Type<HomebuilderActionPayload> TYPE =
            new CustomPacketPayload.Type<>(Proficiency.id("homebuilder_action"));

    public static final StreamCodec<FriendlyByteBuf, HomebuilderActionPayload> STREAM_CODEC = StreamCodec.of(
            (buf, p) -> buf.writeVarInt(p.action()),
            buf -> new HomebuilderActionPayload(buf.readVarInt()));

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
