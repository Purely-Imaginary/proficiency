package dev.amman.proficiency.client;

/**
 * What the client knows about Tempo, per skill: the server sends a skill's chain when it changes, and an
 * action can pay two skills, so each keeps its own record. The clock is a parameter, never read here.
 */
public final class TempoState {

    private final int[] steps;
    private final int[] maxSteps;
    private final int[] permille;
    private final long[] windowMs;
    private final long[] updatedAt;
    private final long[] pulseAt;

    public TempoState(int skills) {
        steps = new int[skills];
        maxSteps = new int[skills];
        permille = new int[skills];
        windowMs = new long[skills];
        updatedAt = new long[skills];
        pulseAt = new long[skills];
        clear();
    }

    public void clear() {
        java.util.Arrays.fill(steps, 0);
        java.util.Arrays.fill(maxSteps, 1);
        java.util.Arrays.fill(permille, 1000);
        java.util.Arrays.fill(windowMs, 1);
        java.util.Arrays.fill(updatedAt, 0);
        java.util.Arrays.fill(pulseAt, Long.MIN_VALUE / 2);
    }

    public void accept(int skill, int newSteps, int newMax, int newPermille, long newWindowMs, long now) {
        if (skill < 0 || skill >= steps.length) {
            return;
        }
        newSteps = Math.max(0, newSteps);
        // Any climb from what is burning now flares, including the first step after a lapse or a break.
        int showing = lapse(skill, now) <= 0f ? steps[skill] : 0;
        if (newSteps > showing) {
            pulseAt[skill] = now;
        }
        steps[skill] = newSteps;
        maxSteps[skill] = Math.max(1, newMax);
        permille[skill] = Math.max(1000, newPermille);
        windowMs[skill] = Math.max(1, newWindowMs);
        updatedAt[skill] = now;
    }

    /** 0 while the chain lives, rising to 1 as its fire dies down after the window lapsed; 1 with no chain. */
    public float lapse(int skill, long now) {
        if (skill < 0 || skill >= steps.length || steps[skill] <= 0) {
            return 1f;
        }
        return TempoFlames.lapse(now - updatedAt[skill], windowMs[skill]);
    }

    /** True while there is anything to draw: a live chain or one still dying down. */
    public boolean visible(int skill, long now) {
        return lapse(skill, now) < 1f;
    }

    /** 1 while the chain lives, 0 once its window lapsed or with no chain (the panel's hover line asks). */
    public float alpha(int skill, long now) {
        return lapse(skill, now) <= 0f ? 1f : 0f;
    }

    public int steps(int skill) {
        return steps[skill];
    }

    public int heat(int skill) {
        return TempoFlames.heat(steps[skill], maxSteps[skill]);
    }

    public int permille(int skill) {
        return permille[skill];
    }

    public long windowMs(int skill) {
        return windowMs[skill];
    }

    public float flare(int skill, long now) {
        return TempoFlames.flare(now - pulseAt[skill]);
    }
}
