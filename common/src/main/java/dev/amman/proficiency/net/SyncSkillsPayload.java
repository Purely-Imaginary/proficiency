package dev.amman.proficiency.net;

import dev.amman.proficiency.Proficiency;
import dev.amman.proficiency.skill.PlayerSkills;
import dev.amman.proficiency.skill.SkillsWire;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;

/** Whole-state push. Sent on login, on respawn, and whenever anything changed in the last second. */
public record SyncSkillsPayload(PlayerSkills skills) implements CustomPacketPayload {

    public static final CustomPacketPayload.Type<SyncSkillsPayload> TYPE =
            new CustomPacketPayload.Type<>(Proficiency.id("sync_skills"));

    /** A player's skills on the wire, laid out by {@link PlayerSkills#write}. */
    public static final StreamCodec<FriendlyByteBuf, PlayerSkills> SKILLS_CODEC = StreamCodec.of(
            (buf, skills) -> skills.write(new SkillsWire.Out() {
                @Override
                public void writeVarInt(int value) {
                    buf.writeVarInt(value);
                }

                @Override
                public void writeFloat(float value) {
                    buf.writeFloat(value);
                }

                @Override
                public void writeUtf(String value) {
                    buf.writeUtf(value);
                }

                @Override
                public void writeVarLong(long value) {
                    buf.writeVarLong(value);
                }
            }),
            buf -> PlayerSkills.read(new SkillsWire.In() {
                @Override
                public int readVarInt() {
                    return buf.readVarInt();
                }

                @Override
                public float readFloat() {
                    return buf.readFloat();
                }

                @Override
                public String readUtf() {
                    return buf.readUtf();
                }

                @Override
                public long readVarLong() {
                    return buf.readVarLong();
                }
            }));

    /** NeoForge's: the protocol version is negotiated as the channel version, so none rides here. */
    public static final StreamCodec<FriendlyByteBuf, SyncSkillsPayload> STREAM_CODEC =
            StreamCodec.composite(SKILLS_CODEC, SyncSkillsPayload::skills, SyncSkillsPayload::new);

    /**
     * Fabric's. Fabric has no channel version to negotiate, and the sync packet is the first one
     * every client gets, so the version rides on it. An older peer sends a level where this reads
     * the version, so the mismatch is caught here with a message instead of as trailing bytes
     * somewhere later.
     */
    public static final StreamCodec<FriendlyByteBuf, SyncSkillsPayload> VERSIONED_CODEC = StreamCodec.of(
            (buf, payload) -> {
                buf.writeVarInt(ProficiencyNetwork.PROTOCOL);
                SKILLS_CODEC.encode(buf, payload.skills());
            },
            buf -> {
                int version = buf.readVarInt();
                if (version != ProficiencyNetwork.PROTOCOL) {
                    throw new io.netty.handler.codec.DecoderException("Proficiency protocol mismatch: the other side"
                            + " speaks version " + version + ", this one " + ProficiencyNetwork.PROTOCOL
                            + ". Update the server and the client together.");
                }
                return new SyncSkillsPayload(SKILLS_CODEC.decode(buf));
            });

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
