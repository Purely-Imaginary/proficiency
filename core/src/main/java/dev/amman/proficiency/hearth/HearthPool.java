package dev.amman.proficiency.hearth;

/**
 * The five charge pools of a Hearth, in a fixed order. The order is the index used in arrays, in
 * the block entity's save and on the wire, so never reorder; add new pools at the end only.
 * Which skills feed a pool and which boon it gives are data ({@link HearthData.PoolSpec}).
 */
public enum HearthPool {
    HEARTY("hearty"),
    REGEN("regen"),
    FORTIFIED("fortified"),
    WORKSHOP("workshop"),
    HOMECOMING("homecoming");

    public static final HearthPool[] VALUES = values();
    public static final int COUNT = VALUES.length;

    private final String id;

    HearthPool(String id) {
        this.id = id;
    }

    public String id() {
        return id;
    }

    /** {@code proficiency.hearth.pool.<id>}, the pool's display name. */
    public String translationKey() {
        return "proficiency.hearth.pool." + id;
    }

    public static HearthPool byId(String id) {
        for (HearthPool pool : VALUES) {
            if (pool.id.equals(id)) {
                return pool;
            }
        }
        return null;
    }

    /** By index, or null when out of range (a packet from a newer version, say). */
    public static HearthPool byIndex(int index) {
        return index >= 0 && index < COUNT ? VALUES[index] : null;
    }
}
