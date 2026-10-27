package dev.amman.proficiency.hearth.net;

import net.minecraft.network.FriendlyByteBuf;
import dev.amman.proficiency.net.codec.StreamCodec;

/**
 * Client to server: the panel asks for something about the Hearth at {@code pos}. {@link #LEDGER}:
 * the keepers tab (Charge answers with {@link HearthLedgerPayload}); {@link #STATE}: a fresh state
 * (Stages answers with {@link HearthStatePayload}, reason UPDATE). Handled through
 * {@code HearthHooks.REQUEST}; the listener checks the player may ask (in range, or an op).
 */
public record HearthRequestPayload(int what, long pos, int arg, String text) {

    public static final int LEDGER = 0;
    public static final int STATE = 1;
    /** Start the self-build of the next stage; {@code arg} is the lane index, or -1 for the closest lane. */
    public static final int BUILD_START = 2;
    public static final int BUILD_STOP = 3;
    /** The plan screen asks for the plan; {@code arg} is the lane index, or -1 for the lane the Hearth stands as. */
    public static final int BUILD_PLAN = 4;
    /** The plan screen picks a block: {@code arg} is the role letter, {@code text} the block id ("" back to automatic). */
    public static final int BUILD_CHOOSE = 5;

    public HearthRequestPayload(int what, long pos) {
        this(what, pos, 0, "");
    }

    public HearthRequestPayload(int what, long pos, int arg) {
        this(what, pos, arg, "");
    }

    /** The payload's name on the wire (its index in {@code ProficiencyNetwork} is what travels). */
    public static final String ID = "hearth_request";

    public static final StreamCodec<FriendlyByteBuf, HearthRequestPayload> STREAM_CODEC = StreamCodec.of(
            (buf, p) -> {
                buf.writeVarInt(p.what());
                buf.writeLong(p.pos());
                buf.writeVarInt(p.arg());
                buf.writeUtf(p.text() == null ? "" : p.text(), 128);
            },
            buf -> new HearthRequestPayload(buf.readVarInt(), buf.readLong(), buf.readVarInt(), buf.readUtf(128)));
}
