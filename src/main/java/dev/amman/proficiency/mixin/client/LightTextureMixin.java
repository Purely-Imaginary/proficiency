package dev.amman.proficiency.mixin.client;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import dev.amman.proficiency.client.DarkSight;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.core.Holder;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffects;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/**
 * Dark Sight (Nightwalker's passive), part 1. The light map only asks for the Night Vision scale
 * when the player has Night Vision. This makes it ask whenever Dark Sight has something to add;
 * {@link GameRendererMixin} then answers with Dark Sight's scale.
 *
 * <p>WrapOperation, not Redirect, so another mod wrapping the same call still works. The mixin
 * config is not required: if this ever fails to apply, the game runs and the passive is dark.
 */
@Mixin(LightTexture.class)
public abstract class LightTextureMixin {

    @WrapOperation(method = "updateLightTexture", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/player/LocalPlayer;hasEffect(Lnet/minecraft/core/Holder;)Z"))
    private boolean proficiency$darkSightCounts(LocalPlayer player, Holder<MobEffect> effect,
            Operation<Boolean> original) {
        boolean real = original.call(player, effect);
        return real || (effect == MobEffects.NIGHT_VISION && DarkSight.scale() > 0f);
    }
}
