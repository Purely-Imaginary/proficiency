package dev.amman.proficiency.xp;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.amman.proficiency.skill.Skill;
import java.util.List;
import java.util.Random;
import org.junit.jupiter.api.Test;

class XpSourcesLoaderTest {

    private static XpSourcesLoader.Result load(String... texts) {
        return load(XpSourcesLoader.Registries.ANY, texts);
    }

    private static XpSourcesLoader.Result load(XpSourcesLoader.Registries registries, String... texts) {
        java.util.ArrayList<XpSourcesLoader.SourceFile> files = new java.util.ArrayList<>();
        for (int i = 0; i < texts.length; i++) {
            files.add(new XpSourcesLoader.SourceFile("test:file" + i, texts[i]));
        }
        return XpSourcesLoader.load(files, registries);
    }

    private static final TestSubject STONE = TestSubject.block("minecraft:stone", 1.5,
            "minecraft:mineable/pickaxe", "c:ores_in_ground");

    @Test
    void summaryNamesRulesAndFiles() {
        XpSourcesLoader.Result r = load(
                "{\"break\":[{\"match\":\"minecraft:stone\",\"skill\":\"mining\",\"xp\":1}]}",
                "{\"break\":[{\"match\":[\"minecraft:dirt\",\"minecraft:sand\"],\"skill\":\"excavation\",\"xp\":1}]}");
        assertEquals("Loaded 3 XP sources from 2 files", r.summary());
        assertEquals(3, r.sources);
    }

    @Test
    void laterFileOverridesEarlierRuleWithTheSameMatch() {
        XpSourcesLoader.Result r = load(
                "{\"break\":[{\"match\":\"minecraft:stone\",\"skill\":\"mining\",\"xp\":1}]}",
                "{\"break\":[{\"match\":\"minecraft:stone\",\"skill\":\"mining\",\"xp\":7}]}");
        assertEquals(1, r.sources);
        assertEquals(7.0, r.table.match(XpDomain.BREAK, STONE).xp());
    }

    @Test
    void laterFileWinsAtEqualRankEvenForDifferentMatches() {
        XpSourcesLoader.Result r = load(
                "{\"break\":[{\"match\":\"#minecraft:mineable/pickaxe\",\"skill\":\"mining\",\"xp\":1}]}",
                "{\"break\":[{\"match\":\"#c:ores_in_ground\",\"skill\":\"spelunking\",\"xp\":2}]}");
        XpMatch m = r.table.match(XpDomain.BREAK, STONE);
        assertEquals(Skill.SPELUNKING, m.rule().skill);
        // The other way round: the later of two equal tag rules still wins.
        r = load(
                "{\"break\":[{\"match\":\"#c:ores_in_ground\",\"skill\":\"spelunking\",\"xp\":2}]}",
                "{\"break\":[{\"match\":\"#minecraft:mineable/pickaxe\",\"skill\":\"mining\",\"xp\":1}]}");
        assertEquals(Skill.MINING, r.table.match(XpDomain.BREAK, STONE).rule().skill);
    }

    @Test
    void replaceTrueDropsEarlierRulesOfThatDomainOnly() {
        XpSourcesLoader.Result r = load(
                "{\"break\":[{\"match\":\"minecraft:stone\",\"skill\":\"mining\",\"xp\":1}],"
                        + "\"biome\":[{\"match\":\"*\",\"xp\":25}]}",
                "{\"replace\":true,\"break\":[{\"match\":\"minecraft:dirt\",\"skill\":\"excavation\",\"xp\":1}]}");
        assertNull(r.table.match(XpDomain.BREAK, STONE), "the stone rule was replaced away");
        assertNotNull(r.table.match(XpDomain.BIOME, TestSubject.plain("biome", "minecraft:plains")),
                "domains the file does not mention stay");
        assertEquals(2, r.sources);
    }

