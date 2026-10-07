package dev.amman.proficiency;

import dev.amman.proficiency.perk.Synergies;
import dev.amman.proficiency.perk.Synergy;
import dev.amman.proficiency.perk.Talent;
import dev.amman.proficiency.perk.Talents;
import dev.amman.proficiency.skill.Skill;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * A special tag that no code reads is a node that takes your points and does nothing. The first
 * version shipped three of those. This reads the main sources and fails on any tag, on a talent or
 * a synergy, that appears nowhere outside the tables that declare it.
 */
class SpecialsWiredTest {

    private static final Set<String> DECLARING = Set.of("TalentTable.java", "Synergies.java");

    @Test
    void everySpecialIsReadSomewhere() throws IOException {
        StringBuilder code = new StringBuilder();
        try (Stream<Path> files = Files.walk(Path.of("src/main/java"))) {
            for (Path file : files.filter(p -> p.toString().endsWith(".java")).toList()) {
                if (!DECLARING.contains(file.getFileName().toString())) {
                    code.append(Files.readString(file));
                }
            }
        }
        Set<String> tags = new TreeSet<>();
        for (Skill skill : Skill.VALUES) {
            for (Talent talent : Talents.of(skill)) {
                if (talent.special() != null) {
                    tags.add(talent.special());
                }
            }
        }
        for (Synergy synergy : Synergies.all()) {
            if (synergy.special() != null) {
                tags.add(synergy.special());
            }
        }
        List<String> dead = new ArrayList<>();
        for (String tag : tags) {
            if (!code.toString().contains("\"" + tag + "\"")) {
                dead.add(tag);
            }
        }
        assertTrue(dead.isEmpty(), dead.size() + " specials are read by nothing: " + dead);
    }
}
