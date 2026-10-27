package dev.amman.proficiency.hearth.comfort;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.amman.proficiency.hearth.HearthData;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;

/**
 * The comfort lane's data files: the mod's own tags, the pack mappings (Pandowo for 1.21.1, Reclamation
 * for 1.20.1) held against the id lists their generator wrote from the packs' jars, and the lang keys and
 * icons of the twelve kinds.
 */
class ComfortDataFilesTest {

    private static final Path COMMON = Path.of("../common/src/main/resources");
    private static final Path TAGS = COMMON.resolve("data/proficiency/tags");
    private static final Path PANDOWO = Path.of(System.getProperty("proficiency.pandowoDir", "../neoforge/packs/pandowo"));
    private static final Path RECLAMATION = Path.of(
            System.getProperty("proficiency.reclamationDir", "../forge-1.20.1/packs/reclamation"));

    private static JsonObject read(Path file) throws IOException {
        return JsonParser.parseString(Files.readString(file)).getAsJsonObject();
    }

    private static List<String> ids(JsonObject tag) {
        List<String> out = new ArrayList<>();
        for (JsonElement v : tag.getAsJsonArray("values")) {
            out.add(v.isJsonPrimitive() ? v.getAsString() : v.getAsJsonObject().get("id").getAsString());
        }
        return out;
    }

    private static List<String> kindsAndFireplace() {
        List<String> all = new ArrayList<>(HearthData.DEFAULT_COMFORT_KINDS);
        all.add("fireplace");
        return all;
    }

    // ---- The mod's own tags ---------------------------------------------------------------------------------

    @Test
    void everyShippedKindHasABlockTagWithTheSpecsMembers() throws IOException {
        for (String kind : kindsAndFireplace()) {
            Path file = TAGS.resolve("block/comfort/" + kind + ".json");
            assertTrue(Files.exists(file), "missing tag file for " + kind);
            assertNotNull(read(file).getAsJsonArray("values"), kind);
        }
        assertTrue(ids(read(TAGS.resolve("block/comfort/bed.json"))).contains("#minecraft:beds"));
        assertTrue(ids(read(TAGS.resolve("block/comfort/seat.json"))).contains("#minecraft:stairs"));
        List<String> fire = ids(read(TAGS.resolve("block/comfort/fire.json")));
        assertTrue(fire.contains("#minecraft:campfires") && fire.contains("#proficiency:comfort/fireplace"), fire.toString());
        assertFalse(fire.contains("minecraft:furnace"), "a lit furnace does not count");
        List<String> trophy = ids(read(TAGS.resolve("block/comfort/trophy.json")));
        assertTrue(trophy.contains("proficiency:siege_trophy") && trophy.contains("minecraft:skeleton_skull")
                && trophy.contains("minecraft:player_wall_head"), trophy.toString());
        List<String> storage = ids(read(TAGS.resolve("block/comfort/storage.json")));
        assertEquals(Set.of("minecraft:barrel", "minecraft:chest", "minecraft:decorated_pot", "#c:chests", "#c:barrels"),
                new HashSet<>(storage));
        // Modded chests and barrels (c:chests, c:barrels) and Macaw's Stairs seats are optional: a pack without
        // them must still load.
        assertTrue(ids(read(TAGS.resolve("block/comfort/seat.json"))).containsAll(List.of("#mcwstairs:compact_stairs",
                "#mcwstairs:bulk_stairs", "#mcwstairs:loft_stairs", "#mcwstairs:skyline_stairs", "#mcwstairs:terrace_stairs")));
        for (String kind : List.of("seat", "storage")) {
            for (JsonElement v : read(TAGS.resolve("block/comfort/" + kind + ".json")).getAsJsonArray("values")) {
                if (v.isJsonObject()) {
                    assertFalse(v.getAsJsonObject().get("required").getAsBoolean(), kind + ": " + v);
                }
            }
        }
        assertEquals(Set.of("minecraft:bookshelf", "minecraft:chiseled_bookshelf", "minecraft:lectern"),
                new HashSet<>(ids(read(TAGS.resolve("block/comfort/books.json")))));
        assertEquals(Set.of("minecraft:jukebox", "minecraft:note_block"),
                new HashSet<>(ids(read(TAGS.resolve("block/comfort/music.json")))));
        assertEquals(Set.of("minecraft:lantern", "minecraft:soul_lantern", "#minecraft:candles", "minecraft:sea_lantern",
                "minecraft:glowstone"), new HashSet<>(ids(read(TAGS.resolve("block/comfort/light.json")))));
        assertTrue(ids(read(TAGS.resolve("block/comfort/carpet.json"))).containsAll(
                List.of("#minecraft:wool_carpets", "minecraft:moss_carpet")));
        assertEquals(List.of("#minecraft:banners"), ids(read(TAGS.resolve("block/comfort/banner.json"))));
        assertEquals(List.of("#minecraft:flower_pots"), ids(read(TAGS.resolve("block/comfort/plant.json"))));
    }

