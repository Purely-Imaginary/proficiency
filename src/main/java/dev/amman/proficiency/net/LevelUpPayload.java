package dev.amman.proficiency.net;

import dev.amman.proficiency.Proficiency;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/** "Skill increased: Axes 14". Separate from the state sync so the toast fires exactly once. */
public record LevelUpPayload(int skillOrdinal, int level) implements CustomPacketPayload {

    public static final CustomPacketPayload.Type<LevelUpPayload> TYPE =
            new CustomPacketPayload.Type<>(Proficiency.id("level_up"));

    public static final StreamCodec<FriendlyByteBuf, LevelUpPayload> STREAM_CODEC =
            StreamCodec.composite(
                    ByteBufCodecs.VAR_INT, LevelUpPayload::skillOrdinal,
                    ByteBufCodecs.VAR_INT, LevelUpPayload::level,
                    LevelUpPayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
