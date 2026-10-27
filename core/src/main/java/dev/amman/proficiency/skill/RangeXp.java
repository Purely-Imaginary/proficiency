package dev.amman.proficiency.skill;

/**
 * The distance factor of a bow or crossbow hit's XP: a longer shot pays more, logarithmically.
 * Every doubling of the distance past 8 blocks adds the same share ({@code rangedXpPerDoubling},
 * default +50%): 16 blocks pay 1.5x, 32 blocks 2x, 64 blocks 2.5x, 128 blocks and beyond 3x.
 * Closer than 8 blocks pays the plain hit. Pure, so the curve is unit tested without a game.
 */
public final class RangeXp {

    /** Where the bonus starts: a shot from closer pays the plain hit. */
    public static final double START_BLOCKS = 8.0;
    /** Counted up to here, so a shot across a valley cannot pay without end. */
    public static final double CAP_BLOCKS = 128.0;
    /** The share each doubling of the distance past {@link #START_BLOCKS} adds. */
    public static final double DEFAULT_PER_DOUBLING = 0.5;

    private RangeXp() {
    }

    /** The skills a shot trains; only these pay for distance. */
    public static boolean pays(Skill skill) {
        return skill == Skill.ARCHERY || skill == Skill.CROSSBOWS;
    }

    /** The XP factor of a hit from {@code distance} blocks: 1 + perDoubling x log2(distance / 8). */
    public static double factor(double distance, double perDoubling) {
        if (!(distance > START_BLOCKS) || !(perDoubling > 0)) {
            return 1.0;
        }
        double counted = Math.min(distance, CAP_BLOCKS);
        return 1.0 + perDoubling * Math.log(counted / START_BLOCKS) / Math.log(2.0);
    }

    /** {@link #factor} for one hit of {@code skill}: 1 for every skill that is not a shot. */
    public static double factor(Skill skill, double distance, double perDoubling) {
        return pays(skill) ? factor(distance, perDoubling) : 1.0;
    }
}
