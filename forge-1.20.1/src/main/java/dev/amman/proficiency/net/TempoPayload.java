package dev.amman.proficiency.net;

import net.minecraft.network.FriendlyByteBuf;
import dev.amman.proficiency.net.codec.StreamCodec;

/**
 * The Tempo chain of the skill the player's last action fed (protocol 14 on this line): its step, the
 * cap, the multiplier in thousandths and the window in ticks. Sent on a change only, never per tick.
 */
public record TempoPayload(int skillOrdinal, int steps, int maxSteps, int permille, int windowTicks) {

    public static final String ID = "tempo";

    public static final StreamCodec<FriendlyByteBuf, TempoPayload> STREAM_CODEC = StreamCodec.of(
            (buf, payload) -> {
                buf.writeVarInt(payload.skillOrdinal());
                buf.writeVarInt(payload.steps());
                buf.writeVarInt(payload.maxSteps());
                buf.writeVarInt(payload.permille());
                buf.writeVarInt(payload.windowTicks());
            },
            buf -> new TempoPayload(buf.readVarInt(), buf.readVarInt(), buf.readVarInt(), buf.readVarInt(),
                    buf.readVarInt()));

}
