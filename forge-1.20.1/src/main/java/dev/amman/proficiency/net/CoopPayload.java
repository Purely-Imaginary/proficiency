package dev.amman.proficiency.net;

import dev.amman.proficiency.coop.CoopBatch;
import dev.amman.proficiency.skill.SkillsWire;
import net.minecraft.network.FriendlyByteBuf;
import dev.amman.proficiency.net.codec.StreamCodec;

/**
 * Co-op XP streams (1.45.0): one second of partners' XP and the pip row, laid out by {@link CoopBatch#write}.
 * Carried on its own optional channel ({@link CoopNetwork}), not the main one, so {@link ProficiencyNetwork#PROTOCOL}
 * does not move and an older client still joins.
 */
public record CoopPayload(CoopBatch batch) {

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
}
