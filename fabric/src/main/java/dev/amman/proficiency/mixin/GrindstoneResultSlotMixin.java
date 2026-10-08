package dev.amman.proficiency.mixin;

import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import dev.amman.proficiency.platform.bus.NeoForge;
import dev.amman.proficiency.platform.event.GrindstoneEvent;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.GrindstoneMenu;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * The grindstone's result slot (an anonymous class). NeoForge posts GrindstoneEvent.OnTakeItem with
 * the XP about to drop; here the XP method's result is the event's, read while the inputs are
 * still in place. Cancellation is not supported (no listener cancels it).
 */
@Mixin(targets = "net.minecraft.world.inventory.GrindstoneMenu$4")
public abstract class GrindstoneResultSlotMixin {

    @Unique private AbstractContainerMenu proficiency$menu;

    @Inject(method = "onTake", at = @At("HEAD"))
    private void proficiency$remember(Player player, ItemStack stack, CallbackInfo ci) {
        proficiency$menu = player.containerMenu;
    }

    @Inject(method = "onTake", at = @At("TAIL"))
    private void proficiency$forget(Player player, ItemStack stack, CallbackInfo ci) {
        proficiency$menu = null;
    }

    @ModifyReturnValue(method = "getExperienceAmount", at = @At("RETURN"))
    private int proficiency$grindstoneTake(int xp) {
        if (!(proficiency$menu instanceof GrindstoneMenu menu)) {
            return xp;
        }
        var event = NeoForge.EVENT_BUS.post(new GrindstoneEvent.OnTakeItem(
                menu.getSlot(0).getItem(), menu.getSlot(1).getItem(), xp));
        return event.getXp();
    }
}