    @Test
    void idBeatsTagBeatsNamespaceBeatsAnyAtEqualPriority() {
        String json = "{\"break\":["
                + "{\"match\":\"*\",\"skill\":\"farming\",\"xp\":1},"
                + "{\"match\":\"minecraft:*\",\"skill\":\"woodcutting\",\"xp\":1},"
                + "{\"match\":\"#minecraft:mineable/pickaxe\",\"skill\":\"excavation\",\"xp\":1},"
                + "{\"match\":\"minecraft:stone\",\"skill\":\"mining\",\"xp\":1}]}";
        XpTable table = load(json).table;
        assertEquals(Skill.MINING, table.match(XpDomain.BREAK, STONE).rule().skill);
        XpTable noId = load(json.replace(",{\"match\":\"minecraft:stone\",\"skill\":\"mining\",\"xp\":1}", "")).table;
        assertEquals(Skill.EXCAVATION, noId.match(XpDomain.BREAK, STONE).rule().skill);
        TestSubject dirt = TestSubject.block("minecraft:dirt", 0.5);
        assertEquals(Skill.WOODCUTTING, noId.match(XpDomain.BREAK, dirt).rule().skill);
        assertEquals(Skill.FARMING, noId.match(XpDomain.BREAK, TestSubject.block("modx:ore", 1)).rule().skill);
    }

    @Test
    void priorityBeatsSpecificityAndNegativeLosesToDefault() {
        XpTable table = load("{\"break\":["
                + "{\"match\":\"minecraft:stone\",\"skill\":\"mining\",\"xp\":1,\"priority\":-5},"
                + "{\"match\":\"#minecraft:mineable/pickaxe\",\"skill\":\"excavation\",\"xp\":1,\"priority\":3}]}").table;
        assertEquals(Skill.EXCAVATION, table.match(XpDomain.BREAK, STONE).rule().skill);
    }

    @Test
    void excludeListsKeepModsIdsAndTagsOut() {
        XpTable table = load("{\"break\":[{\"match\":\"*\",\"skill\":\"mining\",\"xp\":1,"
                + "\"exclude\":{\"mods\":[\"ae2\"],\"ids\":[\"minecraft:dirt\"],\"tags\":[\"c:no_xp\"]}}]}").table;
        assertNotNull(table.match(XpDomain.BREAK, STONE));
        assertNull(table.match(XpDomain.BREAK, TestSubject.block("ae2:controller", 1)));
        assertNull(table.match(XpDomain.BREAK, TestSubject.block("minecraft:dirt", 1)));
        assertNull(table.match(XpDomain.BREAK, TestSubject.block("minecraft:gravel", 1, "c:no_xp")));
    }

    @Test
    void minHealthIsACondition() {
        XpTable table = load("{\"first_time\":["
                + "{\"match\":\"*\",\"kind\":\"entity\",\"min_health\":40,\"multiplier\":3},"
                + "{\"match\":\"*\",\"kind\":\"entity\",\"min_health\":100,\"multiplier\":5}]}").table;
        assertNull(table.match(XpDomain.FIRST_TIME, TestSubject.entity("a:b", 20)));
        assertEquals(3.0, table.match(XpDomain.FIRST_TIME, TestSubject.entity("a:b", 40)).rule().multiplier);
        assertEquals(3.0, table.match(XpDomain.FIRST_TIME, TestSubject.entity("a:b", 60)).rule().multiplier);
    }

    @Test
    void kindRestrictsARule() {
        XpTable table = load("{\"first_time\":[{\"match\":\"minecraft:diamond\",\"kind\":\"item\",\"multiplier\":4}]}").table;
        assertNotNull(table.match(XpDomain.FIRST_TIME, TestSubject.item("minecraft:diamond")));
        assertNull(table.match(XpDomain.FIRST_TIME, TestSubject.block("minecraft:diamond", 1)));
    }

    @Test
    void hardnessSpecClampsAndBedrockPaysNothing() {
        XpSpec spec = new XpSpec(0.5, 0.3, 0, 0, 5.0);
        assertEquals(0.5, spec.eval(0, 0));
        assertEquals(0.5 + 3 * 0.3, spec.eval(3, 0));
        assertEquals(5.0, spec.eval(50, 0));
        assertEquals(0.0, spec.eval(-1, 0));
    }

    @Test
    void malformedJsonIsAnErrorAndEarlierRulesStay() {
        XpSourcesLoader.Result r = load(
                "{\"break\":[{\"match\":\"minecraft:stone\",\"skill\":\"mining\",\"xp\":1}]}",
                "{ this is not json",
                "[1,2,3]",
                "");
        assertEquals(1, r.files);
        assertEquals(1, r.sources);
        assertEquals(3, r.skippedFiles);
        assertEquals(3, r.messages.stream().filter(m -> m.level() == XpSourcesLoader.Level.ERROR).count());
        assertNotNull(r.table.match(XpDomain.BREAK, STONE));
        assertTrue(r.summary().contains("skipped"));
    }

