package dev.amman.proficiency;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

/** The shipped no_combat_xp tag names the known target dummy and keeps it optional (the mod may be absent). */
class NoCombatXpTagTest {

    @Test
    void tagNamesTheTargetDummyAsOptional() throws IOException {
        Path dir = Path.of("src/main/resources/data/proficiency/tags");
        Path file = Files.exists(dir.resolve("entity_type/no_combat_xp.json"))
                ? dir.resolve("entity_type/no_combat_xp.json") : dir.resolve("entity_types/no_combat_xp.json");
        String json = Files.readString(file);
        assertTrue(json.contains("\"dummmmmmy:target_dummy\""));
        assertTrue(json.contains("\"required\": false"));
    }
}
