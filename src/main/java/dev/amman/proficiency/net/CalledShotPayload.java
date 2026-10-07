package dev.amman.proficiency.net;

import net.minecraft.network.FriendlyByteBuf;
import dev.amman.proficiency.net.codec.StreamCodec;

/**
 * Tactician's Called Shot, for the crosshair icon over a marked mob's head. Sent only to the
 * marker and to players within 48 blocks of the mob. {@code ticks} is how long the mark has left;
 * 0 means it ended (the mob died, the time ran out). The glow outline can fail under shader packs,
 * so this icon is the part that always shows.
 */
public record CalledShotPayload(int entityId, int ticks, String marker) {

    private static final int MAX_NAME = 64;

    public static final String ID = "called_shot";

    public static final StreamCodec<FriendlyByteBuf, CalledShotPayload> STREAM_CODEC = StreamCodec.of(
            (buf, payload) -> {
                buf.writeVarInt(payload.entityId());
                buf.writeVarInt(Math.max(0, payload.ticks()));
                String name = payload.marker() == null ? "" : payload.marker();
                buf.writeUtf(name.length() > MAX_NAME ? name.substring(0, MAX_NAME) : name, MAX_NAME);
            },
            buf -> new CalledShotPayload(buf.readVarInt(), Math.max(0, buf.readVarInt()), buf.readUtf(MAX_NAME)));

}
