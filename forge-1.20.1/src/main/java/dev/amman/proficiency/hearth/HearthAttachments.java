package dev.amman.proficiency.hearth;

import com.mojang.serialization.Dynamic;
import dev.amman.proficiency.Proficiency;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;
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
 * A player's Hearth state ({@link PlayerHearth}). On 1.21.1 it is the loader attachment
 * {@code proficiency:hearth}; Forge 1.20.1 has no attachments, so it is a capability under the same
 * id, saved with the same {@link PlayerHearth#CODEC} into the player's {@code ForgeCaps}, the way
 * {@code ProficiencyAttachments} keeps the skills.
 *
 * <p>Not copied on death by Forge: {@code HearthCommonEvents.onClone} revives the old body's
 * capabilities (Forge invalidates them before the clone event), copies the data across and clears
 * what a death costs.
 */
public final class HearthAttachments {

    public static final Capability<PlayerHearth> HEARTH = CapabilityManager.get(new CapabilityToken<>() {
    });

    public static final ResourceLocation KEY = Proficiency.id("hearth");

    private HearthAttachments() {
    }

    /**
     * The player's Hearth data. Present on every player from construction; on a player whose
     * capabilities were invalidated (a removed entity) a throwaway object keeps callers null-safe,
     * and what is written to it is lost.
     */
    public static PlayerHearth of(Player player) {
        return player.getCapability(HEARTH).orElseGet(PlayerHearth::new);
    }

    public static void registerCapabilities(RegisterCapabilitiesEvent event) {
        event.register(PlayerHearth.class);
    }

    public static void attach(AttachCapabilitiesEvent<Entity> event) {
        if (event.getObject() instanceof Player) {
            event.addCapability(KEY, new Provider());
        }
    }

    private static final class Provider implements ICapabilitySerializable<Tag> {

        private final PlayerHearth data = new PlayerHearth();
        private final LazyOptional<PlayerHearth> optional = LazyOptional.of(() -> data);

        @Override
        public <T> @NotNull LazyOptional<T> getCapability(@NotNull Capability<T> cap, @Nullable Direction side) {
            return HEARTH.orEmpty(cap, optional);
        }

        @Override
        public Tag serializeNBT() {
            return PlayerHearth.CODEC.encodeStart(NbtOps.INSTANCE, data)
                    .resultOrPartial(error -> Proficiency.LOG.error("Could not save the Hearth data: {}", error))
                    .orElseGet(CompoundTag::new);
        }

        @Override
        public void deserializeNBT(Tag tag) {
            PlayerHearth.CODEC.parse(new Dynamic<>(NbtOps.INSTANCE, tag))
                    .resultOrPartial(error -> Proficiency.LOG.error("Could not load the Hearth data: {}", error))
                    .ifPresent(data::copyFrom);
        }
    }
}
