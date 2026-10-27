package dev.amman.proficiency.hearth;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The Forge 1.20.1 line's Hearth data against the 1.21.1 data in common (the hand copy must not drift):
 * every Hearth and Comfort tag exists under 1.20.1's plural folder with the same entries, an id 1.20.1
 * lacks is optional ({@code required: false}, or the whole tag would fail to load), every plain
 * vanilla id is a real 1.20.1 id, the loot tables, recipe and advancement exist, and the build lays in
 * common's {@code proficiency/hearth/default.json} and the Hearth's shared assets unchanged.
 */
class HearthForgeDataTest {

    private static final Path COMMON = Path.of("../common/src/main/resources");
    private static final Path FORGE = Path.of("src/main/resources");
    /** What processResources produced (the test task runs after it). */
    private static final Path BUILT = Path.of("build/resources/main");
    /** Vanilla 1.21 blocks the Hearth's tags name that 1.20.1 does not have. */
    private static final Set<String> ONLY_121 = Set.of("minecraft:polished_tuff", "minecraft:tuff_bricks",
            "minecraft:chiseled_tuff_bricks", "minecraft:chiseled_tuff",
            "minecraft:tuff_stairs", "minecraft:tuff_slab", "minecraft:tuff_wall",
            "minecraft:polished_tuff_stairs", "minecraft:polished_tuff_slab", "minecraft:polished_tuff_wall",
            "minecraft:tuff_brick_stairs", "minecraft:tuff_brick_slab", "minecraft:tuff_brick_wall");

    private static JsonObject json(Path path) throws IOException {
        return JsonParser.parseString(Files.readString(path)).getAsJsonObject();
    }

    private static List<Path> files(Path dir) throws IOException {
        try (Stream<Path> walk = Files.walk(dir)) {
            return walk.filter(p -> p.toString().endsWith(".json")).sorted().toList();
        }
    }

    /** Every 1.20.1 item id (the Reclamation registry dump holds all of vanilla's). */
    private static Set<String> vanillaItems() throws IOException {
        Set<String> ids = new HashSet<>();
        for (String line : Files.readAllLines(Path.of("packs/reclamation/item-ids.txt"))) {
            if (line.startsWith("minecraft:")) {
                ids.add(line.trim());
            }
        }
        return ids;
    }

    @Test
    void everyHearthTagIsCopiedWith121OnlyIdsOptional() throws IOException {
        String[][] dirs = {{"data/proficiency/tags/block/hearth", "data/proficiency/tags/blocks/hearth"},
                {"data/proficiency/tags/block/comfort", "data/proficiency/tags/blocks/comfort"},
                {"data/proficiency/tags/entity_type/comfort", "data/proficiency/tags/entity_types/comfort"}};
        int checked = 0;
        for (String[] dir : dirs) {
            Path from = COMMON.resolve(dir[0]);
            for (Path common : files(from)) {
                Path forge = FORGE.resolve(dir[1]).resolve(from.relativize(common).toString());
                assertTrue(Files.exists(forge), "missing on Forge 1.20.1: " + forge);
                JsonObject a = json(common);
                JsonObject b = json(forge);
                assertEquals(a.get("replace"), b.get("replace"), "replace in " + forge);
                JsonArray va = a.getAsJsonArray("values");
                JsonArray vb = b.getAsJsonArray("values");
                assertEquals(va.size(), vb.size(), "entries in " + forge);
                for (int i = 0; i < va.size(); i++) {
                    JsonElement x = va.get(i);
                    JsonElement y = vb.get(i);
                    if (x.isJsonPrimitive() && ONLY_121.contains(x.getAsString())) {
                        assertTrue(y.isJsonObject() && y.getAsJsonObject().get("id").getAsString().equals(x.getAsString())
                                && !y.getAsJsonObject().get("required").getAsBoolean(),
                                x + " is 1.21-only: optional in " + forge + ", got " + y);
                    } else if (x.isJsonObject() && x.getAsJsonObject().get("id").getAsString().startsWith("#c:")) {
                        // 1.21's common tags (#c:chests) are #forge: tags on 1.20.1, optional on both.
                        String id = "#forge:" + x.getAsJsonObject().get("id").getAsString().substring(3);
                        assertTrue(y.isJsonObject() && y.getAsJsonObject().get("id").getAsString().equals(id)
                                && !y.getAsJsonObject().get("required").getAsBoolean(),
                                x + " is " + id + " on 1.20.1, optional, in " + forge + ", got " + y);
                    } else {
                        assertEquals(x, y, "entry " + i + " of " + forge);
                    }
                }
                checked++;
            }
        }
        assertTrue(checked >= 36, "the Hearth and Comfort tags (36 with the dressing roles): " + checked);
        // Nothing left behind under 1.21's singular folder names, which 1.20.1 does not read.
        assertFalse(Files.exists(FORGE.resolve("data/proficiency/tags/block")), "tags/block is 1.21's name");
        assertFalse(Files.exists(FORGE.resolve("data/proficiency/tags/entity_type")), "tags/entity_type is 1.21's name");
    }

