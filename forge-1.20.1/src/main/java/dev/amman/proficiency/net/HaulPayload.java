package dev.amman.proficiency.net;

import dev.amman.proficiency.haul.HaulSummary;
import dev.amman.proficiency.skill.SkillsWire;
import net.minecraft.network.FriendlyByteBuf;
import dev.amman.proficiency.net.codec.StreamCodec;

/**
 * Today's Haul (1.44.0): the card's numbers, laid out by {@link HaulSummary#write}. Carried on its own optional
 * channel ({@link HaulNetwork}), not the main one, so {@link ProficiencyNetwork#PROTOCOL} does not move and an older
 * client still joins.
 */
public record HaulPayload(HaulSummary summary) {

    public static final StreamCodec<FriendlyByteBuf, HaulPayload> STREAM_CODEC = StreamCodec.of(
            (buf, payload) -> payload.summary().write(new SkillsWire.Out() {
                @Override
                public void writeVarInt(int value) {
                    buf.writeVarInt(value);
                }

                @Override
                public void writeFloat(float value) {
                    buf.writeFloat(value);
                }

                @Override
                public void writeUtf(String value) {
                    buf.writeUtf(value);
                }

                @Override
                public void writeVarLong(long value) {
                    buf.writeVarLong(value);
                }
            }),
            buf -> new HaulPayload(HaulSummary.read(new SkillsWire.In() {
                @Override
                public int readVarInt() {
                    return buf.readVarInt();
                }

                @Override
                public float readFloat() {
                    return buf.readFloat();
                }

                @Override
                public String readUtf() {
                    return buf.readUtf();
                }

                @Override
                public long readVarLong() {
                    return buf.readVarLong();
                }
            })));
}
