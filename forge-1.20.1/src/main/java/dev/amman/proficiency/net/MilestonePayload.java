package dev.amman.proficiency.net;

import net.minecraft.network.FriendlyByteBuf;
import dev.amman.proficiency.net.codec.StreamCodec;

/**
 * A milestone ceremony: {@code tier} (10, 25, 50, 75 or 100) reached in a skill (no protocol bump on this
 * line). Sent right after the level-up packet.
 */
public record MilestonePayload(int skillOrdinal, int tier, int procUnlock, int abilityUnlock) {

    public static final String ID = "milestone";

    public static final StreamCodec<FriendlyByteBuf, MilestonePayload> STREAM_CODEC = StreamCodec.of(
            (buf, payload) -> {
                buf.writeVarInt(payload.skillOrdinal());
                buf.writeVarInt(payload.tier());
                buf.writeVarInt(payload.procUnlock());
                buf.writeVarInt(payload.abilityUnlock());
            },
            buf -> new MilestonePayload(buf.readVarInt(), buf.readVarInt(), buf.readVarInt(), buf.readVarInt()));

}
