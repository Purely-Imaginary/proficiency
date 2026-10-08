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
                        "proficiency.tooltip.details", "proficiency.stat", "proficiency.configuration.tooltip",
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

    /** Every key the tooltips added is present in every language, so no player reads a raw key. */
    @Test
    void everyLanguageHasTheTooltipKeys() throws IOException {
        com.google.gson.JsonObject en;
        try (Reader reader = Files.newBufferedReader(LANG.resolve("en_us.json"), StandardCharsets.UTF_8)) {
            en = com.google.gson.JsonParser.parseReader(reader).getAsJsonObject();
        }
        List<String> wanted = new ArrayList<>();
        for (String key : en.keySet()) {
            if (key.startsWith("proficiency.stat.") || key.endsWith(".short")
                    || key.equals("proficiency.tooltip.details") || key.equals("proficiency.tree.click.short")
                    || key.equals("proficiency.tree.materials.short") || key.startsWith("proficiency.synergy.state.")
                    || key.startsWith("proficiency.configuration.tooltip.alwaysDetailed")) {
                wanted.add(key);
            }
        }
        assertFalse(wanted.isEmpty());
        List<String> missing = new ArrayList<>();
        try (Stream<Path> list = Files.list(LANG)) {
            for (Path file : list.filter(p -> p.toString().endsWith(".json")).sorted().toList()) {
                com.google.gson.JsonObject lang;
                try (Reader reader = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
                    lang = com.google.gson.JsonParser.parseReader(reader).getAsJsonObject();
                }
                for (String key : wanted) {
                    if (!lang.has(key)) {
                        missing.add(file.getFileName() + ": " + key);
                    }
                }
            }
        }
        assertTrue(missing.isEmpty(), "missing tooltip keys: " + missing.subList(0, Math.min(10, missing.size())));
    }
}
