package dev.amman.proficiency.net;

import net.minecraft.network.FriendlyByteBuf;
import dev.amman.proficiency.net.codec.StreamCodec;

/**
 * "You have arrived somewhere new": the zone-name banner at the top of the screen, like entering a
 * region in WoW. Sent once, the moment a biome, dimension or structure type is first marked
 * visited, carrying the XP that the discovery actually paid after every multiplier.
 *
 * <p>{@code name} is a translation key. {@code id} is the raw registry id, so a client with no
 * name for a modded structure can still make one up from its path. {@code with} is the name of the
 * player who led when a convoy member is paid for their find, "" otherwise.
 *
 * <p>Wire format changed 2026-09-29 (a trailing string): server and client must be the same build.
 */
public record DiscoveryPayload(int kind, String name, String id, int skill, float xp,
        String with) {

    public static final int BIOME = 0;
    public static final int DIMENSION = 1;
    public static final int STRUCTURE = 2;
    /** A new kind of block, mob or item for one skill. Smaller and shorter than a place. */
    public static final int FIRST = 3;

    /** Stepping into a structure already found: a small banner, no XP. */
    public static final int ENTER = 4;

    private static final int MAX_LENGTH = 256;

    public static final String ID = "discovery";

    public static final StreamCodec<FriendlyByteBuf, DiscoveryPayload> STREAM_CODEC = StreamCodec.of(
            (buf, payload) -> {
                buf.writeVarInt(payload.kind());
                buf.writeUtf(payload.name(), MAX_LENGTH);
                buf.writeUtf(payload.id(), MAX_LENGTH);
                buf.writeVarInt(payload.skill());
                buf.writeFloat(payload.xp());
                buf.writeUtf(payload.with(), MAX_LENGTH);
            },
            buf -> new DiscoveryPayload(buf.readVarInt(), buf.readUtf(MAX_LENGTH),
                    buf.readUtf(MAX_LENGTH), buf.readVarInt(), buf.readFloat(),
                    buf.readUtf(MAX_LENGTH)));

}
