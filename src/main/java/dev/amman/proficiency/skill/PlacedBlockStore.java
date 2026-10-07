package dev.amman.proficiency.skill;

import it.unimi.dsi.fastutil.longs.Long2IntOpenHashMap;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;

import java.util.Arrays;

/**
 * Where players put blocks, so breaking them again pays no gathering XP. Pure data, no Minecraft
 * classes, so it can be unit tested.
 *
 * <p>Storage: one small map per chunk, from a packed in-chunk position to a hash of the block that
 * was placed there. The hash is a guard, not an identity: when the block at a marked spot is no
 * longer the one that was placed (an explosion, a fluid, a piston, crop growth), the mark is stale
 * and is dropped instead of being trusted. Persisted per chunk as a sorted long[] of
 * {@code packedPosition << 32 | hash}, eight bytes per placed block.
 */
public final class PlacedBlockStore {

    private final Long2ObjectOpenHashMap<Long2IntOpenHashMap> chunks = new Long2ObjectOpenHashMap<>();

    /** The packed key of a chunk. */
    public static long chunkKey(int chunkX, int chunkZ) {
        return ((long) chunkX & 0xFFFFFFFFL) | ((long) chunkZ << 32);
    }

    /** Position inside a chunk: 12 bits of height (offset so negative Y packs), then x and z nibbles. */
    public static long local(int x, int y, int z) {
        return ((long) (y + 2048) << 8) | ((x & 15) << 4) | (z & 15);
    }

    /** Marks a position as player placed; {@code hash} identifies the block placed there. */
    public void mark(int x, int y, int z, int hash) {
        chunks.computeIfAbsent(chunkKey(x >> 4, z >> 4), k -> new Long2IntOpenHashMap())
                .put(local(x, y, z), hash);
    }

    public boolean isMarked(int x, int y, int z) {
        Long2IntOpenHashMap chunk = chunks.get(chunkKey(x >> 4, z >> 4));
        return chunk != null && chunk.containsKey(local(x, y, z));
    }

    /** The stored hash, or {@code 0} when nothing is marked (check {@link #isMarked} first for a real 0). */
    public int hashAt(int x, int y, int z) {
        Long2IntOpenHashMap chunk = chunks.get(chunkKey(x >> 4, z >> 4));
        return chunk == null ? 0 : chunk.get(local(x, y, z));
    }

    /** Removes a mark. Returns whether there was one. */
    public boolean clear(int x, int y, int z) {
        long key = chunkKey(x >> 4, z >> 4);
        Long2IntOpenHashMap chunk = chunks.get(key);
        if (chunk == null || !chunk.containsKey(local(x, y, z))) {
            return false;
        }
        chunk.remove(local(x, y, z));
        if (chunk.isEmpty()) {
            chunks.remove(key);
        }
        return true;
    }

    public boolean isEmpty() {
        return chunks.isEmpty();
    }

    public int size() {
        int n = 0;
        for (Long2IntOpenHashMap chunk : chunks.values()) {
            n += chunk.size();
        }
        return n;
    }

    public long[] chunkKeys() {
        long[] keys = chunks.keySet().toLongArray();
        Arrays.sort(keys);
        return keys;
    }

    /** One chunk as a sorted long[] of {@code local << 32 | hash}. */
    public long[] encode(long chunkKey) {
        Long2IntOpenHashMap chunk = chunks.get(chunkKey);
        if (chunk == null) {
            return new long[0];
        }
        long[] out = new long[chunk.size()];
        int i = 0;
        for (Long2IntOpenHashMap.Entry e : chunk.long2IntEntrySet()) {
            out[i++] = (e.getLongKey() << 32) | (e.getIntValue() & 0xFFFFFFFFL);
        }
        Arrays.sort(out);
        return out;
    }

    /** The inverse of {@link #encode}. */
    public void decode(long chunkKey, long[] entries) {
        if (entries.length == 0) {
            return;
        }
        Long2IntOpenHashMap chunk = chunks.computeIfAbsent(chunkKey, k -> new Long2IntOpenHashMap());
        for (long entry : entries) {
            chunk.put(entry >>> 32, (int) entry);
        }
    }

    public static int chunkX(long chunkKey) {
        return (int) chunkKey;
    }

    public static int chunkZ(long chunkKey) {
        return (int) (chunkKey >> 32);
    }
}
