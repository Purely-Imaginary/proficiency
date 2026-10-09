package dev.amman.proficiency.skill;

import dev.amman.proficiency.config.ProficiencyConfig;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BedBlock;
import net.minecraft.world.level.block.piston.PistonStructureResolver;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BedPart;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;

import java.util.ArrayList;
import java.util.List;

/**
 * Blocks a player placed pay no gathering XP when broken. Every player placement is marked; a break
 * of a marked block pays nothing (no XP, first-time bonus, proc, extra drops or talent), then the
 * mark goes. Only the exact placed position is marked, so a log grown from a planted sapling or a
 * new sugar cane segment is natural. Blocks from before this existed are natural.
 *
 * <p>Exceptions: a planted crop harvested ripe still pays. A mark whose block no longer matches
 * (explosion, fluid, growth) is stale and is dropped. Pistons and falling blocks carry the mark.
 */
public final class PlacedBlocks {

    private PlacedBlocks() {
    }

    public static int hash(BlockState state) {
        return BuiltInRegistries.BLOCK.getKey(state.getBlock()).hashCode();
    }

    private static PlacedBlockStore store(ServerLevel level) {
        return PlacedBlocksData.get(level).store;
    }

    private static void dirty(ServerLevel level) {
        PlacedBlocksData.get(level).setDirty();
    }

    /** A player put this block here. Doors, beds and tall plants mark both halves. */
    public static void markPlacement(ServerLevel level, BlockPos pos, BlockState state) {
        if (ProficiencyConfig.placedBlocksPayXp()) {
            return;
        }
        mark(level, pos, state);
        if (state.hasProperty(BlockStateProperties.DOUBLE_BLOCK_HALF)
                && state.getValue(BlockStateProperties.DOUBLE_BLOCK_HALF) == DoubleBlockHalf.LOWER) {
            BlockPos above = pos.above();
            BlockState other = level.getBlockState(above);
            if (other.is(state.getBlock())) {
                mark(level, above, other);
            }
        } else if (state.getBlock() instanceof BedBlock && state.hasProperty(BedBlock.PART)
                && state.getValue(BedBlock.PART) == BedPart.FOOT) {
            BlockPos head = pos.relative(state.getValue(BedBlock.FACING));
            BlockState other = level.getBlockState(head);
            if (other.is(state.getBlock())) {
                mark(level, head, other);
            }
        }
    }

    private static void mark(ServerLevel level, BlockPos pos, BlockState state) {
        store(level).mark(pos.getX(), pos.getY(), pos.getZ(), hash(state));
        dirty(level);
    }

    /** Whether a raw mark is stored here, ignoring what block stands there now. */
    public static boolean rawMarked(ServerLevel level, BlockPos pos) {
        return store(level).isMarked(pos.getX(), pos.getY(), pos.getZ());
    }

    /**
     * Whether breaking this block pays nothing because a player placed it. Does not clear the mark
     * (the drops handler does). A ripe planted crop is not unpaid. A stale mark is dropped here.
     */
    public static boolean isUnpaid(Level level, BlockPos pos, BlockState state) {
        if (!(level instanceof ServerLevel server) || ProficiencyConfig.placedBlocksPayXp()) {
            return false;
        }
        PlacedBlockStore store = store(server);
        if (store.isEmpty() || !store.isMarked(pos.getX(), pos.getY(), pos.getZ())) {
            return false;
        }
        if (store.hashAt(pos.getX(), pos.getY(), pos.getZ()) != hash(state)) {
            store.clear(pos.getX(), pos.getY(), pos.getZ());
            dirty(server);
            return false;
        }
        return !SkillTools.isRipeCrop(state);
    }

    /** The block is gone: drops the mark and says whether it was an unpaid one. */
    public static boolean consume(Level level, BlockPos pos, BlockState state) {
        boolean unpaid = isUnpaid(level, pos, state);
        if (level instanceof ServerLevel server && !store(server).isEmpty()
                && store(server).clear(pos.getX(), pos.getY(), pos.getZ())) {
            dirty(server);
        }
        return unpaid;
    }

    // ---- Refunded blocks ---------------------------------------------------------------------

    private static final int COUNT_MASK = 0xFF;

    private static int refundKey(BlockState state, int count) {
        return (hash(state) & ~COUNT_MASK) | Math.min(COUNT_MASK, Math.max(1, count));
    }

    /**
     * A refund talent paid {@code count} items for placing this block. Breaking or picking it back
     * up must give them back, or place and pick up is a free block per cycle. Unlike the placed
     * mark this is kept even when placed blocks pay XP, because the refund is the problem and not
     * the XP.
     */
    public static void markRefunded(ServerLevel level, BlockPos pos, BlockState state, int count) {
        PlacedBlocksData data = PlacedBlocksData.get(level);
        data.refunded.mark(pos.getX(), pos.getY(), pos.getZ(), refundKey(state, count));
        data.setDirty();
    }

