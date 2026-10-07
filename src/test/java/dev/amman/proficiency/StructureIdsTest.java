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
        assertTrue(StructureIds.alreadySeen("minecraft:village", visited::contains));
        assertFalse(StructureIds.alreadySeen("minecraft:ruined_portal", visited::contains));
        assertFalse(StructureIds.alreadySeen("minecraft:village", Set.<String>of()::contains));
        assertTrue(StructureIds.alreadySeen("minecraft:mineshaft",
                Set.of("structure:minecraft:mineshaft_mesa")::contains));
        assertTrue(StructureIds.alreadySeen("minecraft:village",
                Set.of("structure:minecraft:village")::contains));
    }

    @Test
    void compassTargetsSkipSeenTypesAndBuriedTreasure() {
        java.util.List<String> ids = java.util.List.of("minecraft:village_plains", "minecraft:village_taiga",
                "minecraft:buried_treasure", "minecraft:mansion", "minecraft:pillager_outpost");
        Set<String> seen = Set.of("structure:minecraft:pillager_outpost");
        assertEquals(java.util.List.of("minecraft:village_plains", "minecraft:village_taiga", "minecraft:mansion"),
                StructureIds.unvisitedTargets(ids, seen::contains));
        // A legacy variant key counts for the whole canonical type.
        assertEquals(java.util.List.of("minecraft:mansion"), StructureIds.unvisitedTargets(ids,
                Set.of("structure:minecraft:village_desert", "structure:minecraft:pillager_outpost")::contains));
    }
}
