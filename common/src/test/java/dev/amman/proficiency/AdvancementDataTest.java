package dev.amman.proficiency;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.amman.proficiency.skill.Achievements;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The generated advancement files match the ids the code awards, and every title has English text. */
class AdvancementDataTest {

    private static Path resources() {
        for (Path p = Path.of("").toAbsolutePath(); p != null; p = p.getParent()) {
            Path found = p.resolve("common/src/main/resources");
            if (Files.isDirectory(found)) {
                return found;
            }
            Path here = p.resolve("src/main/resources");
            if (Files.isDirectory(here) && p.getFileName().toString().equals("common")) {
                return here;
            }
        }
        throw new IllegalStateException("common resources not found");
    }

    private static Path forgeResources() {
        Path root = resources().getParent().getParent().getParent().getParent(); // repo root
        return root.resolve("forge-1.20.1/src/main/resources");
    }

    @Test
    void everyIdHasAFileAndEnglishText() throws IOException {
        JsonObject lang = JsonParser.parseString(Files.readString(
                resources().resolve("assets/proficiency/lang/en_us.json"), StandardCharsets.UTF_8)).getAsJsonObject();
        Set<String> ids = Achievements.all();
        // 1.21.1 (NeoForge + Fabric) uses "advancement" and icon {"id"}; Forge 1.20.1 uses "advancements" and {"item"}.
        check(resources().resolve("data/proficiency/advancement"), "id", ids, lang);
        check(forgeResources().resolve("data/proficiency/advancements"), "item", ids, lang);
    }

    private static void check(Path dir, String iconKey, Set<String> ids, JsonObject lang) throws IOException {
        Set<String> files;
        try (Stream<Path> list = Files.list(dir)) {
            files = list.map(p -> p.getFileName().toString()).filter(n -> n.endsWith(".json"))
                    .map(n -> n.substring(0, n.length() - 5)).collect(java.util.stream.Collectors.toSet());
        }
        assertEquals(ids, files, dir.toString());
        assertLayout(dir, ids);
        for (String id : ids) {
            JsonObject json = JsonParser.parseString(Files.readString(dir.resolve(id + ".json"),
                    StandardCharsets.UTF_8)).getAsJsonObject();
            assertTrue(json.has("criteria") && json.getAsJsonObject("criteria").has("event"), id);
            if (!id.equals("root")) {
                String parent = json.get("parent").getAsString().replace("proficiency:", "");
                assertTrue(ids.contains(parent), id + " parent " + parent);
            }
            JsonObject icon = json.getAsJsonObject("display").getAsJsonObject("icon");
            assertTrue(icon.has(iconKey), id + " icon key " + iconKey);
            String item = icon.get(iconKey).getAsString();
            if (item.startsWith("proficiency:")) {
                // A mod item icon needs its item model, or the tab shows a missing texture.
                assertTrue(Files.exists(resources().resolve("assets/proficiency/models/item/"
                        + item.substring("proficiency:".length()) + ".json")), id + " icon " + item);
            } else {
                assertTrue(item.matches("minecraft:[a-z0-9_]+"), id + " icon " + item);
            }
            for (String part : List.of("title", "desc")) {
                assertTrue(lang.has("advancement.proficiency." + id + "." + part), id + " " + part);
            }
        }
    }

    /**
     * The tab must stay wide, not tall: vanilla stacks the children of one node in a column, so the root
     * keeps a handful of branch heads, every node reaches the root, and no node fans out wide.
     */
    private static void assertLayout(Path dir, Set<String> ids) throws IOException {
        java.util.Map<String, String> parent = new java.util.HashMap<>();
        java.util.Map<String, Integer> children = new java.util.HashMap<>();
        for (String id : ids) {
            JsonObject json = JsonParser.parseString(Files.readString(dir.resolve(id + ".json"),
                    StandardCharsets.UTF_8)).getAsJsonObject();
            if (json.has("parent")) {
                String p = json.get("parent").getAsString().replace("proficiency:", "");
                parent.put(id, p);
                children.merge(p, 1, Integer::sum);
            }
        }
        assertEquals(ids.size() - 1, parent.size(), "only the root has no parent");
        for (String id : ids) {
            String cur = id;
            int hops = 0;
            while (parent.containsKey(cur)) {
                cur = parent.get(cur);
                assertTrue(++hops <= ids.size(), "cycle at " + id);
            }
            assertEquals("root", cur, id + " reaches the root");
        }
        int rootKids = children.getOrDefault("root", 0);
        assertTrue(rootKids >= 3 && rootKids <= 8, "root has " + rootKids + " children");
        for (var e : children.entrySet()) {
            assertTrue(e.getValue() <= (e.getKey().equals("root") ? 8 : 4), e.getKey() + " fans out to " + e.getValue());
        }
        assertEquals(java.util.List.of(), java.util.stream.Stream.of("level_combat_10", "level_gathering_10",
                "level_movement_10", "level_mastery_10", "first_star", "hearth_stage_1")
                .filter(h -> !"root".equals(parent.get(h))).toList(), "branch heads sit on the root");
    }
}
