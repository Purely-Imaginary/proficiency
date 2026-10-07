package dev.amman.proficiency;

import dev.amman.proficiency.skill.PlayerSkills;
import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.minecraft.world.entity.player.Player;

/**
 * Skills live on the player as a Fabric data attachment, saved into the player's own NBT like the
 * NeoForge attachment was. Deliberately NOT copyOnDeath: the Clone handler carries them across by
 * hand, because that is where the death penalty is charged.
 */
@SuppressWarnings("UnstableApiUsage")
public final class ProficiencyAttachments {

    public static final AttachmentType<PlayerSkills> SKILLS = AttachmentRegistry.<PlayerSkills>builder()
            .initializer(PlayerSkills::new)
            .persistent(PlayerSkills.CODEC)
            .buildAndRegister(Proficiency.id("skills"));

    private ProficiencyAttachments() {
    }

    /** Forces class init, so the type is registered before any player loads. */
    public static void init() {
    }

    public static PlayerSkills of(Player player) {
        return player.getAttachedOrCreate(SKILLS);
    }
}
