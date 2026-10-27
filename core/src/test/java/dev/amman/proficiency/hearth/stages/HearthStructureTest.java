package dev.amman.proficiency.hearth.stages;

import dev.amman.proficiency.hearth.stages.HearthDesigns.Cell;
import dev.amman.proficiency.hearth.stages.HearthDesigns.Lane;
import dev.amman.proficiency.hearth.stages.HearthStructure.Check;
import dev.amman.proficiency.hearth.stages.HearthStructure.Problem;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The stage designs (docs/HEARTH-DESIGNS-SPEC.md) as the game checks them, and the first problem a player is told. */
class HearthStructureTest {

    private static final HearthDesigns DESIGNS = HearthDesigns.shipped();

    /** A world made of a map: offset -> the role letter standing there; nothing placed is air. */
    private static final class World implements HearthStructure.Probe {
        final Map<String, Character> placed = new HashMap<>();

        static String key(int x, int y, int z) {
            return x + "," + y + "," + z;
        }

        World put(char role, int x, int y, int z) {
            placed.put(key(x, y, z), role);
            return this;
        }

        void remove(int x, int y, int z) {
            placed.remove(key(x, y, z));
        }

        @Override
        public boolean is(char role, int dx, int dy, int dz) {
            Character here = placed.get(key(dx, dy, dz));
            return role == '.' ? here == null : here != null && here == role;
        }
    }

    /** A world holding exactly lane's stage {@code stage}: its air cells stay empty, the fire is left out. */
    private static World build(Lane lane, int stage) {
        World world = new World();
        for (Cell cell : lane.stage(stage).cells()) {
            if (cell.role() != '.' && cell.role() != 'F') {
                world.put(cell.role(), cell.dx(), cell.dy(), cell.dz());
            }
        }
        return world;
    }

    @Test
    void theShippedDesignsAreEightLanesOfSixStages() {
        assertEquals(java.util.List.of("longhouse", "forge", "shrine", "demonic", "gothic", "girly", "ruin", "frost"),
                DESIGNS.lanes().stream().map(Lane::id).toList());
        for (Lane lane : DESIGNS.lanes()) {
            assertFalse(lane.palette().isEmpty(), lane.id() + " has a palette");
        }
        for (Lane lane : DESIGNS.lanes()) {
            assertEquals(6, lane.stages().size());
            assertNotNull(DESIGNS.lane(lane.id()));
            for (int stage = 1; stage <= 6; stage++) {
                assertEquals(stage, lane.stage(stage).stage());
                assertNotNull(lane.stage(stage).cellAt(0, 0, 0), "the fire is a cell");
                assertEquals('F', lane.stage(stage).cellAt(0, 0, 0).role());
            }
        }
    }

    @Test
    void everyLanesStageKStandsAsKAndNotAsKPlusOne() {
        for (Lane lane : DESIGNS.lanes()) {
            for (int stage = 1; stage <= 6; stage++) {
                World world = build(lane, stage);
                Check check = HearthStructure.check(world);
                assertEquals(stage, check.built(), lane.id() + " stage " + stage);
                if (stage < 6) {
                    assertNotNull(check.problem(), lane.id() + " " + stage + ": the next stage has a problem");
                    assertEquals(stage + 1, check.problem().stage());
                    assertFalse(DESIGNS.matches(world, stage + 1), lane.id() + " " + stage + " is not " + (stage + 1));
                } else {
                    assertNull(check.problem());
                }
            }
        }
    }

    @Test
    void nothingBuiltIsStageZeroAndTheProblemIsStageOne() {
        Check check = HearthStructure.check(new World());
        assertEquals(0, check.built());
        assertNotNull(check.problem());
        assertEquals(1, check.problem().stage());
    }

    @Test
    void aMissingCellNamesItsRoleAndWhereItIsFromTheFire() {
        for (Lane lane : DESIGNS.lanes()) {
            for (int stage = 2; stage <= 6; stage++) {
                Cell added = firstAdded(lane, stage);
                if (added == null) {
                    continue;
                }
                World world = build(lane, stage);
                world.remove(added.dx(), added.dy(), added.dz());
                // The stage itself (a later stage's lodestone under the fire is why stage V cannot stand beside VI).
                Problem problem = HearthStructure.layer(world, stage);
                assertNotNull(problem, lane.id() + " " + stage);
                assertEquals(stage, problem.stage(), lane.id() + " " + stage);
                assertEquals(lane.id(), problem.lane());
                assertEquals(1, problem.remaining());
                assertEquals(added.role(), problem.role(), lane.id() + " " + stage);
                assertEquals(added.dx(), problem.dx());
                assertEquals(added.dy(), problem.dy());
                assertEquals(added.dz(), problem.dz());
            }
        }
    }

