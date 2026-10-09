package dev.amman.proficiency.xp;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.amman.proficiency.skill.FirstTimeKinds;
import dev.amman.proficiency.skill.Skill;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * The golden test: every rule the mod hardcoded in 1.2.0, evaluated through the shipped defaults
 * file, must give the same skill and XP. The expectations below are the 1.2.0 code, restated (the
 * block XP formula, the kill formula, the namespace lists, the first-time tiers) and kept apart
 * from the data on purpose. One subclass per tag convention: {@code c:} on 1.21.1, {@code forge:}
 * on 1.20.1.
 */
abstract class XpDefaultsGoldenBase {

    /** The common ore tag of this loader family, for example {@code c:ores}. */
    abstract String oresTag();

    abstract String glassTag();

    private final XpTable table = loadDefaults();

    private static XpTable loadDefaults() {
        String text = XpSources.readDefaults();
        assertTrue(text.length() > 100, "the defaults file is in the jar");
        XpSourcesLoader.Result result = XpSourcesLoader.load(
                List.of(new XpSourcesLoader.SourceFile("proficiency:xp_sources/defaults", text)),
                XpSourcesLoader.Registries.ANY);
        assertTrue(result.messages.isEmpty(), "the defaults load clean: " + result.messages);
        return result.table;
    }

    // ---- 1.2.0 restated ------------------------------------------------------------------------

    private static double legacyBlockXp(double hardness) {
        if (hardness < 0) {
            return 0;
        }
        return Math.min(5.0, 0.5 + hardness * 0.30);
    }

    private static final double[] HARDNESS = {-1, 0, 0.1, 0.3, 0.5, 0.6, 1.0, 1.5, 2.0, 3.0, 4.5, 5.0,
            15.0, 22.5, 50.0, 100.0};

    private static final List<String> MACHINE_MODS = List.of("create", "createaddition",
            "create_dragons_plus", "create_enchantment_industry", "ae2", "arseng", "titanium",
            "functionalstorage", "merequester", "toms_storage");

    private static final List<String> ARCANE_MODS = List.of("ars_nouveau", "ars_additions", "arseng");

    private static final List<String> FURNITURE_MODS = List.of("handcrafted", "another_furniture",
            "supplementaries", "chipped", "adorabuild_structures", "amendments", "immersivelanterns",
            "hearths", "aurelj_paintings", "fastpaintings");

    private static final List<String> DECOR_TAGS = List.of("minecraft:candles", "minecraft:wool_carpets",
            "minecraft:beds", "minecraft:banners", "minecraft:flower_pots", "minecraft:signs",
            "minecraft:flowers", "minecraft:campfires");

    // ---- break --------------------------------------------------------------------------------

    private XpMatch breakOf(double hardness, String... tags) {
        return table.match(XpDomain.BREAK, TestSubject.block("modx:thing", hardness, tags));
    }

    @Test
    void breakingPaysTheSameSkillsAndXp() {
        for (double hardness : HARDNESS) {
            for (String tag : new String[] {"minecraft:logs", "minecraft:leaves"}) {
                XpMatch m = breakOf(hardness, tag);
                assertEquals(Skill.WOODCUTTING, m.rule().skill);
                assertEquals(legacyBlockXp(hardness), m.xp(), 0.0, tag + " at " + hardness);
                assertNull(m.rule().tool);
            }
            XpMatch pick = breakOf(hardness, "minecraft:mineable/pickaxe");
            assertEquals(Skill.MINING, pick.rule().skill);
            assertEquals("pickaxe", pick.rule().tool);
            assertEquals(legacyBlockXp(hardness), pick.xp(), 0.0);
            XpMatch shovel = breakOf(hardness, "minecraft:mineable/shovel");
            assertEquals(Skill.EXCAVATION, shovel.rule().skill);
            assertEquals("shovel", shovel.rule().tool);
            assertEquals(legacyBlockXp(hardness), shovel.xp(), 0.0);
            XpMatch axe = breakOf(hardness, "minecraft:mineable/axe");
            assertEquals(Skill.WOODCUTTING, axe.rule().skill);
            assertNull(axe.rule().tool);
            assertEquals(legacyBlockXp(hardness), axe.xp(), 0.0);
        }
        assertNull(breakOf(2.0), "an untagged block pays nothing");
        assertNull(breakOf(2.0, "minecraft:mineable/hoe"), "the hoe tag was never a source");
    }

