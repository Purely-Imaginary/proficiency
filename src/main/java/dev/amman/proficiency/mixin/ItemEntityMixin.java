package dev.amman.proficiency.mixin;

import dev.amman.proficiency.platform.bus.NeoForge;
import dev.amman.proficiency.platform.event.entity.player.ItemEntityPickupEvent;
import net.fabricmc.fabric.api.util.TriState;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/** NeoForge: ItemEntityPickupEvent.Pre in {@code playerTouch}, server side; FALSE refuses the pickup. */
@Mixin(ItemEntity.class)
public abstract class ItemEntityMixin {

    @Inject(method = "playerTouch", at = @At("HEAD"), cancellable = true)
    private void proficiency$pickupPre(Player player, CallbackInfo ci) {
        ItemEntity self = (ItemEntity) (Object) this;
        if (self.level().isClientSide()) {
            return;
        }
        if (NeoForge.EVENT_BUS.post(new ItemEntityPickupEvent.Pre(player, self)).canPickup() == TriState.FALSE) {
            ci.cancel();
        }
    }
}
