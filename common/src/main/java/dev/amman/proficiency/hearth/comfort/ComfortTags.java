package dev.amman.proficiency.hearth.comfort;

import dev.amman.proficiency.Proficiency;
import dev.amman.proficiency.hearth.HearthData;
import net.minecraft.core.registries.Registries;
import net.minecraft.tags.TagKey;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.block.Block;

import java.util.ArrayList;
import java.util.List;

/**
 * The tags the comfort lane reads, all plain data a pack extends:
 *
 * <ul>
 * <li>block tag {@code proficiency:comfort/<kind>} and entity type tag of the same id: what counts for
 * each Comfort kind;</li>
 * <li>block tag {@code proficiency:hearth/character_ignore}: bulk filler that never counts for Character;</li>
 * <li>block tag {@code proficiency:hearth/character_family} and {@code .../character_family/<name>}:
 * the optional Character folding ({@link CharacterKeys});</li>
 * </ul>
 */
public final class ComfortTags {

    public static final TagKey<Block> CHARACTER_IGNORE = TagKey.create(Registries.BLOCK,
            Proficiency.id(CharacterKeys.IGNORE_TAG));
    public static final TagKey<Block> CHARACTER_FAMILY = TagKey.create(Registries.BLOCK,
            Proficiency.id(CharacterKeys.FAMILY_TAG));

    private ComfortTags() {
    }

    /** {@code #proficiency:comfort/<kind>} over blocks. */
    public static TagKey<Block> block(String kind) {
        return TagKey.create(Registries.BLOCK, Proficiency.id(ComfortKinds.tagPath(kind)));
    }

    /** {@code #proficiency:comfort/<kind>} over entity types. */
    public static TagKey<EntityType<?>> entity(String kind) {
        return TagKey.create(Registries.ENTITY_TYPE, Proficiency.id(ComfortKinds.tagPath(kind)));
    }

    /** The kinds the data lists whose ids can name a tag, in the data's order. */
    public static List<String> kinds(HearthData data) {
        return kindTags(data).kinds;
    }

    /** The block tag of every kind of {@link #kinds}, in the same order. */
    public static List<TagKey<Block>> blockTags(HearthData data) {
        return kindTags(data).tags;
    }

    /** The kinds of one data set with their tags, worked out once per data set (a reload installs a new one). */
    private record KindTags(HearthData data, List<String> kinds, List<TagKey<Block>> tags) {
    }

    private static volatile KindTags cached;

    private static KindTags kindTags(HearthData data) {
        KindTags now = cached;
        if (now != null && now.data == data) {
            return now;
        }
        List<String> kinds = new ArrayList<>();
        List<TagKey<Block>> tags = new ArrayList<>();
        for (String kind : data.comfort().kinds()) {
            if (ComfortKinds.valid(kind) && !kinds.contains(kind)) {
                kinds.add(kind);
                tags.add(block(kind));
            }
        }
        now = new KindTags(data, List.copyOf(kinds), List.copyOf(tags));
        cached = now;
        return now;
    }
}
