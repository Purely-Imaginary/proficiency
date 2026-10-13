package dev.amman.proficiency.homebuilder.net;

import net.minecraft.network.FriendlyByteBuf;
import dev.amman.proficiency.net.codec.StreamCodec;

/**
 * Client to server: a Homebuilder slot of the ability wheel was picked. {@link #MAGNET} flips the
 * magnet switch (the same as {@code /skills magnet}); {@link #HOMEWARD} starts the capstone's channel
 * (the same as {@code /skills homeward}). The server checks everything again.
 */
public record HomebuilderActionPayload(int action) {

    public static final int MAGNET = 0;
    public static final int HOMEWARD = 1;

    /** The payload's name on the wire (its index in {@code ProficiencyNetwork} is what travels). */
    public static final String ID = "homebuilder_action";

    public static final StreamCodec<FriendlyByteBuf, HomebuilderActionPayload> STREAM_CODEC = StreamCodec.of(
            (buf, p) -> buf.writeVarInt(p.action()),
            buf -> new HomebuilderActionPayload(buf.readVarInt()));
}
