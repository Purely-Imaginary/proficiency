package dev.amman.proficiency.mixin;

import dev.amman.proficiency.compat.EffectSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Records an effect's source for the Applicable event. See {@link EffectSource}. */
@Mixin(LivingEntity.class)
public abstract class EffectSourceMixin {

    @Inject(method = "addEffect(Lnet/minecraft/world/effect/MobEffectInstance;Lnet/minecraft/world/entity/Entity;)Z",
            at = @At("HEAD"), require = 1)
    private void proficiency$pushSource(MobEffectInstance effect, Entity source,
            CallbackInfoReturnable<Boolean> result) {
        EffectSource.push(source);
    }

    @Inject(method = "addEffect(Lnet/minecraft/world/effect/MobEffectInstance;Lnet/minecraft/world/entity/Entity;)Z",
            at = @At("RETURN"), require = 1)
    private void proficiency$popSource(MobEffectInstance effect, Entity source,
            CallbackInfoReturnable<Boolean> result) {
        EffectSource.pop();
    }
}
