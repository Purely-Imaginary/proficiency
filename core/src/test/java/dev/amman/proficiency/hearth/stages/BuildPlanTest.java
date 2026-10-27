package dev.amman.proficiency.hearth.stages;

import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;

/** The plan screen's rows: needs, what is on hand for the chosen block, what is missing. */
class BuildPlanTest {

    @Test
    void needsCountOnlyMissingCellsPerRole() {
        List<StageGhost.Cell> diff = List.of(
                new StageGhost.Cell(0, -1, 2, 'S', StageGhost.Kind.MISSING),
                new StageGhost.Cell(1, -1, 2, 'S', StageGhost.Kind.MISSING),
                new StageGhost.Cell(0, 1, 0, '.', StageGhost.Kind.BLOCKED),
                new StageGhost.Cell(2, 1, 2, 'l', StageGhost.Kind.MISSING));
        assertEquals(Map.of('S', 2, 'l', 1), BuildPlan.needs(diff));
    }

    @Test
    void theChosenBlockCountsNotAnotherKindOfTheSameRole() {
        Map<Character, Integer> needs = Map.of('S', 20);
        Map<String, Integer> stock = Map.of("minecraft:granite", 300, "minecraft:deepslate_bricks", 12);
        BuildPlan.Row row = BuildPlan.rows(needs, Map.of('S', "minecraft:deepslate_bricks"), stock).get(0);
        assertEquals(12, row.have());
        assertEquals(8, row.missing());
    }

    @Test
    void twoRolesSharingABlockSplitTheStock() {
        Map<Character, Integer> needs = new LinkedHashMap<>();
        needs.put('w', 8);
        needs.put('p', 4);
        Map<Character, String> chosen = Map.of('w', "minecraft:dark_oak_slab", 'p', "minecraft:dark_oak_slab");
        List<BuildPlan.Row> rows = BuildPlan.rows(needs, chosen, Map.of("minecraft:dark_oak_slab", 10));
        assertEquals(8, rows.get(0).have());
        assertEquals(2, rows.get(1).have());
        assertEquals(2, rows.get(1).missing());
        assertArrayEquals(new int[] {12, 2}, BuildPlan.totals(rows));
    }

    @Test
    void noChoiceMeansAllMissing() {
        BuildPlan.Row row = BuildPlan.rows(Map.of('i', 3), Map.of(), Map.of("minecraft:iron_block", 9)).get(0);
        assertEquals("", row.block());
        assertEquals(3, row.missing());
    }
}
