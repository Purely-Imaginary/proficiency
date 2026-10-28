package dev.amman.proficiency.net;

import dev.amman.proficiency.Proficiency;
import dev.amman.proficiency.haul.HaulSummary;
import dev.amman.proficiency.skill.SkillsWire;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/**
 * Today's Haul (1.44.0): the card's numbers, laid out by {@link HaulSummary#write}. Optional: the server sends it
 * only to a client that registered it, so an older client still joins and simply never sees a card. Not part of
 * {@link ProficiencyNetwork#PROTOCOL}.
 */
public record HaulPayload(HaulSummary summary) implements CustomPacketPayload {

    public static final CustomPacketPayload.Type<HaulPayload> TYPE =
            new CustomPacketPayload.Type<>(Proficiency.id("haul"));

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

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
