package dev.amman.proficiency.platform.event.entity.player;

import dev.amman.proficiency.platform.bus.Event;
import dev.amman.proficiency.platform.bus.ICancellableEvent;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BonemealableBlock;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;

/** Bone meal used on a block. Canceling stops vanilla growth. */
public class BonemealEvent extends Event implements ICancellableEvent {

    @Nullable
    private final Player player;
    private final Level level;
    private final BlockPos pos;
    private final BlockState state;
    private final ItemStack stack;
    private final boolean isValidBonemealTarget;
    private boolean success;

    public BonemealEvent(@Nullable Player player, Level level, BlockPos pos, BlockState state, ItemStack stack) {
        this.player = player;
        this.level = level;
        this.pos = pos;
        this.state = state;
        this.stack = stack;
        this.isValidBonemealTarget = state.getBlock() instanceof BonemealableBlock b
                && b.isValidBonemealTarget(level, pos, state);
    }

    @Nullable
    public Player getPlayer() {
        return player;
    }

    public Level getLevel() {
        return level;
    }

    public BlockPos getPos() {
        return pos;
    }

    public BlockState getState() {
        return state;
    }

    public ItemStack getStack() {
        return stack;
    }

    public boolean isValidBonemealTarget() {
        return isValidBonemealTarget;
    }

    public void setSuccessful(boolean success) {
        this.success = success;
    }

    public boolean isSuccessful() {
        return success;
    }
}
