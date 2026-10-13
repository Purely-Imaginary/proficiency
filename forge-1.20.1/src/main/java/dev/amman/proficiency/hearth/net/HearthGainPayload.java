package dev.amman.proficiency.hearth.net;

import net.minecraft.network.FriendlyByteBuf;
import dev.amman.proficiency.net.codec.StreamCodec;

import java.util.ArrayList;
import java.util.List;

/**
 * The mirrored shares of this player's gains since the last half-second sync tick: which pool, how
 * much, and the skill whose gain it mirrored, so the tracker launches its dots from that skill's
 * bar in the same frame the skill sync lands. Buffered by {@link HearthNet#queueGain} (Charge) and
 * flushed with the skill sync cadence. Client: {@code HearthClient.onGains}.
 */
public record HearthGainPayload(List<Gain> gains) {

    /** One mirrored share: pool ordinal, charge added, skill ordinal (-1 for none: Tales, a burst). */
    public record Gain(int pool, float amount, int skill) {
    }

    public static final int MAX = 64;

    /** The payload's name on the wire (its index in {@code ProficiencyNetwork} is what travels). */
    public static final String ID = "hearth_gain";

    public static final StreamCodec<FriendlyByteBuf, HearthGainPayload> STREAM_CODEC = StreamCodec.of(
            (buf, p) -> {
                int count = Math.min(MAX, p.gains().size());
                buf.writeVarInt(count);
                for (int i = 0; i < count; i++) {
                    Gain gain = p.gains().get(i);
                    buf.writeVarInt(gain.pool());
                    buf.writeFloat(gain.amount());
                    buf.writeVarInt(gain.skill() + 1);
                }
            },
            buf -> {
                int count = Math.min(MAX, Math.max(0, buf.readVarInt()));
                List<Gain> gains = new ArrayList<>(count);
                for (int i = 0; i < count; i++) {
                    gains.add(new Gain(buf.readVarInt(), buf.readFloat(), buf.readVarInt() - 1));
                }
                return new HearthGainPayload(List.copyOf(gains));
            });
}
