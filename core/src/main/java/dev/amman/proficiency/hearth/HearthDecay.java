package dev.amman.proficiency.hearth;

/**
 * Lazy decay (docs/HEARTH-SPEC.md, "Tiers, cap and decay"). The block entity stores when someone
 * was last there and when decay was last applied; whenever it ticks (or first ticks after its
 * chunk loads) the time in between is charged at once: {@code perHour} of the charge per game hour,
 * compounding, so applying it in many small steps or one big one gives the same result. For the
 * first {@code graceHours} after the last visit the charge never falls below the floor of the tier
 * it had. Game time only, so server downtime costs nothing.
 *
 * <p>Call it only when nobody counts as present: while someone is home the caller moves both
 * clocks to now and nothing decays.
 */
public final class HearthDecay {

    private HearthDecay() {
    }

    /**
     * The charge after decaying from {@code max(lastVisited, lastApplied)} to {@code now}.
     *
     * @param lastVisited game time of the last visit, or a negative number for "never visited"
     *                    (then the grace has long passed)
     */
    public static double apply(double charge, long now, long lastVisited, long lastApplied, HearthData data) {
        if (Double.isNaN(charge) || charge <= 0) {
            return 0.0;
        }
        if (Double.isInfinite(charge)) {
            return charge;
        }
        long from = Math.max(lastVisited, lastApplied);
        if (now <= from) {
            return charge;
        }
        double perHour = data.decay().perHour();
        if (perHour <= 0) {
            return charge;
        }
        long graceEnd = lastVisited < 0 ? Long.MIN_VALUE
                : lastVisited + Math.round(data.decay().graceHours() * HearthData.TICKS_PER_HOUR);
        double result = charge;
        if (from < graceEnd) {
            // Inside the grace: decay, but never below the floor of the tier held now.
            long end = Math.min(now, graceEnd);
            double floor = data.threshold(HearthRules.chargeTier(charge, data));
            result = Math.max(floor, decay(result, end - from, perHour));
            from = end;
        }
        if (now > from) {
            result = decay(result, now - from, perHour);
        }
        return result;
    }

    private static double decay(double charge, long ticks, double perHour) {
        double hours = ticks / (double) HearthData.TICKS_PER_HOUR;
        return charge * Math.pow(1.0 - Math.min(1.0, perHour), hours);
    }
}
