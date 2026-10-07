package dev.amman.proficiency;

import dev.amman.proficiency.perk.MaterialOverrides;
import dev.amman.proficiency.perk.Requirement;
import dev.amman.proficiency.perk.Talents;
import dev.amman.proficiency.skill.Skill;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The override shipped in the Reclamation pack names only items that pack registers, and once it
 * is applied no requirement in any tree silently disappears. The id list is a dump of the real
 * registry from the pack (PROFICIENCY_DUMP_ITEMS), cut to the namespaces the trees use.
 */
class ReclamationMaterialsTest {

    private static final Path OVERRIDE = Path.of("packs/reclamation/config/" + MaterialOverrides.FILE_NAME);
    private static final Path ITEM_IDS = Path.of("packs/reclamation/item-ids.txt");

    @AfterEach
    void backToDefaults() {
        Talents.resetMaterials();
    }

    private static MaterialOverrides.Parsed parsed() throws IOException {
        try (Reader reader = Files.newBufferedReader(OVERRIDE, StandardCharsets.UTF_8)) {
            return MaterialOverrides.parse(reader);
        }
    }

    private static Set<String> packItems() throws IOException {
        Set<String> ids = new HashSet<>();
        for (String line : Files.readAllLines(ITEM_IDS, StandardCharsets.UTF_8)) {
            if (!line.isBlank() && !line.startsWith("#")) {
                ids.add(line.trim());
            }
        }
        assertTrue(ids.size() > 1000, "item id list looks truncated: " + ids.size());
        return ids;
    }

    @Test
    void theOverrideParsesCleanly() throws IOException {
        MaterialOverrides.Parsed parsed = parsed();
        assertTrue(parsed.errors().isEmpty(), parsed.errors().toString());
        assertTrue(parsed.tierCount() > 0);
    }

    @Test
    void everyOverrideIdExistsInThePack() throws IOException {
        Set<String> pack = packItems();
        List<String> unknown = parsed().itemIds().stream().map(Object::toString)
                .filter(id -> !pack.contains(id)).toList();
        assertTrue(unknown.isEmpty(), "not in the Reclamation registry: " + unknown);
    }

    /** With the override on, every requirement of every tree is something the pack has. */
    @Test
    void noRequirementIsSilentlySkippedInReclamation() throws IOException {
        Set<String> pack = packItems();
        Talents.applyMaterialOverrides(parsed().tiers());
        List<String> missing = new ArrayList<>();
        for (Skill skill : Skill.VALUES) {
            for (int tier = 1; tier <= Talents.TIERS; tier++) {
                for (Requirement requirement : Talents.tierMaterials(skill, tier)) {
                    if (requirement.tag() == null && !pack.contains(requirement.itemId().toString())) {
                        missing.add(skill.id() + "/" + tier + " " + requirement.itemId());
                    }
                }
            }
        }
        assertTrue(missing.isEmpty(), "requirements Reclamation cannot show: " + missing);
    }
}
