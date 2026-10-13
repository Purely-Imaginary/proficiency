package dev.amman.proficiency.hearth.sieges;

import dev.amman.proficiency.hearth.HearthData;

/**
 * The douse meter (docs/HEARTH-SPEC.md, "They come for the fire"): every siege mob standing next to
 * the Hearth adds {@code dousePerMobSecond} a second; at {@code douseMax} the Hearth is doused. The
 * meter never drains during a siege and starts at 0 with each one. Pure.
 */
public final class DouseMeter {

    /** The warning steps, in percent: the players in range hear the fire hiss and read a line. */
    public static final int[] WARNINGS = {50, 80};

    private DouseMeter() {
    }

    /** The meter after {@code seconds} with {@code mobsNear} siege mobs by the fire, capped at the maximum. */
    public static double step(double douse, int mobsNear, double seconds, HearthData.Siege s) {
        double now = Double.isFinite(douse) ? Math.max(0.0, douse) : 0.0;
        if (mobsNear <= 0 || !(seconds > 0)) {
            return Math.min(now, s.douseMax());
        }
        return Math.min(s.douseMax(), now + mobsNear * s.dousePerMobSecond() * seconds);
    }

    public static boolean full(double douse, HearthData.Siege s) {
        return douse >= s.douseMax();
    }

    /** The meter in whole percent, 0..100. */
    public static int percent(double douse, HearthData.Siege s) {
        if (!(s.douseMax() > 0)) {
            return 0;
        }
        return (int) Math.max(0, Math.min(100, Math.floor(douse * 100.0 / s.douseMax())));
    }

    /** The highest warning step the meter crossed going from {@code before} to {@code after}, or 0. */
    public static int crossed(double before, double after, HearthData.Siege s) {
        int was = percent(before, s);
        int is = percent(after, s);
        int hit = 0;
        for (int step : WARNINGS) {
            if (was < step && is >= step && is < 100) {
                hit = step;
            }
        }
        return hit;
    }
}
