package dev.amman.proficiency;

import com.mojang.serialization.Dynamic;
import dev.amman.proficiency.skill.PlayerSkills;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.Tag;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.common.capabilities.CapabilityManager;
import net.minecraftforge.common.capabilities.CapabilityToken;
import net.minecraftforge.common.capabilities.ICapabilitySerializable;
import net.minecraftforge.common.capabilities.RegisterCapabilitiesEvent;
import net.minecraftforge.common.util.LazyOptional;
import net.minecraftforge.event.AttachCapabilitiesEvent;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

/**
 * The player's skills, as a Forge capability. On NeoForge 1.21 this is a data attachment; Forge
 * 1.20.1 has none, so the same {@link PlayerSkills} object hangs off every player through a
 * capability, saved with the same {@link PlayerSkills#CODEC} under {@code ForgeCaps}.
 *
 * <p>Death works as on master: the attachment there is not copy-on-death, and
 * {@code PlayerLifecycleEvents.onClone} carries the skills across by hand. Forge invalidates the
 * dead player's capabilities before the clone event, so that handler revives them first.
 */
public final class ProficiencyAttachments {

    public static final Capability<PlayerSkills> SKILLS =
            CapabilityManager.get(new CapabilityToken<>() {
            });

    public static final net.minecraft.resources.ResourceLocation KEY = Proficiency.id("skills");

    private ProficiencyAttachments() {
    }

    public static PlayerSkills of(Player player) {
        // Present on every player from construction. Missing only on a player whose capabilities
        // were invalidated (a removed entity); a throwaway object keeps callers null-safe there.
        return player.getCapability(SKILLS).orElseGet(PlayerSkills::new);
    }

    public static void registerCapabilities(RegisterCapabilitiesEvent event) {
        event.register(PlayerSkills.class);
    }

    public static void attach(AttachCapabilitiesEvent<Entity> event) {
        if (event.getObject() instanceof Player) {
            event.addCapability(KEY, new Provider());
        }
    }

    private static final class Provider implements ICapabilitySerializable<Tag> {

        private final PlayerSkills skills = new PlayerSkills();
        private final LazyOptional<PlayerSkills> optional = LazyOptional.of(() -> skills);

        @Override
        public <T> @NotNull LazyOptional<T> getCapability(@NotNull Capability<T> cap, @Nullable Direction side) {
            return SKILLS.orEmpty(cap, optional);
        }

        @Override
        public Tag serializeNBT() {
            return PlayerSkills.CODEC.encodeStart(NbtOps.INSTANCE, skills)
                    .resultOrPartial(error -> Proficiency.LOG.error("Could not save skills: {}", error))
                    .orElseGet(CompoundTag::new);
        }

        @Override
        public void deserializeNBT(Tag tag) {
            PlayerSkills.CODEC.parse(new Dynamic<>(NbtOps.INSTANCE, tag))
                    .resultOrPartial(error -> Proficiency.LOG.error("Could not load skills: {}", error))
                    .ifPresent(skills::copyFrom);
        }
    }
}