    @Test
    void cropsPayFlatFarmingXpAndAreRipenessChecked() {
        for (double hardness : HARDNESS) {
            for (String tag : new String[] {"minecraft:crops", "proficiency:builtin/crop_block"}) {
                XpMatch m = breakOf(hardness, tag);
                assertEquals(Skill.FARMING, m.rule().skill);
                assertEquals(1.0, m.xp(), 0.0, "farming is flat 1.0 whatever the hardness");
                assertTrue(m.has("crop"));
            }
        }
    }

    @Test
    void breakPrecedenceIsTheOldIfChain() {
        // logs/leaves, then crops, then pickaxe, shovel, axe.
        assertEquals(Skill.WOODCUTTING, breakOf(1, "minecraft:crops", "minecraft:logs").rule().skill);
        assertEquals(Skill.WOODCUTTING, breakOf(1, "minecraft:mineable/pickaxe", "minecraft:leaves").rule().skill);
        assertEquals(Skill.FARMING, breakOf(1, "minecraft:mineable/pickaxe", "minecraft:crops").rule().skill);
        assertEquals(Skill.FARMING, breakOf(1, "minecraft:mineable/axe", "proficiency:builtin/crop_block").rule().skill);
        assertEquals(Skill.MINING, breakOf(1, "minecraft:mineable/shovel", "minecraft:mineable/pickaxe").rule().skill);
        assertEquals(Skill.EXCAVATION, breakOf(1, "minecraft:mineable/axe", "minecraft:mineable/shovel").rule().skill);
    }

    @Test
    void shippedPriorityIsBelowADefaultPackRule() {
        XpSourcesLoader.Result pack = XpSourcesLoader.load(List.of(
                new XpSourcesLoader.SourceFile("proficiency:xp_sources/defaults", XpSources.readDefaults()),
                new XpSourcesLoader.SourceFile("pack:xp_sources/ores",
                        "{\"break\":[{\"match\":\"#" + oresTag() + "\",\"skill\":\"mining\",\"xp\":9}]}")),
                XpSourcesLoader.Registries.ANY);
        XpMatch m = pack.table.match(XpDomain.BREAK,
                TestSubject.block("modx:ruby_ore", 3, "minecraft:mineable/shovel", oresTag()));
        assertEquals(Skill.MINING, m.rule().skill);
        assertEquals(9.0, m.xp());
    }

    // ---- place --------------------------------------------------------------------------------

    private XpMatch placeOf(String id, String... tags) {
        return table.match(XpDomain.PLACE, TestSubject.block(id, 1.0, tags));
    }

    private void assertPlace(XpMatch m, Skill skill, double xp) {
        assertEquals(skill, m.rule().skill);
        assertEquals(xp, m.xp(), 0.0);
    }

    @Test
    void placingPaysTheSameSkillsAndXp() {
        // Full cube: Masonry 1. Anything else: Decorating 1.
        assertPlace(placeOf("minecraft:stone", "proficiency:builtin/full_cube"), Skill.MASONRY, 1.0);
        assertPlace(placeOf("minecraft:torch"), Skill.DECORATING, 1.0);
        // Stairs, slabs, walls: half Masonry, half Decorating.
        for (String tag : new String[] {"minecraft:stairs", "minecraft:slabs", "minecraft:walls",
                "proficiency:builtin/split_block"}) {
            XpMatch m = placeOf("minecraft:oak_stairs", tag, "proficiency:builtin/full_cube");
            assertPlace(m, Skill.MASONRY, 0.5);
            assertEquals(Skill.DECORATING, m.rule().also.skill());
            assertEquals(0.5, m.rule().also.xp().eval());
        }
        // Planting: Farming 0.5, never split, flowers excluded.
        for (String tag : new String[] {"minecraft:crops", "minecraft:saplings", "proficiency:builtin/planting_block"}) {
            XpMatch m = placeOf("minecraft:wheat", tag);
            assertPlace(m, Skill.FARMING, 0.5);
            assertTrue(m.has("planting"));
        }
        XpMatch flowerCrop = placeOf("modx:sunflower", "minecraft:crops", "minecraft:flowers");
        assertPlace(flowerCrop, Skill.DECORATING, 1.0);
        assertTrue(flowerCrop.has("decor"));
        assertTrue(!flowerCrop.has("planting"));
    }

