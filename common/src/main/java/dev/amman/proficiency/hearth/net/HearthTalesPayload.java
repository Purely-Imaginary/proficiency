package dev.amman.proficiency.hearth.net;

import dev.amman.proficiency.Proficiency;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

import java.util.ArrayList;
import java.util.List;

/**
 * A tale was told at the Hearth: the pay-in panel after a homecoming, the moment a Chronicle page is
 * set down, the moment a rated tale is retold (docs/HEARTH-SPEC.md, "Setting out and coming home").
 * One packet per moment, never a stream. Server: the tales lane. Client: {@code HearthClient.onTales}
 * (the default shows the panel in {@code hearth.tales.client.TalesPayInHud}).
 *
 * @param kind   {@link #PAID}, {@link #CHRONICLE} or {@link #RETOLD}
 * @param pool   the pool the charge went into (a {@code HearthPool} ordinal)
 * @param amount the charge added
 * @param blocks blocks walked on the trip (PAID; 0 when unknown)
 * @param stars  0..5 for RETOLD, -1 otherwise
 * @param places language keys of what the trip found, best first, at most {@link #MAX_PLACES}
 * @param title  the page's title (CHRONICLE, RETOLD), may be empty
 */
public record HearthTalesPayload(int kind, int pool, float amount, int blocks, int stars, List<String> places,
        String title) implements CustomPacketPayload {

    /** Tales paid into the Hearth on arrival. */
    public static final int PAID = 0;
    /** A Chronicle page was set down (the flat bonus). */
    public static final int CHRONICLE = 1;
    /** A page was rated by /mcjournal and retold at the Hearth. */
    public static final int RETOLD = 2;

    public static final int MAX_PLACES = 3;
    private static final int MAX_STRING = 128;

    public static final CustomPacketPayload.Type<HearthTalesPayload> TYPE =
            new CustomPacketPayload.Type<>(Proficiency.id("hearth_tales"));

    public static final StreamCodec<FriendlyByteBuf, HearthTalesPayload> STREAM_CODEC = StreamCodec.of(
            (buf, p) -> {
                buf.writeVarInt(p.kind());
                buf.writeVarInt(p.pool());
                buf.writeFloat(p.amount());
                buf.writeVarInt(p.blocks());
                buf.writeVarInt(p.stars() + 1);
                int count = Math.min(MAX_PLACES, p.places().size());
                buf.writeVarInt(count);
                for (int i = 0; i < count; i++) {
                    buf.writeUtf(p.places().get(i), MAX_STRING);
                }
                buf.writeUtf(p.title(), MAX_STRING);
            },
            buf -> {
                int kind = buf.readVarInt();
                int pool = buf.readVarInt();
                float amount = buf.readFloat();
                int blocks = Math.max(0, buf.readVarInt());
                int stars = buf.readVarInt() - 1;
                int count = Math.min(MAX_PLACES, Math.max(0, buf.readVarInt()));
                List<String> places = new ArrayList<>(count);
                for (int i = 0; i < count; i++) {
                    places.add(buf.readUtf(MAX_STRING));
                }
                return new HearthTalesPayload(kind, pool, amount, blocks, stars, List.copyOf(places),
                        buf.readUtf(MAX_STRING));
            });

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