    @Test
    void aBadRuleIsSkippedAndItsNeighboursStay() {
        XpSourcesLoader.Result r = load("{\"break\":["
                + "{\"match\":\"minecraft:stone\",\"skill\":\"mining\",\"xp\":-3},"
                + "{\"match\":\"minecraft:dirt\",\"skill\":\"nope\",\"xp\":1},"
                + "{\"skill\":\"mining\",\"xp\":1},"
                + "\"just a string\","
                + "{\"match\":\"minecraft:sand\",\"skill\":\"mining\",\"xp\":\"lots\"},"
                + "{\"match\":\"minecraft:gravel\",\"skill\":\"excavation\",\"xp\":2,\"flags\":[\"nonsense\"]},"
                + "{\"match\":\"minecraft:clay\",\"skill\":\"excavation\",\"xp\":2}]}");
        assertEquals(1, r.sources);
        assertEquals(6, r.messages.stream().filter(m -> m.level() == XpSourcesLoader.Level.ERROR).count());
        assertNotNull(r.table.match(XpDomain.BREAK, TestSubject.block("minecraft:clay", 1)));
    }

    @Test
    void unknownIdsAreLoggedOnceAndSkipped() {
        XpSourcesLoader.Registries known = new XpSourcesLoader.Registries() {
            @Override
            public boolean knowsId(String registry, String id) {
                return id.startsWith("minecraft:");
            }

            @Override
            public boolean knowsTag(String registry, String tag) {
                return true;
            }
        };
        XpSourcesLoader.Result r = load(known,
                "{\"break\":[{\"match\":\"somemod:ruby_ore\",\"skill\":\"mining\",\"xp\":3},"
                        + "{\"match\":\"minecraft:stone\",\"skill\":\"mining\",\"xp\":1}]}",
                "{\"break\":[{\"match\":\"somemod:ruby_ore\",\"skill\":\"mining\",\"xp\":4}]}");
        assertEquals(1, r.sources);
        long unknown = r.messages.stream().filter(m -> m.text().contains("somemod:ruby_ore")).count();
        assertEquals(1, unknown, "logged once even though two files name it");
        assertTrue(r.messages.stream().noneMatch(m -> m.level() == XpSourcesLoader.Level.ERROR));
    }

    @Test
    void anUnknownTagStaysButWarnsOnce() {
        XpSourcesLoader.Registries noTags = new XpSourcesLoader.Registries() {
            @Override
            public boolean knowsId(String registry, String id) {
                return true;
            }

            @Override
            public boolean knowsTag(String registry, String tag) {
                return false;
            }
        };
        XpSourcesLoader.Result r = load(noTags,
                "{\"break\":[{\"match\":\"#pack:rubies\",\"skill\":\"mining\",\"xp\":3}]}",
                "{\"break\":[{\"match\":\"#pack:rubies\",\"skill\":\"mining\",\"xp\":4}]}");
        assertEquals(1, r.sources);
        assertEquals(1, r.messages.stream().filter(m -> m.text().contains("#pack:rubies")).count());
    }

    @Test
    void skillNonePaysNothingAndBeatsLowerRules() {
        XpTable table = load("{\"break\":["
                + "{\"match\":\"#minecraft:mineable/pickaxe\",\"skill\":\"mining\",\"xp\":2,\"priority\":-5},"
                + "{\"match\":\"minecraft:stone\",\"skill\":\"none\"}]}").table;
        XpMatch m = table.match(XpDomain.BREAK, STONE);
        assertTrue(m.paysNothing());
        assertEquals(0.0, m.xp());
    }

    @Test
    void flagsAreCheckedPerDomain() {
        XpSourcesLoader.Result r = load("{\"place\":[{\"match\":\"minecraft:stone\",\"skill\":\"masonry\",\"xp\":1,"
                + "\"flags\":[\"machine\"]}],\"break\":[{\"match\":\"minecraft:stone\",\"skill\":\"mining\",\"xp\":1,"
                + "\"flags\":[\"machine\"]}]}");
        assertEquals(1, r.sources);
        assertEquals(1, r.messages.stream().filter(m -> m.level() == XpSourcesLoader.Level.ERROR).count());
    }