    /** How many refunded items ride on the block standing here, 0 for none. A stale mark is dropped. */
    public static int refundedCount(Level level, BlockPos pos, BlockState state) {
        if (!(level instanceof ServerLevel server)) {
            return 0;
        }
        PlacedBlocksData data = PlacedBlocksData.get(server);
        PlacedBlockStore refunded = data.refunded;
        if (refunded.isEmpty() || !refunded.isMarked(pos.getX(), pos.getY(), pos.getZ())) {
            return 0;
        }
        int stored = refunded.hashAt(pos.getX(), pos.getY(), pos.getZ());
        if ((stored & ~COUNT_MASK) != (hash(state) & ~COUNT_MASK)) {
            refunded.clear(pos.getX(), pos.getY(), pos.getZ());
            data.setDirty();
            return 0;
        }
        return stored & COUNT_MASK;
    }

    /** As {@link #refundedCount}, and the mark goes: the block is being taken. */
    public static int consumeRefunded(Level level, BlockPos pos, BlockState state) {
        int count = refundedCount(level, pos, state);
        if (count > 0) {
            clearRefunded((ServerLevel) level, pos);
        }
        return count;
    }

    /** Whether a refunded mark is stored here, whatever block stands there now. */
    public static boolean rawRefunded(ServerLevel level, BlockPos pos) {
        return PlacedBlocksData.get(level).refunded.isMarked(pos.getX(), pos.getY(), pos.getZ());
    }

    /** The refunded count stored here whatever block stands now (0 for none), for the post-click check. */
    public static int rawRefundedCount(ServerLevel level, BlockPos pos) {
        PlacedBlockStore refunded = PlacedBlocksData.get(level).refunded;
        return refunded.isMarked(pos.getX(), pos.getY(), pos.getZ())
                ? refunded.hashAt(pos.getX(), pos.getY(), pos.getZ()) & COUNT_MASK : 0;
    }

    public static void clearRefunded(ServerLevel level, BlockPos pos) {
        PlacedBlocksData data = PlacedBlocksData.get(level);
        if (data.refunded.clear(pos.getX(), pos.getY(), pos.getZ())) {
            data.setDirty();
        }
    }

    /** A piston is about to move or crush blocks: marks travel with the blocks pushed. */
    public static void pistonMove(Level level, BlockPos pistonPos, Direction facing, boolean extending) {
        if (!(level instanceof ServerLevel server)) {
            return;
        }
        PlacedBlocksData data = PlacedBlocksData.get(server);
        if (data.store.isEmpty() && data.refunded.isEmpty()) {
            return;
        }
        PistonStructureResolver resolver = new PistonStructureResolver(level, pistonPos, facing, extending);
        if (!resolver.resolve()) {
            return;
        }
        Direction move = extending ? facing : facing.getOpposite();
        movePushed(data.store, resolver, move);
        movePushed(data.refunded, resolver, move);
        data.setDirty();
    }

    private static void movePushed(PlacedBlockStore store, PistonStructureResolver resolver, Direction move) {
        if (store.isEmpty()) {
            return;
        }
        for (BlockPos crushed : resolver.getToDestroy()) {
            store.clear(crushed.getX(), crushed.getY(), crushed.getZ());
        }
        List<BlockPos> from = new ArrayList<>();
        List<Integer> hashes = new ArrayList<>();
        for (BlockPos p : resolver.getToPush()) {
            if (store.isMarked(p.getX(), p.getY(), p.getZ())) {
                from.add(p);
                hashes.add(store.hashAt(p.getX(), p.getY(), p.getZ()));
            }
        }
        for (BlockPos p : from) {
            store.clear(p.getX(), p.getY(), p.getZ());
        }
        for (int i = 0; i < from.size(); i++) {
            BlockPos to = from.get(i).relative(move);
            store.mark(to.getX(), to.getY(), to.getZ(), hashes.get(i));
        }
    }

    /** A falling block starts to fall from here: take the mark off the origin. Returns whether it had one. */
    public static boolean pickUp(ServerLevel level, BlockPos origin, BlockState falling) {
        PlacedBlockStore store = store(level);
        if (store.isEmpty() || !store.isMarked(origin.getX(), origin.getY(), origin.getZ())
                || store.hashAt(origin.getX(), origin.getY(), origin.getZ()) != hash(falling)) {
            return false;
        }
        store.clear(origin.getX(), origin.getY(), origin.getZ());
        dirty(level);
        return true;
    }

    /** The falling block landed here: put the mark back. */
    public static void land(ServerLevel level, BlockPos pos, BlockState landed) {
        mark(level, pos, landed);
    }
}
