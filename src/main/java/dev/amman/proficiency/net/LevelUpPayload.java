package dev.amman.proficiency.net;

import net.minecraft.network.FriendlyByteBuf;
import dev.amman.proficiency.net.codec.ByteBufCodecs;
import dev.amman.proficiency.net.codec.StreamCodec;

/** "Skill increased: Axes 14". Separate from the state sync so the toast fires exactly once. */
public record LevelUpPayload(int skillOrdinal, int level) {

    public static final String ID = "level_up";

    public static final StreamCodec<FriendlyByteBuf, LevelUpPayload> STREAM_CODEC =
            StreamCodec.composite(
                    ByteBufCodecs.VAR_INT, LevelUpPayload::skillOrdinal,
                    ByteBufCodecs.VAR_INT, LevelUpPayload::level,
                    LevelUpPayload::new);

}
