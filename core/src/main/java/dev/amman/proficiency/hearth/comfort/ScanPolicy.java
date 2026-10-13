package dev.amman.proficiency.hearth.comfort;

import dev.amman.proficiency.hearth.HearthData;
import dev.amman.proficiency.hearth.HearthState;

/**
 * When a Hearth rescans its Comfort and Character (docs/HEARTH-SPEC.md, "Scan cost"): never per
 * tick. A scan is due
 *
 * <ul>
 * <li>once for a Hearth that never scanned;</li>
 * <li>when something changed in range (a placement, a break, an explosion) and the last scan is at
 * least {@code scanCooldownSeconds} (30) old;</li>
 * <li>every {@code rescanSeconds} (300) while someone is in range, to catch what no event reports
 * (fire, endermen, water, pistons, WorldEdit).</li>
 * </ul>
 *
 * Times are game ticks. A last scan in the future (the clock was set back) counts as never.
 */
public final class ScanPolicy {

    private ScanPolicy() {
    }

    public static boolean due(long lastScan, long now, boolean changed, boolean someoneInRange,
            HearthData.Comfort config) {
        if (lastScan == HearthState.NEVER || lastScan > now) {
            return true;
        }
        long since = now - lastScan;
        if (changed && since >= ticks(config.scanCooldownSeconds())) {
            return true;
        }
        return someoneInRange && since >= ticks(config.rescanSeconds());
    }

    /** Seconds to game ticks, rounded up; never negative. */
    public static long ticks(double seconds) {
        return seconds > 0 ? (long) Math.ceil(seconds * 20.0) : 0L;
    }
}
