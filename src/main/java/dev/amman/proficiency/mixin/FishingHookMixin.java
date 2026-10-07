package dev.amman.proficiency.mixin;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import com.llamalad7.mixinextras.sugar.Share;
import com.llamalad7.mixinextras.sugar.ref.LocalRef;
import dev.amman.proficiency.platform.bus.NeoForge;
import dev.amman.proficiency.platform.event.entity.player.ItemFishedEvent;
import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import net.minecraft.world.entity.projectile.FishingHook;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * NeoForge: ItemFishedEvent right after the loot roll. As on NeoForge the event holds a copy of the
 * loot; vanilla still spawns its own list. Canceled discards the hook and spawns nothing. The rod
 * damage returned is the event's.
 */
@Mixin(FishingHook.class)
public abstract class FishingHookMixin {

    @ModifyExpressionValue(method = "retrieve", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/world/level/storage/loot/LootTable;getRandomItems(Lnet/minecraft/world/level/storage/loot/LootParams;)Lit/unimi/dsi/fastutil/objects/ObjectArrayList;"))
    private ObjectArrayList<ItemStack> proficiency$fished(ObjectArrayList<ItemStack> loot,
            @Share("proficiencyFished") LocalRef<ItemFishedEvent> shared) {
        FishingHook self = (FishingHook) (Object) this;
        shared.set(NeoForge.EVENT_BUS.post(new ItemFishedEvent(loot, self.onGround() ? 2 : 1, self)));
        return loot;
    }

    @Inject(method = "retrieve", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/advancements/critereon/FishingRodHookedTrigger;trigger(Lnet/minecraft/server/level/ServerPlayer;Lnet/minecraft/world/item/ItemStack;Lnet/minecraft/world/entity/projectile/FishingHook;Ljava/util/Collection;)V",
            ordinal = 1), cancellable = true)
    private void proficiency$fishedCanceled(ItemStack rod, CallbackInfoReturnable<Integer> cir,
            @Share("proficiencyFished") LocalRef<ItemFishedEvent> shared) {
        ItemFishedEvent event = shared.get();
        if (event != null && event.isCanceled()) {
            ((FishingHook) (Object) this).discard();
            cir.setReturnValue(event.getRodDamage());
        }
    }

    @ModifyReturnValue(method = "retrieve", at = @At("RETURN"))
    private int proficiency$rodDamage(int damage, @Share("proficiencyFished") LocalRef<ItemFishedEvent> shared) {
        return shared.get() != null ? shared.get().getRodDamage() : damage;
    }
}
