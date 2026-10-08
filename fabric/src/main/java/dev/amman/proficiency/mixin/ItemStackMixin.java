package dev.amman.proficiency.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import dev.amman.proficiency.platform.bus.NeoForge;
import dev.amman.proficiency.platform.event.level.BlockEvent;
import dev.amman.proficiency.platform.hooks.PlaceCapture;
import net.minecraft.core.BlockPos;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.BucketItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * NeoForge's CommonHooks.onPlaceItemIntoWorld: capture the blocks an item use changes, then post
 * EntityPlaceEvent. One changed block posts for that block; several (a bed, a door) post once,
 * for the first, which is what NeoForge's EntityMultiPlaceEvent presents through its base class.
 * Buckets are not captured, as on NeoForge. A canceled event reverts every captured block.
 */
@Mixin(ItemStack.class)
public abstract class ItemStackMixin {

    @WrapOperation(method = "useOn", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/world/item/Item;useOn(Lnet/minecraft/world/item/context/UseOnContext;)Lnet/minecraft/world/InteractionResult;"))
    private InteractionResult proficiency$placeEvents(Item item, UseOnContext context, Operation<InteractionResult> original) {
        Level level = context.getLevel();
        if (level.isClientSide || item instanceof BucketItem) {
            return original.call(item, context);
        }
        PlaceCapture.Frame frame = PlaceCapture.begin(level);
        InteractionResult result;
        try {
            result = original.call(item, context);
        } finally {
            PlaceCapture.end(frame);
        }
        if (!result.consumesAction() || frame.before().isEmpty()) {
            return result;
        }
        Map.Entry<BlockPos, BlockState> first = frame.before().entrySet().iterator().next();
        BlockPos pos = first.getKey();
        BlockState placedAgainst = level.getBlockState(pos.relative(context.getClickedFace().getOpposite()));
        BlockEvent.EntityPlaceEvent event = NeoForge.EVENT_BUS.post(new BlockEvent.EntityPlaceEvent(
                level, pos, first.getValue(), level.getBlockState(pos), placedAgainst, context.getPlayer()));
        if (event.isCanceled()) {
            List<Map.Entry<BlockPos, BlockState>> reverse = new ArrayList<>(frame.before().entrySet());
            java.util.Collections.reverse(reverse);
            for (Map.Entry<BlockPos, BlockState> e : reverse) {
                level.setBlock(e.getKey(), e.getValue(), Block.UPDATE_ALL);
            }
            return InteractionResult.FAIL;
        }
        return result;
    }
}
