package dev.amman.proficiency.mixin;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import com.llamalad7.mixinextras.sugar.Local;
import com.llamalad7.mixinextras.sugar.Share;
import com.llamalad7.mixinextras.sugar.ref.LocalRef;
import dev.amman.proficiency.platform.bus.NeoForge;
import dev.amman.proficiency.platform.damage.DamageContainer;
import dev.amman.proficiency.platform.event.entity.player.PlayerEvent;
import dev.amman.proficiency.platform.event.tick.PlayerTickEvent;
import dev.amman.proficiency.platform.hooks.DamageHooks;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Player.class)
public abstract class PlayerMixin {

    @Unique
    private Player proficiency$self() {
        return (Player) (Object) this;
    }

    /** NeoForge: firePlayerTickPre / firePlayerTickPost, at the two ends of Player.tick, both sides. */
    @Inject(method = "tick", at = @At("HEAD"))
    private void proficiency$tickPre(CallbackInfo ci) {
        NeoForge.EVENT_BUS.post(new PlayerTickEvent.Pre(proficiency$self()));
    }

    @Inject(method = "tick", at = @At("TAIL"))
    private void proficiency$tickPost(CallbackInfo ci) {
        NeoForge.EVENT_BUS.post(new PlayerTickEvent.Post(proficiency$self()));
    }

    /** NeoForge: EventHooks.getBreakSpeed at the end of getDigSpeed; -1 when canceled. */
    @ModifyReturnValue(method = "getDestroySpeed", at = @At("RETURN"))
    private float proficiency$breakSpeed(float original, BlockState state) {
        var event = NeoForge.EVENT_BUS.post(new PlayerEvent.BreakSpeed(proficiency$self(), state, original, null));
        return event.isCanceled() ? -1 : event.getNewSpeed();
    }

    /** Player overrides actuallyHurt without calling super, so the Pre/Post pair is posted here too. */
    @ModifyExpressionValue(method = "actuallyHurt", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/world/entity/player/Player;getDamageAfterMagicAbsorb(Lnet/minecraft/world/damagesource/DamageSource;F)F"))
    private float proficiency$damagePre(float afterMagic, DamageSource source,
            @Share("proficiencyDamage") LocalRef<DamageContainer> shared) {
        DamageContainer container = DamageHooks.current(proficiency$self(), source, afterMagic);
        shared.set(container);
        return DamageHooks.pre(proficiency$self(), container, afterMagic);
    }

    @Inject(method = "actuallyHurt", at = @At("TAIL"))
    private void proficiency$damagePost(DamageSource source, float ignored, CallbackInfo ci,
            @Local(argsOnly = true) float toHealth,
            @Share("proficiencyDamage") LocalRef<DamageContainer> shared) {
        if (shared.get() != null) {
            DamageHooks.post(proficiency$self(), shared.get(), toHealth);
        }
    }
}
