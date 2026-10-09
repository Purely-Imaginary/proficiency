package dev.amman.proficiency;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.amman.proficiency.event.StructureIds;
import java.util.Set;
import org.junit.jupiter.api.Test;

class StructureIdsTest {

    @Test
    void variantsCollapse() {
        assertEquals("minecraft:village", StructureIds.canonical("minecraft:village_taiga"));
        assertEquals("minecraft:ruined_portal", StructureIds.canonical("minecraft:ruined_portal_nether"));
        assertEquals("minecraft:ruined_portal", StructureIds.canonical("minecraft:ruined_portal"));
        assertEquals("minecraft:shipwreck", StructureIds.canonical("minecraft:shipwreck_beached"));
        assertEquals("minecraft:ocean_ruin", StructureIds.canonical("minecraft:ocean_ruin_warm"));
        assertEquals("minecraft:mineshaft", StructureIds.canonical("minecraft:mineshaft_mesa"));
    }

    @Test
    void othersStayPut() {
        assertEquals("minecraft:mansion", StructureIds.canonical("minecraft:mansion"));
        assertEquals("minecraft:shipwreck", StructureIds.canonical("minecraft:shipwreck"));
        assertEquals("mod:village_big", StructureIds.canonical("mod:village_big"));
    }

    @Test
    void legacyKeyCountsAsSeen() {
        Set<String> visited = Set.of("structure:minecraft:village_plains");
        assertTrue(StructureIds.alreadySeen("minecraft:village", visited));
        assertFalse(StructureIds.alreadySeen("minecraft:ruined_portal", visited));
        assertFalse(StructureIds.alreadySeen("minecraft:village", Set.<String>of()));
        assertTrue(StructureIds.alreadySeen("minecraft:mineshaft",
                Set.of("structure:minecraft:mineshaft_mesa")));
        assertTrue(StructureIds.alreadySeen("minecraft:village",
                Set.of("structure:minecraft:village")));
    }

    @Test
    void compassTargetsSkipSeenTypesAndBuriedTreasure() {
        java.util.List<String> ids = java.util.List.of("minecraft:village_plains", "minecraft:village_taiga",
                "minecraft:buried_treasure", "minecraft:mansion", "minecraft:pillager_outpost");
        Set<String> seen = Set.of("structure:minecraft:pillager_outpost");
        assertEquals(java.util.List.of("minecraft:village_plains", "minecraft:village_taiga", "minecraft:mansion"),
                StructureIds.unvisitedTargets(ids, seen));
        // A legacy variant key counts for the whole canonical type.
        assertEquals(java.util.List.of("minecraft:mansion"), StructureIds.unvisitedTargets(ids,
                Set.of("structure:minecraft:village_desert", "structure:minecraft:pillager_outpost")));
    }

    @Test
    void replacementStructuresCountAsTheVanillaOne() {
        assertEquals("minecraft:stronghold", StructureIds.canonical("betterstrongholds:stronghold"));
        assertEquals("minecraft:monument", StructureIds.canonical("betteroceanmonuments:ocean_monument"));
        assertEquals("minecraft:fortress", StructureIds.canonical("betterfortresses:fortress"));
        assertEquals("minecraft:mansion", StructureIds.canonical("nova_structures:illager_manor"));
        assertEquals("minecraft:jungle_pyramid", StructureIds.canonical("betterjungletemples:jungle_temple"));
        // A different Yung's structure is not a replacement.
        assertEquals("betterdungeons:zombie_dungeon", StructureIds.canonical("betterdungeons:zombie_dungeon"));
    }

    @Test
    void mineshaftAndRepurposedVariantsCollapseToOneDiscovery() {
        java.util.Set<String> mineshafts = new java.util.HashSet<>();
        for (String id : java.util.List.of("bettermineshafts:mineshaft_oak", "bettermineshafts:mineshaft_spruce_snowy",
                "bettermineshafts:mineshaft_mesa", "repurposed_structures:mineshaft_warped",
                "minecraft:mineshaft_mesa", "minecraft:mineshaft")) {
            mineshafts.add(StructureIds.canonical(id));
        }
        assertEquals(java.util.Set.of("minecraft:mineshaft"), mineshafts);
        assertEquals("minecraft:mansion", StructureIds.canonical("repurposed_structures:mansion_taiga"));
        assertEquals("minecraft:igloo", StructureIds.canonical("repurposed_structures:igloo_mushroom"));
        assertEquals("minecraft:ancient_city", StructureIds.canonical("repurposed_structures:ancient_city_ocean"));
        assertEquals("minecraft:pillager_outpost", StructureIds.canonical("repurposed_structures:outpost_giant_tree_taiga"));
        assertEquals("minecraft:desert_pyramid", StructureIds.canonical("repurposed_structures:pyramid_ocean"));
        assertEquals("minecraft:village", StructureIds.canonical("repurposed_structures:village_cherry"));
        assertEquals("minecraft:jungle_pyramid", StructureIds.canonical("repurposed_structures:temple_taiga"));
        assertEquals("repurposed_structures:temple_nether", StructureIds.canonical("repurposed_structures:temple_nether_soul"));
        assertEquals("repurposed_structures:ruins_land", StructureIds.canonical("repurposed_structures:ruins_land_icy"));
        // Not in a family: stay put.
        assertEquals("repurposed_structures:city_nether", StructureIds.canonical("repurposed_structures:city_nether"));
        assertEquals("repurposed_structures:bastion_underground",
                StructureIds.canonical("repurposed_structures:bastion_underground"));
    }

    @Test
    void everyKnownModdedVariantHasAHome() throws Exception {
        // The Repurposed Structures ids listed in the 1.21.1 jar: all but a handful fall in a family.
        java.util.List<String> alone = java.util.List.of("repurposed_structures:bastion_underground",
                "repurposed_structures:city_nether", "repurposed_structures:city_overworld",
                "repurposed_structures:ruins_nether");
        for (String id : alone) {
            assertEquals(id, StructureIds.canonical(id));
        }
        assertTrue(StructureIds.alreadySeen("minecraft:mineshaft",
                Set.of("structure:bettermineshafts:mineshaft_overgrown")));
        assertTrue(StructureIds.alreadySeen("minecraft:stronghold",
                Set.of("structure:betterstrongholds:stronghold")));
        assertTrue(StructureIds.alreadySeen("minecraft:village",
                Set.of("structure:repurposed_structures:village_bamboo")));
        assertFalse(StructureIds.alreadySeen("minecraft:village",
                Set.of("structure:repurposed_structures:mansion_oak")));
    }

    @Test
    void anUnlistedVariantKeyStillCountsAsSeen() {
        // No list names this id: the key's canonical id is what counts.
        assertTrue(StructureIds.alreadySeen("minecraft:village",
                Set.of("structure:repurposed_structures:village_some_future_biome")));
        assertTrue(StructureIds.seenCanonicals(Set.of("structure:repurposed_structures:mansion_oak", "first:x:y"))
                .contains("minecraft:mansion"));
        assertFalse(StructureIds.seenCanonicals(Set.of("first:x:y")).contains("minecraft:mansion"));
    }
}
