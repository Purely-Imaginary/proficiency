package dev.amman.proficiency;

import dev.amman.proficiency.perk.MaterialOverrides;
import dev.amman.proficiency.perk.Requirement;
import dev.amman.proficiency.perk.Talent;
import dev.amman.proficiency.perk.Talents;
import dev.amman.proficiency.skill.Skill;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.io.StringReader;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** A pack's config/proficiency-materials.json: what it may say, and what applying it changes. */
class MaterialOverridesTest {

    @AfterEach
    void backToDefaults() {
        Talents.resetMaterials();
    }

    private static MaterialOverrides.Parsed parse(String json) {
        return MaterialOverrides.parse(new StringReader(json));
    }

    @Test
    void aTierReplacesThatWholeListAndNothingElse() {
        Talent root = Talents.of(Skill.WOODCUTTING).get(0);
        String rootKey = root.key();
        List<Requirement> tier2Before = Talents.tierMaterials(Skill.WOODCUTTING, 2);

        MaterialOverrides.Parsed parsed = parse("""
                {"skills": {"woodcutting": {"1": [
                    {"item": "kubejs:dead_log", "count": 64}, {"item": "kubejs:scrap_wood", "count": 64}]}}}
                """);
        assertTrue(parsed.errors().isEmpty(), parsed.errors().toString());
        Talents.applyMaterialOverrides(parsed.tiers());

        Talent after = Talents.byKey(rootKey);
        assertNotNull(after, "node keys survive a rebuild, so paid nodes stay paid");
        assertEquals(List.of("kubejs:dead_log", "kubejs:scrap_wood"),
                after.materials().stream().map(r -> r.itemId().toString()).toList());
        assertEquals(64, after.materials().get(0).count());
        assertEquals(tier2Before, Talents.tierMaterials(Skill.WOODCUTTING, 2), "tier 2 was not named");
        assertEquals(Talents.defaultTierMaterials(Skill.MINING, 1), Talents.tierMaterials(Skill.MINING, 1));
    }

    @Test
    void theCompassReadsTheReplacedList() {
        Talents.applyMaterialOverrides(parse("""
                {"skills": {"smithing": {"1": [{"item": "minecraft:charcoal", "count": 64}]}}}
                """).tiers());
        Talent next = Talents.unpaidMaterialNodes(Skill.SMITHING, talent -> false).get(0);
        assertEquals("minecraft:charcoal", next.materials().get(0).itemId().toString());
    }

    @Test
    void resetGoesBackToTheShippedLists() {
        Talents.applyMaterialOverrides(parse("""
                {"skills": {"mining": {"1": [{"item": "minecraft:charcoal", "count": 64}]}}}
                """).tiers());
        Talents.resetMaterials();
        assertEquals("minecraft:coal", Talents.tierMaterials(Skill.MINING, 1).get(1).itemId().toString());
    }

    @Test
    void tagsAreNotSupported() {
        assertFalse(parse("""
                {"skills": {"woodcutting": {"1": [{"tag": "minecraft:logs", "count": 32}]}}}
                """).errors().isEmpty());
    }

    @Test
    void mistakesAreReportedAndSkippedNotHalfApplied() {
        MaterialOverrides.Parsed parsed = parse("""
                {"skills": {
                  "not_a_skill": {"1": []},
                  "mining": {"5": [{"item": "minecraft:coal", "count": 1}],
                             "x": [],
                             "2": [{"item": "minecraft:iron_ingot", "count": 0}]},
                  "smithing": {"1": [{"item": "minecraft:coal", "count": 1}, {"item": "minecraft:coal", "count": 2}]},
                  "farming": {"1": [{"item": "Not A Valid Id", "count": 1}]},
                  "fishing": {"1": [{"count": 3}]}
                }}
                """);
        assertEquals(7, parsed.errors().size(), parsed.errors().toString());
        assertTrue(parsed.tiers().isEmpty(), "every tier above was broken, so none applies");
    }

    @Test
    void garbageIsAnErrorNotACrash() {
        assertFalse(parse("not json {").errors().isEmpty());
        assertFalse(parse("[1, 2]").errors().isEmpty());
        assertFalse(parse("{\"tiers\": {}}").errors().isEmpty());
    }

    @Test
    void otherTopLevelKeysAreNotes() {
        MaterialOverrides.Parsed parsed = parse("""
                {"_about": "x", "_why": {"mining/1": "no coal"}, "skills": {}}
                """);
        assertTrue(parsed.errors().isEmpty(), parsed.errors().toString());
    }
}
