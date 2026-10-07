package dev.amman.proficiency.mixin;

import dev.amman.proficiency.platform.bus.NeoForge;
import dev.amman.proficiency.platform.event.entity.ProjectileImpactEvent;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.entity.projectile.ProjectileDeflection;
import net.minecraft.world.phys.HitResult;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * NeoForge's ProjectileImpactEvent, fired where NeoForge fires it: at the start of
 * hitTargetOrDeflectSelf. A canceled event means no hit and no deflection; the projectile flies on.
 */
@Mixin(Projectile.class)
public abstract class ProjectileMixin {

    @Inject(method = "hitTargetOrDeflectSelf", at = @At("HEAD"), cancellable = true)
    private void proficiency$impact(HitResult hit, CallbackInfoReturnable<ProjectileDeflection> cir) {
        Projectile self = (Projectile) (Object) this;
        if (self.level().isClientSide()) {
            return;
        }
        if (NeoForge.EVENT_BUS.post(new ProjectileImpactEvent(self, hit)).isCanceled()) {
            cir.setReturnValue(ProjectileDeflection.NONE);
        }
    }
}
