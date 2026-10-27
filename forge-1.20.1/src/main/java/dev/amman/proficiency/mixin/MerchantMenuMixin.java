package dev.amman.proficiency.mixin;

import com.mojang.logging.LogUtils;
import dev.amman.proficiency.event.CommerceEvents;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.MerchantMenu;
import net.minecraft.world.item.trading.Merchant;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Commerce: the trade screen on the server. When the menu has been made for a player, their
 * discount is written into the merchant's offers ({@link CommerceEvents#menuOpened}); when it closes
 * the cut is taken back first, before a villager resets its own prices.
 * The Forge 1.20.1 copy of common's mixin (listed in {@code proficiency.mixins.json}).
 */
@Mixin(MerchantMenu.class)
public abstract class MerchantMenuMixin {

    @Shadow @Final private Merchant trader;

    @Inject(method = "<init>(ILnet/minecraft/world/entity/player/Inventory;Lnet/minecraft/world/item/trading/Merchant;)V",
            at = @At("TAIL"), require = 1)
    private void proficiency$opened(int containerId, Inventory inventory, Merchant merchant, CallbackInfo ci) {
        if (inventory.player instanceof ServerPlayer player && !merchant.isClientSide()) {
            try {
                CommerceEvents.menuOpened(player, merchant, (MerchantMenu) (Object) this);
            } catch (RuntimeException e) {
                LogUtils.getLogger().warn("Proficiency: trade discount failed", e);
            }
        }
    }

    @Inject(method = "removed", at = @At("HEAD"), require = 1)
    private void proficiency$closed(Player player, CallbackInfo ci) {
        if (player instanceof ServerPlayer serverPlayer && !trader.isClientSide()) {
            try {
                CommerceEvents.menuClosed(serverPlayer, (MerchantMenu) (Object) this);
            } catch (RuntimeException e) {
                LogUtils.getLogger().warn("Proficiency: trade discount reset failed", e);
            }
        }
    }
}
