package dev.amman.proficiency.hearth.net;

import net.minecraft.network.FriendlyByteBuf;
import dev.amman.proficiency.net.codec.StreamCodec;

/**
 * The player's own Hearth HUD: home or not, Warmth and the rate it is changing at (so the glyph
 * animates between packets; send on a change of state, not every second), Provisioned (strength and
 * the game times it fades between) and the banked Tales. Server: Effects (Warmth, Provisioned),
 * Tales (the scroll count). Client: {@code HearthClient.onHud}. The edge cues ride on the flags
 * ({@link #edge()}), decided by the server.
 */
public record HearthHudPayload(boolean home, long hearthPos, float warmth, float warmthPerSecond,
        float provisioned, long provisionedFrom, long provisionedUntil, float tales, int flags) {

    /** The streak's stacks are safe at the Hearth (the badge shows the hearth glyph). */
    public static final int FLAG_STREAK_SAFE = 1;
    /** The Hearth in range smoulders. */
    public static final int FLAG_SMOULDERING = 2;
    /** Temp HP from the Hearth is active. */
    public static final int FLAG_TEMP_HP = 4;
    /**
     * Edge, set on the one payload of the second the player came into range of a lit Hearth: "By the
     * Hearth" and the crackle. The server decides every edge (a login, or a Hearth lit again under
     * the player, is no walk in); the client plays the cue for the edge flag and never infers one
     * from {@code home} flipping.
     */
    public static final int FLAG_EDGE_HOME = 8;
    /** Edge: walked (or travelled) out of range below full Warmth: "Left the Hearth's warmth". */
    public static final int FLAG_EDGE_LEFT = 16;
    /**
     * Edge: walked out of a Hearth that still burns at full Warmth: "Set out from the Hearth", with
     * or without Provisioned (no Hearty tier, nothing to carry). A Hearth that breaks, smoulders or
     * is switched off under the player sends no edge at all.
     */
    public static final int FLAG_EDGE_SET_OUT = 32;
    /** Every edge flag; at most one is set. */
    public static final int EDGE_FLAGS = FLAG_EDGE_HOME | FLAG_EDGE_LEFT | FLAG_EDGE_SET_OUT;

    /** The payload's name on the wire (its index in {@code ProficiencyNetwork} is what travels). */
    public static final String ID = "hearth_hud";

    public static final StreamCodec<FriendlyByteBuf, HearthHudPayload> STREAM_CODEC = StreamCodec.of(
            (buf, p) -> {
                buf.writeBoolean(p.home());
                buf.writeLong(p.hearthPos());
                buf.writeFloat(p.warmth());
                buf.writeFloat(p.warmthPerSecond());
                buf.writeFloat(p.provisioned());
                buf.writeLong(p.provisionedFrom());
                buf.writeLong(p.provisionedUntil());
                buf.writeFloat(p.tales());
                buf.writeVarInt(p.flags());
            },
            buf -> new HearthHudPayload(buf.readBoolean(), buf.readLong(), buf.readFloat(), buf.readFloat(),
                    buf.readFloat(), buf.readLong(), buf.readLong(), buf.readFloat(), buf.readVarInt()));

    public boolean has(int flag) {
        return (flags & flag) != 0;
    }

    /** The edge this payload carries ({@link #FLAG_EDGE_HOME}, {@code LEFT} or {@code SET_OUT}), or 0. */
    public int edge() {
        return flags & EDGE_FLAGS;
    }
}
