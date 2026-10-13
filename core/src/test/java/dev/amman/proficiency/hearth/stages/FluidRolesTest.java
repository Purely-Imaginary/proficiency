package dev.amman.proficiency.hearth.stages;

import org.junit.jupiter.api.Test;

import java.io.StringReader;
import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The fluid roles of the designs: v is a water source block, V a lava source block. */
class FluidRolesTest {

    /** Stage 1: a stone base, two water cells and one lava cell under and beside the fire. */
    private static final String WET = """
            {"legend": {}, "lanes": [{"id": "only", "stages": [
              {"stage": 1, "layers": {"-1": ["         ","         ","         ","   vSV   ","    S    ","   vS    ","         ","         ","         "],
                                      "0":  ["         ","         ","         ","         ","    F    ","         ","         ","         ","         "]}},
              {"stage": 2, "layers": {}}, {"stage": 3, "layers": {}}, {"stage": 4, "layers": {}},
              {"stage": 5, "layers": {}}, {"stage": 6, "layers": {}}
            ]}]}
            """;

    /** What the game's probe says: a cell holds the role letter placed there; 'f' is flowing water, 'w' waterlogged stone. */
    private static HearthDesigns.Probe world(Map<String, Character> placed) {
        return (role, dx, dy, dz) -> {
            Character here = placed.get(dx + "," + dy + "," + dz);
            return role == '.' ? here == null : here != null && here == role;
        };
    }

    private static Map<String, Character> built(HearthDesigns.Stage stage) {
        Map<String, Character> placed = new HashMap<>();
        for (HearthDesigns.Cell cell : stage.cells()) {
            if (cell.role() != '.' && cell.role() != 'F') {
                placed.put(cell.dx() + "," + cell.dy() + "," + cell.dz(), cell.role());
            }
        }
        return placed;
    }

    private final HearthDesigns wet = HearthDesigns.parse(new StringReader(WET));

    @Test
    void theFluidRolesAreInTheLegendWithDefaultBlocks() {
        assertTrue(HearthDesigns.ROLES.indexOf('v') >= 0 && HearthDesigns.ROLES.indexOf('V') >= 0);
        assertEquals("minecraft:water", HearthDesigns.defaultBlock('v'));
        assertEquals("minecraft:lava", HearthDesigns.defaultBlock('V'));
        assertTrue(HearthDesigns.isFluid('v') && HearthDesigns.isFluid('V'));
        assertFalse(HearthDesigns.isFluid('S') || HearthDesigns.isFluid('.'));
    }

    @Test
    void aStageWithFluidSourcesMatchesOnlyWithThem() {
        HearthDesigns.Stage stage = wet.lanes().get(0).stage(1);
        Map<String, Character> placed = built(stage);
        assertEquals(3, placed.values().stream().filter(c -> c == 'v' || c == 'V').count());
        assertTrue(wet.matches(world(placed), 1));

        // Flowing water (the probe answers "not a source") fails, and so does waterlogged stone.
        for (char wrong : new char[] {'f', 'w', 'S', 'V'}) {
            Map<String, Character> bad = new HashMap<>(placed);
            bad.put("-1,-1,1", wrong);
            assertFalse(wet.matches(world(bad), 1), "water cell holding " + wrong);
            HearthDesigns.Problem problem = wet.problem(world(bad), 1);
            assertNotNull(problem);
            assertEquals('v', problem.role());
            assertEquals(-1, problem.dx());
            assertEquals(-1, problem.dy());
            assertEquals(1, problem.dz());
            assertEquals(1, problem.remaining());
        }
        // Lava where water goes is no water, and water where lava goes is no lava.
        Map<String, Character> swapped = new HashMap<>(placed);
        swapped.put("1,-1,-1", 'v');
        assertFalse(wet.matches(world(swapped), 1));
        assertEquals('V', wet.problem(world(swapped), 1).role());
        // Taking the water away is a miss, putting it back mends it.
        Map<String, Character> dry = new HashMap<>(placed);
        dry.remove("-1,-1,-1");
        assertFalse(wet.matches(world(dry), 1));
        assertTrue(wet.matches(world(placed), 1));
    }

    @Test
    void theGhostShowsAMissingFluidCellAsMissing() {
        HearthDesigns.Stage stage = wet.lanes().get(0).stage(1);
        Map<String, Character> dry = built(stage);
        dry.values().removeIf(c -> c == 'v' || c == 'V');
        var diff = StageGhost.diff(stage, world(dry));
        assertEquals(3, diff.size());
        assertTrue(diff.stream().allMatch(c -> c.kind() == StageGhost.Kind.MISSING && HearthDesigns.isFluid(c.role())));
    }

    @Test
    void materialsCountFluidSourcesAsBuckets() {
        Map<String, Integer> counts = StageMaterials.count(wet.lanes().get(0).stage(1));
        assertEquals(2, counts.get("water"));
        assertEquals(1, counts.get("lava"));
        assertEquals(3, counts.get("stone"));
        assertTrue(StageMaterials.inBuckets("water") && StageMaterials.inBuckets("lava"));
        assertFalse(StageMaterials.inBuckets("stone"));
        assertEquals("water", StageMaterials.group('v'));
        assertEquals("lava", StageMaterials.group('V'));
        // The fluid groups come last, after every block group.
        String[] groups = StageMaterials.GROUPS;
        assertEquals("lava", groups[groups.length - 1]);
        assertNull(StageMaterials.group('.'));
    }
}
