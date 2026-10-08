package dev.amman.proficiency.mixin;

import dev.amman.proficiency.compat.DamageBridge;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;
import org.objectweb.asm.Opcodes;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * NeoForge's incoming-damage and post-damage hooks, which Forge 1.20.1 does not have. See
 * {@link DamageBridge}. Plain injections only (no overwrite, no redirect), so another mod's
 * injection into the same methods still applies.
 */
@Mixin(LivingEntity.class)
public abstract class LivingEntityDamageMixin {

    @Unique
    private float proficiency$incoming = Float.NaN;

    /** NeoForge's spot: the first statement after the invulnerable, dead and fire checks. */
    @Inject(method = "hurt", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/world/entity/LivingEntity;isSleeping()Z"), cancellable = true, require = 1)
    private void proficiency$incomingDamage(DamageSource source, float amount,
            CallbackInfoReturnable<Boolean> result) {
        float changed = DamageBridge.incoming((LivingEntity) (Object) this, source, amount);
        if (Float.isNaN(changed)) {
            result.setReturnValue(false);
            return;
        }
        proficiency$incoming = changed;
    }

    /** Hands the changed amount to the rest of hurt(): the shield, i-frames and actuallyHurt. */
    @ModifyVariable(method = "hurt", at = @At(value = "FIELD",
            target = "Lnet/minecraft/world/entity/LivingEntity;noActionTime:I", opcode = Opcodes.PUTFIELD),
            argsOnly = true, require = 1)
    private float proficiency$incomingAmount(float amount) {
        float changed = proficiency$incoming;
        proficiency$incoming = Float.NaN;
        return Float.isNaN(changed) ? amount : changed;
    }

    /** NeoForge's Pre: after armour and enchantments, before the absorption hearts. */
    // The third store into the amount: after onLivingHurt, after armour, after enchantments.
    // A STORE, not the absorption call: the amount is loaded before that call is made.
    @ModifyVariable(method = "actuallyHurt", at = @At(value = "STORE", ordinal = 2),
            argsOnly = true, require = 1)
    private float proficiency$damagePre(float amount, DamageSource source) {
        return DamageBridge.damagePre((LivingEntity) (Object) this, source, amount);
    }

    @Inject(method = "actuallyHurt", at = @At("RETURN"), require = 1)
    private void proficiency$damagePost(DamageSource source, float amount, CallbackInfo info) {
        DamageBridge.afterActuallyHurt((LivingEntity) (Object) this, source);
    }
}
