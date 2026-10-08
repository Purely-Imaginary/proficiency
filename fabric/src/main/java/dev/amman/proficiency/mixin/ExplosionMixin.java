package dev.amman.proficiency.mixin;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.llamalad7.mixinextras.sugar.Local;
import dev.amman.proficiency.platform.bus.NeoForge;
import dev.amman.proficiency.platform.event.level.ExplosionKnockbackEvent;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Explosion;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;

/** NeoForge: EventHooks.getExplosionKnockback on the push vector, before it is applied. */
@Mixin(Explosion.class)
public abstract class ExplosionMixin {

    @Shadow @Final private Level level;

    @ModifyExpressionValue(method = "explode", at = @At(value = "NEW",
            target = "(DDD)Lnet/minecraft/world/phys/Vec3;", ordinal = 2))
    private Vec3 proficiency$knockback(Vec3 push, @Local Entity entity) {
        return NeoForge.EVENT_BUS.post(new ExplosionKnockbackEvent(level, (Explosion) (Object) this, entity, push))
                .getKnockbackVelocity();
    }
}
