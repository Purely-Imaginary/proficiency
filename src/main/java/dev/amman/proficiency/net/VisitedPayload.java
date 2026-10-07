package dev.amman.proficiency.net;

import dev.amman.proficiency.Proficiency;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

import java.util.ArrayList;
import java.util.List;

/**
 * The player's visited keys for the Discovery journal ({@code biome:}, {@code dim:},
 * {@code structure:} and {@code first:<skill>:<kind>}). The first message after login has
 * {@code full} set and replaces the client's copy; later ones only add. It is not part of
 * {@link SyncSkillsPayload}, which is sent whole every half second and would carry this set each time.
 *
 * <p>A full message also lists every structure (already collapsed to one id per family) and every
 * dimension the server has. Structures are not in the registries the server syncs to a client, so
 * the journal could not count "x of N" without this. Non-full messages leave both lists empty.
 */
public record VisitedPayload(boolean full, List<String> keys, List<String> structures,
        List<String> dimensions) implements CustomPacketPayload {

    private static final int MAX_LENGTH = 256;
    /** Bounds a hostile or broken packet; one message never carries more than the sender's chunk. */
    private static final int MAX_LIST = 8192;

    public static final CustomPacketPayload.Type<VisitedPayload> TYPE =
            new CustomPacketPayload.Type<>(Proficiency.id("visited"));

    public static final StreamCodec<FriendlyByteBuf, VisitedPayload> STREAM_CODEC = StreamCodec.of(
            (buf, payload) -> {
                buf.writeBoolean(payload.full());
                write(buf, payload.keys());
                write(buf, payload.structures());
                write(buf, payload.dimensions());
            },
            buf -> new VisitedPayload(buf.readBoolean(), read(buf), read(buf), read(buf)));

    private static void write(FriendlyByteBuf buf, List<String> values) {
        buf.writeVarInt(values.size());
        for (String value : values) {
            buf.writeUtf(value, MAX_LENGTH);
        }
    }

    private static List<String> read(FriendlyByteBuf buf) {
        int size = Math.min(MAX_LIST, Math.max(0, buf.readVarInt()));
        List<String> values = new ArrayList<>(size);
        for (int i = 0; i < size; i++) {
            values.add(buf.readUtf(MAX_LENGTH));
        }
        return List.copyOf(values);
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
