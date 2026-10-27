package dev.amman.proficiency.mixin;

import com.mojang.logging.LogUtils;
import dev.amman.proficiency.event.TrialEvents;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.entity.trialspawner.TrialSpawner;
import net.minecraft.world.level.block.entity.trialspawner.TrialSpawnerState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.HashSet;

/**
 * Trial Chambers: the moment a cleared spawner turns from waiting to ejecting its reward is the end
 * of the wave set. The players it has registered at that moment are the tracked ones (vanilla
 * removes them one by one as it ejects). Shared by NeoForge and Fabric; Forge 1.20.1 has no trials.
 */
@Mixin(TrialSpawnerState.class)
public abstract class TrialSpawnerStateMixin {

    @Inject(method = "tickAndGetNext", at = @At("RETURN"), require = 1)
    private void proficiency$waveCleared(BlockPos pos, TrialSpawner spawner, ServerLevel level,
            CallbackInfoReturnable<TrialSpawnerState> cir) {
        if ((Object) this != TrialSpawnerState.WAITING_FOR_REWARD_EJECTION
                || cir.getReturnValue() != TrialSpawnerState.EJECTING_REWARD) {
            return;
        }
        try {
            TrialEvents.onWaveCleared(level, pos, spawner.isOminous(), spawner.getRequiredPlayerRange(),
                    new HashSet<>(((TrialSpawnerDataAccessor) spawner.getData()).proficiency$detectedPlayers()));
        } catch (RuntimeException e) {
            LogUtils.getLogger().warn("Proficiency: trial wave bonus failed", e);
        }
    }
}