    @Test
    void everyRequiredVanillaIdExistsOn1201() throws IOException {
        Set<String> items = vanillaItems();
        assertTrue(items.size() > 1000, "the dump holds vanilla: " + items.size());
        List<String> unknown = new ArrayList<>();
        for (String dir : List.of("data/proficiency/tags/blocks/hearth", "data/proficiency/tags/blocks/comfort",
                "data/proficiency/tags/entity_types/comfort")) {
            for (Path file : files(FORGE.resolve(dir))) {
                for (JsonElement entry : json(file).getAsJsonArray("values")) {
                    if (!entry.isJsonPrimitive()) {
                        continue;
                    }
                    String id = entry.getAsString();
                    // Tags, the mod's own ids and wall heads (blocks without an item of their own) aside.
                    if (id.startsWith("#") || id.startsWith("proficiency:") || id.contains("_wall_")) {
                        continue;
                    }
                    if (dir.contains("entity_types")) {
                        continue;
                    }
                    if (!items.contains(id)) {
                        unknown.add(file.getFileName() + ": " + id);
                    }
                }
            }
        }
        assertTrue(unknown.isEmpty(), "not a 1.20.1 id (the whole tag would fail): " + unknown);
    }

    /**
     * Review of the Forge foundation (2026-10-09): only the sneak-hold breaks a Hearth, so the Wither (its
     * charge and its blue skulls) and the dragon must not, on either line.
     */
    @Test
    void theWitherAndTheDragonCannotBreakTheHearthOnEitherLine() throws IOException {
        for (String tag : List.of("wither_immune", "dragon_immune")) {
            JsonObject forge = json(FORGE.resolve("data/minecraft/tags/blocks/" + tag + ".json"));
            JsonObject common = json(COMMON.resolve("data/minecraft/tags/block/" + tag + ".json"));
            assertEquals(common, forge, tag + ": the Forge copy is common's");
            assertFalse(forge.get("replace").getAsBoolean(), tag + " adds to vanilla's list");
            assertTrue(forge.getAsJsonArray("values").toString().contains("\"proficiency:hearth\""), tag);
        }
    }

    @Test
    void theLootTablesRecipeAndAdvancementExistIn1201Layout() throws IOException {
        JsonObject hearth = json(FORGE.resolve("data/proficiency/loot_tables/blocks/hearth.json"));
        assertEquals("minecraft:campfire", hearth.getAsJsonArray("pools").get(0).getAsJsonObject()
                .getAsJsonArray("entries").get(0).getAsJsonObject().get("name").getAsString());
        JsonObject trophy = json(FORGE.resolve("data/proficiency/loot_tables/blocks/siege_trophy.json"));
        assertTrue(trophy.toString().contains("minecraft:copy_state") && trophy.toString().contains("\"tier\""),
                "the trophy keeps its tier");
        JsonObject recipe = json(FORGE.resolve("data/proficiency/recipes/siege_horn.json"));
        assertEquals("proficiency:siege_horn", recipe.getAsJsonObject("result").get("item").getAsString(),
                "1.20.1 recipe results name the item with \"item\"");
        assertTrue(Files.exists(FORGE.resolve("data/proficiency/advancements/recipes/combat/siege_horn.json")));
        for (int level = 2; level <= 5; level++) {
            String horn = "siege_horn_" + level;
            JsonObject upgrade = json(FORGE.resolve("data/proficiency/recipes/" + horn + ".json"));
            assertEquals("proficiency:" + horn, upgrade.getAsJsonObject("result").get("item").getAsString(),
                    horn + " results name the item with \"item\"");
            assertTrue(Files.exists(FORGE.resolve("data/proficiency/advancements/recipes/combat/" + horn + ".json")),
                    horn + " has its unlock advancement");
            assertTrue(Files.exists(COMMON.resolve("assets/proficiency/models/item/" + horn + ".json")),
                    horn + " has its item model");
        }
    }

    @Test
    void theBuildLaysInTheHearthDataAndAssetsUnchanged() throws IOException {
        // One default file for every line: the Forge build copies common's (no hand copy to drift).
        assertFalse(Files.exists(FORGE.resolve("data/proficiency/proficiency/hearth")),
                "the Hearth defaults come from common, not a copy here");
        Path defaults = Path.of("data/proficiency/proficiency/hearth/default.json");
        assertEquals(Files.readString(COMMON.resolve(defaults)), Files.readString(BUILT.resolve(defaults)));
        for (String asset : List.of("assets/proficiency/blockstates/hearth.json",
                "assets/proficiency/blockstates/siege_trophy.json",
                "assets/proficiency/models/item/siege_horn.json", "assets/proficiency/models/item/siege_trophy.json",
                "assets/proficiency/models/block/hearth_stage_6_smouldering.json",
                "assets/proficiency/models/block/siege_trophy_tier_6.json",
                "assets/proficiency/textures/gui/hearth/pool/hearty.png", "assets/minecraft/font/default.json")) {
            assertTrue(Files.exists(BUILT.resolve(asset)), "the jar should carry " + asset);
        }
        // The Hearth's models ask for cutout through render_type, which Forge 1.20.1 reads.
        assertTrue(Files.readString(BUILT.resolve("assets/proficiency/models/block/hearth_stage_1.json"))
                .contains("\"render_type\": \"minecraft:cutout\""));
        // The font carries the Hearth's glyphs (U+E010..U+E01E).
        String font = Files.readString(BUILT.resolve("assets/minecraft/font/default.json"));
        assertTrue(font.contains("\\ue010") || font.contains(""), "the Warmth glyphs");
    }
}
