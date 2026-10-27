package dev.amman.proficiency.mixin;

import net.minecraft.world.level.block.entity.trialspawner.TrialSpawnerData;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

import java.util.Set;
import java.util.UUID;

/** The players a trial spawner has registered for the current trial (vanilla keeps the set protected). */
@Mixin(TrialSpawnerData.class)
public interface TrialSpawnerDataAccessor {

    @Accessor("detectedPlayers")
    Set<UUID> proficiency$detectedPlayers();
}
