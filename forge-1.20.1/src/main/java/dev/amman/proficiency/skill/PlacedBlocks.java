package dev.amman.proficiency.skill;

import dev.amman.proficiency.config.ProficiencyConfig;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BedBlock;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.piston.PistonStructureResolver;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.BedPart;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.DoubleBlockHalf;
import net.minecraft.world.level.chunk.LevelChunk;
import org.jetbrains.annotations.Nullable;

import it.unimi.dsi.fastutil.longs.LongArrayList;

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
        markHalves(level, store(level), pos, state);
    }

    /** Writes the mark for a block and for the other half of a door, a tall plant or a bed. */
    private static void markHalves(ServerLevel level, PlacedBlockStore into, BlockPos pos, BlockState state) {
        put(level, into, pos, state);
        if (state.hasProperty(BlockStateProperties.DOUBLE_BLOCK_HALF)
                && state.getValue(BlockStateProperties.DOUBLE_BLOCK_HALF) == DoubleBlockHalf.LOWER) {
            BlockPos above = pos.above();
            BlockState other = level.getBlockState(above);
            if (other.is(state.getBlock())) {
                put(level, into, above, other);
            }
        } else if (state.getBlock() instanceof BedBlock && state.hasProperty(BedBlock.PART)
                && state.getValue(BedBlock.PART) == BedPart.FOOT) {
            BlockPos head = pos.relative(state.getValue(BedBlock.FACING));
            BlockState other = level.getBlockState(head);
            if (other.is(state.getBlock())) {
                put(level, into, head, other);
            }
        }
    }

    private static void put(ServerLevel level, PlacedBlockStore into, BlockPos pos, BlockState state) {
        into.mark(pos.getX(), pos.getY(), pos.getZ(), hash(state));
        dirty(level);
    }

    private static void mark(ServerLevel level, BlockPos pos, BlockState state) {
        put(level, store(level), pos, state);
    }

    // ---- The comfort mark (the Hearth's Comfort and Character) --------------------------------

    private static PlacedBlockStore comfortStore(ServerLevel level) {
        return PlacedBlocksData.get(level).comfort;
    }

    /**
     * A player put this block here, for the Hearth: written on every player placement whatever
     * {@code placedBlocksPayXp} says (the gathering mark above is not written when that option is
     * on). Doors, beds and tall plants mark both halves.
     */
    public static void markComfort(ServerLevel level, BlockPos pos, BlockState state) {
        markHalves(level, comfortStore(level), pos, state);
    }

    /** The block is gone (a player broke it): drops its comfort mark. Returns whether there was one. */
    public static boolean clearComfort(ServerLevel level, BlockPos pos) {
        PlacedBlockStore store = comfortStore(level);
        if (store.isEmpty() || !store.clear(pos.getX(), pos.getY(), pos.getZ())) {
            return false;
        }
        dirty(level);
        return true;
    }

    /**
     * A player broke {@code state} at {@code pos}: drops its comfort mark and the one of the other half of
     * a door, a tall plant or a bed, which the game removes with it (both halves were marked on placement).
     */
    public static void clearComfortPlacement(ServerLevel level, BlockPos pos, BlockState state) {
        clearComfort(level, pos);
        BlockPos other = otherHalf(pos, state);
        if (other != null) {
            clearComfort(level, other);
        }
    }

    /** The position of the other half of a door, tall plant or bed, or null for a block of one piece. */
    @Nullable
    private static BlockPos otherHalf(BlockPos pos, BlockState state) {
        if (state.hasProperty(BlockStateProperties.DOUBLE_BLOCK_HALF)) {
            return state.getValue(BlockStateProperties.DOUBLE_BLOCK_HALF) == DoubleBlockHalf.LOWER
                    ? pos.above() : pos.below();
        }
        if (state.getBlock() instanceof BedBlock && state.hasProperty(BedBlock.PART)) {
            Direction facing = state.getValue(BedBlock.FACING);
            return state.getValue(BedBlock.PART) == BedPart.FOOT ? pos.relative(facing) : pos.relative(facing.getOpposite());
        }
        return null;
    }

    /** Whether a comfort mark is stored here, whatever block stands there now. */
    public static boolean rawComfortMarked(ServerLevel level, BlockPos pos) {
        return comfortStore(level).isMarked(pos.getX(), pos.getY(), pos.getZ());
    }

    /**
     * A chunk has loaded: drops the comfort marks whose block is no longer the one placed (burnt, blown
     * up, washed away, popped off when its support broke, taken by an enderman, the far half of a door).
     * Only a break by a player or a scan of a Hearth cleans a mark otherwise, so without this the marks of
     * blocks destroyed outside every Hearth's range would stay in the save for good. Returns how many went.
     */
    public static int sweepComfort(ServerLevel level, LevelChunk chunk) {
        PlacedBlockStore store = comfortStore(level);
        if (store.isEmpty()) {
            return 0;
        }
        LongArrayList stale = new LongArrayList();
        BlockPos.MutableBlockPos at = new BlockPos.MutableBlockPos();
        store.forEachInChunk(chunk.getPos().x, chunk.getPos().z, level.getMinBuildHeight(), level.getMaxBuildHeight() - 1,
                (x, y, z, hash) -> {
                    BlockState there = chunk.getBlockState(at.set(x, y, z));
                    // A block mid-push is a moving piston for three ticks; its mark already went with it.
                    if (hash(there) != hash && !there.is(Blocks.MOVING_PISTON)) {
                        stale.add(at.asLong());
                    }
                });
        for (int i = 0; i < stale.size(); i++) {
            BlockPos gone = BlockPos.of(stale.getLong(i));
            store.clear(gone.getX(), gone.getY(), gone.getZ());
        }
        if (!stale.isEmpty()) {
            dirty(level);
        }
        return stale.size();
    }

    /** One placed block found by {@link #forEachPlaced}; {@code comfortMark} is false for a gathering mark alone. */
    @FunctionalInterface
    public interface PlacedVisitor {
        void visit(int x, int y, int z, int hash, boolean comfortMark);
    }

    /**
     * Every player-placed mark of one chunk column in the height band: the comfort marks, then the
     * gathering marks that have no comfort mark at the same spot (blocks placed before the comfort
     * mark existed). The hash is the stored one: the caller compares it with the block standing
     * there. The stores must not change while this runs.
     */
    public static void forEachPlaced(ServerLevel level, int chunkX, int chunkZ, int minY, int maxY,
            PlacedVisitor visitor) {
        PlacedBlockStore comfort = comfortStore(level);
        comfort.forEachInChunk(chunkX, chunkZ, minY, maxY, (x, y, z, hash) -> visitor.visit(x, y, z, hash, true));
        PlacedBlockStore legacy = store(level);
        if (!legacy.isEmpty()) {
            legacy.forEachInChunk(chunkX, chunkZ, minY, maxY, (x, y, z, hash) -> {
                if (!comfort.isMarked(x, y, z)) {
                    visitor.visit(x, y, z, hash, false);
                }
            });
        }
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
        return !SkillTools.isRipeCrop(level, pos, state);
    }

    /**
     * {@link #isUnpaid} without its one side effect: a mark whose block changed is left for the break to
     * drop. The Jade tooltip asks this one, so a look never changes saved state.
     */
    public static boolean peekUnpaid(Level level, BlockPos pos, BlockState state) {
        if (!(level instanceof ServerLevel server) || ProficiencyConfig.placedBlocksPayXp()) {
            return false;
        }
        PlacedBlockStore store = store(server);
        return !store.isEmpty() && store.isMarked(pos.getX(), pos.getY(), pos.getZ())
                && store.hashAt(pos.getX(), pos.getY(), pos.getZ()) == hash(state)
                && !SkillTools.isRipeCrop(level, pos, state);
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
        if (data.store.isEmpty() && data.refunded.isEmpty() && data.comfort.isEmpty()) {
            return;
        }
        PistonStructureResolver resolver = new PistonStructureResolver(level, pistonPos, facing, extending);
        if (!resolver.resolve()) {
            return;
        }
        Direction move = extending ? facing : facing.getOpposite();
        movePushed(data.store, resolver, move);
        movePushed(data.refunded, resolver, move);
        movePushed(data.comfort, resolver, move);
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

    /** A mark a falling block carries: the gathering mark. */
    public static final int MARK_GATHERING = 1;

    /** A mark a falling block carries: the comfort mark. */
    public static final int MARK_COMFORT = 2;

    /**
     * A falling block starts to fall from here: take the marks off the origin. Returns which it had
     * ({@link #MARK_GATHERING}, {@link #MARK_COMFORT}, both or 0), so that {@link #land} puts back exactly
     * those: with {@code placedBlocksPayXp} on only the comfort mark exists, and a landed block must not
     * gain a gathering mark it never had.
     */
    public static int pickUp(ServerLevel level, BlockPos origin, BlockState falling) {
        int marks = 0;
        if (takeMark(level, store(level), origin, falling)) {
            marks |= MARK_GATHERING;
        }
        if (takeMark(level, comfortStore(level), origin, falling)) {
            marks |= MARK_COMFORT;
        }
        return marks;
    }

    private static boolean takeMark(ServerLevel level, PlacedBlockStore store, BlockPos origin, BlockState falling) {
        if (store.isEmpty() || !store.isMarked(origin.getX(), origin.getY(), origin.getZ())
                || store.hashAt(origin.getX(), origin.getY(), origin.getZ()) != hash(falling)) {
            return false;
        }
        store.clear(origin.getX(), origin.getY(), origin.getZ());
        dirty(level);
        return true;
    }

    /** The falling block landed here: put back the marks {@link #pickUp} took ({@code marks}). */
    public static void land(ServerLevel level, BlockPos pos, BlockState landed, int marks) {
        if ((marks & MARK_GATHERING) != 0) {
            mark(level, pos, landed);
        }
        if ((marks & MARK_COMFORT) != 0) {
            put(level, comfortStore(level), pos, landed);
        }
    }
}
