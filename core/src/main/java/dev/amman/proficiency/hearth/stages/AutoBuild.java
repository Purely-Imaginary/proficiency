package dev.amman.proficiency.hearth.stages;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * The Hearth's self-build: which wrong cell of the next stage it fixes next, one block per step. Pure: the game
 * says what stands in each cell and whether a cell can take its block now. The order keeps every block placeable:
 * terrain in the way goes first (top down, so sand and gravel cannot fall into a cleared cell), then the solid
 * blocks bottom up, then what hangs on or stands on them (lanterns, chains, candles, carpets, pots, banners, skulls,
 * the bell), then water and lava once their walls stand. A cell held by a block the player placed is never touched.
 */
public final class AutoBuild {

    /** What stands in a wrong cell. */
    public enum Occupant {
        /** Air, or something a block simply replaces (grass, a flower, flowing water). */
        EMPTY,
        /** Natural ground: dirt, stone, sand, gravel, snow. Cleared, its drops go to a home chest. */
        TERRAIN,
        /** A block of the Hearth's own lower stage that this stage reshapes (a full stone that becomes a slab). */
        OWN,
        /** Anything else: the player's block. The build leaves it and says so. */
        FOREIGN
    }

    public enum Action { CLEAR, PLACE }

    /** One step: a cell and what to do there. A PLACE on TERRAIN or OWN replaces the block in one step. */
    public record Step(StageGhost.Cell cell, Action action) {
    }

    /** The game's side of the plan. */
    public interface World {
        Occupant at(int dx, int dy, int dz);

        /** Whether the sources hold a block (or a bucket) for this role. */
        boolean hasMaterial(char role);

        /** Whether the role's block would stay where it goes now (a lantern needs a block below or above). */
        boolean canStand(char role, int dx, int dy, int dz);
    }

    /** Why nothing can be done this step. */
    public enum Wait { NONE, MATERIAL, FOREIGN, SUPPORT }

    /** The next step, or null when nothing can be done now ({@link #why} says what is wrong). */
    public static Step next(List<StageGhost.Cell> diff, World world) {
        for (StageGhost.Cell cell : ordered(diff)) {
            Step step = stepFor(cell, world);
            if (step != null) {
                return step;
            }
        }
        return null;
    }

    /** What blocks the build when {@link #next} gives null: no material first, then a player's block, then support. */
    public static Wait why(List<StageGhost.Cell> diff, World world) {
        if (diff.isEmpty()) {
            return Wait.NONE;
        }
        boolean foreign = false;
        boolean material = false;
        for (StageGhost.Cell cell : diff) {
            Occupant here = world.at(cell.dx(), cell.dy(), cell.dz());
            if (here == Occupant.FOREIGN) {
                foreign = true;
            } else if (cell.kind() == StageGhost.Kind.MISSING && !world.hasMaterial(cell.role())) {
                material = true;
            }
        }
        return material ? Wait.MATERIAL : foreign ? Wait.FOREIGN : Wait.SUPPORT;
    }

    /** The cells held by a player's block, in scan order. */
    public static List<StageGhost.Cell> foreign(List<StageGhost.Cell> diff, World world) {
        List<StageGhost.Cell> out = new ArrayList<>();
        for (StageGhost.Cell cell : diff) {
            if (world.at(cell.dx(), cell.dy(), cell.dz()) == Occupant.FOREIGN) {
                out.add(cell);
            }
        }
        return out;
    }

    /** Material group to blocks still missing that the sources cannot give, in {@link StageMaterials#GROUPS} order. */
    public static Map<String, Integer> missing(List<StageGhost.Cell> diff, World world) {
        Map<String, Integer> counts = new LinkedHashMap<>();
        for (String group : StageMaterials.GROUPS) {
            counts.put(group, 0);
        }
        for (StageGhost.Cell cell : diff) {
            if (cell.kind() == StageGhost.Kind.MISSING && !world.hasMaterial(cell.role())) {
                String group = StageMaterials.group(cell.role());
                if (group != null) {
                    counts.merge(group, 1, Integer::sum);
                }
            }
        }
        counts.values().removeIf(n -> n == 0);
        return counts;
    }

    private static Step stepFor(StageGhost.Cell cell, World world) {
        Occupant here = world.at(cell.dx(), cell.dy(), cell.dz());
        if (here == Occupant.FOREIGN) {
            return null;
        }
        if (cell.kind() == StageGhost.Kind.BLOCKED) {
            return new Step(cell, Action.CLEAR);
        }
        if (!world.hasMaterial(cell.role()) || !world.canStand(cell.role(), cell.dx(), cell.dy(), cell.dz())) {
            return null;
        }
        return new Step(cell, Action.PLACE);
    }

    /** Clears top down, then solids, attachments and fluids, each bottom up; scan order within a layer. */
    static List<StageGhost.Cell> ordered(List<StageGhost.Cell> diff) {
        List<StageGhost.Cell> out = new ArrayList<>(diff);
        out.sort(Comparator.comparingInt((StageGhost.Cell c) -> phase(c))
                .thenComparingInt(c -> phase(c) == 0 ? -c.dy() : c.dy())
                .thenComparingInt(StageGhost.Cell::dz)
                .thenComparingInt(StageGhost.Cell::dx));
        return out;
    }

    /** 0 clear, 1 solid, 2 attachment, 3 fluid. */
    static int phase(StageGhost.Cell cell) {
        if (cell.kind() == StageGhost.Kind.BLOCKED) {
            return 0;
        }
        return switch (cell.role()) {
            case 'l', 'h', 'f', 'C', 'm', 'z', 'N', 'B' -> 2;
            case 'v', 'V' -> 3;
            default -> 1;
        };
    }

    /**
     * The design the Hearth stands as: of the lanes whose stage {@code target - 1} stands complete, the one closest
     * to {@code target}; with none complete, the closest lane. A later stage reshapes its own lane's blocks, so
     * picking by the next stage alone could take another lane and call the Hearth's own blocks the player's.
     */
    public static int builtLane(HearthDesigns designs, HearthDesigns.Probe probe, int target) {
        int best = -1;
        int bestWrong = Integer.MAX_VALUE;
        if (target > 1) {
            List<HearthDesigns.Lane> lanes = designs.lanes();
            for (int i = 0; i < lanes.size(); i++) {
                if (StageGhost.wrong(lanes.get(i).stage(target - 1), probe) != 0) {
                    continue;
                }
                int wrong = StageGhost.wrong(lanes.get(i).stage(target), probe);
                if (wrong < bestWrong) {
                    best = i;
                    bestWrong = wrong;
                }
            }
        }
        return best >= 0 ? best : StageGhost.closestLane(designs, probe, target);
    }

    private AutoBuild() {
    }
}
