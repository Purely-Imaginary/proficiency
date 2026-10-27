package dev.amman.proficiency.mixin;

import com.mojang.logging.LogUtils;
import dev.amman.proficiency.event.TrialEvents;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.entity.trialspawner.TrialSpawner;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.Optional;
import java.util.UUID;

/**
 * Trial Chambers: tags each mob a trial spawner makes. Vanilla runs the finalize-spawn event only
 * for a bare entity id, so the usual spawner tag would miss most trial mobs. Shared by NeoForge and
 * Fabric; Forge 1.20.1 has no trials.
 */
@Mixin(TrialSpawner.class)
public abstract class TrialSpawnerMixin {

    @Inject(method = "spawnMob", at = @At("RETURN"), require = 1)
    private void proficiency$tag(ServerLevel level, BlockPos pos, CallbackInfoReturnable<Optional<UUID>> cir) {
        Optional<UUID> made = cir.getReturnValue();
        if (made == null || made.isEmpty()) {
            return;
        }
        try {
            TrialEvents.onSpawn(level, pos, ((TrialSpawner) (Object) this).isOminous(), made.get());
        } catch (RuntimeException e) {
            LogUtils.getLogger().warn("Proficiency: trial mob tag failed", e);
        }
    }
}
