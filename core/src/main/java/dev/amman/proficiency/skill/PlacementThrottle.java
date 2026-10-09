package dev.amman.proficiency.skill;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Stops a tool that places many blocks in one action from paying once per block. Building Gadgets,
 * Construction Wand, the Create symmetry wand and the like post an ordinary placement event for
 * every block, as the player, with the wand in hand, so the event alone cannot tell them from
 * someone bridging by hand. What does tell them apart is speed.
 *
 * <p>Two rules, both on game ticks:
 * <ul>
 *   <li>One paid placement per tick per player. A wand puts all its blocks down in one tick, so
 *       one use pays as one placement.</li>
 *   <li>At most {@link #MAX_PER_WINDOW} paid placements in any {@link #WINDOW} ticks, counted over
 *       a true sliding window (the last eight paid placements must span at least a second). A tool
 *       that spreads its blocks over several ticks is held to a pace a fast human can match.</li>
 * </ul>
 * Use {@link #canPay} to ask and {@link #record} once a placement really paid, so a torch or a
 * sign that pays nothing does not use up the budget. A placement the throttle refuses pays
 * nothing: no XP and no refund. The block is still marked as player placed by the caller, so
 * breaking it back pays nothing either.
 */
public final class PlacementThrottle {

    /** Ticks in the sliding window: one second. */
    public static final long WINDOW = 20;

    /** Paid placements allowed per window. Fast bridging by hand is about 5 to 8 a second. */
    public static final int MAX_PER_WINDOW = 8;

    private static final class State {
        /** Ticks of the last paid placements, a ring buffer. */
        final long[] ring = new long[MAX_PER_WINDOW];
        int count;
        int next;
        long lastTick = Long.MIN_VALUE;

        void reset() {
            count = 0;
            next = 0;
            lastTick = Long.MIN_VALUE;
        }
    }

    private final Map<UUID, State> players = new ConcurrentHashMap<>();

    /** Whether a placement at this tick may pay. Counts nothing. */
    public boolean canPay(UUID player, long tick) {
        State s = players.computeIfAbsent(player, id -> new State());
        synchronized (s) {
            return check(s, tick);
        }
    }

    /** Counts a placement that paid. */
    public void record(UUID player, long tick) {
        State s = players.computeIfAbsent(player, id -> new State());
        synchronized (s) {
            check(s, tick); // handles a clock that went backwards
            s.ring[s.next] = tick;
            s.next = (s.next + 1) % MAX_PER_WINDOW;
            s.count = Math.min(MAX_PER_WINDOW, s.count + 1);
            s.lastTick = tick;
        }
    }

    /** Whether this placement may pay; counts it when it may. Same as {@link #canPay} then {@link #record}. */
    public boolean allow(UUID player, long tick) {
        if (!canPay(player, tick)) {
            return false;
        }
        record(player, tick);
        return true;
    }

    private static boolean check(State s, long tick) {
        if (tick < s.lastTick) {
            // The clock went backwards (a different dimension's clock, a world reload): start over.
            s.reset();
        }
        if (tick == s.lastTick) {
            return false;
        }
        if (s.count < MAX_PER_WINDOW) {
            return true;
        }
        // The oldest of the last eight sits where the next write will go.
        return tick - s.ring[s.next] >= WINDOW;
    }

    public void forget(UUID player) {
        players.remove(player);
    }
}
