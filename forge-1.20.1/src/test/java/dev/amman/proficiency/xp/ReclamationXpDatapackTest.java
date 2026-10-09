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
        // agricraft:crop is a block with no item of its own (the dump holds items), so it is named here.
        ids.add("agricraft:crop");
        // The furniture and machine blocks are named from the jars' blockstates (many have no item).
        ids.addAll(Files.readAllLines(Path.of("packs/reclamation/block-ids.txt")));
        int seen = 0;
        for (String domain : List.of("break", "place")) {
            for (JsonElement rule : root.getAsJsonArray(domain)) {
                for (JsonElement match : rule.getAsJsonObject().getAsJsonArray("match")) {
                    assertTrue(ids.contains(match.getAsString()), match.getAsString() + " is not a pack id");
                    seen++;
                }
            }
        }
        assertTrue(seen > 280, "looked at " + seen);
    }

    @Test
    void agriCraftCropPaysWhatWheatPays() throws Exception {
        XpTable table = load().table;
        XpMatch harvest = table.match(XpDomain.BREAK, TestSubject.block("agricraft:crop", 0.0));
        assertEquals(Skill.FARMING, harvest.rule().skill);
        assertTrue(harvest.has("crop"));
        XpMatch wheat = table.match(XpDomain.BREAK, TestSubject.block("minecraft:wheat", 0.0, "minecraft:crops"));
        assertEquals(wheat.xp(), harvest.xp());
        XpMatch plant = table.match(XpDomain.PLACE, TestSubject.block("agricraft:crop", 0.0));
        assertEquals(Skill.FARMING, plant.rule().skill);
        assertTrue(plant.has("planting"));
        XpMatch plantWheat = table.match(XpDomain.PLACE, TestSubject.block("minecraft:wheat", 0.0, "minecraft:crops"));
        assertEquals(plantWheat.xp(), plant.xp());
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

    @Test
    void furnitureAndMachinesInTheAxeTagPayNothingWhenBroken() throws Exception {
        XpTable table = load().table;
        for (String id : List.of("create:andesite_encased_shaft", "create:belt", "storagedrawers:oak_full_drawers_1",
                "farmersdelight:cutting_board", "botania:bellows", "naturesaura:auto_crafter")) {
            XpMatch m = table.match(XpDomain.BREAK, TestSubject.block(id, 2.0, "minecraft:mineable/axe"));
            assertTrue(m != null && m.paysNothing(), id + " pays nothing");
        }
        XpMatch both = table.match(XpDomain.BREAK, TestSubject.block("create:andesite_casing", 2.0,
                "minecraft:mineable/axe", "minecraft:mineable/pickaxe"));
        assertEquals(Skill.MINING, both.rule().skill, "a pickaxe block in both tags still pays Mining");
        XpMatch log = table.match(XpDomain.BREAK, TestSubject.block("botania:livingwood_log", 2.0, "minecraft:logs"));
        assertEquals(Skill.WOODCUTTING, log.rule().skill);
        XpMatch other = table.match(XpDomain.BREAK, TestSubject.block("quark:oak_chest", 2.0, "minecraft:mineable/axe"));
        assertEquals(Skill.WOODCUTTING, other.rule().skill, "a block we did not list is untouched");
    }
}
