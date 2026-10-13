package dev.amman.proficiency.hearth.client;

/**
 * Where the range ring's particles go and how many (docs/HEARTH-SPEC.md, "Showing the range": a
 * ring of client particles where the cylinder meets the terrain, about 75 points, a pulse every
 * 2 s, and a red ring for other Hearths' spacing). No Minecraft here; the client finds the ground
 * under each point.
 */
public final class RangeRing {

    /** Points on the ring at full particles. */
    public static final int POINTS = 75;
    /** One pulse every this many milliseconds. */
    public static final long PULSE_MS = 2000;
    /** How long the panel's "Show range" keeps the ring up. */
    public static final long SHOW_MS = 30_000;
    /** The spacing ring is only drawn this close to the player (blocks), so a 64-block ring stays cheap. */
    public static final double SPACING_VIEW = 48;

    /** The Particles video setting, as vanilla orders it. */
    public enum Particles { ALL, DECREASED, MINIMAL }

    private RangeRing() {
    }

    /** Points per ring for the Particles setting: all, half, a quarter (the ring stays readable). */
    public static int points(Particles setting) {
        return switch (setting) {
            case ALL -> POINTS;
            case DECREASED -> (POINTS + 1) / 2;
            case MINIMAL -> (POINTS + 3) / 4;
        };
    }

    /**
     * The x (index 0) and z (index 1) of point {@code i} of {@code count} on a circle of
     * {@code radius} round ({@code cx}, {@code cz}); the circle starts north and runs clockwise.
     * {@code phase} turns the whole ring (0..1 of a step) so successive pulses do not land on the
     * same blocks.
     */
    public static double[] point(double cx, double cz, double radius, int i, int count, double phase) {
        double angle = 2 * Math.PI * (i + phase) / Math.max(1, count);
        return new double[] {cx + Math.sin(angle) * radius, cz - Math.cos(angle) * radius};
    }

    /** True when a pulse is due: the first one at once, then every {@link #PULSE_MS}. */
    public static boolean pulseDue(long lastPulse, long now) {
        return lastPulse == Long.MIN_VALUE || now - lastPulse >= PULSE_MS || now < lastPulse;
    }

    /** True when the point is close enough to the player to be worth a particle. */
    public static boolean near(double x, double z, double px, double pz, double reach) {
        double dx = x - px;
        double dz = z - pz;
        return dx * dx + dz * dz <= reach * reach;
    }

    /**
     * Where the ring meets the ground at one column: the first top surface from {@code startY}
     * within {@code [minY, maxY]}. {@code solid(y)} says whether the block at that height stops you.
     * Inside solid ground it climbs to the first open block; in the air it drops to the first solid
     * one and returns the open block above it. {@link Integer#MIN_VALUE} when the column has no
     * surface in range (a cliff face, open air all the way down).
     */
    public static int surface(int startY, int minY, int maxY, java.util.function.IntPredicate solid) {
        int y = Math.max(minY, Math.min(maxY, startY));
        if (solid.test(y)) {
            for (int up = y + 1; up <= maxY; up++) {
                if (!solid.test(up)) {
                    return up;
                }
            }
            return Integer.MIN_VALUE;
        }
        for (int down = y - 1; down >= minY; down--) {
            if (solid.test(down)) {
                return down + 1;
            }
        }
        return Integer.MIN_VALUE;
    }
}
