package dev.amman.proficiency.hearth.stages;

/**
 * The Hearth's build rules (docs/HEARTH-SPEC.md "Building and upgrading", docs/HEARTH-DESIGNS-SPEC.md):
 * the shipped {@link HearthDesigns} read through a {@link Probe}. Pure, so a unit test builds a Hearth out
 * of a map and the game reads the world through the same rules.
 *
 * <p>The built stage is the highest stage that any lane matches in full. The problem a player is told about
 * is the next stage's: the lane closest to complete, its first wrong cell and the count still wrong.
 */
public final class HearthStructure {

    /** The check box: 9 x 9 around the campfire, from the floor (y -1) to five above the fire. */
    public static final int BOX_RADIUS = HearthDesigns.RADIUS;
    public static final int BOX_BELOW = -HearthDesigns.MIN_Y;
    public static final int BOX_ABOVE = HearthDesigns.MAX_Y;

    /** What stands where, relative to the campfire. The game answers from block tags. */
    @FunctionalInterface
    public interface Probe extends HearthDesigns.Probe {
    }

    /** The first wrong cell of a stage's closest lane (see {@link HearthDesigns.Problem}). */
    public record Problem(int stage, String lane, char role, int dx, int dy, int dz, int remaining) {
        static Problem of(HearthDesigns.Problem p) {
            return p == null ? null : new Problem(p.stage(), p.lane(), p.role(), p.dx(), p.dy(), p.dz(), p.remaining());
        }
    }

    /** A whole check: the built stage (0..6) and the problem of the next one (null at VI). */
    public record Check(int built, Problem problem) {
    }

    private HearthStructure() {
    }

    private static HearthDesigns designs() {
        return HearthDesigns.shipped();
    }

    /** The built stage and the next stage's problem. */
    public static Check check(Probe probe) {
        HearthDesigns.Result result = designs().check(probe);
        return new Check(result.built(), Problem.of(result.problem()));
    }

    /** Stage {@code stage}'s problem, or null when some lane's stage {@code stage} stands. */
    public static Problem layer(Probe probe, int stage) {
        if (stage < 1 || stage > HearthDesigns.STAGES) {
            return null;
        }
        return Problem.of(designs().problem(probe, stage));
    }

    /**
     * What keeps stage {@code upTo} from standing: null when it, or a later stage, stands (a later stage
     * may reshape what an earlier one asks for, so the earlier ones are not asked).
     */
    public static Problem firstProblem(Probe probe, int upTo) {
        int stage = Math.min(HearthDesigns.STAGES, upTo);
        if (stage < 1) {
            return null;
        }
        HearthDesigns designs = designs();
        for (int k = stage; k <= HearthDesigns.STAGES; k++) {
            if (designs.matches(probe, k)) {
                return null;
            }
        }
        return Problem.of(designs.problem(probe, stage));
    }

    /** The built stage a Hearth has: what stands, never above the offerings paid. */
    public static int stage(int built, int highestOffering) {
        return Math.max(0, Math.min(built, highestOffering));
    }

    /** Whether an offset lies in the check box (block changes there re-check the build). */
    public static boolean inBox(int dx, int dy, int dz) {
        return Math.abs(dx) <= BOX_RADIUS && Math.abs(dz) <= BOX_RADIUS && dy >= -BOX_BELOW && dy <= BOX_ABOVE;
    }

    /**
     * The positions of the design the Hearth stands as (its fire only when it stands as nothing). The Comfort
     * scan skips these ("the Hearth structure's own blocks never count"); the bell hangs anywhere in the box
     * and is not a fixed position.
     */
    public static HearthDesigns.Footprint footprint(Probe probe) {
        return designs().footprint(probe);
    }
}
