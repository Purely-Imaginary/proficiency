package dev.amman.proficiency.skill;

/**
 * When the server tells a client about a Tempo chain. Never per tick: a packet goes out when the
 * skill or the step count changed, and as a slow heartbeat when a chain sits at its cap (the step
 * does not move there, but the window keeps being renewed and the client has to know).
 */
public final class TempoSync {

    /** The least gap between two heartbeats of an unchanged chain, in ticks. */
    public static final int MIN_HEARTBEAT_TICKS = 10;

    private TempoSync() {
    }

    /**
     * How long an unchanged chain waits before it is announced again: a sixth of the window. The client
     * counts the window from the last packet, so the real expiry and the shown one differ by at most this.
     */
    public static long heartbeat(long windowTicks) {
        return Math.max(MIN_HEARTBEAT_TICKS, windowTicks / 6);
    }

    /**
     * @param lastSkill ordinal last sent, or -1 for nothing sent yet
     * @param lastSteps steps last sent
     * @param lastTick  game time of the last send
     */
    public static boolean shouldSend(int lastSkill, int lastSteps, long lastTick, int skill, int steps, long now,
            long windowTicks) {
        if (lastSkill != skill || lastSteps != steps || now < lastTick) {
            return true;
        }
        return now - lastTick >= heartbeat(windowTicks);
    }
}
