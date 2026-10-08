package dev.amman.proficiency.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import dev.amman.proficiency.platform.bus.NeoForge;
import dev.amman.proficiency.platform.event.entity.player.BonemealEvent;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.BoneMealItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * NeoForge: BonemealEvent at the start of applyBonemeal (vanilla growCrop), with the player who
 * used it. Vanilla growCrop has no player parameter, so useOn hands it over for the call.
 */
@Mixin(BoneMealItem.class)
public abstract class BoneMealItemMixin {

    @Unique private static Player proficiency$user;

    @WrapOperation(method = "useOn", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/world/item/BoneMealItem;growCrop(Lnet/minecraft/world/item/ItemStack;Lnet/minecraft/world/level/Level;Lnet/minecraft/core/BlockPos;)Z"))
    private boolean proficiency$withPlayer(ItemStack stack, Level level, BlockPos pos, Operation<Boolean> original,
            UseOnContext context) {
        Player previous = proficiency$user;
        proficiency$user = context.getPlayer();
        try {
            return original.call(stack, level, pos);
        } finally {
            proficiency$user = previous;
        }
    }

    @Inject(method = "growCrop", at = @At("HEAD"), cancellable = true)
    private static void proficiency$bonemeal(ItemStack stack, Level level, BlockPos pos,
            CallbackInfoReturnable<Boolean> cir) {
        var event = NeoForge.EVENT_BUS.post(new BonemealEvent(proficiency$user, level, pos, level.getBlockState(pos), stack));
        if (event.isCanceled()) {
            cir.setReturnValue(event.isSuccessful());
        }
    }
}
