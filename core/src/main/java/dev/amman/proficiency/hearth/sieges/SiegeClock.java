package dev.amman.proficiency.hearth.sieges;

import dev.amman.proficiency.hearth.HearthData;
import dev.amman.proficiency.hearth.SiegeRules;

/**
 * Siege clock arithmetic the rules in {@link SiegeRules} leave to the caller: when a due siege will
 * start (for the countdown panel), and how many nightfalls a Hearth slept through while its chunk was
 * unloaded (nobody can be home then, so each one is a postponement, docs/HEARTH-SPEC.md "The siege
 * itself").
 *
 * <p>Two clocks: game time (never skipped, the unit of the due time) and day time (beds and
 * {@code /time} move it, the unit of dusk). Pure.
 */
public final class SiegeClock {

    public static final long DAY = HearthData.TICKS_PER_DAY;

    /** A Hearth ticks once a second; a dusk inside the last second is the live check's to see. */
    public static final long LIVE_TICKS = 20L;

    private SiegeClock() {
    }

    /**
     * The game time a due siege should start: the first dusk after {@code max(due, now + 1)}, read
     * from today's clocks. A bed or {@code /time} can move dusk later; the caller sends the panel
     * again when that happens (a postponement, say).
     */
    public static long expectedStart(long due, long gameNow, long dayNow) {
        long base = Math.max(due, gameNow + 1);
        long dayAtBase = dayNow + (base - gameNow);
        return base + Math.floorMod(SiegeRules.DUSK - dayAtBase, DAY);
    }

    /**
     * The dusks between two Hearth ticks that had a gap (its chunk was unloaded), counting only those
     * at or after {@code due} and leaving out one in the last {@link #LIVE_TICKS} (the live nightfall
     * check sees that one). Day time is mapped to game time linearly between the two readings, so a
     * bed skip in the gap stretches it. At most {@code max}.
     */
    public static int missedNightfalls(long lastGame, long lastDay, long gameNow, long dayNow, long due, int max) {
        if (dayNow <= lastDay || gameNow <= lastGame || max <= 0) {
            return 0;
        }
        long end = dayNow - LIVE_TICKS;
        double scale = (double) (gameNow - lastGame) / (double) (dayNow - lastDay);
        long first = (Math.floorDiv(lastDay - SiegeRules.DUSK, DAY) + 1) * DAY + SiegeRules.DUSK;
        int count = 0;
        for (long dusk = first; dusk <= end && count < max; dusk += DAY) {
            long game = lastGame + Math.round((dusk - lastDay) * scale);
            if (game >= due) {
                count++;
            }
        }
        return count;
    }

    /**
     * Whether the day clock ran ahead of game time between two Hearth ticks by more than a live step:
     * a night slept through, or {@code /time add}. The dusks it jumped over are caught up like an
     * unloaded stretch's ({@link #missedNightfalls}): nobody stood at the fire at them.
     */
    public static boolean dayJumped(long lastGame, long lastDay, long gameNow, long dayNow) {
        return (dayNow - lastDay) - (gameNow - lastGame) > 2 * LIVE_TICKS;
    }

    /** The day-clock time the night {@code dayNow} lies in began (its dusk); only meaningful at night. */
    public static long nightFell(long dayNow) {
        return dayNow - Math.floorMod(dayNow - SiegeRules.DUSK, DAY);
    }

    /**
     * Whether a bed now would sleep through the nightfall that starts a counting-down siege. A night
     * skip jumps the day clock to the next morning, so a bed before dusk (from about 12 540 in clear
     * weather, any time in a thunderstorm) on an evening the siege is due by skips that dusk; a bed in
     * the first second of the night, before the live check saw it fall, skips the start as well. A bed
     * later in the night skips no dusk (the start or the postponement already happened), and neither
     * does one in the last hour before dawn.
     *
     * @param nightBefore the Hearth's {@code nightBefore}: night at its last live check
     */
    public static boolean bedSkipsStart(long due, long gameNow, long dayNow, boolean nightBefore) {
        if (SiegeRules.isNight(dayNow)) {
            return SiegeRules.startsNow(gameNow, due, true, nightBefore);
        }
        long t = Math.floorMod(dayNow, DAY);
        return t < SiegeRules.DUSK && due <= gameNow + (SiegeRules.DUSK - t);
    }

    /** Whether the night had already begun one live step before {@code dayNow} (what "night before" means after a gap). */
    public static boolean nightBeforeLiveStep(long dayNow) {
        return SiegeRules.isNight(dayNow - LIVE_TICKS);
    }

    /** Game ticks to whole real seconds, rounded up (a countdown never reads 0 early). */
    public static long seconds(long ticks) {
        return ticks <= 0 ? 0 : (ticks + 19) / 20;
    }
}
