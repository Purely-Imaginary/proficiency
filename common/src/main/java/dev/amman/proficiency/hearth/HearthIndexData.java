package dev.amman.proficiency.hearth;

import it.unimi.dsi.fastutil.longs.LongArrayList;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.saveddata.SavedData;
import org.jetbrains.annotations.Nullable;

/**
 * Every Hearth of one dimension, saved as {@code data/proficiency_hearths.dat} of that dimension: the
 * positions for the spacing check, and the per-chunk lookup behind {@link HearthRange#hearthAt}.
 *
 * <p>The index heals itself both ways. The Hearth block adds itself when placed and
 * {@link #forget}s itself when replaced; its block entity re-adds itself every second (a Hearth from
 * a structure, an old save or {@code /setblock}). An entry whose block vanished without the block
 * hearing it (a trimmed or regenerated chunk, WorldEdit, an editor) is dropped once its chunk is
 * loaded and the block there is no Hearth: by the sweep every {@link #SWEEP_TICKS}
 * ({@link #prune}), by {@link HearthRange#entity} when it finds no block entity there, and by the
 * spacing check ({@link #tooClose}) before it refuses a lighting.
 */
public final class HearthIndexData extends SavedData {

    static final String NAME = "proficiency_hearths";

    /** How often the server sweeps every dimension's index for entries whose block is gone. */
    public static final int SWEEP_TICKS = 600;

    private static final SavedData.Factory<HearthIndexData> FACTORY =
            new SavedData.Factory<>(HearthIndexData::create, HearthIndexData::load, null);

    private final HearthIndexStore store = new HearthIndexStore();

    public static HearthIndexData create() {
        return new HearthIndexData();
    }

    public static HearthIndexData load(CompoundTag tag, HolderLookup.Provider registries) {
        HearthIndexData data = new HearthIndexData();
        data.store.decode(tag.getLongArray("hearths"));
        data.store.decodeRadii(tag.getLongArray("radii"));
        return data;
    }

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
        tag.putLongArray("hearths", store.encode());
        tag.putLongArray("radii", store.encodeRadii());
        return tag;
    }

    /** This dimension's index. One map lookup, no allocation: fine on a hot path. */
    public static HearthIndexData get(ServerLevel level) {
        return level.getDataStorage().computeIfAbsent(FACTORY, NAME);
    }

    public HearthIndexStore store() {
        return store;
    }

    public void add(BlockPos pos) {
        if (store.add(pos.asLong())) {
            setDirty();
        }
    }

    /** Removes the entry only; {@link #forget} is what a Hearth going away calls. */
    public void remove(BlockPos pos) {
        if (store.remove(pos.asLong())) {
            setDirty();
        }
    }

    public boolean contains(BlockPos pos) {
        return store.contains(pos.asLong());
    }

    /** Sets the Hearth's own radius (Reach, whole blocks); saved. True when it changed. */
    public boolean setRadius(BlockPos pos, int radius) {
        if (store.setRadius(pos.asLong(), radius)) {
            setDirty();
            return true;
        }
        return false;
    }

    /** The range radius of the Hearth at {@code pos}: its Reach radius, or the base range. */
    public double radius(BlockPos pos) {
        return store.radius(pos.asLong(), HearthRange.range().radius());
    }

    /**
     * The Hearth at {@code pos} is gone (broken, replaced, found missing): out of the index, its
     * mirrored siege entry out of the world data, and its comfort lane's pending flags and placer notes
     * dropped. Everything that removes a Hearth calls this.
     */
    public static void forget(ServerLevel level, BlockPos pos) {
        get(level).remove(pos);
        HearthWorldData.get(level.getServer()).forgetHearth(HearthBlockEntity.key(level, pos));
        dev.amman.proficiency.hearth.comfort.HearthComfort.hearthGone(level, pos);
    }

    /**
     * The packed position of the Hearth whose range (its own Reach radius, the configured heights) holds the block, or
     * {@link HearthIndexStore#NONE}. Allocates nothing: the one a furnace tick uses.
     */
    public long hearthAt(int x, int y, int z) {
        return store.hearthAt(x, y, z, HearthRange.range());
    }

    /** As {@link #hearthAt(int, int, int)} for the house: the base radius of every Hearth, Reach ignored. */
    public long houseAt(int x, int y, int z) {
        return store.houseAt(x, y, z, HearthRange.range());
    }

    /** The Hearth whose range holds {@code pos}, or null. */
    @Nullable
    public BlockPos hearthAt(BlockPos pos) {
        long found = hearthAt(pos.getX(), pos.getY(), pos.getZ());
        return found == HearthIndexStore.NONE ? null : BlockPos.of(found);
    }

    /**
     * The spacing check: the nearest other Hearth closer than {@code minSpacing} blocks horizontally,
     * or null. Each one found is confirmed in the world first (its chunk loaded if it has to be,
     * which a lighting can afford), and an entry whose block is gone is forgotten and skipped, so a
     * ghost never refuses a lighting.
     */
    @Nullable
    public BlockPos tooClose(ServerLevel level, BlockPos pos, double minSpacing) {
        LongOpenHashSet gone = new LongOpenHashSet();
        while (true) {
            long found = store.tooClose(pos.asLong(), minSpacing, gone::contains);
            if (found == HearthIndexStore.NONE) {
                return null;
            }
            BlockPos at = BlockPos.of(found);
            if (level.getBlockState(at).is(HearthBlocks.HEARTH.get())) {
                return at;
            }
            gone.add(found);
            forget(level, at);
        }
    }

    /**
     * Drops every entry whose chunk is loaded and holds no Hearth block there; entries in unloaded
     * chunks wait (nothing is loaded for this). Returns how many went. The server runs it every
     * {@link #SWEEP_TICKS} for every dimension.
     */
    public int prune(ServerLevel level) {
        if (store.size() == 0) {
            return 0;
        }
        LongArrayList gone = new LongArrayList();
        for (long packed : store.encode()) {
            LevelChunk chunk = level.getChunkSource().getChunkNow(HearthPos.unpackX(packed) >> 4,
                    HearthPos.unpackZ(packed) >> 4);
            if (chunk != null && !chunk.getBlockState(BlockPos.of(packed)).is(HearthBlocks.HEARTH.get())) {
                gone.add(packed);
            }
        }
        for (int i = 0; i < gone.size(); i++) {
            forget(level, BlockPos.of(gone.getLong(i)));
        }
        return gone.size();
    }
}
