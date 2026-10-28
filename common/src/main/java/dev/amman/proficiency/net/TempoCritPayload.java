package dev.amman.proficiency.net;

import dev.amman.proficiency.Proficiency;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/**
 * A vanilla jump crit just gave a weapon skill's Tempo chain the long window (1.49.0): the client flares that
 * skill's flames white. Optional, no protocol bump: the {@link TempoPayload} sent just before carries the longer
 * window itself, and a client without this channel simply sees no white flare.
 */
public record TempoCritPayload(int skillOrdinal) implements CustomPacketPayload {

    public static final CustomPacketPayload.Type<TempoCritPayload> TYPE =
            new CustomPacketPayload.Type<>(Proficiency.id("tempo_crit"));

    public static final StreamCodec<FriendlyByteBuf, TempoCritPayload> STREAM_CODEC =
            StreamCodec.composite(ByteBufCodecs.VAR_INT, TempoCritPayload::skillOrdinal, TempoCritPayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
