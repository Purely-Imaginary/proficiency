package dev.amman.proficiency;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

/**
 * Lang keys that only en_us has on purpose (docs/english-only/*.txt, one substring per line). The
 * tests that insist on a key in every language skip these; the game falls back to English.
 */
final class EnglishOnly {

    private static final List<String> PARTS = load();

    private EnglishOnly() {
    }

    static boolean is(String key) {
        for (String part : PARTS) {
            if (key.contains(part)) {
                return true;
            }
        }
        return false;
    }

    private static List<String> load() {
        List<String> parts = new ArrayList<>();
        Path dir = Path.of("..", "docs", "english-only");
        if (!Files.isDirectory(dir)) {
            return parts;
        }
        try (Stream<Path> files = Files.list(dir)) {
            for (Path file : files.filter(p -> p.toString().endsWith(".txt")).toList()) {
                for (String line : Files.readAllLines(file)) {
                    String trimmed = line.trim();
                    if (!trimmed.isEmpty() && !trimmed.startsWith("#")) {
                        parts.add(trimmed);
                    }
                }
            }
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
        return parts;
    }
}