    @Test
    void theEntityKindsAndTheCharacterTagsAreThere() throws IOException {
        assertEquals(Set.of("minecraft:painting", "minecraft:item_frame", "minecraft:glow_item_frame"),
                new HashSet<>(ids(read(TAGS.resolve("entity_type/comfort/art.json")))));
        assertEquals(List.of("minecraft:armor_stand"), ids(read(TAGS.resolve("entity_type/comfort/trophy.json"))));
        List<String> ignore = ids(read(TAGS.resolve("block/hearth/character_ignore.json")));
        for (String filler : List.of("minecraft:dirt", "minecraft:cobblestone", "minecraft:cobbled_deepslate",
                "minecraft:netherrack", "minecraft:gravel", "minecraft:sand", "proficiency:hearth")) {
            assertTrue(ignore.contains(filler), filler + " never counts for Character");
        }
        assertFalse(ignore.stream().anyMatch(i -> i.contains("planks") || i.contains("brick")), "real building blocks count");
        assertTrue(Files.exists(TAGS.resolve("block/hearth/character_family.json")));
    }

    // ---- The pack mappings -------------------------------------------------------------------------------------

    private static void checkPack(Path pack, String tagFolder) throws IOException {
        Set<String> verified = new HashSet<>(Files.readAllLines(pack.resolve("comfort-tag-ids.txt")));
        Set<String> blockDump = new HashSet<>(Files.readAllLines(pack.resolve("block-ids.txt")));
        Path dir = pack.resolve("datapack/data/proficiency/tags/" + tagFolder + "/comfort");
        assertTrue(Files.isDirectory(dir), "no comfort tags in " + dir);
        int files = 0;
        try (Stream<Path> list = Files.list(dir)) {
            for (Path file : list.filter(p -> p.toString().endsWith(".json")).sorted().toList()) {
                files++;
                String kind = file.getFileName().toString().replace(".json", "");
                assertTrue(kindsAndFireplace().contains(kind), "not a Comfort kind: " + kind);
                JsonObject tag = read(file);
                assertFalse(tag.get("replace").getAsBoolean(), "a pack extends the tag, it never replaces it: " + file);
                Set<String> seen = new HashSet<>();
                for (JsonElement v : tag.getAsJsonArray("values")) {
                    JsonObject entry = v.getAsJsonObject();
                    String id = entry.get("id").getAsString();
                    assertFalse(entry.get("required").getAsBoolean(), "a pack without the mod must still load: " + id);
                    assertTrue(seen.add(id), "twice in " + kind + ": " + id);
                    if (id.startsWith("#")) {
                        assertTrue(verified.contains("tag " + id), id + " was not found in the pack's jars");
                    } else {
                        assertTrue(verified.contains("block " + id), id + " was not found in the pack's jars");
                        if (id.startsWith("farmersdelight:")) {
                            assertTrue(blockDump.contains(id), id + " is not in the registry dump (block-ids.txt)");
                        }
                    }
                }
            }
        }
        assertTrue(files >= 4, "a pack maps at least seat, storage, books or beds, carpet, fireplace");
        // Every verified line is used: no stale line outlives a mapping.
        Set<String> used = new HashSet<>();
        try (Stream<Path> list = Files.list(dir)) {
            for (Path file : list.filter(p -> p.toString().endsWith(".json")).toList()) {
                for (String id : ids(read(file))) {
                    used.add((id.startsWith("#") ? "tag " : "block ") + id);
                }
            }
        }
        assertEquals(verified, used, "comfort-tag-ids.txt lists exactly what the tags name");
    }

