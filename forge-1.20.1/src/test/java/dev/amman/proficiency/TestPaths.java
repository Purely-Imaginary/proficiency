package dev.amman.proficiency;

import java.nio.file.Path;

/**
 * The lang files the tests read. This line's lang files are built: the shared ones with the
 * Forge 1.20.1 additions laid over them ({@code mergeLang}). The build passes where they are.
 */
final class TestPaths {

    static final Path LANG = Path.of(System.getProperty("proficiency.langDir",
            "build/generated/lang/assets/proficiency/lang"));

    private TestPaths() {
    }
}