    @Test
    void decorativeListsPayDecoratingEvenForFullCubesAndSlabs() {
        for (String mod : FURNITURE_MODS) {
            XpMatch m = placeOf(mod + ":table", "proficiency:builtin/full_cube", "minecraft:slabs");
            assertPlace(m, Skill.DECORATING, 1.0);
            assertTrue(m.has("decor"));
            assertNull(m.rule().also);
        }
        for (String tag : DECOR_TAGS) {
            XpMatch m = placeOf("minecraft:x", tag, "proficiency:builtin/full_cube", "minecraft:stairs");
            assertPlace(m, Skill.DECORATING, 1.0);
            assertTrue(m.has("decor"));
        }
    }

    @Test
    void decorativeShapesBeatSplitAndCube() {
        for (String tag : new String[] {"proficiency:builtin/decorative_shape", glassTag(), "minecraft:fences",
                "minecraft:fence_gates", "minecraft:doors", "minecraft:trapdoors", "minecraft:buttons",
                "minecraft:pressure_plates"}) {
            XpMatch m = placeOf("minecraft:x", tag, "proficiency:builtin/full_cube", "minecraft:walls");
            assertPlace(m, Skill.DECORATING, 1.0);
            assertTrue(!m.has("decor"), "shapes are not cozy-room decorations");
        }
    }

    @Test
    void machinesPayEngineeringAfterPlantingBeforeDecor() {
        for (String mod : MACHINE_MODS) {
            XpMatch m = placeOf(mod + ":thing", "proficiency:builtin/full_cube");
            assertPlace(m, Skill.ENGINEERING, 2.0);
            assertTrue(m.has("machine"));
            // A machine that is also furniture is still a machine (the handler checked it first).
            assertTrue(placeOf(mod + ":thing", "minecraft:candles").has("machine"));
            // But a planting block wins over machine, as the handler checked planting first.
            assertTrue(placeOf(mod + ":crop", "minecraft:crops").has("planting"));
            // The client's ability key never knew machines.
            XpMatch client = table.matchWithout(XpDomain.PLACE,
                    TestSubject.block(mod + ":thing", 1, "proficiency:builtin/full_cube"), "machine");
            assertPlace(client, Skill.MASONRY, 1.0);
        }
        assertTrue(!placeOf("minecraft:stone").has("machine"));
    }

    // ---- craft, cast --------------------------------------------------------------------------

    @Test
    void craftingAndCastingNamespaces() {
        for (String mod : MACHINE_MODS) {
            XpMatch m = table.match(XpDomain.CRAFT, TestSubject.item(mod + ":part"));
            assertEquals(Skill.ENGINEERING, m.rule().skill);
            assertEquals(2.0, m.xp());
        }
        assertNull(table.match(XpDomain.CRAFT, TestSubject.item("minecraft:piston")));
        assertNull(table.match(XpDomain.CRAFT, TestSubject.item("ars_nouveau:wand")));
        for (String mod : ARCANE_MODS) {
            // The mod's entities (spell projectiles) cast.
            XpMatch bolt = table.match(XpDomain.CAST, TestSubject.entity(mod + ":bolt", 0));
            assertEquals(Skill.SPELLCASTING, bolt.rule().skill);
            assertEquals(0.5, bolt.xp());
            // Its items cast only when they are caster items. A glyph, a scroll, a ritual stone do not.
            XpMatch wand = table.match(XpDomain.CAST, TestSubject.item(mod + ":wand", "proficiency:caster_items"));
            assertEquals(Skill.SPELLCASTING, wand.rule().skill);
            assertEquals(0.5, wand.xp());
            assertNull(table.match(XpDomain.CAST, TestSubject.item(mod + ":glyph_projectile")),
                    mod + " items outside the caster tag pay nothing");
            assertNull(table.match(XpDomain.CAST, TestSubject.item(mod + ":blank_parchment")));
        }
        // The tag is for items only: an entity in it is no spell, and an item in an arcane namespace is no bolt.
        assertNull(table.match(XpDomain.CAST, TestSubject.entity("modx:thing", 0, "proficiency:caster_items")));
        assertNull(table.match(XpDomain.CAST, TestSubject.item("minecraft:stick")));
        assertNull(table.match(XpDomain.CAST, TestSubject.item("create:wrench")));
    }

    // ---- kills --------------------------------------------------------------------------------

    @Test
    void killsPayBeastslayingForModdedMobsOnly() {
        for (double health : new double[] {0, 1, 4, 20, 40, 100, 236, 239, 240, 241, 500, 2000}) {
            XpMatch m = table.match(XpDomain.KILL, TestSubject.entity("modx:troll", health));
            assertEquals(Skill.BEASTSLAYING, m.rule().skill);
            assertEquals(Math.min(60.0, 1.0 + health / 4.0), m.xp(), 0.0, "health " + health);
            assertNull(table.match(XpDomain.KILL, TestSubject.entity("minecraft:zombie", health)));
        }
        assertNull(table.match(XpDomain.KILL_BONUS, TestSubject.entity("modx:troll", 50)),
                "no override: the config formula applies");
    }

