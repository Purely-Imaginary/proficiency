package dev.amman.proficiency.mixin;

import dev.amman.proficiency.platform.bus.NeoForge;
import dev.amman.proficiency.platform.event.entity.player.PlayerEvent;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.FurnaceResultSlot;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** NeoForge: firePlayerSmeltedEvent, at the end of checkTakeAchievements. */
@Mixin(FurnaceResultSlot.class)
public abstract class FurnaceResultSlotMixin {

    @Shadow @Final private Player player;

    @Inject(method = "checkTakeAchievements", at = @At("TAIL"))
    private void proficiency$smelted(ItemStack stack, CallbackInfo ci) {
        NeoForge.EVENT_BUS.post(new PlayerEvent.ItemSmeltedEvent(player, stack));
    }
}
