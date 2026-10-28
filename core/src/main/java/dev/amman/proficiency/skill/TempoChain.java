package dev.amman.proficiency.skill;

/**
 * One player's Tempo chain in one skill, and what the server last told the client about it. Pure
 * numbers so the break, restart and announce rules are unit tested; {@code Tempo} owns the game side.
 */
public final class TempoChain {

    public long lastTick;
    public long startedTick;
    public int steps;
    /** Actions in this chain, uncapped (the steps stop at the Tempo cap): what Records counts. */
    public int actions;
    /** False until the first {@link #advance}: a chain nobody has stepped is not alive. */
    public boolean begun;
    /**
     * The longer window the next gap gets after a vanilla jump crit (1.49.0), or 0: one gap only, the next
     * action sets it again (to 0 unless that action is a crit too).
     */
    public long graceWindow;

    private boolean sent;
    private int sentSteps;
    private long sentTick;
    private long sentGrace;

    public TempoChain copy() {
        TempoChain copy = new TempoChain();
        copy.lastTick = lastTick;
        copy.startedTick = startedTick;
        copy.steps = steps;
        copy.actions = actions;
        copy.begun = begun;
        copy.graceWindow = graceWindow;
        return copy;
    }

    /**
     * Takes one action at {@code now}: the chain breaks when the window lapsed, the clock went backwards or it
     * has run for {@code maxStreakTicks}, and otherwise climbs one step up to {@code maxSteps}.
     */
    public void advance(long now, long window, int maxSteps, long maxStreakTicks) {
        advance(now, window, maxSteps, maxStreakTicks, 0);
    }

    /**
     * {@link #advance(long, long, int, long)} for an action that also sets the next gap's window: {@code nextGrace}
     * ticks after a vanilla jump crit, 0 for an ordinary action. The gap this action closes is judged by the grace
     * the previous action left.
     */
    public void advance(long now, long window, int maxSteps, long maxStreakTicks, long nextGrace) {
        boolean broken = !begun || now - lastTick > window(window) || now < lastTick || now - startedTick > maxStreakTicks;
        if (broken) {
            steps = 0;
            actions = 1;
            startedTick = now;
        } else {
            if (steps < maxSteps) {
                steps++;
            }
            actions++;
        }
        lastTick = now;
        begun = true;
        graceWindow = Math.max(0, nextGrace);
    }

    /** The window the gap after the last action gets: {@code window}, or the crit grace when that is longer. */
    public long window(long window) {
        return Math.max(window, graceWindow);
    }

    /**
     * True when the next {@link #advance} at {@code now} would climb rather than break: the chain has a first
     * action, the window has not lapsed, the clock did not go backwards and the chain is younger than
     * {@code maxStreakTicks}. A rider reads a weapon chain only while this holds.
     */
    public boolean alive(long now, long window, long maxStreakTicks) {
        return begun && now >= lastTick && now - lastTick <= window(window) && now - startedTick <= maxStreakTicks;
    }

    /**
     * True when the client should be told about this chain now (and records that it was): the first time, a
     * changed step, or the heartbeat of a capped chain. Each skill has its own record, so an action that pays two
     * skills sends one packet per skill that changed, never a flip-flop.
     */
    public boolean takeAnnouncement(long now, long windowTicks) {
        return takeAnnouncement(now, windowTicks, false);
    }

    /**
     * As {@link #takeAnnouncement(long, long)}, and also when the crit grace changed or {@code force} (a crit at
     * the cap renews a long window the client must hear about at once, or its flames fade early).
     */
    public boolean takeAnnouncement(long now, long windowTicks, boolean force) {
        if (sent && !force && graceWindow == sentGrace
                && !TempoSync.shouldSend(0, sentSteps, sentTick, 0, steps, now, windowTicks)) {
            return false;
        }
        sent = true;
        sentSteps = steps;
        sentTick = now;
        sentGrace = graceWindow;
        return true;
    }
}
