package dev.amman.proficiency.xp;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.amman.proficiency.skill.Skill;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.Test;

/** The README's complete example must load clean and do what the text above it says. */
class ReadmeXpExampleTest {

    private static String example() throws Exception {
        String readme = Files.readString(Path.of(System.getProperty("proficiency.readme", "../README.md")));
        int heading = readme.indexOf("## Tuning XP with a datapack");
        assertTrue(heading >= 0, "the README has no 'Tuning XP with a datapack' section");
        int open = readme.indexOf("```json", heading);
        int close = readme.indexOf("```", open + 7);
        assertTrue(open > heading && close > open, "the section has no json example");
        return readme.substring(open + 7, close);
    }

    @Test
    void theExampleLoadsCleanAndBehaves() throws Exception {
        XpSourcesLoader.Result r = XpSourcesLoader.load(List.of(
                new XpSourcesLoader.SourceFile("proficiency:xp_sources/defaults", XpSources.readDefaults()),
                new XpSourcesLoader.SourceFile("mymod:xp_sources/example", example())),
                XpSourcesLoader.Registries.ANY);
        assertTrue(r.messages.isEmpty(), "the example reported: " + r.messages);
        XpTable table = r.table;

        XpMatch ruby = table.match(XpDomain.BREAK, TestSubject.block("mymod:ruby_ore", 3.0,
                "minecraft:mineable/pickaxe"));
        assertEquals(Skill.MINING, ruby.rule().skill);
        assertEquals("pickaxe", ruby.rule().tool);
        assertEquals(3.5, ruby.xp(), 1e-9);

        assertEquals(1.5, table.match(XpDomain.BREAK, TestSubject.block("mymod:cherry_log", 2,
                "mymod:orchard_logs", "minecraft:logs")).xp());
        assertTrue(table.match(XpDomain.BREAK, TestSubject.block("minecraft:bookshelf", 1.5,
                "minecraft:mineable/axe")).paysNothing());

        XpMatch lamp = table.match(XpDomain.PLACE, TestSubject.block("mymod:lamp", 1,
                "proficiency:builtin/full_cube"));
        assertEquals(Skill.DECORATING, lamp.rule().skill);
        assertTrue(lamp.has("decor"));
        assertEquals(Skill.ENGINEERING, table.match(XpDomain.PLACE,
                TestSubject.block("mymod:steam_engine", 1)).rule().skill);

        assertTrue(table.match(XpDomain.KILL, TestSubject.entity("mymod:pet_dog", 20)).paysNothing());
        assertEquals(Skill.BEASTSLAYING, table.match(XpDomain.KILL, TestSubject.entity("mymod:troll", 20)).rule().skill);
        assertEquals(40.0, table.match(XpDomain.KILL_BONUS, TestSubject.entity("mymod:titan", 5000)).xp());
        assertEquals(Boolean.TRUE, table.match(XpDomain.BOSS, TestSubject.entity("mymod:titan", 5000)).rule().boss);

        XpMatch temple = table.match(XpDomain.STRUCTURE, TestSubject.plain("structure", "mymod:sunken_temple"));
        assertEquals(80.0, temple.xp());
        assertTrue(temple.has("grand"));
        assertEquals(40.0, table.match(XpDomain.BIOME, TestSubject.plain("biome", "mymod:glow_forest")).xp());
        assertEquals(25.0, table.match(XpDomain.BIOME, TestSubject.plain("biome", "minecraft:plains")).xp());
        XpMatch gem = table.match(XpDomain.FIRST_TIME, TestSubject.block("mymod:flawless_gem_block", 3));
        assertEquals(4.0, gem.rule().multiplier);
        assertNotNull(gem);
    }
}
