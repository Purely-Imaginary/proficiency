package dev.amman.proficiency.skill;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Tells the block a player broke from the extra blocks an area tool took with it, with no game
 * classes so the rule is unit tested. A hammer, an excavator, a broadaxe, a paxel or a vein-mining
 * mod breaks the neighbours through the player, so each neighbour fires a block break of its own
 * for that player. The rule is generic and knows no mod: per player and per server tick, the first
 * break seen is the primary, and every other position broken by that player in the same tick is an
 * extra. A new tick starts afresh.
 *
 * <p>The caller must classify at the highest priority, so the primary is seen before an area tool
 * (which listens at normal priority) starts breaking its neighbours from inside the primary's event.
 */
public final class AoeBreaks {

    /** The default share of the normal XP an extra block pays. */
    public static final double DEFAULT_SHARE = 0.25;

    private record Key(int dimension, long pos) {
    }

    private static final class State {
        long tick = Long.MIN_VALUE;
        final Map<Key, Boolean> verdicts = new HashMap<>();
    }

    /** Above this many tracked players, entries not touched for a while are dropped (fake players never log out). */
    private static final int PRUNE_ABOVE = 128;
    private static final long STALE_TICKS = 200;

    private final Map<UUID, State> states = new HashMap<>();

    /** As {@link #classify(UUID, long, int, long)} in dimension 0. */
    public boolean classify(UUID player, long tick, long pos) {
        return classify(player, tick, 0, pos);
    }

    /**
     * Records a break and returns whether it is an extra. The same position again in the same tick
     * keeps its first verdict, so an event posted twice for one block never turns a primary into an extra.
     * The dimension is part of the position: the same coordinates in two dimensions are two blocks.
     */
    public synchronized boolean classify(UUID player, long tick, int dimension, long pos) {
        if (states.size() > PRUNE_ABOVE) {
            states.values().removeIf(st -> tick - st.tick > STALE_TICKS || st.tick > tick);
        }
        State state = states.computeIfAbsent(player, id -> new State());
        Key key = new Key(dimension, pos);
        if (state.tick != tick) {
            state.tick = tick;
            state.verdicts.clear();
            state.verdicts.put(key, Boolean.FALSE);
            return false;
        }
        Boolean known = state.verdicts.get(key);
        if (known != null) {
            return known;
        }
        state.verdicts.put(key, Boolean.TRUE);
        return true;
    }

    /**
     * Gives the primary slot back when the break that took it was cancelled (a claim or spawn
     * protection), so the player's next block is the primary rather than an extra. Only acts when
     * that cancelled block is the only break seen this tick.
     */
    public synchronized void release(UUID player, long tick, int dimension, long pos) {
        State state = states.get(player);
        if (state == null || state.tick != tick || state.verdicts.size() != 1) {
            return;
        }
        if (Boolean.FALSE.equals(state.verdicts.get(new Key(dimension, pos)))) {
            states.remove(player);
        }
    }

    /** Whether this position was classified an extra for this player in this tick; false when unknown. */
    public synchronized boolean isExtra(UUID player, long tick, int dimension, long pos) {
        State state = states.get(player);
        if (state == null || state.tick != tick) {
            return false;
        }
        return Boolean.TRUE.equals(state.verdicts.get(new Key(dimension, pos)));
    }

    /** As {@link #isExtra(UUID, long, int, long)} in dimension 0. */
    public boolean isExtra(UUID player, long tick, long pos) {
        return isExtra(player, tick, 0, pos);
    }

    public synchronized void forget(UUID player) {
        states.remove(player);
    }

    /** The XP an extra block pays: its normal XP times the share, never negative, never more than the full XP. */
    public static double extraXp(double normalXp, double share) {
        if (!Double.isFinite(normalXp) || !Double.isFinite(share) || normalXp <= 0 || share <= 0) {
            return 0.0;
        }
        return normalXp * Math.min(1.0, share);
    }
}
