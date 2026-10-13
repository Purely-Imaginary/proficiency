package dev.amman.proficiency.hearth;

import java.util.ArrayList;
import java.util.List;

/**
 * The Hearth's range is a cylinder around the campfire (docs/HEARTH-SPEC.md, "Building and
 * upgrading"): radius 24 horizontally, from 8 below to 16 above (server config
 * {@code hearth.range}, {@code hearth.rangeBelow}, {@code hearth.rangeAbove}), so the branch mine
 * under the base is not home. Spacing between Hearths is horizontal only; the spacing check itself
 * is {@link HearthIndexStore#tooClose} (the one implementation).
 */
public final class HearthGeometry {

    /** The cylinder: {@code radius} blocks across, {@code below} down and {@code above} up from the campfire. */
    public record Range(double radius, int below, int above) {

        public static final Range DEFAULT = new Range(24, 8, 16);

        /** Whether a block offset from the campfire is inside. */
        public boolean contains(int dx, int dy, int dz) {
            return dy >= -below && dy <= above && (double) dx * dx + (double) dz * dz <= radius * radius;
        }

        /** Whether a point (entity feet) offset from the campfire block's corner is inside. */
        public boolean contains(double dx, double dy, double dz) {
            return dy >= -below && dy < above + 1 && dx * dx + dz * dz <= radius * radius;
        }
    }

    private HearthGeometry() {
    }

    /** Whether {@code (x, y, z)} is in range of the Hearth at {@code hearth}. */
    public static boolean inRange(HearthPos hearth, int x, int y, int z, Range range) {
        return range.contains(x - hearth.x(), y - hearth.y(), z - hearth.z());
    }

    /** As above for an entity's position; the campfire's centre is {@code hearth + 0.5}. */
    public static boolean inRange(HearthPos hearth, double x, double y, double z, Range range) {
        return range.contains(x - (hearth.x() + 0.5), y - hearth.y(), z - (hearth.z() + 0.5));
    }

    /** Horizontal distance between two Hearths, for the spacing check. */
    public static double horizontalDistance(HearthPos a, HearthPos b) {
        double dx = a.x() - b.x();
        double dz = a.z() - b.z();
        return Math.sqrt(dx * dx + dz * dz);
    }

    /** Packs a chunk position like {@code ChunkPos.asLong}. */
    public static long chunkKey(int chunkX, int chunkZ) {
        return ((long) chunkX & 0xFFFFFFFFL) | (((long) chunkZ & 0xFFFFFFFFL) << 32);
    }

    /** Every chunk column the cylinder touches. */
    public static List<Long> chunksCovered(HearthPos hearth, double radius) {
        int r = (int) Math.ceil(radius);
        int minX = (hearth.x() - r) >> 4;
        int maxX = (hearth.x() + r) >> 4;
        int minZ = (hearth.z() - r) >> 4;
        int maxZ = (hearth.z() + r) >> 4;
        List<Long> out = new ArrayList<>();
        for (int cx = minX; cx <= maxX; cx++) {
            for (int cz = minZ; cz <= maxZ; cz++) {
                out.add(chunkKey(cx, cz));
            }
        }
        return out;
    }
}
