package dev.amman.proficiency;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.stream.JsonReader;
import com.google.gson.stream.JsonToken;
import java.io.IOException;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;

/**
 * Minecraft keeps the last of two equal keys, so a repeated key silently replaces the earlier text.
 * A plain JSON parse hides that, so this reads the names one by one.
 */
class LangFilesTest {

    private static final Path LANG = Path.of("src/main/resources/assets/proficiency/lang");

    @Test
    void noLangFileRepeatsAKey() throws IOException {
        List<Path> files;
        try (Stream<Path> list = Files.list(LANG)) {
            files = list.filter(p -> p.toString().endsWith(".json")).sorted().toList();
        }
        assertFalse(files.isEmpty());
        List<String> problems = new ArrayList<>();
        for (Path file : files) {
            Set<String> seen = new HashSet<>();
            try (Reader reader = Files.newBufferedReader(file, StandardCharsets.UTF_8);
                    JsonReader json = new JsonReader(reader)) {
                json.beginObject();
                while (json.peek() != JsonToken.END_OBJECT) {
                    String name = json.nextName();
                    if (!seen.add(name)) {
                        problems.add(file.getFileName() + ": " + name);
                    }
                    json.skipValue();
                }
            }
        }
        assertTrue(problems.isEmpty(), "duplicate lang keys: " + problems);
    }

    @Test
    void noEmDashInPlayerText() throws IOException {
        try (Stream<Path> list = Files.list(LANG)) {
            for (Path file : list.filter(p -> p.toString().endsWith(".json")).toList()) {
                String text = Files.readString(file);
                for (String key : new String[] {"proficiency.tooltip.skill", "proficiency.tooltip.ability",
                        "proficiency.tooltip.hold_shift", "proficiency.configuration.tooltip",
                        "proficiency.configuration.banners.reveal"}) {
                    for (String line : text.split("\n")) {
                        if (line.contains("\"" + key)) {
                            assertFalse(line.contains("—"), file.getFileName() + ": " + line);
                        }
                    }
                }
            }
        }
    }
}
