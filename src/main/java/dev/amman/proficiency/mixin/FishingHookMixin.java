package dev.amman.proficiency.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.llamalad7.mixinextras.sugar.Local;
import net.minecraft.world.entity.projectile.FishingHook;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.event.entity.player.ItemFishedEvent;
import net.minecraftforge.eventbus.api.Event;
import net.minecraftforge.eventbus.api.IEventBus;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

import java.util.List;

/**
 * Makes {@link ItemFishedEvent#getDrops()} the catch, as it is on NeoForge 1.21. Forge 1.20.1's
 * event copies the loot into its own list and {@code FishingHook.retrieve} then spawns its
 * original list, so Fishing's talents (Double Hook, Fresh Catch, Sunken Treasure, the passive's
 * second fish and the proc's treasure) changed nothing. After the event, the hook's list is
 * replaced by the event's.
 */
@Mixin(FishingHook.class)
public abstract class FishingHookMixin {

    @WrapOperation(method = "retrieve", at = @At(value = "INVOKE",
            target = "Lnet/minecraftforge/eventbus/api/IEventBus;post(Lnet/minecraftforge/eventbus/api/Event;)Z"),
            require = 1)
    private boolean proficiency$catchFromEvent(IEventBus bus, Event event, Operation<Boolean> original,
            @Local List<ItemStack> loot) {
        boolean cancelled = original.call(bus, event);
        if (!cancelled && event instanceof ItemFishedEvent fished && loot != null) {
            List<ItemStack> drops = List.copyOf(fished.getDrops());
            loot.clear();
            loot.addAll(drops);
        }
        return cancelled;
    }
}
