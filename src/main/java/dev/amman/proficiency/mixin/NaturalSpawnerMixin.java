package dev.amman.proficiency.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import dev.amman.proficiency.platform.bus.NeoForge;
import dev.amman.proficiency.platform.event.entity.living.MobSpawnEvent;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.NaturalSpawner;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/**
 * NeoForge: MobSpawnEvent.PositionCheck, asked where a natural spawn checks its spot. Only FAIL is
 * honoured; SUCCEED and DEFAULT leave the vanilla answer alone.
 */
@Mixin(NaturalSpawner.class)
public abstract class NaturalSpawnerMixin {

    @WrapOperation(method = "isValidPositionForMob",
            at = @At(value = "INVOKE",
                    target = "Lnet/minecraft/world/entity/Mob;checkSpawnRules(Lnet/minecraft/world/level/LevelAccessor;Lnet/minecraft/world/entity/MobSpawnType;)Z"))
    private static boolean proficiency$positionCheck(Mob mob, LevelAccessor level, MobSpawnType spawnType,
            Operation<Boolean> original) {
        boolean vanilla = original.call(mob, level, spawnType);
        if (!vanilla || !(level instanceof ServerLevel server)) {
            return vanilla;
        }
        var event = NeoForge.EVENT_BUS.post(new MobSpawnEvent.PositionCheck(mob, server, spawnType));
        return event.getResult() != MobSpawnEvent.PositionCheck.Result.FAIL;
    }
}
