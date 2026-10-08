package dev.amman.proficiency.compat;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.eventbus.api.Event;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * NeoForge's {@code BlockDropsEvent}, rebuilt for Forge 1.20.1 by {@link DropsModifier}: what a
 * broken block is about to drop, as item entities a handler may change, add to or remove.
 */
public class BlockDropsEvent extends Event {

    private final ServerLevel level;
    private final BlockPos pos;
    private final BlockState state;
    private final List<ItemEntity> drops;
    @Nullable
    private final Entity breaker;
    private final ItemStack tool;

    public BlockDropsEvent(ServerLevel level, BlockPos pos, BlockState state, List<ItemEntity> drops,
            @Nullable Entity breaker, ItemStack tool) {
        this.level = level;
        this.pos = pos;
        this.state = state;
        this.drops = drops;
        this.breaker = breaker;
        this.tool = tool;
    }

    public ServerLevel getLevel() {
        return level;
    }

    public BlockPos getPos() {
        return pos;
    }

    public BlockState getState() {
        return state;
    }

    public List<ItemEntity> getDrops() {
        return drops;
    }

    @Nullable
    public Entity getBreaker() {
        return breaker;
    }

    public ItemStack getTool() {
        return tool;
    }
}
