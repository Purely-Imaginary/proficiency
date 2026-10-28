package dev.amman.proficiency.net;

import dev.amman.proficiency.Proficiency;
import dev.amman.proficiency.coop.CoopBatch;
import dev.amman.proficiency.skill.SkillsWire;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/**
 * Co-op XP streams (1.45.0): one second of a partner's XP and the pip row, laid out by {@link CoopBatch#write}.
 * Optional: the server sends it only to a client that registered it, so an older client still joins and simply
 * sees no streams. Not part of {@link ProficiencyNetwork#PROTOCOL}.
 */
public record CoopPayload(CoopBatch batch) implements CustomPacketPayload {

    public static final CustomPacketPayload.Type<CoopPayload> TYPE =
            new CustomPacketPayload.Type<>(Proficiency.id("coop"));

    public static final StreamCodec<FriendlyByteBuf, CoopPayload> STREAM_CODEC = StreamCodec.of(
            (buf, payload) -> payload.batch().write(new SkillsWire.Out() {
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
            buf -> new CoopPayload(CoopBatch.read(new SkillsWire.In() {
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
