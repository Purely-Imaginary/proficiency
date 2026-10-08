package dev.amman.proficiency.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import dev.amman.proficiency.platform.bus.NeoForge;
import dev.amman.proficiency.platform.event.level.BlockDropsEvent;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;

/**
 * NeoForge's block drop capture: every {@code dropResources} overload collects the item entities
 * its popResource calls would have spawned, posts BlockDropsEvent, and only then spawns what is
 * left and runs spawnAfterBreak (experience). Canceled means neither happens.
 */
@Mixin(Block.class)
public abstract class BlockMixin {

    /** Server thread only; a stack because a drop can in principle cause another block's drop. */
    @Unique
    private static final ArrayDeque<List<ItemEntity>> PROFICIENCY$CAPTURE = new ArrayDeque<>();

    @Inject(method = {
            "dropResources(Lnet/minecraft/world/level/block/state/BlockState;Lnet/minecraft/world/level/Level;Lnet/minecraft/core/BlockPos;)V",
            "dropResources(Lnet/minecraft/world/level/block/state/BlockState;Lnet/minecraft/world/level/LevelAccessor;Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/level/block/entity/BlockEntity;)V",
            "dropResources(Lnet/minecraft/world/level/block/state/BlockState;Lnet/minecraft/world/level/Level;Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/level/block/entity/BlockEntity;Lnet/minecraft/world/entity/Entity;Lnet/minecraft/world/item/ItemStack;)V"
    }, at = @At(value = "INVOKE", target = "Ljava/util/List;forEach(Ljava/util/function/Consumer;)V"))
    private static void proficiency$beginCapture(CallbackInfo ci) {
        PROFICIENCY$CAPTURE.push(new ArrayList<>());
    }

    @WrapOperation(method = "popResource(Lnet/minecraft/world/level/Level;Ljava/util/function/Supplier;Lnet/minecraft/world/item/ItemStack;)V",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/world/level/Level;addFreshEntity(Lnet/minecraft/world/entity/Entity;)Z"))
    private static boolean proficiency$capture(Level level, net.minecraft.world.entity.Entity entity, Operation<Boolean> original) {
        List<ItemEntity> capture = PROFICIENCY$CAPTURE.peek();
        if (capture != null && entity instanceof ItemEntity item) {
            capture.add(item);
            return true;
        }
        return original.call(level, entity);
    }

    @WrapOperation(method = "dropResources(Lnet/minecraft/world/level/block/state/BlockState;Lnet/minecraft/world/level/Level;Lnet/minecraft/core/BlockPos;)V",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/world/level/block/state/BlockState;spawnAfterBreak(Lnet/minecraft/server/level/ServerLevel;Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/item/ItemStack;Z)V"))
    private static void proficiency$drops1(BlockState state, ServerLevel level, BlockPos pos, ItemStack tool, boolean xp,
            Operation<Void> original) {
        proficiency$fire(state, level, pos, null, null, tool, xp, original);
    }

    @WrapOperation(method = "dropResources(Lnet/minecraft/world/level/block/state/BlockState;Lnet/minecraft/world/level/LevelAccessor;Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/level/block/entity/BlockEntity;)V",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/world/level/block/state/BlockState;spawnAfterBreak(Lnet/minecraft/server/level/ServerLevel;Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/item/ItemStack;Z)V"))
    private static void proficiency$drops2(BlockState state, ServerLevel level, BlockPos pos, ItemStack tool, boolean xp,
            Operation<Void> original, BlockState s, LevelAccessor l, BlockPos p, @Nullable BlockEntity blockEntity) {
        proficiency$fire(state, level, pos, blockEntity, null, tool, xp, original);
    }

    @WrapOperation(method = "dropResources(Lnet/minecraft/world/level/block/state/BlockState;Lnet/minecraft/world/level/Level;Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/level/block/entity/BlockEntity;Lnet/minecraft/world/entity/Entity;Lnet/minecraft/world/item/ItemStack;)V",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/world/level/block/state/BlockState;spawnAfterBreak(Lnet/minecraft/server/level/ServerLevel;Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/item/ItemStack;Z)V"))
    private static void proficiency$drops3(BlockState state, ServerLevel level, BlockPos pos, ItemStack tool, boolean xp,
            Operation<Void> original, BlockState s, Level l, BlockPos p, @Nullable BlockEntity blockEntity,
            @Nullable Entity breaker, ItemStack t) {
        proficiency$fire(state, level, pos, blockEntity, breaker, tool, xp, original);
    }

    @Unique
    private static void proficiency$fire(BlockState state, ServerLevel level, BlockPos pos,
            @Nullable BlockEntity blockEntity, @Nullable Entity breaker, ItemStack tool, boolean xp,
            Operation<Void> spawnAfterBreak) {
        List<ItemEntity> drops = PROFICIENCY$CAPTURE.poll();
        if (drops == null) {
            spawnAfterBreak.call(state, level, pos, tool, xp);
            return;
        }
        BlockDropsEvent event = NeoForge.EVENT_BUS.post(
                new BlockDropsEvent(level, pos, state, blockEntity, drops, breaker, tool));
        if (event.isCanceled()) {
            return;
        }
        for (ItemEntity drop : event.getDrops()) {
            level.addFreshEntity(drop);
        }
        spawnAfterBreak.call(state, level, pos, tool, xp);
    }
}