    @Test
    void bossesAreTheNotableBossesTag() {
        XpMatch m = table.match(XpDomain.BOSS, TestSubject.entity("modx:dragon", 20, "proficiency:notable_bosses"));
        assertEquals(Boolean.TRUE, m.rule().boss);
        assertNull(table.match(XpDomain.BOSS, TestSubject.entity("modx:dragon", 900)),
                "health alone is the config backstop, not a rule");
    }

    @Test
    void bossesAlsoFollowTheCommonBossTags() {
        for (String tag : new String[] {"c:bosses", "neoforge:bosses", "forge:bosses"}) {
            XpMatch m = table.match(XpDomain.BOSS, TestSubject.entity("modx:colossus", 20, tag));
            assertEquals(Boolean.TRUE, m.rule().boss, tag);
            XpMatch tier = table.match(XpDomain.FIRST_TIME, TestSubject.entity("modx:colossus", 20, tag));
            assertEquals(10.0, tier.rule().multiplier, 0.0, tag + " is a x10 first-time kill");
        }
    }

    // ---- discoveries --------------------------------------------------------------------------

    @Test
    void discoveriesKeepTheirAmounts() {
        XpMatch grand = table.match(XpDomain.STRUCTURE,
                TestSubject.plain("structure", "minecraft:stronghold", "proficiency:grand_structures"));
        assertTrue(grand.has("grand"));
        assertNull(grand.rule().xp, "no number: the config grandStructureXp applies");
        assertNull(table.match(XpDomain.STRUCTURE, TestSubject.plain("structure", "minecraft:pillager_outpost")));
        assertEquals(25.0, table.match(XpDomain.BIOME, TestSubject.plain("biome", "minecraft:plains")).xp());
        assertEquals(150.0, table.match(XpDomain.DIMENSION, TestSubject.plain("dimension", "minecraft:the_nether")).xp());
    }

    // ---- first-time tiers ---------------------------------------------------------------------

    private double tier(XpSubject subject) {
        XpMatch m = table.match(XpDomain.FIRST_TIME, subject);
        return m == null ? 1.0 : m.rule().multiplier;
    }

    @Test
    void firstTimeBlockTiers() {
        String ores = oresTag();
        assertEquals(FirstTimeKinds.blockMultiplier(false, false), tier(TestSubject.block("minecraft:stone", 1)));
        assertEquals(FirstTimeKinds.blockMultiplier(true, false), tier(TestSubject.block("minecraft:iron_ore", 3, ores)));
        for (String premium : new String[] {ores + "/diamond", ores + "/emerald", ores + "/netherite_scrap"}) {
            assertEquals(FirstTimeKinds.blockMultiplier(true, true),
                    tier(TestSubject.block("minecraft:deepslate_diamond_ore", 3, ores, premium)));
            assertEquals(FirstTimeKinds.blockMultiplier(true, true),
                    tier(TestSubject.block("modx:odd", 3, premium)), "premium without the ores tag still pays x5");
        }
        assertEquals(FirstTimeKinds.blockMultiplier(true, true),
                tier(TestSubject.block("minecraft:ancient_debris", 30)));
    }

    @Test
    void firstTimeEntityTiers() {
        for (double health : new double[] {0, 4, 20, 39.9, 40, 99.9, 100, 300, 1000}) {
            assertEquals(FirstTimeKinds.entityMultiplier(false, health),
                    tier(TestSubject.entity("modx:troll", health)), "health " + health);
            assertEquals(FirstTimeKinds.entityMultiplier(true, health),
                    tier(TestSubject.entity("modx:boss", health, "proficiency:notable_bosses")),
                    "a boss is x10 whatever its health, even none");
        }
    }

    @Test
    void firstTimeItemTiersFollowRarity() {
        assertEquals(FirstTimeKinds.itemMultiplier("COMMON"), tier(TestSubject.item("minecraft:stick")));
        for (String rarity : new String[] {"UNCOMMON", "RARE", "EPIC"}) {
            assertEquals(FirstTimeKinds.itemMultiplier(rarity), tier(TestSubject.item("modx:gem",
                    "proficiency:builtin/rarity_" + rarity.toLowerCase())), rarity);
        }
        assertNotNull(table);
    }
}
