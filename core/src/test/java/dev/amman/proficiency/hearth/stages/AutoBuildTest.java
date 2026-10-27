package dev.amman.proficiency.hearth.stages;

import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The self-build's order and its reasons to wait. */
class AutoBuildTest {

    private static StageGhost.Cell missing(int dx, int dy, int dz, char role) {
        return new StageGhost.Cell(dx, dy, dz, role, StageGhost.Kind.MISSING);
    }

    private static StageGhost.Cell blocked(int dx, int dy, int dz) {
        return new StageGhost.Cell(dx, dy, dz, '.', StageGhost.Kind.BLOCKED);
    }

    /** A world with every material and every support, and these occupants. */
    private static final class Fake implements AutoBuild.World {
        final Map<String, AutoBuild.Occupant> at = new HashMap<>();
        final Set<Character> noMaterial = new HashSet<>();
        final Set<String> noSupport = new HashSet<>();

        Fake put(int dx, int dy, int dz, AutoBuild.Occupant occupant) {
            at.put(dx + "," + dy + "," + dz, occupant);
            return this;
        }

        @Override
        public AutoBuild.Occupant at(int dx, int dy, int dz) {
            return at.getOrDefault(dx + "," + dy + "," + dz, AutoBuild.Occupant.EMPTY);
        }

        @Override
        public boolean hasMaterial(char role) {
            return !noMaterial.contains(role);
        }

        @Override
        public boolean canStand(char role, int dx, int dy, int dz) {
            return !noSupport.contains(dx + "," + dy + "," + dz);
        }
    }

    @Test
    void clearsComeFirstFromTheTopDown() {
        List<StageGhost.Cell> diff = List.of(missing(0, -1, 2, 'S'), blocked(1, 1, 0), blocked(1, 3, 0));
        Fake world = new Fake().put(1, 1, 0, AutoBuild.Occupant.TERRAIN).put(1, 3, 0, AutoBuild.Occupant.TERRAIN);
        AutoBuild.Step step = AutoBuild.next(diff, world);
        assertNotNull(step);
        assertEquals(AutoBuild.Action.CLEAR, step.action());
        assertEquals(3, step.cell().dy());
    }

    @Test
    void solidsGoBottomUpBeforeLanternsAndFluidsLast() {
        List<StageGhost.Cell> diff = List.of(missing(0, 2, 2, 'v'), missing(0, 3, 2, 'l'), missing(0, 2, 3, 'S'),
                missing(0, -1, 3, 'S'));
        List<StageGhost.Cell> order = AutoBuild.ordered(diff);
        assertEquals(List.of(diff.get(3), diff.get(2), diff.get(1), diff.get(0)), order);
    }

    @Test
    void aPlayersBlockIsNeverTouched() {
        List<StageGhost.Cell> diff = List.of(blocked(1, 1, 0), missing(2, 0, 0, 'S'));
        Fake world = new Fake().put(1, 1, 0, AutoBuild.Occupant.FOREIGN).put(2, 0, 0, AutoBuild.Occupant.FOREIGN);
        assertNull(AutoBuild.next(diff, world));
        assertEquals(AutoBuild.Wait.FOREIGN, AutoBuild.why(diff, world));
        assertEquals(2, AutoBuild.foreign(diff, world).size());
    }

    @Test
    void terrainAndOwnBlocksAreReplacedInOneStep() {
        List<StageGhost.Cell> diff = List.of(missing(2, 0, 0, 's'), missing(3, 0, 0, 'S'));
        Fake world = new Fake().put(2, 0, 0, AutoBuild.Occupant.OWN).put(3, 0, 0, AutoBuild.Occupant.TERRAIN);
        AutoBuild.Step step = AutoBuild.next(diff, world);
        assertNotNull(step);
        assertEquals(AutoBuild.Action.PLACE, step.action());
    }

