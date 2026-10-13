package dev.amman.proficiency.mixin;

import dev.amman.proficiency.event.ExhaustionWatch;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.food.FoodData;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * The exhaustion watcher around vanilla's food tick ({@link ExhaustionWatch}: Hearty, Night Owl and
 * Lean Times). At the head, the climb since the last look is scaled before vanilla checks whether
 * exhaustion passed 4.0 and takes a food point, so the climb that would cost the point is scaled too
 * (a watcher after the tick alone sees the roll-over and cannot give the point back). At the tail, what
 * the tick itself added (natural regeneration) is scaled; the head recorded the value the tick leaves
 * after its roll-over, so that look sees only the addition. Vanilla's tick stays untouched. Shared by
 * NeoForge and Fabric (both list it in {@code proficiency.mixins.json}).
 */
@Mixin(FoodData.class)
public abstract class HearthFoodDataMixin {

    @Inject(method = "tick", at = @At("HEAD"), require = 1)
    private void proficiency$hearty(Player player, CallbackInfo ci) {
        if (player instanceof ServerPlayer server) {
            ExhaustionWatch.beforeFoodTick(server, (FoodData) (Object) this);
        }
    }

    @Inject(method = "tick", at = @At("TAIL"), require = 1)
    private void proficiency$regeneration(Player player, CallbackInfo ci) {
        if (player instanceof ServerPlayer server) {
            ExhaustionWatch.afterFoodTick(server, (FoodData) (Object) this);
        }
    }
}
