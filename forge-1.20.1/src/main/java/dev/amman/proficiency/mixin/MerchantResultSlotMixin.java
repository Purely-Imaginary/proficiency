package dev.amman.proficiency.mixin;

import com.mojang.logging.LogUtils;
import dev.amman.proficiency.event.CommerceEvents;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.MerchantContainer;
import net.minecraft.world.inventory.MerchantResultSlot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.trading.Merchant;
import net.minecraft.world.item.trading.MerchantOffer;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Commerce: a trade was taken from the result slot. Every merchant that opens the vanilla screen
 * (villagers, wandering traders, modded traders) goes through here, so one hook covers them all.
 * Runs right after {@code Merchant.notifyTrade}, when the offer's uses have gone up.
 * The Forge 1.20.1 copy of common's mixin (listed in {@code proficiency.mixins.json}).
 */
@Mixin(MerchantResultSlot.class)
public abstract class MerchantResultSlotMixin {

    @Shadow @Final private MerchantContainer slots;
    @Shadow @Final private Merchant merchant;

    @Inject(method = "onTake", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/world/item/trading/Merchant;notifyTrade(Lnet/minecraft/world/item/trading/MerchantOffer;)V",
            shift = At.Shift.AFTER), require = 1)
    private void proficiency$traded(Player player, ItemStack stack, CallbackInfo ci) {
        if (player instanceof ServerPlayer serverPlayer && !merchant.isClientSide()) {
            MerchantOffer offer = slots.getActiveOffer();
            if (offer != null) {
                try {
                    CommerceEvents.tradeDone(serverPlayer, merchant, offer);
                } catch (RuntimeException e) {
                    LogUtils.getLogger().warn("Proficiency: trade XP failed", e);
                }
            }
        }
    }
}
