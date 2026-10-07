package dev.amman.proficiency.mixin;

import dev.amman.proficiency.event.TalentRangedEvents;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.enchantment.ProtectionEnchantment;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Immovable (Blocking) against blasts. NeoForge 1.21 has {@code ExplosionKnockbackEvent}; Forge
 * 1.20.1 does not. Every explosion asks this static method how much of its push a living entity
 * keeps, so answering 0 there removes the push and nothing else (the damage still lands).
 */
@Mixin(ProtectionEnchantment.class)
public abstract class ProtectionEnchantmentMixin {

    @Inject(method = "getExplosionKnockbackAfterDampener", at = @At("HEAD"), cancellable = true, require = 1)
    private static void proficiency$immovable(LivingEntity entity, double knockback,
            CallbackInfoReturnable<Double> result) {
        if (TalentRangedEvents.immovableAgainstBlast(entity)) {
            result.setReturnValue(0.0);
        }
    }
}
