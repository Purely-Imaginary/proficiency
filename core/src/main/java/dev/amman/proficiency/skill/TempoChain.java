package dev.amman.proficiency.skill;

/**
 * One player's Tempo chain in one skill, and what the server last told the client about it. Pure
 * numbers so the break, restart and announce rules are unit tested; {@code Tempo} owns the game side.
 */
public final class TempoChain {

    public long lastTick;
    public long startedTick;
    public int steps;

    private boolean sent;
    private int sentSteps;
    private long sentTick;

    public TempoChain copy() {
        TempoChain copy = new TempoChain();
        copy.lastTick = lastTick;
        copy.startedTick = startedTick;
        copy.steps = steps;
        return copy;
    }

    /**
     * Takes one action at {@code now}: the chain breaks when the window lapsed, the clock went backwards or it
     * has run for {@code maxStreakTicks}, and otherwise climbs one step up to {@code maxSteps}.
     */
    public void advance(long now, long window, int maxSteps, long maxStreakTicks) {
        boolean broken = now - lastTick > window || now < lastTick || now - startedTick > maxStreakTicks;
        if (broken) {
            steps = 0;
            startedTick = now;
        } else if (steps < maxSteps) {
            steps++;
        }
        lastTick = now;
    }

    /**
     * True when the client should be told about this chain now (and records that it was): the first time, a
     * changed step, or the heartbeat of a capped chain. Each skill has its own record, so an action that pays two
     * skills sends one packet per skill that changed, never a flip-flop.
     */
    public boolean takeAnnouncement(long now, long windowTicks) {
        if (sent && !TempoSync.shouldSend(0, sentSteps, sentTick, 0, steps, now, windowTicks)) {
            return false;
        }
        sent = true;
        sentSteps = steps;
        sentTick = now;
        return true;
    }
}
