package dev.amman.proficiency.net;

import dev.amman.proficiency.Proficiency;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/** The client worked out which skill the player meant; the server decides whether it fires. */
public record ActivateAbilityPayload(int skillOrdinal) implements CustomPacketPayload {

    public static final CustomPacketPayload.Type<ActivateAbilityPayload> TYPE =
            new CustomPacketPayload.Type<>(Proficiency.id("activate_ability"));

    public static final StreamCodec<FriendlyByteBuf, ActivateAbilityPayload> STREAM_CODEC =
            StreamCodec.composite(
                    ByteBufCodecs.VAR_INT, ActivateAbilityPayload::skillOrdinal,
                    ActivateAbilityPayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
