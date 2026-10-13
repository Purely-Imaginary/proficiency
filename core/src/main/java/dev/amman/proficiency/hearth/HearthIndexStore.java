package dev.amman.proficiency.hearth;

import it.unimi.dsi.fastutil.longs.Long2IntOpenHashMap;
import it.unimi.dsi.fastutil.longs.Long2ObjectMap;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.longs.LongArrayList;
import it.unimi.dsi.fastutil.longs.LongIterator;
import it.unimi.dsi.fastutil.longs.LongLinkedOpenHashSet;
import it.unimi.dsi.fastutil.longs.LongSet;
import it.unimi.dsi.fastutil.longs.LongSets;

import java.util.function.LongPredicate;

/**
 * Every Hearth of one dimension: the positions (saved) and a per-chunk lookup (derived), so "which
 * Hearth's range is this block in" costs one primitive map lookup and a few distance checks, with
 * no allocation: cheap enough for a furnace tick. Positions are packed longs ({@link HearthPos});
 * "none" is {@link #NONE}, never a boxed null. The lookup is rebuilt when the set, the base radius
 * or one Hearth's own radius changes. Pure, no Minecraft.
 *
 * <p>Each Hearth may have its own, larger radius (Reach, {@link HearthReach}); a Hearth with none
 * set uses the base radius passed to the lookups. Height limits are the same for every Hearth.
 */
public final class HearthIndexStore {

    /**
     * "No Hearth". It unpacks to x = -33 554 432, outside every world border (30 000 000), so no real
     * block position packs to it.
     */
    public static final long NONE = Long.MIN_VALUE;

    private static final long[] EMPTY = new long[0];

    private final LongLinkedOpenHashSet hearths = new LongLinkedOpenHashSet();
    private final Long2ObjectOpenHashMap<long[]> byChunk = new Long2ObjectOpenHashMap<>();
    private double indexedRadius = -1;
    /** Each Hearth's own radius in blocks, when it has grown past the base (Reach). Saved. */
    private final Long2IntOpenHashMap radii = new Long2IntOpenHashMap();

    public boolean add(long pos) {
        if (pos == NONE) {
            return false;
        }
        boolean added = hearths.add(pos);
        if (added) {
            invalidate();
        }
        return added;
    }

    public boolean remove(long pos) {
        radii.remove(pos);
        boolean removed = hearths.remove(pos);
        if (removed) {
            invalidate();
        }
        return removed;
    }

    public boolean contains(long pos) {
        return hearths.contains(pos);
    }

    /** Every indexed position, read-only (a {@code Set<Long>} too). Copy it before removing while iterating. */
    public LongSet all() {
        return LongSets.unmodifiable(hearths);
    }

    public int size() {
        return hearths.size();
    }

    /**
     * Sets one Hearth's own radius (whole blocks). True when it changed, which rebuilds the lookup.
     * A position that is not indexed is ignored.
     */
    public boolean setRadius(long pos, int radius) {
        if (!hearths.contains(pos)) {
            return false;
        }
        int old = radii.containsKey(pos) ? radii.get(pos) : -1;
        if (old == radius) {
            return false;
        }
        radii.put(pos, radius);
        invalidate();
        return true;
    }

    /** The radius of the Hearth at {@code pos}: its own when bigger, else {@code base}. */
    public double radius(long pos, double base) {
        return radii.containsKey(pos) ? Math.max(base, radii.get(pos)) : base;
    }

    /** The biggest radius of any Hearth here (at least {@code base}). */
    public double maxRadius(double base) {
        double max = base;
        for (int r : radii.values()) {
            max = Math.max(max, r);
        }
        return max;
    }

    private void invalidate() {
        byChunk.clear();
        indexedRadius = -1;
    }

    private void index(double radius) {
        if (indexedRadius == radius) {
            return;
        }
        Long2ObjectOpenHashMap<LongArrayList> lists = new Long2ObjectOpenHashMap<>();
        for (LongIterator it = hearths.iterator(); it.hasNext(); ) {
            long packed = it.nextLong();
            for (long chunk : HearthGeometry.chunksCovered(HearthPos.of(packed), radius(packed, radius))) {
                LongArrayList list = lists.get(chunk);
                if (list == null) {
                    list = new LongArrayList(1);
                    lists.put(chunk, list);
                }
                list.add(packed);
            }
        }
        byChunk.clear();
        for (Long2ObjectMap.Entry<LongArrayList> entry : lists.long2ObjectEntrySet()) {
            byChunk.put(entry.getLongKey(), entry.getValue().toLongArray());
        }
        indexedRadius = radius;
    }

