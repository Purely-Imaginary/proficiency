package dev.amman.proficiency.net;

import dev.amman.proficiency.Proficiency;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/**
 * A milestone ceremony: {@code tier} (10, 25, 50, 75 or 100) reached in a skill. Sent right after
 * the level-up packet, only to a client that registered it ({@code canReceive}), so a client without
 * it still joins and simply plays the ordinary level-up. The protocol number is unchanged. The two unlock
 * levels are the server's, so the banner names what really opened (the signature at 25 and the ability
 * at 50 by default, but both are server settings).
 */
public record MilestonePayload(int skillOrdinal, int tier, int procUnlock, int abilityUnlock) implements CustomPacketPayload {

    public static final CustomPacketPayload.Type<MilestonePayload> TYPE =
            new CustomPacketPayload.Type<>(Proficiency.id("milestone"));

    public static final StreamCodec<FriendlyByteBuf, MilestonePayload> STREAM_CODEC =
            StreamCodec.composite(
                    ByteBufCodecs.VAR_INT, MilestonePayload::skillOrdinal,
                    ByteBufCodecs.VAR_INT, MilestonePayload::tier,
                    ByteBufCodecs.VAR_INT, MilestonePayload::procUnlock,
                    ByteBufCodecs.VAR_INT, MilestonePayload::abilityUnlock,
                    MilestonePayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
