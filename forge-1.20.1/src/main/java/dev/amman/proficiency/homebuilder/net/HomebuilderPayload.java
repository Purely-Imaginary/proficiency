package dev.amman.proficiency.homebuilder.net;

import net.minecraft.network.FriendlyByteBuf;
import dev.amman.proficiency.net.codec.StreamCodec;

/**
 * Server to client, Homebuilder's own news (docs/HEARTH-CONTRACT.md, "Payloads"). One small record
 * for every kind, sent only when something changed, never on a timer:
 *
 * <ul>
 * <li>{@link #MAGNET}: {@code value} 1 when the magnet switch is off, 0 when on (login and toggle).</li>
 * <li>{@link #CHANNEL}: Homeward started at game time {@code time}, lasting {@code value} ticks.</li>
 * <li>{@link #CHANNEL_END}: it ended; {@code value} is {@link #END_ARRIVED}, {@link #END_MOVED} or
 * {@link #END_HURT}.</li>
 * <li>{@link #SHARED}: a friend's House Proud reaches you until game time {@code time} (0: it ended);
 * {@code name} is the friend.</li>
 * <li>{@link #HOMEWARD_DAY}: the in-game day Homeward was last used ({@code time}, Long.MIN_VALUE for never).</li>
 * <li>{@link #HOMEWARD_AT}: the game time it was last used ({@code time}, Long.MIN_VALUE for unknown), for
 * worlds whose day never turns.</li>
 * </ul>
 */
public record HomebuilderPayload(int kind, long time, int value, String name) {

    public static final int MAGNET = 0;
    public static final int CHANNEL = 1;
    public static final int CHANNEL_END = 2;
    public static final int SHARED = 3;
    public static final int HOMEWARD_DAY = 4;
    public static final int HOMEWARD_AT = 5;

    public static final int END_ARRIVED = 0;
    public static final int END_MOVED = 1;
    public static final int END_HURT = 2;

    private static final int MAX_NAME = 64;

    /** The payload's name on the wire (its index in {@code ProficiencyNetwork} is what travels). */
    public static final String ID = "homebuilder";

    public static final StreamCodec<FriendlyByteBuf, HomebuilderPayload> STREAM_CODEC = StreamCodec.of(
            (buf, p) -> {
                buf.writeVarInt(p.kind());
                buf.writeLong(p.time());
                buf.writeVarInt(p.value());
                buf.writeUtf(p.name() == null ? "" : p.name(), MAX_NAME);
            },
            buf -> new HomebuilderPayload(buf.readVarInt(), buf.readLong(), buf.readVarInt(), buf.readUtf(MAX_NAME)));
}
