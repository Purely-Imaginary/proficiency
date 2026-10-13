package dev.amman.proficiency.hearth.net;

import dev.amman.proficiency.hearth.HearthData;
import dev.amman.proficiency.hearth.HearthPool;
import dev.amman.proficiency.hearth.HearthRules;
import dev.amman.proficiency.hearth.HearthState;
import net.minecraft.network.FriendlyByteBuf;

import java.util.ArrayList;
import java.util.List;

/**
 * One pool as the panel and the tracker show it.
 *
 * @param pool        {@link HearthPool} ordinal
 * @param charge      the pool's charge
 * @param chargeTier  the tier the charge alone reaches
 * @param tier        the effective tier (what boons use)
 * @param progress    0..1 from this charge tier's threshold to the next
 * @param lock        {@link HearthRules.Lock} ordinal: what holds the pool below its charge tier
 * @param need        what the next tier needs from that lock ({@link HearthState#need}): the Comfort
 *                    ({@code COMFORT}), the stage to build ({@code CEILING}) or the Character the
 *                    next stage wants ({@code CHARACTER}, "Character 11/14")
 */
public record PoolRow(int pool, float charge, int chargeTier, int tier, float progress, int lock, int need) {

    public static final int MAX = 16;

    /** The rows of every pool with any charge, from a Hearth's state. */
    public static List<PoolRow> of(HearthState state, HearthData data) {
        List<PoolRow> rows = new ArrayList<>();
        for (HearthPool pool : HearthPool.VALUES) {
            double charge = state.charge(pool);
            int chargeTier = HearthRules.chargeTier(charge, data);
            rows.add(new PoolRow(pool.ordinal(), (float) charge, chargeTier, state.effectiveTier(pool, data),
                    (float) HearthRules.progress(charge, data), state.lock(pool, data).ordinal(),
                    state.need(pool, data)));
        }
        return rows;
    }

    public void write(FriendlyByteBuf buf) {
        buf.writeVarInt(pool);
        buf.writeFloat(charge);
        buf.writeVarInt(chargeTier);
        buf.writeVarInt(tier);
        buf.writeFloat(progress);
        buf.writeVarInt(lock);
        buf.writeVarInt(need);
    }

    public static PoolRow read(FriendlyByteBuf buf) {
        return new PoolRow(buf.readVarInt(), buf.readFloat(), buf.readVarInt(), buf.readVarInt(), buf.readFloat(),
                buf.readVarInt(), buf.readVarInt());
    }

    public static void writeList(FriendlyByteBuf buf, List<PoolRow> rows) {
        int count = Math.min(MAX, rows.size());
        buf.writeVarInt(count);
        for (int i = 0; i < count; i++) {
            rows.get(i).write(buf);
        }
    }

    public static List<PoolRow> readList(FriendlyByteBuf buf) {
        int count = Math.min(MAX, Math.max(0, buf.readVarInt()));
        List<PoolRow> rows = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            rows.add(read(buf));
        }
        return List.copyOf(rows);
    }
}
