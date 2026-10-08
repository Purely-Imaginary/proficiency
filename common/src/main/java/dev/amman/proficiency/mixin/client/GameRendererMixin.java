package dev.amman.proficiency.mixin.client;

import dev.amman.proficiency.client.DarkSight;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Dark Sight (Nightwalker's passive), part 2. Everything that asks how strong Night Vision is
 * asks here: the vanilla light map, and shader packs through Iris (its {@code nightVision}
 * uniform), which do their own lighting and ignore the light map. Without the real effect, the
 * answer is Dark Sight's scale; with it, the real one.
 */
@Mixin(GameRenderer.class)
public abstract class GameRendererMixin {

    @Inject(method = "getNightVisionScale", at = @At("HEAD"), cancellable = true)
    private static void proficiency$darkSight(LivingEntity entity, float partialTick,
            CallbackInfoReturnable<Float> result) {
        float dark = DarkSight.scaleFor(entity);
        if (dark > 0f && !entity.hasEffect(MobEffects.NIGHT_VISION)) {
            result.setReturnValue(dark);
        }
    }
}
