package dev.amman.proficiency.hearth.stages;

import java.util.ArrayList;
import java.util.List;

/**
 * What the Hearth panel's "Show build guide" draws in the world: the cells of one lane's stage that the
 * world does not yet match (docs/HEARTH-DESIGNS-SPEC.md). A cell that must hold a block but does not is
 * {@link Kind#MISSING} (a ghost of the role's default block); a cell that must be air but is blocked is
 * {@link Kind#BLOCKED} (a red outline). Cells that match, blank cells and the fire are left out. Pure: the
 * game answers the probe from block tags.
 */
public final class StageGhost {

    public enum Kind { MISSING, BLOCKED }

    /** One cell still wrong: an offset from the fire, the role it must hold and how it is wrong. */
    public record Cell(int dx, int dy, int dz, char role, Kind kind) {
    }

    private StageGhost() {
    }

    /** Every wrong cell of the stage, in scan order. */
    public static List<Cell> diff(HearthDesigns.Stage stage, HearthDesigns.Probe probe) {
        List<Cell> out = new ArrayList<>();
        for (HearthDesigns.Cell cell : stage.cells()) {
            char role = cell.role();
            if (role == 'F' || probe.is(role, cell.dx(), cell.dy(), cell.dz())) {
                continue;
            }
            out.add(new Cell(cell.dx(), cell.dy(), cell.dz(), role, role == '.' ? Kind.BLOCKED : Kind.MISSING));
        }
        return out;
    }

    /** How many cells of the stage are wrong. */
    public static int wrong(HearthDesigns.Stage stage, HearthDesigns.Probe probe) {
        int wrong = 0;
        for (HearthDesigns.Cell cell : stage.cells()) {
            if (cell.role() != 'F' && !probe.is(cell.role(), cell.dx(), cell.dy(), cell.dz())) {
                wrong++;
            }
        }
        return wrong;
    }

    /**
     * The index (in {@link HearthDesigns#lanes()}) of the lane closest to {@code stage} (1..6): the fewest
     * wrong cells, the first lane on a tie.
     */
    public static int closestLane(HearthDesigns designs, HearthDesigns.Probe probe, int stage) {
        int s = Math.max(1, Math.min(HearthDesigns.STAGES, stage));
        int best = 0;
        int bestWrong = Integer.MAX_VALUE;
        List<HearthDesigns.Lane> lanes = designs.lanes();
        for (int i = 0; i < lanes.size(); i++) {
            int wrong = wrong(lanes.get(i).stage(s), probe);
            if (wrong < bestWrong) {
                best = i;
                bestWrong = wrong;
            }
        }
        return best;
    }
}
