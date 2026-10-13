package dev.amman.proficiency.hearth.effects;

/**
 * The per-player loop's small decisions (docs/HEARTH-SPEC.md, "Warmth" and "Setting out and coming
 * home"): which edge a second crossed, whether a wake-up was a real night's sleep, and when the HUD
 * payload is worth sending. Pure.
 */
public final class HomeRules {

    /** How long after a night-time sighting in bed a wake-up still counts as that night's sleep. */
    public static final long NIGHT_SLEEP_GRACE_TICKS = 60L;

    /** Seconds after a login before the edge cues may fire (the Hearth has not seen the player yet). */
    public static final int LOGIN_GRACE_SECONDS = 3;

    private HomeRules() {
    }

    /** What happened at the edge of home this second. */
    public enum Edge {
        /** No change, or a change nobody walked (the Hearth went out, broke, or was switched off). */
        NONE,
        /** Came into range of a lit Hearth. */
        ENTER,
        /** Walked (or travelled) out of range below full Warmth. */
        LEAVE,
        /** Walked out at full Warmth: the cue, and Provisioned. */
        SET_OUT
    }

    /**
     * @param wasHome    home at the end of the last second
     * @param home       home now (in range of a lit Hearth, not smouldering, switched on)
     * @param wasFull    Warmth was full at the end of the last second
     * @param walkedAway the player left a Hearth that still burns (not one that broke, smouldered or
     *                   was switched off under them)
     */
    public static Edge edge(boolean wasHome, boolean home, boolean wasFull, boolean walkedAway) {
        if (home == wasHome) {
            return Edge.NONE;
        }
        if (home) {
            return Edge.ENTER;
        }
        if (!walkedAway) {
            return Edge.NONE;
        }
        return wasFull ? Edge.SET_OUT : Edge.LEAVE;
    }

    /**
     * A wake-up fills Warmth only after a real night's sleep: the sleep timer reached 100, and it is
     * night (or a thunderstorm) now, or the player was seen asleep at night moments ago (vanilla
     * wakes everyone at dawn, when it is already day). A nap a mod allows by day does nothing.
     *
     * @param lastNightSighting the game time the player was last seen asleep at night, or
     *                          {@code Long.MIN_VALUE}
     */
    public static boolean realSleep(boolean sleptLongEnough, boolean nightNow, long lastNightSighting, long now) {
        if (!sleptLongEnough) {
            return false;
        }
        if (nightNow) {
            return true;
        }
        return lastNightSighting != Long.MIN_VALUE && now >= lastNightSighting
                && now - lastNightSighting <= NIGHT_SLEEP_GRACE_TICKS;
    }

    /**
     * What the HUD payload last told the client. The client interpolates Warmth with the rate, so a
     * new payload is due only when something it cannot predict changed.
     */
    public record Hud(boolean home, long hearthPos, float warmth, float rate, float provisioned,
            long provisionedFrom, long provisionedUntil, float tales, int flags, long sentAt) {
    }

    /** Warmth may drift this far from what the client predicts before a fresh payload goes out. */
    public static final double WARMTH_DRIFT = 0.02;

    /** Whether {@code next} needs sending after {@code last} ({@code now} in game ticks). */
    public static boolean needsSend(Hud last, Hud next, long now) {
        if (last == null) {
            return true;
        }
        if (last.home() != next.home() || last.hearthPos() != next.hearthPos() || last.flags() != next.flags()
                || last.provisionedFrom() != next.provisionedFrom() || last.provisionedUntil() != next.provisionedUntil()
                || Math.abs(last.provisioned() - next.provisioned()) > 1e-4
                || Math.abs(last.rate() - next.rate()) > 1e-6
                || Math.abs(last.tales() - next.tales()) >= 0.5f) {
            return true;
        }
        double seconds = Math.max(0, now - last.sentAt()) / 20.0;
        double predicted = Math.max(0.0, Math.min(1.0, last.warmth() + last.rate() * seconds));
        return Math.abs(predicted - next.warmth()) > WARMTH_DRIFT;
    }
}
