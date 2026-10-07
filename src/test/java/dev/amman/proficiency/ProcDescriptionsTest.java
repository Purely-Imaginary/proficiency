package dev.amman.proficiency;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.amman.proficiency.skill.Skill;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Every skill explains its signature move in both languages. A new skill must add its line. */
class ProcDescriptionsTest {

    private static JsonObject lang(String file) throws Exception {
        return JsonParser.parseString(Files.readString(
                Path.of("src/main/resources/assets/proficiency/lang/" + file))).getAsJsonObject();
    }

    @Test
    void everySkillHasAProcDescriptionInBothLanguages() throws Exception {
        for (String file : new String[] {"en_us.json", "pl_pl.json"}) {
            JsonObject lang = lang(file);
            for (Skill skill : Skill.values()) {
                String key = skill.procDescKey();
                assertTrue(lang.has(key), file + " is missing " + key);
                assertFalse(lang.get(key).getAsString().isBlank(), file + " has an empty " + key);
                assertTrue(lang.has(skill.procKey()), file + " is missing " + skill.procKey());
            }
        }
    }
}
