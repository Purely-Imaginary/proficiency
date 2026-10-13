package dev.amman.proficiency.hearth.net;

import net.minecraft.network.FriendlyByteBuf;
import dev.amman.proficiency.net.codec.StreamCodec;

/**
 * The left-side siege panel (docs/HEARTH-SPEC.md, "Warning"): a start time plus wave events, sent
 * only to the players a siege concerns, never every second. The client counts down from
 * {@code due} itself.
 *
 * <ul>
 * <li>{@link #COUNTDOWN}: "Siege in 18:20", {@code due} is the game time it is due, {@code warm} of
 * {@code group} home and warm.</li>
 * <li>{@link #WAVES}: "Wave 2/3, 7 left".</li>
 * <li>{@link #OUTCOME}: {@code outcome} is a {@code SiegeRules.Outcome} ordinal; shown 10 s, then gone.</li>
 * <li>{@link #CLEAR}: hide it now.</li>
 * </ul>
 * Server: Sieges. Client: {@code HearthClient.onSiegePanel}.
 */
public record SiegePanelPayload(int phase, long pos, String dimension, long due, int wave, int waves, int left,
        int outcome, int warm, int group) {

    public static final int CLEAR = 0;
    public static final int COUNTDOWN = 1;
    public static final int WAVES = 2;
    public static final int OUTCOME = 3;

    private static final int MAX_STRING = 256;

    /** The payload's name on the wire (its index in {@code ProficiencyNetwork} is what travels). */
    public static final String ID = "siege_panel";

    public static final StreamCodec<FriendlyByteBuf, SiegePanelPayload> STREAM_CODEC = StreamCodec.of(
            (buf, p) -> {
                buf.writeVarInt(p.phase());
                buf.writeLong(p.pos());
                buf.writeUtf(p.dimension(), MAX_STRING);
                buf.writeLong(p.due());
                buf.writeVarInt(p.wave());
                buf.writeVarInt(p.waves());
                buf.writeVarInt(p.left());
                buf.writeVarInt(p.outcome());
                buf.writeVarInt(p.warm());
                buf.writeVarInt(p.group());
            },
            buf -> new SiegePanelPayload(buf.readVarInt(), buf.readLong(), buf.readUtf(MAX_STRING), buf.readLong(),
                    buf.readVarInt(), buf.readVarInt(), buf.readVarInt(), buf.readVarInt(), buf.readVarInt(),
                    buf.readVarInt()));
}
