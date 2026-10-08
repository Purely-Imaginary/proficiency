package dev.amman.proficiency;

import java.nio.file.Path;

/** Files the pure tests read from the shared resources; the build passes their location. */
final class TestPaths {

    /** The shared lang files, {@code common/src/main/resources/assets/proficiency/lang}. */
    static final Path LANG = Path.of(System.getProperty("proficiency.langDir",
            "../common/src/main/resources/assets/proficiency/lang"));

    private TestPaths() {
    }
}
