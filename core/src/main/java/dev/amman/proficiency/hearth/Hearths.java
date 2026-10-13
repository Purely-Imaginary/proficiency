package dev.amman.proficiency.hearth;

/**
 * The live {@link HearthData}. The reload listener in common installs a new copy after every
 * datapack (re)load; until then, and in unit tests, it is {@link HearthData#DEFAULTS}. Reads are a
 * volatile load, so any thread may ask.
 */
public final class Hearths {

    private static volatile HearthData data = HearthData.DEFAULTS;

    private Hearths() {
    }

    public static HearthData data() {
        return data;
    }

    public static void install(HearthData next) {
        data = next == null ? HearthData.DEFAULTS : next;
    }

    /** Back to the defaults (server stop, tests). */
    public static void reset() {
        data = HearthData.DEFAULTS;
    }
}
