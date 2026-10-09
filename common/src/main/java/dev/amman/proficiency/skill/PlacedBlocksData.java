package dev.amman.proficiency.skill;

import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.saveddata.SavedData;

/** The per-dimension save file for {@link PlacedBlockStore}. Lives in data/proficiency_placed_blocks.dat. */
public final class PlacedBlocksData extends SavedData {

    static final String NAME = "proficiency_placed_blocks";

    final PlacedBlockStore store = new PlacedBlockStore();

    /** Blocks a refund talent paid for: position to (block hash with the refunded count in the low byte). */
    final PlacedBlockStore refunded = new PlacedBlockStore();

    public static PlacedBlocksData create() {
        return new PlacedBlocksData();
    }

    public static PlacedBlocksData load(CompoundTag tag, HolderLookup.Provider registries) {
        PlacedBlocksData data = new PlacedBlocksData();
        ListTag chunks = tag.getList("chunks", 10);
        for (int i = 0; i < chunks.size(); i++) {
            CompoundTag chunk = chunks.getCompound(i);
            data.store.decode(chunk.getLong("c"), chunk.getLongArray("e"));
        }
        ListTag refunds = tag.getList("refunded", 10);
        for (int i = 0; i < refunds.size(); i++) {
            CompoundTag chunk = refunds.getCompound(i);
            data.refunded.decode(chunk.getLong("c"), chunk.getLongArray("e"));
        }
        return data;
    }

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider registries) {
        ListTag chunks = new ListTag();
        for (long key : store.chunkKeys()) {
            CompoundTag chunk = new CompoundTag();
            chunk.putLong("c", key);
            chunk.putLongArray("e", store.encode(key));
            chunks.add(chunk);
        }
        tag.put("chunks", chunks);
        ListTag refunds = new ListTag();
        for (long key : refunded.chunkKeys()) {
            CompoundTag chunk = new CompoundTag();
            chunk.putLong("c", key);
            chunk.putLongArray("e", refunded.encode(key));
            refunds.add(chunk);
        }
        tag.put("refunded", refunds);
        return tag;
    }

    static PlacedBlocksData get(ServerLevel level) {
        return level.getDataStorage().computeIfAbsent(
                new SavedData.Factory<>(PlacedBlocksData::create, PlacedBlocksData::load, null), NAME);
    }
}
