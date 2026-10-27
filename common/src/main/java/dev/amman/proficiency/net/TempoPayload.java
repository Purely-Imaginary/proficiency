package dev.amman.proficiency.net;

import dev.amman.proficiency.Proficiency;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/**
 * The Tempo chain of the skill the player's last action fed (protocol 13): its step, the cap, the
 * multiplier in thousandths and the window in ticks. Sent on a change only, never per tick.
 */
public record TempoPayload(int skillOrdinal, int steps, int maxSteps, int permille, int windowTicks)
        implements CustomPacketPayload {

    public static final CustomPacketPayload.Type<TempoPayload> TYPE =
            new CustomPacketPayload.Type<>(Proficiency.id("tempo"));

    public static final StreamCodec<FriendlyByteBuf, TempoPayload> STREAM_CODEC =
            StreamCodec.composite(
                    ByteBufCodecs.VAR_INT, TempoPayload::skillOrdinal,
                    ByteBufCodecs.VAR_INT, TempoPayload::steps,
                    ByteBufCodecs.VAR_INT, TempoPayload::maxSteps,
                    ByteBufCodecs.VAR_INT, TempoPayload::permille,
                    ByteBufCodecs.VAR_INT, TempoPayload::windowTicks,
                    TempoPayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
