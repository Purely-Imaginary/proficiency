package dev.amman.proficiency.platform.event.level;

import dev.amman.proficiency.platform.bus.Event;
import dev.amman.proficiency.platform.bus.ICancellableEvent;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;

public abstract class BlockEvent extends Event {

    private final LevelAccessor level;
    private final BlockPos pos;
    private final BlockState state;

    protected BlockEvent(LevelAccessor level, BlockPos pos, BlockState state) {
        this.pos = pos;
        this.level = level;
        this.state = state;
    }

    public LevelAccessor getLevel() {
        return level;
    }

    public BlockPos getPos() {
        return pos;
    }

    public BlockState getState() {
        return state;
    }

    /** Server side, a player is about to break a block. Canceling keeps the block. */
    public static class BreakEvent extends BlockEvent implements ICancellableEvent {

        private final Player player;

        public BreakEvent(Level level, BlockPos pos, BlockState state, Player player) {
            super(level, pos, state);
            this.player = player;
        }

        public Player getPlayer() {
            return player;
        }
    }

    /** Server side, an entity placed a block (it is already in the world). */
    public static class EntityPlaceEvent extends BlockEvent implements ICancellableEvent {

        @Nullable
        private final Entity entity;
        private final BlockState placedBlock;
        private final BlockState placedAgainst;

        public EntityPlaceEvent(LevelAccessor level, BlockPos pos, BlockState replaced, BlockState placedBlock,
                BlockState placedAgainst, @Nullable Entity entity) {
            super(level, pos, replaced);
            this.entity = entity;
            this.placedBlock = placedBlock;
            this.placedAgainst = placedAgainst;
        }

        @Nullable
        public Entity getEntity() {
            return entity;
        }

        public BlockState getPlacedBlock() {
            return placedBlock;
        }

        public BlockState getPlacedAgainst() {
            return placedAgainst;
        }
    }
}
