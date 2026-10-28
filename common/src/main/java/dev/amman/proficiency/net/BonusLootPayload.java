package dev.amman.proficiency.net;

import dev.amman.proficiency.Proficiency;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

import java.util.ArrayList;
import java.util.List;

/**
 * Bonus loot (optional, no protocol bump): the extra items a skill's own luck just added (Motherlode and the double drop,
 * Second Catch, Double Hook, Forager and the other talent drops, a Mastery gift), so the HUD can fly each as its
 * item icon from where it appeared to the hotbar. One packet per tick at most, {@link #MAX} entries; the item
 * stays on the ground or in the bag as it always did, this only says where it came from.
 */
public record BonusLootPayload(List<Entry> entries) implements CustomPacketPayload {

    /** Most entries in one packet; the client flies at most this many at once as well. */
    public static final int MAX = 8;

    /** One bonus: the item's registry id, how many, and the world point it appeared at. */
    public record Entry(String item, int count, float x, float y, float z) {
    }

    public static final CustomPacketPayload.Type<BonusLootPayload> TYPE =
            new CustomPacketPayload.Type<>(Proficiency.id("bonus_loot"));

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

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
