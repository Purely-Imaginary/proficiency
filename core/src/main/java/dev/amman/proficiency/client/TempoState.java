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
    private final long[] critAt;

    public TempoState(int skills) {
        steps = new int[skills];
        maxSteps = new int[skills];
        permille = new int[skills];
        windowMs = new long[skills];
        updatedAt = new long[skills];
        pulseAt = new long[skills];
        critAt = new long[skills];
        clear();
    }

    public void clear() {
        java.util.Arrays.fill(steps, 0);
        java.util.Arrays.fill(maxSteps, 1);
        java.util.Arrays.fill(permille, 1000);
        java.util.Arrays.fill(windowMs, 1);
        java.util.Arrays.fill(updatedAt, 0);
        java.util.Arrays.fill(pulseAt, Long.MIN_VALUE / 2);
        java.util.Arrays.fill(critAt, Long.MIN_VALUE / 2);
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

    /**
     * The record whose fire the HUD line of {@code skill} shows, or -1 for none (1.49.0). A skill that owns a
     * chain shows its own. Endurance and the lump-paid skills show none. A support skill (Courage, Charger,
     * Guardian, Tactician, Blocking) shows the weapon chain it rides, by the server's rule
     * ({@link dev.amman.proficiency.skill.TempoRules#ride}) over what the client last heard: a live chain first,
     * else the newest one still dying down, so its smoke shows on the support line too.
     */
    public int source(int skill, long now) {
        if (skill < 0 || skill >= steps.length) {
            return -1;
        }
        dev.amman.proficiency.skill.Skill of = dev.amman.proficiency.skill.Skill.VALUES[skill];
        switch (dev.amman.proficiency.skill.TempoRules.role(of)) {
            case EXEMPT:
                return -1;
            case RIDE:
                break;
            default:
                return skill;
        }
        dev.amman.proficiency.skill.Skill ridden = dev.amman.proficiency.skill.TempoRules.ride(weapon -> {
            int w = weapon.ordinal();
            return w < steps.length && steps[w] > 0
                    ? new dev.amman.proficiency.skill.TempoRules.Live(updatedAt[w], steps[w], lapse(w, now) <= 0f)
                    : null;
        });
        if (ridden == null) {
            ridden = dev.amman.proficiency.skill.TempoRules.ride(weapon -> {
                int w = weapon.ordinal();
                return w < steps.length && steps[w] > 0
                        ? new dev.amman.proficiency.skill.TempoRules.Live(updatedAt[w], steps[w], visible(w, now))
                        : null;
            });
        }
        return ridden == null ? -1 : ridden.ordinal();
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

    /** A vanilla jump crit lengthened {@code skill}'s window: its flames flare white ({@link #critFlare}). */
    public void acceptCrit(int skill, long now) {
        if (skill >= 0 && skill < critAt.length) {
            critAt[skill] = now;
        }
    }

    /** The white crit flare of {@code skill}'s fire, 1 at the crit to 0; 0 once the chain has lapsed. */
    public float critFlare(int skill, long now) {
        if (skill < 0 || skill >= critAt.length || lapse(skill, now) > 0f) {
            return 0f;
        }
        return TempoFlames.critFlare(now - critAt[skill]);
    }

    public float flare(int skill, long now) {
        return TempoFlames.flare(now - pulseAt[skill]);
    }
}
