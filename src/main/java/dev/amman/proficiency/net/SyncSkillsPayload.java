package dev.amman.proficiency.net;

import dev.amman.proficiency.skill.PlayerSkills;
import net.minecraft.network.FriendlyByteBuf;
import dev.amman.proficiency.net.codec.StreamCodec;

/** Whole-state push. Sent on login, on respawn, and whenever anything changed in the last second. */
public record SyncSkillsPayload(PlayerSkills skills) {

    public static final String ID = "sync_skills";

    public static final StreamCodec<FriendlyByteBuf, SyncSkillsPayload> STREAM_CODEC =
            StreamCodec.composite(
                    PlayerSkills.STREAM_CODEC, SyncSkillsPayload::skills,
                    SyncSkillsPayload::new);

}
