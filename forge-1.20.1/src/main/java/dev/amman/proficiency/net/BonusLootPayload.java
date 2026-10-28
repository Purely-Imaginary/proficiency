package dev.amman.proficiency.net;

import net.minecraft.network.FriendlyByteBuf;
import dev.amman.proficiency.net.codec.StreamCodec;

import java.util.ArrayList;
import java.util.List;

/**
 * Bonus loot (no protocol bump): the extra items a skill's own luck just added (Motherlode and the double
 * drop, Second Catch, Double Hook, Forager and the other talent drops, a Mastery gift), so the HUD can fly each as
 * its item icon from where it appeared to the hotbar. One packet per tick at most, {@link #MAX} entries.
 */
public record BonusLootPayload(List<Entry> entries) {

    public static final int MAX = 8;

    public record Entry(String item, int count, float x, float y, float z) {
    }

    public static final StreamCodec<FriendlyByteBuf, BonusLootPayload> STREAM_CODEC = StreamCodec.of(
            (buf, payload) -> {
                int size = Math.min(MAX, payload.entries().size());
                buf.writeVarInt(size);
                for (int i = 0; i < size; i++) {
                    Entry e = payload.entries().get(i);
                    buf.writeUtf(e.item(), 128);
                    buf.writeVarInt(e.count());
                    buf.writeFloat(e.x());
                    buf.writeFloat(e.y());
                    buf.writeFloat(e.z());
                }
            },
            buf -> {
                int size = Math.min(MAX, Math.max(0, buf.readVarInt()));
                List<Entry> entries = new ArrayList<>(size);
                for (int i = 0; i < size; i++) {
                    entries.add(new Entry(buf.readUtf(128), buf.readVarInt(), buf.readFloat(), buf.readFloat(),
                            buf.readFloat()));
                }
                return new BonusLootPayload(List.copyOf(entries));
            });
}
