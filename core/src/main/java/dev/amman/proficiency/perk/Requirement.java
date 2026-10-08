package dev.amman.proficiency.perk;

import org.jetbrains.annotations.Nullable;

import java.util.regex.Pattern;

/**
 * A pile of something a perk wants handed over.
 *
 * <p>Items are named by id and resolved at use, never at class-load. Half of these ids belong to
 * Biomes We've Gone, the Aether or the Twilight Forest, and a requirement for a mod that is not
 * installed has to quietly disappear rather than crash the mod or, worse, make a perk impossible.
 *
 * <p>Plain strings here, so the talent tables need no Minecraft. {@code perk/Requirements} in each
 * loader's code resolves them against the item registry and the player's inventory.
 *
 * @param itemId a full item id such as {@code minecraft:bone}, or null for a tag
 * @param tag    a full item tag id such as {@code minecraft:logs}, or null for an item
 */
public record Requirement(@Nullable String itemId, @Nullable String tag, int count) {

    /** What a resource location accepts: namespace, a colon, path. */
    private static final Pattern ID = Pattern.compile("[a-z0-9_.-]+:[a-z0-9_./-]+");

    public static Requirement of(String id, int count) {
        return new Requirement(normalize(id), null, count);
    }

    public static Requirement ofTag(String tagPath, int count) {
        return new Requirement(null, normalize(tagPath), count);
    }

    /** The namespace of the item, or of the tag. */
    public String namespace() {
        String id = tag != null ? tag : itemId;
        return id.substring(0, id.indexOf(':'));
    }

    /** The part after the colon of the item, or of the tag. */
    public String path() {
        String id = tag != null ? tag : itemId;
        return id.substring(id.indexOf(':') + 1);
    }

    /** Defaults to the minecraft namespace and rejects a malformed id at once, as parsing it did. */
    private static String normalize(String id) {
        String full = id.indexOf(':') < 0 ? "minecraft:" + id : id;
        if (!ID.matcher(full).matches()) {
            throw new IllegalArgumentException("Not a valid id: " + id);
        }
        return full;
    }
}