    /** The first solid cell stage {@code stage} has that the stage before it leaves blank. */
    private static Cell firstAdded(Lane lane, int stage) {
        for (Cell cell : lane.stage(stage).cells()) {
            if (cell.role() == '.' || cell.role() == 'F') {
                continue;
            }
            if (lane.stage(stage - 1).cellAt(cell.dx(), cell.dy(), cell.dz()) == null) {
                return cell;
            }
        }
        return null;
    }

    @Test
    void theClosestLaneIsTheOneToldAbout() {
        // Stage I of the forge lane with one stone missing: the forge lane is one block short, the others are many.
        Lane forge = DESIGNS.lane("forge");
        World world = build(forge, 1);
        Cell stone = null;
        for (Cell cell : forge.stage(1).cells()) {
            if (cell.role() == 'S') {
                stone = cell;
                break;
            }
        }
        world.remove(stone.dx(), stone.dy(), stone.dz());
        Problem problem = HearthStructure.check(world).problem();
        assertEquals(1, problem.stage());
        assertEquals("forge", problem.lane());
        assertEquals(1, problem.remaining());
        assertEquals('S', problem.role());
    }

    @Test
    void anAirCellThatIsBlockedFails() {
        Lane longhouse = DESIGNS.lane("longhouse");
        World world = build(longhouse, 1);
        assertEquals(1, HearthStructure.check(world).built());
        // The smoke column: one block above the fire must stay air.
        assertEquals('.', longhouse.stage(1).cellAt(0, 1, 0).role());
        world.put('S', 0, 1, 0);
        Check check = HearthStructure.check(world);
        assertEquals(0, check.built());
        Problem problem = check.problem();
        assertEquals(1, problem.stage());
        assertEquals("longhouse", problem.lane());
        assertEquals('.', problem.role());
        assertEquals(0, problem.dx());
        assertEquals(1, problem.dy());
        assertEquals(0, problem.dz());
        assertEquals(1, problem.remaining());
    }

    /** A tiny design file whose stage 2 reshapes the stone under the fire's side (S to s) and a plank (P to w). */
    private static final String RESHAPE = """
            {"legend": {}, "lanes": [{"id": "only", "stages": [
              {"stage": 1, "layers": {"-1": ["         ","         ","         ","    S    ","   SSS   ","    P    ","         ","         ","         "],
                                      "0":  ["         ","         ","         ","         ","    F    ","         ","         ","         ","         "]}},
              {"stage": 2, "layers": {"-1": ["         ","         ","         ","    S    ","   sSS   ","    w    ","         ","         ","         "],
                                      "0":  ["         ","         ","         ","    W    ","    F    ","         ","         ","         ","         "]}},
              {"stage": 3, "layers": {}}, {"stage": 4, "layers": {}}, {"stage": 5, "layers": {}}, {"stage": 6, "layers": {}}
            ]}]}
            """;

    @Test
    void aLaterStageMayReshapeACellWithoutTheEarlierOneStanding() {
        HearthDesigns tiny = HearthDesigns.parse(new java.io.StringReader(RESHAPE));
        World one = build(tiny.lanes().get(0), 1);
        World two = build(tiny.lanes().get(0), 2);
        // (The blank stages 3 to 6 match anything, so only matches() says something here, not check().)
        assertTrue(tiny.matches(one, 1));
        assertFalse(tiny.matches(one, 2));
        assertTrue(tiny.matches(two, 2), "stage 2 stands with its s and w where stage 1 had S and P");
        assertFalse(tiny.matches(two, 1), "stage 1 no longer stands once the cells were reshaped");
    }

