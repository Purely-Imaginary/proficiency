package dev.amman.proficiency.xp;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.amman.proficiency.skill.Skill;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;

/** The Reclamation datapack loads on top of the shipped defaults and says what its README says. */
class ReclamationXpDatapackTest {

    private static final Path PACK = Path.of("packs/reclamation/datapack");
    private static final Path FILE = PACK.resolve("data/reclamation/proficiency/xp_sources/reclamation.json");

    private static XpSourcesLoader.Result load() throws Exception {
        return XpSourcesLoader.load(List.of(
                new XpSourcesLoader.SourceFile("proficiency:xp_sources/defaults", XpSources.readDefaults()),
                new XpSourcesLoader.SourceFile("reclamation:xp_sources/reclamation", Files.readString(FILE))),
                XpSourcesLoader.Registries.ANY);
    }

    @Test
    void loadsCleanAndTheMetaIsForThisGameVersion() throws Exception {
        XpSourcesLoader.Result result = load();
        assertTrue(result.messages.isEmpty(), "reported: " + result.messages);
        assertEquals(2, result.files);
        JsonObject meta = JsonParser.parseString(Files.readString(PACK.resolve("pack.mcmeta"))).getAsJsonObject();
        assertEquals(15, meta.getAsJsonObject("pack").get("pack_format").getAsInt(), "1.20.1 data packs are format 15");
    }

    @Test
    void everyBlockIdIsInThePackRegistryDump() throws Exception {
        Set<String> ids = new HashSet<>(Files.readAllLines(Path.of("packs/reclamation/item-ids.txt")));
        JsonObject root = JsonParser.parseString(Files.readString(FILE)).getAsJsonObject();
        int seen = 0;
        for (JsonElement rule : root.getAsJsonArray("break")) {
            for (JsonElement match : rule.getAsJsonObject().getAsJsonArray("match")) {
                assertTrue(ids.contains(match.getAsString()), match.getAsString() + " is not a pack id");
                seen++;
            }
        }
        assertEquals(26, seen);
    }

    @Test
    void behavesAsDescribed() throws Exception {
        XpTable table = load().table;
        XpMatch fruit = table.match(XpDomain.BREAK, TestSubject.block("croptopia:apple_crop", 0.2,
                "minecraft:leaves"));
        assertEquals(Skill.FARMING, fruit.rule().skill);
        assertTrue(fruit.has("crop"));
        // A plain leaf block is still Woodcutting.
        assertEquals(Skill.WOODCUTTING, table.match(XpDomain.BREAK, TestSubject.block("minecraft:oak_leaves", 0.2,
                "minecraft:leaves")).rule().skill);
        assertTrue(table.match(XpDomain.KILL, TestSubject.entity("ars_nouveau:summon_wolf", 20)).paysNothing());
        assertEquals(Skill.BEASTSLAYING, table.match(XpDomain.KILL,
                TestSubject.entity("ars_nouveau:wilden_hunter", 30)).rule().skill);
        assertEquals(Boolean.TRUE, table.match(XpDomain.BOSS,
                TestSubject.entity("botania:doppleganger", 400)).rule().boss);
        assertEquals(10.0, table.match(XpDomain.FIRST_TIME,
                TestSubject.entity("botania:doppleganger", 400)).rule().multiplier);
    }
}
