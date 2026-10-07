package dev.amman.proficiency;

import dev.amman.proficiency.skill.PlayerSkills;
import net.minecraft.world.entity.player.Player;
import net.neoforged.neoforge.attachment.AttachmentType;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.registries.NeoForgeRegistries;

import java.util.function.Supplier;

public final class ProficiencyAttachments {

    public static final DeferredRegister<AttachmentType<?>> TYPES =
            DeferredRegister.create(NeoForgeRegistries.Keys.ATTACHMENT_TYPES, Proficiency.MOD_ID);

    public static final Supplier<AttachmentType<PlayerSkills>> SKILLS = TYPES.register(
            "skills",
            () -> AttachmentType.builder(PlayerSkills::new)
                    .serialize(PlayerSkills.CODEC)
                    .build());

    private ProficiencyAttachments() {
    }

    public static PlayerSkills of(Player player) {
        return player.getData(SKILLS.get());
    }
}