    @Test
    void theShippedDesignsOnlyReshapeStoneToShapedStoneAndPlanksToWoodShapes() {
        for (Lane lane : DESIGNS.lanes()) {
            for (int stage = 2; stage <= 6; stage++) {
                for (Cell cell : lane.stage(stage).cells()) {
                    Cell before = lane.stage(stage - 1).cellAt(cell.dx(), cell.dy(), cell.dz());
                    if (before == null || before.role() == cell.role() || cell.role() == 'k') {
                        continue;
                    }
                    assertTrue(before.role() == 'S' && cell.role() == 's' || before.role() == 'P' && cell.role() == 'w'
                                    || (before.role() == 'P' || before.role() == 'w') && cell.role() == 'p',
                            "only S to s, P to w and P or w to p reshape: " + before + " to " + cell);
                    World world = build(lane, stage);
                    assertEquals(stage, HearthStructure.check(world).built(), lane.id() + " " + stage);
                }
            }
        }
    }

    @Test
    void theFireCellIsNeverLookedAt() {
        World world = build(DESIGNS.lane("shrine"), 3);
        world.put('F', 0, 0, 0);
        assertEquals(3, HearthStructure.check(world).built());
    }

    @Test
    void firstProblemAsksOnlyTheStageOfferedUnlessALaterOneStands() {
        World two = build(DESIGNS.lane("longhouse"), 2);
        assertNull(HearthStructure.firstProblem(two, 2));
        assertNull(HearthStructure.firstProblem(two, 1));
        Problem three = HearthStructure.firstProblem(two, 3);
        assertNotNull(three);
        assertEquals(3, three.stage());
        World six = build(DESIGNS.lane("shrine"), 6);
        assertNull(HearthStructure.firstProblem(six, 4), "a later stage standing covers an earlier offering");
    }

    @Test
    void theBuiltStageNeverPassesWhatWasOffered() {
        assertEquals(2, HearthStructure.stage(5, 2));
        assertEquals(3, HearthStructure.stage(3, 6));
        assertEquals(0, HearthStructure.stage(-1, 6));
    }

    @Test
    void theCheckBoxAndTheStructuresOwnPositions() {
        assertTrue(HearthStructure.inBox(4, 5, -4));
        assertTrue(HearthStructure.inBox(0, -1, 0));
        assertFalse(HearthStructure.inBox(5, 0, 0));
        assertFalse(HearthStructure.inBox(0, 6, 0));
        assertFalse(HearthStructure.inBox(0, -2, 0));
    }

    @Test
    void theFootprintIsTheDesignTheHearthStandsAs() {
        // Standing as nothing: only the fire.
        HearthDesigns.Footprint none = HearthStructure.footprint(new World());
        assertTrue(none.contains(0, 0, 0), "the fire");
        assertFalse(none.contains(0, -1, 0));
        // Standing as longhouse I: its stone, posts and lanterns, but not its air, and nothing of stage II.
        Lane longhouse = DESIGNS.lane("longhouse");
        HearthDesigns.Footprint one = HearthStructure.footprint(build(longhouse, 1));
        assertTrue(one.contains(0, 0, 0));
        assertTrue(one.contains(0, -1, 0), "the stone under the fire");
        assertTrue(one.contains(1, 0, 1), "a post");
        assertTrue(one.contains(1, 1, 1), "a lantern");
        assertFalse(one.contains(0, 1, 0), "the smoke column is air");
        assertFalse(one.contains(2, -1, 2), "stage II's floor is not built");
        assertFalse(one.contains(9, 0, 0));
        // Standing as VI: the lodestone under the fire and the last stage's cells.
        HearthDesigns.Footprint six = HearthStructure.footprint(build(longhouse, 6));
        assertTrue(six.contains(0, -1, 0));
        assertTrue(six.contains(2, -1, 2));
    }

    @Test
    void everyRoleHasADefaultBlock() {
        Set<Character> roles = new HashSet<>();
        for (Lane lane : DESIGNS.lanes()) {
            for (var stage : lane.stages()) {
                for (Cell cell : stage.cells()) {
                    roles.add(cell.role());
                }
            }
        }
        for (char role : roles) {
            assertNotNull(HearthDesigns.defaultBlock(role), "default block of " + role);
        }
        assertEquals("minecraft:stone_bricks", HearthDesigns.defaultBlock('S'));
        assertEquals("minecraft:oak_log", HearthDesigns.defaultBlock('W'));
        assertEquals("minecraft:lodestone", HearthDesigns.defaultBlock('k'));
        assertEquals("minecraft:air", HearthDesigns.defaultBlock('.'));
    }