    /** The Hearths whose range might cover a block in this chunk column. Do not modify the array. */
    public long[] candidates(int chunkX, int chunkZ, double radius) {
        index(radius);
        long[] found = byChunk.get(HearthGeometry.chunkKey(chunkX, chunkZ));
        return found == null ? EMPTY : found;
    }

    /**
     * The Hearth whose range holds the block, or {@link #NONE}. With overlapping ranges (a pack with
     * small spacing) the nearest wins. Allocates nothing.
     */
    public long hearthAt(int x, int y, int z, HearthGeometry.Range range) {
        long best = NONE;
        double bestDistance = Double.MAX_VALUE;
        for (long packed : candidates(x >> 4, z >> 4, range.radius())) {
            int dx = x - HearthPos.unpackX(packed);
            int dy = y - HearthPos.unpackY(packed);
            int dz = z - HearthPos.unpackZ(packed);
            double r = radius(packed, range.radius());
            double d = (double) dx * dx + (double) dz * dz;
            if (dy >= -range.below() && dy <= range.above() && d <= r * r && d < bestDistance) {
                bestDistance = d;
                best = packed;
            }
        }
        return best;
    }

    /**
     * As {@link #hearthAt}, but every Hearth counts with the base radius only: the house (the Comfort
     * scan, the bell, the lecterns), which never grows with Reach. Allocates nothing.
     */
    public long houseAt(int x, int y, int z, HearthGeometry.Range range) {
        long best = NONE;
        double bestDistance = Double.MAX_VALUE;
        for (long packed : candidates(x >> 4, z >> 4, range.radius())) {
            int dx = x - HearthPos.unpackX(packed);
            int dz = z - HearthPos.unpackZ(packed);
            if (range.contains(dx, y - HearthPos.unpackY(packed), dz)) {
                double d = (double) dx * dx + (double) dz * dz;
                if (d < bestDistance) {
                    bestDistance = d;
                    best = packed;
                }
            }
        }
        return best;
    }

    /**
     * The nearest Hearth closer than {@code minSpacing} horizontally to {@code pos} (itself
     * excluded), or {@link #NONE}: the spacing check. {@code skip} leaves out positions the caller
     * already found to be gone ({@code null} for none).
     */
    public long tooClose(long pos, double minSpacing, LongPredicate skip) {
        int x = HearthPos.unpackX(pos);
        int z = HearthPos.unpackZ(pos);
        long best = NONE;
        double bestDistance = Double.MAX_VALUE;
        for (LongIterator it = hearths.iterator(); it.hasNext(); ) {
            long other = it.nextLong();
            if (other == pos || skip != null && skip.test(other)) {
                continue;
            }
            double dx = x - HearthPos.unpackX(other);
            double dz = z - HearthPos.unpackZ(other);
            double d = Math.sqrt(dx * dx + dz * dz);
            if (d < minSpacing && d < bestDistance) {
                bestDistance = d;
                best = other;
            }
        }
        return best;
    }

    /** {@link #tooClose(long, double, LongPredicate)} with nothing skipped. */
    public long tooClose(long pos, double minSpacing) {
        return tooClose(pos, minSpacing, null);
    }

    public long[] encode() {
        return hearths.toLongArray();
    }

    /** Every own radius as pairs {pos, radius}, for the save. */
    public long[] encodeRadii() {
        long[] out = new long[radii.size() * 2];
        int i = 0;
        for (Long2IntOpenHashMap.Entry e : radii.long2IntEntrySet()) {
            out[i++] = e.getLongKey();
            out[i++] = e.getIntValue();
        }
        return out;
    }

    /** Restores {@link #encodeRadii()}; pairs naming no indexed Hearth are dropped. Call after {@link #decode}. */
    public void decodeRadii(long[] saved) {
        radii.clear();
        for (int i = 0; i + 1 < saved.length; i += 2) {
            if (hearths.contains(saved[i])) {
                radii.put(saved[i], (int) saved[i + 1]);
            }
        }
        invalidate();
    }

    public void decode(long[] saved) {
        radii.clear();
        hearths.clear();
        for (long pos : saved) {
            if (pos != NONE) {
                hearths.add(pos);
            }
        }
        invalidate();
    }
}
