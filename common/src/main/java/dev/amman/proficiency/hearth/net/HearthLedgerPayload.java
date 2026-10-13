package dev.amman.proficiency.hearth.net;

import dev.amman.proficiency.Proficiency;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * The panel's "Hearth-keepers" tab, both windows at once so the week/lifetime toggle and the
 * per-pool ranking need no second packet. Sent once when the panel opens or the tab asks
 * ({@link HearthRequestPayload#LEDGER}); never on a timer. Server: Charge. Client:
 * {@code HearthClient.onLedger}.
 */
public record HearthLedgerPayload(long pos, List<Row> rows) implements CustomPacketPayload {

    /** A source and its total, for "mostly Mining, Masonry, Tales". */
    public record Source(String source, float amount) {
    }

    /**
     * One player: charge per pool (indexed by {@code HearthPool} ordinal) this week and lifetime,
     * their top sources in each window, and their siege counts.
     */
    public record Row(UUID player, String name, List<Float> week, List<Float> lifetime, List<Source> topWeek,
            List<Source> topLifetime, int siegeKills, int siegesDefended) {
    }

    public static final int MAX_ROWS = 64;
    public static final int MAX_POOLS = 16;
    public static final int MAX_SOURCES = 8;
    private static final int MAX_STRING = 64;

    public static final CustomPacketPayload.Type<HearthLedgerPayload> TYPE =
            new CustomPacketPayload.Type<>(Proficiency.id("hearth_ledger"));

    private static void writeFloats(FriendlyByteBuf buf, List<Float> values) {
        int count = Math.min(MAX_POOLS, values.size());
        buf.writeVarInt(count);
        for (int i = 0; i < count; i++) {
            buf.writeFloat(values.get(i));
        }
    }

    private static List<Float> readFloats(FriendlyByteBuf buf) {
        int count = Math.min(MAX_POOLS, Math.max(0, buf.readVarInt()));
        List<Float> out = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            out.add(buf.readFloat());
        }
        return List.copyOf(out);
    }

    private static void writeSources(FriendlyByteBuf buf, List<Source> sources) {
        int count = Math.min(MAX_SOURCES, sources.size());
        buf.writeVarInt(count);
        for (int i = 0; i < count; i++) {
            buf.writeUtf(sources.get(i).source(), MAX_STRING);
            buf.writeFloat(sources.get(i).amount());
        }
    }

    private static List<Source> readSources(FriendlyByteBuf buf) {
        int count = Math.min(MAX_SOURCES, Math.max(0, buf.readVarInt()));
        List<Source> out = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            out.add(new Source(buf.readUtf(MAX_STRING), buf.readFloat()));
        }
        return List.copyOf(out);
    }

    public static final StreamCodec<FriendlyByteBuf, HearthLedgerPayload> STREAM_CODEC = StreamCodec.of(
            (buf, p) -> {
                buf.writeLong(p.pos());
                int count = Math.min(MAX_ROWS, p.rows().size());
                buf.writeVarInt(count);
                for (int i = 0; i < count; i++) {
                    Row row = p.rows().get(i);
                    buf.writeUUID(row.player());
                    buf.writeUtf(row.name(), MAX_STRING);
                    writeFloats(buf, row.week());
                    writeFloats(buf, row.lifetime());
                    writeSources(buf, row.topWeek());
                    writeSources(buf, row.topLifetime());
                    buf.writeVarInt(row.siegeKills());
                    buf.writeVarInt(row.siegesDefended());
                }
            },
            buf -> {
                long pos = buf.readLong();
                int count = Math.min(MAX_ROWS, Math.max(0, buf.readVarInt()));
                List<Row> rows = new ArrayList<>(count);
                for (int i = 0; i < count; i++) {
                    rows.add(new Row(buf.readUUID(), buf.readUtf(MAX_STRING), readFloats(buf), readFloats(buf),
                            readSources(buf), readSources(buf), buf.readVarInt(), buf.readVarInt()));
                }
                return new HearthLedgerPayload(pos, List.copyOf(rows));
            });

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