    // ---- The shipped file against tools/hearth_designs/validate.py ----------------------------------

    /**
     * A lantern stands on a block with a full centre or hangs under one: never on a {@code w} or {@code s}, which
     * the guide draws as a bottom slab (the lantern pops off it). A wood shape under a lantern is a {@code p}.
     */
    @Test
    void everyLanternStandsOnAFullCentreOrHangs() {
        String stands = "SWPpicgkInqYOLGxbh";
        String hangs = stands + "sw";
        for (Lane lane : DESIGNS.lanes()) {
            for (var stage : lane.stages()) {
                for (Cell cell : stage.cells()) {
                    if (cell.role() != 'l') {
                        continue;
                    }
                    Cell below = stage.cellAt(cell.dx(), cell.dy() - 1, cell.dz());
                    Cell above = stage.cellAt(cell.dx(), cell.dy() + 1, cell.dz());
                    boolean stood = below != null && stands.indexOf(below.role()) >= 0;
                    boolean hung = above != null && hangs.indexOf(above.role()) >= 0;
                    assertTrue(stood || hung, lane.id() + " " + stage.stage() + " lantern " + cell + " on " + below);
                }
            }
        }
    }

    private static final String METALS = "icgbhlauBrk";

    @Test
    void theShippedFilePassesTheValidatorsStructuralRules() {
        for (Lane lane : DESIGNS.lanes()) {
            Map<String, Character> previous = null;
            for (var stage : lane.stages()) {
                String name = lane.id() + " " + stage.stage();
                Map<String, Character> now = new HashMap<>();
                int fires = 0;
                boolean stone = false;
                boolean wood = false;
                boolean metal = false;
                for (Cell cell : stage.cells()) {
                    // Bounds: 9x9 around the fire, layers -1..5.
                    assertTrue(Math.abs(cell.dx()) <= 4 && Math.abs(cell.dz()) <= 4, name + " grid " + cell);
                    assertTrue(cell.dy() >= -1 && cell.dy() <= 5, name + " layers " + cell);
                    now.put(World.key(cell.dx(), cell.dy(), cell.dz()), cell.role());
                    if (cell.role() == 'F') {
                        fires++;
                        assertTrue(cell.dx() == 0 && cell.dy() == 0 && cell.dz() == 0, name + " fire position " + cell);
                    }
                    stone |= cell.role() == 'S' || cell.role() == 's';
                    wood |= cell.role() == 'W' || cell.role() == 'P' || cell.role() == 'w' || cell.role() == 'p';
                    metal |= METALS.indexOf(cell.role()) >= 0;
                }
                assertEquals(1, fires, name + " has one fire");
                assertTrue(stone && wood && metal, name + " has stone, wood and metal");
                // 4-fold rotational symmetry around the fire, on every layer.
                for (Cell cell : stage.cells()) {
                    Character turned = now.get(World.key(cell.dz(), cell.dy(), -cell.dx()));
                    assertEquals(cell.role(), turned == null ? ' ' : turned, name + " symmetry at " + cell);
                }
                // The smoke column above the fire is clear.
                for (int y = 1; y <= 5; y++) {
                    Character above = now.get(World.key(0, y, 0));
                    assertTrue(above == null || above == '.', name + " smoke column at y " + y);
                }
                // Under the fire: stone, and the lodestone at VI.
                assertEquals(stage.stage() == 6 ? 'k' : 'S', (char) now.get(World.key(0, -1, 0)), name + " under the fire");
                // A superset of the stage before, but for S to s, P to w, P or w to p and S to k under the fire.
                if (previous != null) {
                    for (var entry : previous.entrySet()) {
                        Character was = entry.getValue();
                        Character is = now.get(entry.getKey());
                        boolean fine = was.equals(is)
                                || was == 'S' && is != null && (is == 's' || is == 'k' && entry.getKey().equals("0,-1,0"))
                                || was == 'P' && is != null && is == 'w'
                                || (was == 'P' || was == 'w') && is != null && is == 'p';
                        assertTrue(fine, name + " drops or changes " + entry.getKey() + " " + was + " to " + is);
                    }
                }
                previous = now;
            }
        }
    }
}