    @Test
    void noMaterialSkipsToWhatCanBeBuiltThenWaits() {
        List<StageGhost.Cell> diff = List.of(missing(0, -1, 2, 'i'), missing(0, 0, 2, 'S'));
        Fake world = new Fake();
        world.noMaterial.add('i');
        AutoBuild.Step step = AutoBuild.next(diff, world);
        assertNotNull(step);
        assertEquals('S', step.cell().role());
        assertEquals(Map.of("iron", 1), AutoBuild.missing(diff, world));
        assertNull(AutoBuild.next(List.of(diff.get(0)), world));
        assertEquals(AutoBuild.Wait.MATERIAL, AutoBuild.why(List.of(diff.get(0)), world));
    }

    @Test
    void aLanternWithNothingToHoldItWaitsForItsSupport() {
        List<StageGhost.Cell> diff = List.of(missing(0, 3, 2, 'l'));
        Fake world = new Fake();
        world.noSupport.add("0,3,2");
        assertNull(AutoBuild.next(diff, world));
        assertEquals(AutoBuild.Wait.SUPPORT, AutoBuild.why(diff, world));
    }

    @Test
    void everyShippedStageBuildsFromBareGroundInOrder() {
        // Placing in the planned order onto empty ground, where an attachment needs a solid cell under or over it,
        // never leaves a cell waiting for support.
        for (HearthDesigns.Lane lane : HearthDesigns.shipped().lanes()) {
            for (int s = 1; s <= HearthDesigns.STAGES; s++) {
                HearthDesigns.Stage stage = lane.stage(s);
                Set<String> placed = new HashSet<>();
                AutoBuild.World world = new AutoBuild.World() {
                    @Override
                    public AutoBuild.Occupant at(int dx, int dy, int dz) {
                        return AutoBuild.Occupant.EMPTY;
                    }

                    @Override
                    public boolean hasMaterial(char role) {
                        return true;
                    }

                    @Override
                    public boolean canStand(char role, int dx, int dy, int dz) {
                        if (AutoBuild.phase(new StageGhost.Cell(dx, dy, dz, role, StageGhost.Kind.MISSING)) != 2) {
                            return true;
                        }
                        return placed.contains(dx + "," + (dy - 1) + "," + dz) || placed.contains(dx + "," + (dy + 1) + "," + dz)
                                || role == 'N' || role == 'B';
                    }
                };
                List<StageGhost.Cell> diff = new java.util.ArrayList<>(StageGhost.diff(stage, (r, x, y, z) -> false));
                diff.removeIf(c -> c.kind() == StageGhost.Kind.BLOCKED);
                while (!diff.isEmpty()) {
                    AutoBuild.Step step = AutoBuild.next(diff, world);
                    assertNotNull(step, lane.id() + " " + s + " stuck at " + diff.get(0));
                    placed.add(step.cell().dx() + "," + step.cell().dy() + "," + step.cell().dz());
                    diff.remove(step.cell());
                }
                assertTrue(placed.size() > 0);
            }
        }
    }

    @Test
    void theNextStageKeepsTheLaneTheHearthIsBuiltAs() {
        // Every lane's stage II built in turn: stage III must come from that same lane, so its reshaped cells
        // count as the Hearth's own and not as the player's blocks.
        HearthDesigns designs = HearthDesigns.shipped();
        for (int i = 0; i < designs.lanes().size(); i++) {
            HearthDesigns.Stage built = designs.lanes().get(i).stage(2);
            Map<String, Character> placed = new HashMap<>();
            for (HearthDesigns.Cell cell : built.cells()) {
                if (cell.role() != '.' && cell.role() != 'F') {
                    placed.put(cell.dx() + "," + cell.dy() + "," + cell.dz(), cell.role());
                }
            }
            HearthDesigns.Probe probe = (role, dx, dy, dz) -> {
                Character here = placed.get(dx + "," + dy + "," + dz);
                return role == '.' ? here == null : here != null && here == role;
            };
            int lane = AutoBuild.builtLane(designs, probe, 3);
            assertEquals(0, StageGhost.wrong(designs.lanes().get(lane).stage(2), probe),
                    designs.lanes().get(i).id() + " built, picked " + designs.lanes().get(lane).id());
        }
    }
}
