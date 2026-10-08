package dev.amman.proficiency.platform.event.level;

import dev.amman.proficiency.platform.bus.ICancellableEvent;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * A broken block's drops, captured as item entities before any of them enter the world. The list
 * is live: add to it, remove from it, or cancel to drop nothing (experience included).
 */
public class BlockDropsEvent extends BlockEvent implements ICancellableEvent {

    @Nullable
    private final BlockEntity blockEntity;
    private final List<ItemEntity> drops;
    @Nullable
    private final Entity breaker;
    private final ItemStack tool;
    private int experience;

    public BlockDropsEvent(ServerLevel level, BlockPos pos, BlockState state, @Nullable BlockEntity blockEntity,
            List<ItemEntity> drops, @Nullable Entity breaker, ItemStack tool) {
        super(level, pos, state);
        this.blockEntity = blockEntity;
        this.drops = drops;
        this.breaker = breaker;
        this.tool = tool;
    }

    public List<ItemEntity> getDrops() {
        return drops;
    }

    @Nullable
    public BlockEntity getBlockEntity() {
        return blockEntity;
    }

    @Nullable
    public Entity getBreaker() {
        return breaker;
    }

    public ItemStack getTool() {
        return tool;
    }

    @Override
    public ServerLevel getLevel() {
        return (ServerLevel) super.getLevel();
    }

    public int getDroppedExperience() {
        return experience;
    }

    public void setDroppedExperience(int experience) {
        this.experience = Math.max(0, experience);
    }
}
