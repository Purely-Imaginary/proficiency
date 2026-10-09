package dev.amman.proficiency.xp;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

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

/** The Pandowo datapack loads on top of the shipped defaults and does what its README says. */
class PandowoXpDatapackTest {

    private static final Path PACK = Path.of(System.getProperty("proficiency.pandowoDir", "../neoforge/packs/pandowo"));
    private static final Path FILE = PACK.resolve("datapack/data/pandowo/proficiency/xp_sources/pandowo.json");

    private static XpSourcesLoader.Result load() throws Exception {
        return XpSourcesLoader.load(List.of(
                new XpSourcesLoader.SourceFile("proficiency:xp_sources/defaults", XpSources.readDefaults()),
                new XpSourcesLoader.SourceFile("pandowo:xp_sources/pandowo", Files.readString(FILE))),
                XpSourcesLoader.Registries.ANY);
    }

    private static Set<String> lines(String name) throws Exception {
        return new HashSet<>(Files.readAllLines(PACK.resolve(name)));
    }

    @Test
    void loadsCleanAndTheMetaIsForThisGameVersion() throws Exception {
        XpSourcesLoader.Result result = load();
        assertTrue(result.messages.isEmpty(), "reported: " + result.messages);
        assertEquals(2, result.files);
        JsonObject meta = JsonParser.parseString(Files.readString(PACK.resolve("datapack/pack.mcmeta"))).getAsJsonObject();
        assertEquals(48, meta.getAsJsonObject("pack").get("pack_format").getAsInt(), "1.21.1 data packs are format 48");
    }

    @Test
    void everyIdIsOneTheJarsRegister() throws Exception {
        Set<String> entities = lines("entity-ids.txt");
        Set<String> blocks = lines("block-ids.txt");
        JsonObject root = JsonParser.parseString(Files.readString(FILE)).getAsJsonObject();
        int seen = 0;
        for (String domain : List.of("break", "kill", "kill_bonus", "boss", "first_time")) {
            for (JsonElement rule : root.getAsJsonArray(domain)) {
                for (JsonElement match : rule.getAsJsonObject().getAsJsonArray("match")) {
                    String id = match.getAsString();
                    if (id.endsWith(":*")) {
                        continue;
                    }
                    assertFalse(id.startsWith("#"), "this pack names ids, not tags: " + id);
                    boolean ok = domain.equals("break") ? blocks.contains(id) : entities.contains(id);
                    assertTrue(ok, domain + " names " + id + " which no Pandowo jar registers");
                    seen++;
                }
            }
        }
        assertTrue(seen > 300, "looked at " + seen);
    }

    @Test
    void summonsLivestockTradersAndGolemsPayNothing() throws Exception {
        XpTable table = load().table;
        for (String id : List.of("ars_nouveau:summon_wolf", "ars_nouveau:ally_vex", "ars_nouveau:starbuncle",
                "aether:flying_cow", "aether:phyg", "aether:sheepuff", "aether:moa", "aether:aerbunny",
                "friendsandfoes:copper_golem", "friendsandfoes:tuff_golem", "friendsandfoes:moobloom",
                "goblintraders:goblin_trader", "supplementaries:red_merchant", "dmr:dragon",
                "twilightforest:boar", "twilightforest:loyal_zombie", "dummmmmmy:target_dummy",
                "mowziesmobs:umvuthana_follower_player")) {
            XpMatch m = table.match(XpDomain.KILL, TestSubject.entity(id, 20));
            assertNotNull(m, id);
            assertTrue(m.paysNothing(), id + " pays nothing");
        }
        // Nor does the weapon skill's kill bonus (and the first-time bonus that rides on it): a free
        // summon would still farm Swordsmanship otherwise.
        for (String id : List.of("ars_nouveau:summon_wolf", "aether:phyg", "friendsandfoes:copper_golem",
                "goblintraders:goblin_trader", "dummmmmmy:target_dummy", "dmr:dragon")) {
            XpMatch bonus = table.match(XpDomain.KILL_BONUS, TestSubject.entity(id, 20));
            assertNotNull(bonus, id + " has a kill bonus rule");
            assertTrue(bonus.paysNothing(), id + " pays no kill bonus");
        }
        assertNull(table.match(XpDomain.KILL_BONUS, TestSubject.entity("aether:valkyrie", 20)), "wild mobs keep the formula");
        // The wild, hostile ones still pay, and so does an unlisted modded mob.
        for (String id : List.of("aether:valkyrie", "aether:zephyr", "twilightforest:hydra", "cataclysm:ignis",
                "mowziesmobs:foliaath", "modx:troll")) {
            XpMatch m = table.match(XpDomain.KILL, TestSubject.entity(id, 20));
            assertEquals(Skill.BEASTSLAYING, m.rule().skill, id);
            assertEquals(6.0, m.xp(), id);
        }
        assertNull(table.match(XpDomain.KILL, TestSubject.entity("minecraft:zombie", 20)));
    }

