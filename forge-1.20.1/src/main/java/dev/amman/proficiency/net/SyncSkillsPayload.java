package dev.amman.proficiency.net;

import dev.amman.proficiency.skill.PlayerSkills;
import dev.amman.proficiency.skill.SkillsWire;
import net.minecraft.network.FriendlyByteBuf;
import dev.amman.proficiency.net.codec.StreamCodec;

/** Whole-state push. Sent on login, on respawn, and whenever anything changed in the last second. */
public record SyncSkillsPayload(PlayerSkills skills) {

    public static final String ID = "sync_skills";

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

    public static final StreamCodec<FriendlyByteBuf, SyncSkillsPayload> STREAM_CODEC =
            StreamCodec.composite(SKILLS_CODEC, SyncSkillsPayload::skills, SyncSkillsPayload::new);

}
