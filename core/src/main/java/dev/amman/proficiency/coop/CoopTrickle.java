package dev.amman.proficiency.coop;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * The client's pacing of co-op dots (1.45.0): a batch of n dots from a partner becomes a short trickle, one dot
 * every {@link #SPACING_MS}, never more than {@link CoopRules#DOTS_PER_SECOND} launches from one partner in any
 * second and never more than {@link #MAX_LIVE} dots in the air from everyone. A dot that does not fit is dropped,
 * not queued: the stream is a hint of what a partner is doing, not a ledger. Pure, driven by a clock the caller
 * passes, so the tests use a fake one.
 */
public final class CoopTrickle {

    /** Gap between two dots of one partner. */
    public static final long SPACING_MS = 130;
    /** Flight time of one dot into the bar; a faint dot lives as long. */
    public static final long FLIGHT_MS = 720;
    /** Most dots in the air at once, all partners together. */
    public static final int MAX_LIVE = 18;
    /** The window the per-partner cap counts over. */
    public static final long WINDOW_MS = 1000;

    /** The partner colours: bright, apart from each other and from the skill bar's track. */
    private static final int[] COLOURS = {
            0xFF6FD3FF, 0xFFFF8A5C, 0xFFB98CFF, 0xFF7EE787, 0xFFFFD166, 0xFFFF6FB1, 0xFF5CE1C6, 0xFFFFA94D};

    private final Map<UUID, ArrayDeque<Long>> launches = new HashMap<>();
    private final ArrayDeque<Long> live = new ArrayDeque<>();

    /** A partner's own colour, the same for the same player on every client and every session. */
    public static int colour(UUID id) {
        if (id == null) {
            return COLOURS[0];
        }
        long h = id.getMostSignificantBits() ^ id.getLeastSignificantBits();
        h ^= (h >>> 33);
        h *= 0xff51afd7ed558ccdL;
        h ^= (h >>> 33);
        return COLOURS[(int) Math.floorMod(h, (long) COLOURS.length)];
    }

    /**
     * Schedules up to {@code dots} launches for {@code partner} at {@code now} or later and returns their times
     * (ascending). Fewer, or none, when the partner's second or the shared sky is full.
     */
    public List<Long> schedule(UUID partner, int dots, long now) {
        List<Long> out = new ArrayList<>();
        if (dots <= 0 || partner == null) {
            return out;
        }
        // Dots that have landed no longer count against the sky.
        while (!live.isEmpty() && live.peekFirst() + FLIGHT_MS <= now) {
            live.pollFirst();
        }
        ArrayDeque<Long> mine = launches.computeIfAbsent(partner, id -> new ArrayDeque<>());
        long next = now;
        if (!mine.isEmpty()) {
            next = Math.max(now, mine.peekLast() + SPACING_MS);
        }
        int want = Math.min(dots, CoopRules.DOTS_PER_SECOND);
        for (int i = 0; i < want; i++) {
            long at = next + i * SPACING_MS;
            if (countInWindow(mine, at) >= CoopRules.DOTS_PER_SECOND) {
                break;
            }
            if (liveAt(at) >= MAX_LIVE) {
                break;
            }
            mine.addLast(at);
            insertLive(at);
            out.add(at);
        }
        // Keep the history short: only the last window matters.
        while (!mine.isEmpty() && mine.peekFirst() <= now - WINDOW_MS * 2) {
            mine.pollFirst();
        }
        return out;
    }

    /** Dots in the air (or waiting for their slot) at {@code now}. */
    public int inAir(long now) {
        int n = 0;
        for (long at : live) {
            if (at + FLIGHT_MS > now) {
                n++;
            }
        }
        return n;
    }

    public void clear() {
        launches.clear();
        live.clear();
    }

    public void forget(UUID partner) {
        launches.remove(partner);
    }

    private static int countInWindow(ArrayDeque<Long> times, long at) {
        int n = 0;
        for (long t : times) {
            if (t > at - WINDOW_MS && t <= at) {
                n++;
            }
        }
        return n;
    }

    /** How many scheduled dots are still flying at {@code at}. */
    private int liveAt(long at) {
        int n = 0;
        for (long t : live) {
            if (t <= at && t + FLIGHT_MS > at) {
                n++;
            } else if (t > at) {
                // Scheduled later than this one but launched before it lands: it shares the sky too.
                n++;
            }
        }
        return n;
    }

    private void insertLive(long at) {
        // Mostly appended in order; keep the deque sorted so the landed ones drain from the front.
        if (live.isEmpty() || live.peekLast() <= at) {
            live.addLast(at);
            return;
        }
        List<Long> sorted = new ArrayList<>(live);
        sorted.add(at);
        sorted.sort(Long::compare);
        live.clear();
        live.addAll(sorted);
    }
}
