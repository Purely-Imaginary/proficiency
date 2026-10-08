package dev.amman.proficiency.net;

import dev.amman.proficiency.Proficiency;
import dev.amman.proficiency.skill.PlayerSkills;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/** Whole-state push. Sent on login, on respawn, and whenever anything changed in the last second. */
public record SyncSkillsPayload(PlayerSkills skills) implements CustomPacketPayload {

    public static final CustomPacketPayload.Type<SyncSkillsPayload> TYPE =
            new CustomPacketPayload.Type<>(Proficiency.id("sync_skills"));

    /**
     * Fabric has no channel version to negotiate, and the sync packet is the first one every
     * client gets, so the version rides on it. 3 is the HUD effects release (frenzies in the sync
     * packet, the death recap packet). An older peer sends a level where this reads the version, so
     * the mismatch is caught here with a message instead of as trailing bytes somewhere later.
     */
    public static final int PROTOCOL = 4;

    public static final StreamCodec<FriendlyByteBuf, SyncSkillsPayload> STREAM_CODEC = StreamCodec.of(
            (buf, payload) -> {
                buf.writeVarInt(PROTOCOL);
                PlayerSkills.STREAM_CODEC.encode(buf, payload.skills());
            },
            buf -> {
                int version = buf.readVarInt();
                if (version != PROTOCOL) {
                    throw new io.netty.handler.codec.DecoderException("Proficiency protocol mismatch: the other side"
                            + " speaks version " + version + ", this one " + PROTOCOL
                            + ". Update the server and the client together.");
                }
                return new SyncSkillsPayload(PlayerSkills.STREAM_CODEC.decode(buf));
            });

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
