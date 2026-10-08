package dev.amman.proficiency.mixin;

import dev.amman.proficiency.platform.bus.NeoForge;
import dev.amman.proficiency.platform.event.entity.living.FinalizeSpawnEvent;
import dev.amman.proficiency.platform.event.entity.living.LivingChangeTargetEvent;
import net.minecraft.world.DifficultyInstance;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.SpawnGroupData;
import net.minecraft.world.level.ServerLevelAccessor;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** NeoForge: LivingChangeTargetEvent (Mob.setTarget) and FinalizeSpawnEvent (Mob.finalizeSpawn). */
@Mixin(Mob.class)
public abstract class MobMixin {

    @Inject(method = "setTarget", at = @At("HEAD"), cancellable = true)
    private void proficiency$changeTarget(@Nullable LivingEntity target, CallbackInfo ci) {
        Mob self = (Mob) (Object) this;
        if (self.level().isClientSide()) {
            return;
        }
        if (NeoForge.EVENT_BUS.post(new LivingChangeTargetEvent(self, target)).isCanceled()) {
            ci.cancel();
        }
    }

    @Inject(method = "finalizeSpawn", at = @At("HEAD"))
    private void proficiency$finalizeSpawn(ServerLevelAccessor level, DifficultyInstance difficulty,
            MobSpawnType spawnType, @Nullable SpawnGroupData spawnData,
            CallbackInfoReturnable<SpawnGroupData> cir) {
        NeoForge.EVENT_BUS.post(new FinalizeSpawnEvent((Mob) (Object) this, level, spawnType));
    }
}
