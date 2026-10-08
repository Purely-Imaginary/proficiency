package dev.amman.proficiency.net;

import net.minecraft.network.FriendlyByteBuf;
import dev.amman.proficiency.net.codec.ByteBufCodecs;
import dev.amman.proficiency.net.codec.StreamCodec;

/** The client worked out which skill the player meant; the server decides whether it fires. */
public record ActivateAbilityPayload(int skillOrdinal) {

    public static final String ID = "activate_ability";

    public static final StreamCodec<FriendlyByteBuf, ActivateAbilityPayload> STREAM_CODEC =
            StreamCodec.composite(
                    ByteBufCodecs.VAR_INT, ActivateAbilityPayload::skillOrdinal,
                    ActivateAbilityPayload::new);

}