    @Test
    void bossesTheMissingOnesAndNoSegments() throws Exception {
        XpTable table = load().table;
        for (String id : List.of("cataclysm:the_harbinger", "cataclysm:maledictus", "cataclysm:scylla",
                "mowziesmobs:sculptor", "twilightforest:plateau_boss", "deep_aether:eots_controller")) {
            assertEquals(Boolean.TRUE, table.match(XpDomain.BOSS, TestSubject.entity(id, 100)).rule().boss, id);
            assertEquals(10.0, table.match(XpDomain.FIRST_TIME, TestSubject.entity(id, 100)).rule().multiplier, id);
        }
        // The segments are in the boss tags, and the exact id still wins over the tag.
        TestSubject segment = TestSubject.entity("deep_aether:eots_segment", 100, "c:bosses");
        assertEquals(Boolean.FALSE, table.match(XpDomain.BOSS, segment).rule().boss);
        assertEquals(1.0, table.match(XpDomain.FIRST_TIME, segment).rule().multiplier, 0.0);
        assertTrue(table.match(XpDomain.KILL_BONUS, segment).paysNothing());
        assertTrue(table.match(XpDomain.KILL, segment).paysNothing());
        // Through the tags alone a boss from a mod we never heard of counts, and a segment does not.
        assertEquals(Boolean.TRUE, table.match(XpDomain.BOSS,
                TestSubject.entity("newmod:colossus", 100, "neoforge:bosses")).rule().boss);
    }

    @Test
    void furnitureAndMachinesInTheAxeTagPayNothingWhenBroken() throws Exception {
        XpTable table = load().table;
        for (String id : List.of("handcrafted:oak_table", "another_furniture:oak_chair", "create:andesite_encased_shaft",
                "farmersdelight:cutting_board", "farmersdelight:oak_cabinet", "functionalstorage:oak_1",
                "toms_storage:storage_terminal", "twilightdelight:canopy_cabinet")) {
            XpMatch m = table.match(XpDomain.BREAK, TestSubject.block(id, 2.0, "minecraft:mineable/axe"));
            assertNotNull(m, id);
            assertTrue(m.paysNothing(), id + " pays nothing");
        }
        // A block in both the axe and the pickaxe tag still pays Mining when mined, and Excavation likewise.
        XpMatch both = table.match(XpDomain.BREAK, TestSubject.block("create:andesite_casing", 2.0,
                "minecraft:mineable/axe", "minecraft:mineable/pickaxe"));
        assertEquals(Skill.MINING, both.rule().skill);
        // Real wood and the crops next to the furniture still pay.
        XpMatch log = table.match(XpDomain.BREAK, TestSubject.block("aether:skyroot_log", 2.0, "minecraft:logs"));
        assertEquals(Skill.WOODCUTTING, log.rule().skill);
        XpMatch tomato = table.match(XpDomain.BREAK, TestSubject.block("farmersdelight:tomatoes", 0.0, "minecraft:crops"));
        assertEquals(Skill.FARMING, tomato.rule().skill);
        XpMatch plain = table.match(XpDomain.BREAK, TestSubject.block("modx:chest", 2.0, "minecraft:mineable/axe"));
        assertEquals(Skill.WOODCUTTING, plain.rule().skill, "a modded block we did not list is untouched");
    }
}
