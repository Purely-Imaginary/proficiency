package dev.amman.proficiency.skill;

/** Which announced level a level-up crossed. Pure, so the unit tests can pin it down. */
public final class Milestones {

    private Milestones() {
    }

    /**
     * The highest multiple of {@code interval} in {@code (from, to]} that is at least
     * {@code minLevel}, or -1 if the step crossed none. One gain can jump two levels (9 to 11),
     * and checking only the level it landed on would skip the 10 it passed.
     */
    public static int crossed(int from, int to, int interval, int minLevel) {
        if (interval <= 0 || to <= from) {
            return -1;
        }
        int highest = to - Math.floorMod(to, interval);
        return highest > from && highest >= minLevel && highest > 0 ? highest : -1;
    }

    /** True if {@code (from, to]} contains {@code level}: a threshold such as the proc unlock. */
    public static boolean passed(int from, int to, int level) {
        return from < level && level <= to;
    }
}
