package dev.amman.proficiency.hearth.net;

import net.minecraft.network.FriendlyByteBuf;
import dev.amman.proficiency.net.codec.StreamCodec;

/**
 * The bell's arrow and compass ping (docs/HEARTH-SPEC.md, "The bell"): show the Hearth at
 * {@code pos} for {@code ticks} (a HUD arrow, the siege panel's arrow, a ping on a compass bound to
 * its lodestone). {@code call} is a {@code BellRules.Call} ordinal. One packet per ring, no
 * streaming. Server: Tales (the bell). Client: {@code HearthClient.onPing}.
 */
public record HearthPingPayload(int call, long pos, String dimension, int ticks) {

    private static final int MAX_STRING = 256;

    /** The payload's name on the wire (its index in {@code ProficiencyNetwork} is what travels). */
    public static final String ID = "hearth_ping";

    public static final StreamCodec<FriendlyByteBuf, HearthPingPayload> STREAM_CODEC = StreamCodec.of(
            (buf, p) -> {
                buf.writeVarInt(p.call());
                buf.writeLong(p.pos());
                buf.writeUtf(p.dimension(), MAX_STRING);
                buf.writeVarInt(p.ticks());
            },
            buf -> new HearthPingPayload(buf.readVarInt(), buf.readLong(), buf.readUtf(MAX_STRING), buf.readVarInt()));
}
