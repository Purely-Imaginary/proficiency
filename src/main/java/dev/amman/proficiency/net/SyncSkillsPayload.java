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

    public static final StreamCodec<FriendlyByteBuf, SyncSkillsPayload> STREAM_CODEC =
            StreamCodec.composite(
                    PlayerSkills.STREAM_CODEC, SyncSkillsPayload::skills,
                    SyncSkillsPayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
