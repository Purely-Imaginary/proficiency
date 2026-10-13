package dev.amman.proficiency.hearth;

/**
 * A block position in core, packed exactly like Minecraft's {@code BlockPos.asLong} (26 bits x,
 * 26 bits z, 12 bits y), so common code converts with {@code BlockPos.of(long)} and
 * {@code pos.asLong()} and a saved long means the same on every line.
 */
public record HearthPos(int x, int y, int z) {

    private static final int XZ_BITS = 26;
    private static final int Y_BITS = 12;
    private static final long XZ_MASK = (1L << XZ_BITS) - 1L;
    private static final long Y_MASK = (1L << Y_BITS) - 1L;
    private static final int X_OFFSET = Y_BITS + XZ_BITS;
    private static final int Z_OFFSET = Y_BITS;

    public static long pack(int x, int y, int z) {
        return ((x & XZ_MASK) << X_OFFSET) | ((z & XZ_MASK) << Z_OFFSET) | (y & Y_MASK);
    }

    public long pack() {
        return pack(x, y, z);
    }

    public static HearthPos of(long packed) {
        return new HearthPos(unpackX(packed), unpackY(packed), unpackZ(packed));
    }

    public static int unpackX(long packed) {
        return (int) (packed >> X_OFFSET);
    }

    public static int unpackY(long packed) {
        return (int) (packed << (64 - Y_BITS) >> (64 - Y_BITS));
    }

    public static int unpackZ(long packed) {
        return (int) (packed << (64 - Z_OFFSET - XZ_BITS) >> (64 - XZ_BITS));
    }

    /** "x,y,z", the readable key used in world data and Chronicle pages. */
    public String key() {
        return x + "," + y + "," + z;
    }
}
