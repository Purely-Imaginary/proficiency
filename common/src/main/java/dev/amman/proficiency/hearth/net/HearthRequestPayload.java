package dev.amman.proficiency.hearth.net;

import dev.amman.proficiency.Proficiency;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/**
 * Client to server: the panel asks for something about the Hearth at {@code pos}. {@link #LEDGER}:
 * the keepers tab (Charge answers with {@link HearthLedgerPayload}); {@link #STATE}: a fresh state
 * (Stages answers with {@link HearthStatePayload}, reason UPDATE). Handled through
 * {@code HearthHooks.REQUEST}; the listener checks the player may ask (in range, or an op).
 */
public record HearthRequestPayload(int what, long pos) implements CustomPacketPayload {

    public static final int LEDGER = 0;
    public static final int STATE = 1;

    public static final CustomPacketPayload.Type<HearthRequestPayload> TYPE =
            new CustomPacketPayload.Type<>(Proficiency.id("hearth_request"));

    public static final StreamCodec<FriendlyByteBuf, HearthRequestPayload> STREAM_CODEC = StreamCodec.of(
            (buf, p) -> {
                buf.writeVarInt(p.what());
                buf.writeLong(p.pos());
            },
            buf -> new HearthRequestPayload(buf.readVarInt(), buf.readLong()));

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
