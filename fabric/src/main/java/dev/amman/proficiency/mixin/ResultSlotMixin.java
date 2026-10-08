package dev.amman.proficiency.mixin;

import dev.amman.proficiency.platform.bus.NeoForge;
import dev.amman.proficiency.platform.event.entity.player.PlayerEvent;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.CraftingContainer;
import net.minecraft.world.inventory.ResultSlot;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** NeoForge: firePlayerCraftingEvent, right after onCraftedBy, only when something was crafted. */
@Mixin(ResultSlot.class)
public abstract class ResultSlotMixin {

    @Shadow @Final private Player player;
    @Shadow @Final private CraftingContainer craftSlots;

    @Inject(method = "checkTakeAchievements", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/world/item/ItemStack;onCraftedBy(Lnet/minecraft/world/level/Level;Lnet/minecraft/world/entity/player/Player;I)V",
            shift = At.Shift.AFTER))
    private void proficiency$crafted(ItemStack stack, CallbackInfo ci) {
        NeoForge.EVENT_BUS.post(new PlayerEvent.ItemCraftedEvent(player, stack, craftSlots));
    }
}
