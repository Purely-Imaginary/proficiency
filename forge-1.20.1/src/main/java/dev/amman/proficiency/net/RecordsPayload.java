package dev.amman.proficiency.net;

import dev.amman.proficiency.net.codec.StreamCodec;
import net.minecraft.network.FriendlyByteBuf;

import java.util.ArrayList;
import java.util.List;

/**
 * Personal records (1.37.0, no protocol bump): the bests of the skills that changed (all of them at login),
 * and optionally one "new best" toast. The skill sync packet is unchanged.
 *
 * @param toastSkill  skill ordinal of the toast, or -1 for none
 * @param toastMetric {@link dev.amman.proficiency.skill.RecordMetric} ordinal
 */
public record RecordsPayload(List<Row> rows, int toastSkill, int toastMetric, float toastValue) {

    /** One skill's four bests. */
    public record Row(int skill, float rate, int chain, float grant, int extra) {
    }

    private static final int MAX_ROWS = 64;

    public static final StreamCodec<FriendlyByteBuf, RecordsPayload> STREAM_CODEC = StreamCodec.of(
            (buf, payload) -> {
                buf.writeVarInt(payload.rows().size());
                for (Row row : payload.rows()) {
                    buf.writeVarInt(row.skill());
                    buf.writeFloat(row.rate());
                    buf.writeVarInt(row.chain());
                    buf.writeFloat(row.grant());
                    buf.writeVarInt(row.extra());
                }
                buf.writeVarInt(payload.toastSkill() + 1);
                buf.writeVarInt(payload.toastMetric());
                buf.writeFloat(payload.toastValue());
            },
            buf -> {
                int count = Math.min(buf.readVarInt(), MAX_ROWS);
                List<Row> rows = new ArrayList<>();
                for (int i = 0; i < count; i++) {
                    rows.add(new Row(buf.readVarInt(), buf.readFloat(), buf.readVarInt(), buf.readFloat(),
                            buf.readVarInt()));
                }
                return new RecordsPayload(rows, buf.readVarInt() - 1, buf.readVarInt(), buf.readFloat());
            });
}
