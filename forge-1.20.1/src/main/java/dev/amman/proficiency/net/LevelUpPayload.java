package dev.amman.proficiency.net;

import net.minecraft.network.FriendlyByteBuf;
import dev.amman.proficiency.net.codec.StreamCodec;

/**
 * "Skill increased: Axes 14". Separate from the state sync so the toast fires exactly once. With
 * {@code stars} above 0 it is a Mastery star earned at level 100 instead (protocol 5).
 */
public record LevelUpPayload(int skillOrdinal, int level, int stars) {

    public static final String ID = "level_up";

    public static final StreamCodec<FriendlyByteBuf, LevelUpPayload> STREAM_CODEC = StreamCodec.of(
            (buf, payload) -> {
                buf.writeVarInt(payload.skillOrdinal());
                buf.writeVarInt(payload.level());
                buf.writeVarInt(payload.stars());
            },
            buf -> new LevelUpPayload(buf.readVarInt(), buf.readVarInt(), buf.readVarInt()));

}
