package dev.amman.proficiency.skill;

/**
 * Archaeology's pure numbers: Careful Hands (the Excavation talent) on a brush. No Minecraft here,
 * so it is unit tested; the game side is {@code ArchaeologyEvents} and the brush mixin.
 *
 * <p>A suspicious block needs {@link #REQUIRED_STROKES} strokes. Each rank of Careful Hands gives a
 * stroke {@link #EXTRA_STROKE_PER_RANK} to count twice (rank 3: 60%, so about 1.6 strokes per
 * stroke), and finishing the block gives {@link #SECOND_ROLL_PER_RANK} per rank to roll its loot a
 * second time (rank 3: 24%). A stroke that would finish the block on its own never counts twice:
 * the block is always finished by a stroke the game counted itself.
 */
public final class BrushRules {

    /** Vanilla's strokes to finish a suspicious block (1.20.1 and 1.21.1). */
    public static final int REQUIRED_STROKES = 10;

    public static final double EXTRA_STROKE_PER_RANK = 0.20;
    public static final double SECOND_ROLL_PER_RANK = 0.08;

    private BrushRules() {
    }

    public static double extraStrokeChance(int rank) {
        return Math.min(0.9, Math.max(0, rank) * EXTRA_STROKE_PER_RANK);
    }

    public static double secondRollChance(int rank) {
        return Math.min(0.5, Math.max(0, rank) * SECOND_ROLL_PER_RANK);
    }

    /**
     * Whether the stroke just counted ({@code strokesNow} of them so far, not finished) counts a
     * second time. {@code roll} is a uniform number in [0, 1).
     */
    public static boolean extraStroke(int rank, int strokesNow, double roll) {
        return rank > 0 && strokesNow > 0 && strokesNow < REQUIRED_STROKES - 1 && roll < extraStrokeChance(rank);
    }

    public static boolean secondRoll(int rank, double roll) {
        return rank > 0 && roll < secondRollChance(rank);
    }
}
