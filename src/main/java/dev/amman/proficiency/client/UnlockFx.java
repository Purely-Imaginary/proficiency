package dev.amman.proficiency.client;

/**
 * The timing of the talent tree's unlock animation, with no Minecraft in it, so the unit tests can
 * drive it with a fake clock. {@link TalentTreeScreen} feeds it each node's real rank every frame
 * and draws what it answers.
 *
 * <p>A rank going up sweeps the node's fill bottom to top in {@link #SWEEP_MS}. When the node
 * reaches full rank, the lines to the nodes it opens light up travelling away from it over
 * {@link #TRAVEL_MS}. A shift-fill raises several ranks in one frame; they play one after another,
 * with the gap squeezed so the whole run never exceeds {@link #TOTAL_CAP_MS}. Synergies get one
 * {@link #PULSE_MS} pulse when they switch on. Nothing here allocates after construction.
 */
public final class UnlockFx {

    public static final long SWEEP_MS = 350;
    public static final long TRAVEL_MS = 400;
    public static final long PULSE_MS = 900;
    /** One node's whole fill run, however many ranks a shift-click bought. */
    public static final long TOTAL_CAP_MS = 1000;
    /** Longest gap between two ranks of one run. */
    public static final long MAX_STAGGER_MS = 160;
    /** The capstone shimmer repeats this often, one pass of {@link #SHIMMER_PASS_MS}. */
    public static final long SHIMMER_PERIOD_MS = 3200;
    public static final long SHIMMER_PASS_MS = 1100;

    private final int[] last;
    private final int[] from;
    private final int[] steps;
    private final long[] start;
    private final long[] stagger;
    private final boolean[] full;
    private final boolean[] seen;
    private final boolean[] synergyOn;
    private final boolean[] synergySeen;
    private final long[] pulseAt;

    public UnlockFx(int nodes, int synergies) {
        last = new int[nodes];
        from = new int[nodes];
        steps = new int[nodes];
        start = new long[nodes];
        stagger = new long[nodes];
        full = new boolean[nodes];
        seen = new boolean[nodes];
        synergyOn = new boolean[synergies];
        synergySeen = new boolean[synergies];
        pulseAt = new long[synergies];
        java.util.Arrays.fill(pulseAt, Long.MIN_VALUE / 2);
    }

    /** Forget the node's history and show {@code rank} as it is. For the toggle being off. */
    public void snap(int node, int rank) {
        last[node] = rank;
        from[node] = rank;
        steps[node] = 0;
        full[node] = false;
        seen[node] = true;
    }

    /**
     * The node's real rank this frame. A rise starts a run of {@code rise} sweeps; a drop (a
     * respec) snaps. The first sighting of a node only records it.
     */
    public void observe(int node, int rank, int maxRank, long now) {
        if (!seen[node] || rank < last[node]) {
            snap(node, rank);
            return;
        }
        if (rank > last[node]) {
            int shown = shownRank(node, now);
            if (steps[node] > 0 && now < runEnd(node)) {
                // A rank arrives while an older run still plays (the server answers a shift-click
                // one packet at a time): carry on from what is shown, keeping the sweep in flight.
                int done = shown - from[node];
                long elapsed = Math.max(0, Math.min(SWEEP_MS - 1, now - (start[node] + done * stagger[node])));
                from[node] = shown;
                steps[node] = rank - shown;
                start[node] = now - elapsed;
            } else {
                from[node] = last[node];
                steps[node] = rank - last[node];
                start[node] = now;
            }
            stagger[node] = steps[node] <= 1 ? 0
                    : Math.min(MAX_STAGGER_MS, (TOTAL_CAP_MS - SWEEP_MS) / (steps[node] - 1));
            full[node] = rank >= maxRank;
            last[node] = rank;
        }
    }

    private long runEnd(int node) {
        return start[node] + (steps[node] - 1) * stagger[node] + SWEEP_MS;
    }

    /** The rank to draw: the old rank plus every step whose sweep has finished. */
    public int shownRank(int node, long now) {
        if (steps[node] == 0) {
            return last[node];
        }
        if (now >= runEnd(node)) {
            return last[node];
        }
        int done = 0;
        for (int i = 0; i < steps[node]; i++) {
            if (now >= start[node] + i * stagger[node] + SWEEP_MS) {
                done++;
            }
        }
        return from[node] + done;
    }

    /** Progress 0..1 of the sweep now running on this node, or -1 when none is. */
    public float sweep(int node, long now) {
        if (steps[node] == 0 || now >= runEnd(node)) {
            return -1f;
        }
        int done = shownRank(node, now) - from[node];
        long t = now - (start[node] + done * stagger[node]);
        if (done >= steps[node] || t < 0) {
            return -1f;
        }
        return Math.min(1f, t / (float) SWEEP_MS);
    }

    /**
     * Progress 0..1 of the light travelling down the node's outgoing lines, or -1 when it is not
     * running. It runs only after a run that took the node to full rank.
     */
    public float travel(int node, long now) {
        if (steps[node] == 0 || !full[node]) {
            return -1f;
        }
        long t = now - runEnd(node);
        if (t < 0 || t >= TRAVEL_MS) {
            return -1f;
        }
        return t / (float) TRAVEL_MS;
    }

    /** True while the node's lines should still show their old, unlit state. */
    public boolean linesPending(int node, long now) {
        return steps[node] > 0 && full[node] && now < runEnd(node) + TRAVEL_MS;
    }

    /** A synergy's state this frame; a switch from off to on starts its pulse. */
    public void observeSynergy(int index, boolean active, long now) {
        if (synergySeen[index] && active && !synergyOn[index]) {
            pulseAt[index] = now;
        }
        synergyOn[index] = active;
        synergySeen[index] = true;
    }

    /** Progress 0..1 of the synergy's pulse, or -1 when it is not pulsing. */
    public float pulse(int index, long now) {
        long t = now - pulseAt[index];
        return t >= 0 && t < PULSE_MS ? t / (float) PULSE_MS : -1f;
    }

    /** Where the shimmer band is across a node, 0..1, or -1 between passes. */
    public static float shimmer(long now, long phase) {
        long t = Math.floorMod(now + phase, SHIMMER_PERIOD_MS);
        return t < SHIMMER_PASS_MS ? t / (float) SHIMMER_PASS_MS : -1f;
    }
}
