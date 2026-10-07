package dev.amman.proficiency.mixin;

import dev.amman.proficiency.compat.DamageBridge;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** Player overrides actuallyHurt without calling super, so the Pre and Post hooks go here too. */
@Mixin(Player.class)
public abstract class PlayerDamageMixin {

    /** NeoForge's Pre: after armour and enchantments, before the absorption hearts. */
    // The third store into the amount: after onLivingHurt, after armour, after enchantments.
    // A STORE, not the absorption call: the amount is loaded before that call is made.
    @ModifyVariable(method = "actuallyHurt", at = @At(value = "STORE", ordinal = 2),
            argsOnly = true, require = 1)
    private float proficiency$damagePre(float amount, DamageSource source) {
        return DamageBridge.damagePre((Player) (Object) this, source, amount);
    }

    @Inject(method = "actuallyHurt", at = @At("RETURN"), require = 1)
    private void proficiency$damagePost(DamageSource source, float amount, CallbackInfo info) {
        DamageBridge.afterActuallyHurt((Player) (Object) this, source);
    }
}
