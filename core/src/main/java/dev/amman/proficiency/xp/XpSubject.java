package dev.amman.proficiency.xp;

/**
 * The thing a rule is asked about: a block, an item, an entity type, a structure, a biome or a
 * dimension. The loader side implements it over the real registries, a unit test over a map.
 */
public interface XpSubject {

    /** "block", "item", "entity", "structure", "biome" or "dimension". */
    String kind();

    /** The registry id, {@code namespace:path}. */
    String id();

    /** Membership of a tag, {@code namespace:path} without the leading hash. */
    boolean hasTag(String tag);

    /** Block hardness, for specs that scale with it. */
    default double hardness() {
        return 0;
    }

    /** Max health of an entity type, for specs and conditions that use it. */
    default double maxHealth() {
        return 0;
    }

    default String namespace() {
        String id = id();
        int colon = id.indexOf(':');
        return colon < 0 ? "minecraft" : id.substring(0, colon);
    }
}
