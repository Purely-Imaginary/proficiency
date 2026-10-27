package dev.amman.proficiency.mixin;

import com.mojang.logging.LogUtils;
import dev.amman.proficiency.event.CommerceEvents;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.monster.piglin.Piglin;
import net.minecraft.world.entity.monster.piglin.PiglinAi;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.ArrayList;
import java.util.List;

/**
 * Commerce: piglin bartering. {@code getBarterResponseItems} runs exactly once per finished barter
 * (the piglin's loot roll), so it is where the XP is paid and where Gold Rush adds its second roll.
 * An ingot a player threw is credited to the thrower when the piglin picks it up.
 * The Forge 1.20.1 copy of common's mixin (listed in {@code proficiency.mixins.json}).
 */
@Mixin(PiglinAi.class)
public abstract class PiglinAiMixin {

    @Inject(method = "getBarterResponseItems", at = @At("RETURN"), cancellable = true, require = 1)
    private static void proficiency$barter(Piglin piglin, CallbackInfoReturnable<List<ItemStack>> cir) {
        try {
            List<ItemStack> extra = CommerceEvents.barterDone(piglin, cir.getReturnValue());
            if (!extra.isEmpty()) {
                List<ItemStack> all = new ArrayList<>(cir.getReturnValue());
                all.addAll(extra);
                cir.setReturnValue(all);
            }
        } catch (RuntimeException e) {
            LogUtils.getLogger().warn("Proficiency: barter XP failed", e);
        }
    }

    @Inject(method = "pickUpItem", at = @At("HEAD"), require = 1)
    private static void proficiency$pickUp(Piglin piglin, ItemEntity item, CallbackInfo ci) {
        try {
            CommerceEvents.thrownIngot(piglin, item);
        } catch (RuntimeException e) {
            LogUtils.getLogger().warn("Proficiency: barter credit failed", e);
        }
    }
}
