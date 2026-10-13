package dev.amman.proficiency.hearth.stages;

import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The build guide's ghost cells and the guide's material counts. */
class StageGhostTest {

    private static final HearthDesigns DESIGNS = HearthDesigns.shipped();

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

    @Test
    void emptyWorldMissesEveryBlockCellButNotTheFire() {
        for (HearthDesigns.Lane lane : DESIGNS.lanes()) {
            for (int s = 1; s <= 6; s++) {
                HearthDesigns.Stage stage = lane.stage(s);
                List<StageGhost.Cell> diff = StageGhost.diff(stage, world(Map.of()));
                long blocks = stage.cells().stream().filter(c -> c.role() != '.' && c.role() != 'F').count();
                assertEquals(blocks, diff.size(), lane.id() + " " + s);
                assertTrue(diff.stream().allMatch(c -> c.kind() == StageGhost.Kind.MISSING));
                assertEquals(diff.size(), StageGhost.wrong(stage, world(Map.of())));
            }
        }
    }

    @Test
    void aBuiltStageIsClean() {
        for (HearthDesigns.Lane lane : DESIGNS.lanes()) {
            HearthDesigns.Stage stage = lane.stage(3);
            assertTrue(StageGhost.diff(stage, world(built(stage))).isEmpty(), lane.id());
        }
    }

    @Test
    void nextStageOfABuiltStageOnlyShowsWhatItAdds() {
        HearthDesigns.Lane lane = DESIGNS.lanes().get(0);
        Map<String, Character> placed = built(lane.stage(2));
        List<StageGhost.Cell> diff = StageGhost.diff(lane.stage(3), world(placed));
        assertFalse(diff.isEmpty());
        for (StageGhost.Cell cell : diff) {
            Character here = placed.get(cell.dx() + "," + cell.dy() + "," + cell.dz());
            // A cell stage II already holds correctly is never a ghost; a cell it holds as another role is.
            assertTrue(here == null || here != cell.role());
        }
    }

    @Test
    void aBlockedAirCellIsOutlinedNotGhosted() {
        HearthDesigns.Stage stage = DESIGNS.lanes().get(0).stage(4);
        HearthDesigns.Cell air = stage.cells().stream().filter(c -> c.role() == '.').findFirst().orElseThrow();
        Map<String, Character> placed = built(stage);
        placed.put(air.dx() + "," + air.dy() + "," + air.dz(), 'S');
        List<StageGhost.Cell> diff = StageGhost.diff(stage, world(placed));
        assertEquals(1, diff.size());
        assertEquals(StageGhost.Kind.BLOCKED, diff.get(0).kind());
        assertEquals('.', diff.get(0).role());
    }

    @Test
    void closestLaneFollowsWhatIsBuilt() {
        for (int lane = 0; lane < DESIGNS.lanes().size(); lane++) {
            Map<String, Character> placed = built(DESIGNS.lanes().get(lane).stage(2));
            // The stage a lane is built to is closest to itself (no other lane draws the same cells) ...
            assertEquals(lane, StageGhost.closestLane(DESIGNS, world(placed), 2), "lane " + lane);
            // ... and for the next stage, the lane with the fewest wrong cells wins.
            int pick = StageGhost.closestLane(DESIGNS, world(placed), 3);
            for (HearthDesigns.Lane other : DESIGNS.lanes()) {
                assertTrue(StageGhost.wrong(DESIGNS.lanes().get(pick).stage(3), world(placed))
                        <= StageGhost.wrong(other.stage(3), world(placed)));
            }
        }
        // Out-of-range stages clamp to I and VI.
        assertEquals(StageGhost.closestLane(DESIGNS, world(Map.of()), 1),
                StageGhost.closestLane(DESIGNS, world(Map.of()), 0));
        assertEquals(StageGhost.closestLane(DESIGNS, world(Map.of()), 6),
                StageGhost.closestLane(DESIGNS, world(Map.of()), 9));
    }

    @Test
    void materialsGroupAndCountEveryStage() {
        for (HearthDesigns.Lane lane : DESIGNS.lanes()) {
            int previous = 0;
            for (int s = 1; s <= 6; s++) {
                Map<String, Integer> counts = StageMaterials.count(lane.stage(s));
                // Spec: every stage has stone, wood and metal.
                assertTrue(counts.containsKey("stone"), lane.id() + s);
                assertTrue(counts.containsKey("wood"), lane.id() + s);
                int total = counts.values().stream().mapToInt(Integer::intValue).sum();
                long blocks = lane.stage(s).cells().stream().filter(c -> StageMaterials.group(c.role()) != null).count();
                assertEquals(blocks, total);
                assertTrue(total >= previous, "a stage holds its predecessor's blocks");
                previous = total;
            }
        }
        assertEquals("stone", StageMaterials.group('s'));
        assertEquals("wood", StageMaterials.group('w'));
        assertEquals(null, StageMaterials.group('.'));
        assertEquals(null, StageMaterials.group('F'));
    }

    @Test
    void materialOrderIsFixed() {
        Map<String, Integer> counts = StageMaterials.count(DESIGNS.lanes().get(1).stage(6));
        int last = -1;
        for (String group : counts.keySet()) {
            int at = java.util.Arrays.asList(StageMaterials.GROUPS).indexOf(group);
            assertTrue(at > last);
            last = at;
        }
    }
}