    @Test
    void thePandowoMappingsNameOnlyWhatItsJarsCarry() throws IOException {
        checkPack(PANDOWO, "block");
        // Handcrafted, Another Furniture and Farmer's Delight are in the pack; Macaw's is not.
        String all = Files.readString(PANDOWO.resolve("comfort-tag-ids.txt"));
        assertTrue(all.contains("#handcrafted:chairs") && all.contains("#another_furniture:chairs")
                && all.contains("farmersdelight:stove") && all.contains("farmersdelight:oak_cabinet"));
        assertFalse(all.contains("mcwfurnitures"));
    }

    @Test
    void theReclamationMappingsNameOnlyWhatItsJarsCarry() throws IOException {
        checkPack(RECLAMATION, "blocks");
        // Macaw's Furniture and Farmer's Delight are in the pack; Handcrafted and Another Furniture are not.
        String all = Files.readString(RECLAMATION.resolve("comfort-tag-ids.txt"));
        assertTrue(all.contains("#mcwfurnitures:chair") && all.contains("#farmersdelight:cabinets")
                && all.contains("farmersdelight:stove"));
        assertFalse(all.contains("handcrafted") || all.contains("another_furniture"));
    }

    // ---- Names and icons -------------------------------------------------------------------------------------------

    @Test
    void everyShippedKindHasAnIconAndNamesInEnglishAndPolish() throws IOException {
        JsonObject en = read(COMMON.resolve("assets/proficiency/lang/en_us.json"));
        JsonObject pl = read(COMMON.resolve("assets/proficiency/lang/pl_pl.json"));
        for (String kind : HearthData.DEFAULT_COMFORT_KINDS) {
            for (JsonObject lang : List.of(en, pl)) {
                assertTrue(lang.has(ComfortKinds.translationKey(kind)), "name of " + kind);
                assertTrue(lang.has(ComfortKinds.descriptionKey(kind)), "description of " + kind);
            }
            assertTrue(Files.exists(COMMON.resolve("assets/proficiency/" + ComfortKinds.iconPath(kind))), "icon of " + kind);
            assertFalse(en.get(ComfortKinds.descriptionKey(kind)).getAsString().contains("—"), "no em dash");
            assertFalse(pl.get(ComfortKinds.descriptionKey(kind)).getAsString().contains("—"), "no em dash");
        }
        for (String key : List.of("none", "unloaded", "none_yet", "comfort", "missing", "character", "character.next")) {
            assertTrue(en.has("proficiency.hearth.comfort.command." + key), key);
            assertTrue(pl.has("proficiency.hearth.comfort.command." + key), key);
        }
        for (String key : List.of("gained", "complete")) {
            assertTrue(en.has("proficiency.hearth.comfort." + key), key);
            assertTrue(pl.has("proficiency.hearth.comfort." + key), key);
            assertFalse(en.get("proficiency.hearth.comfort." + key).getAsString().contains("\u2014"), "no em dash");
        }
        // The Polish text is translated, not copied: no kind name is the English one except where the words agree.
        assertFalse(pl.get("proficiency.hearth.comfort.kind.bed").getAsString().equals("Bed"));
    }
}