    @Test
    void unknownTopLevelAndFieldNamesWarn() {
        XpSourcesLoader.Result r = load("{\"brek\":[],\"break\":[{\"match\":\"minecraft:stone\",\"skill\":\"mining\","
                + "\"xp\":1,\"xpp\":3}]}");
        assertEquals(1, r.sources);
        assertEquals(2, r.messages.stream().filter(m -> m.level() == XpSourcesLoader.Level.WARN).count());
    }

    @Test
    void killBonusBossAndDiscoveryRulesParse() {
        XpTable table = load("{"
                + "\"kill_bonus\":[{\"match\":\"minecraft:warden\",\"xp\":{\"base\":4,\"per_health\":0.05,\"max\":30}}],"
                + "\"boss\":[{\"match\":\"minecraft:wither\",\"boss\":true},{\"match\":\"modx:minion\",\"boss\":false}],"
                + "\"structure\":[{\"match\":\"minecraft:village\",\"xp\":60,\"flags\":[\"grand\"]}],"
                + "\"kill\":[{\"match\":\"modx:*\",\"xp\":{\"base\":1,\"per_health\":0.25,\"max\":60}}]}").table;
        TestSubject warden = TestSubject.entity("minecraft:warden", 500);
        assertEquals(29.0, table.match(XpDomain.KILL_BONUS, warden).xp());
        assertEquals(Boolean.TRUE, table.match(XpDomain.BOSS, TestSubject.entity("minecraft:wither", 300)).rule().boss);
        assertEquals(Boolean.FALSE, table.match(XpDomain.BOSS, TestSubject.entity("modx:minion", 300)).rule().boss);
        XpMatch village = table.match(XpDomain.STRUCTURE, TestSubject.plain("structure", "minecraft:village"));
        assertEquals(60.0, village.xp());
        assertTrue(village.has("grand"));
        assertEquals(Skill.BEASTSLAYING, table.match(XpDomain.KILL, TestSubject.entity("modx:troll", 40)).rule().skill);
        assertEquals(11.0, table.match(XpDomain.KILL, TestSubject.entity("modx:troll", 40)).xp());
    }

    @Test
    void garbageNeverThrows() {
        Random random = new Random(7);
        for (int i = 0; i < 300; i++) {
            StringBuilder sb = new StringBuilder();
            int len = random.nextInt(80);
            String alphabet = "{}[]\":,0123456789.-truefalsnmchpioxyk _#*";
            for (int j = 0; j < len; j++) {
                sb.append(alphabet.charAt(random.nextInt(alphabet.length())));
            }
            XpSourcesLoader.Result r = load(sb.toString());
            assertNotNull(r.table);
        }
        // Valid shell, hostile values.
        for (String value : List.of("null", "[]", "{}", "1e999", "\"\"", "true", "[[]]", "{\"match\":[]}")) {
            XpSourcesLoader.Result r = load("{\"break\":[" + value + "],\"place\":" + value + ",\"replace\":" + value + "}");
            assertNotNull(r.table);
        }
    }

    @Test
    void explainNamesTheWinnerAndWhyOthersLose() {
        XpTable table = load("{\"break\":["
                + "{\"match\":\"#minecraft:mineable/pickaxe\",\"skill\":\"mining\",\"xp\":{\"base\":0.5,\"per_hardness\":0.3,\"max\":5},\"priority\":-30},"
                + "{\"match\":\"#c:ores_in_ground\",\"skill\":\"spelunking\",\"xp\":2,\"priority\":-40},"
                + "{\"match\":\"minecraft:*\",\"skill\":\"farming\",\"xp\":1,\"exclude\":{\"ids\":[\"minecraft:stone\"]}}]}").table;
        List<String> lines = XpExplain.lines(table, XpDomain.BREAK, STONE);
        String text = String.join("\n", lines);
        assertTrue(text.contains("WINS  #minecraft:mineable/pickaxe"), text);
        assertTrue(text.contains("pays mining 0.95 XP"), text);
        assertTrue(text.contains("priority -40 is below -30"), text);
        assertTrue(text.contains("excludes the id minecraft:stone"), text);
        assertFalse(text.contains("null"));
    }
}
