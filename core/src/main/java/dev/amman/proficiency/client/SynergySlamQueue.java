package dev.amman.proficiency.client;

import java.util.ArrayDeque;
import java.util.Deque;

/**
 * The discoveries waiting for their slam, one plays at a time. Pure: the caller supplies the clock.
 * The same item is never queued twice (a respec and a re-invest in one breath), the queue is bounded, and
 * the clock of the playing item runs faster the more wait behind it ({@link SynergySlamMath#speed}).
 *
 * @param <T> what a discovery is (a synergy id in the game, a string in tests)
 */
public final class SynergySlamQueue<T> {

    /** Most that wait behind the one playing; a bigger burst drops the extras (they are still awake). */
    public static final int MAX_WAITING = 8;

    private final Deque<T> waiting = new ArrayDeque<>();
    private T current;
    private long start;
    private float speed = 1f;
    private long timeline;

    /** Queues one; false when it is already playing or waiting, or the queue is full. */
    public boolean add(T item) {
        if (item == null || item.equals(current) || waiting.contains(item) || waiting.size() >= MAX_WAITING) {
            return false;
        }
        waiting.add(item);
        return true;
    }

    /**
     * Moves the queue to {@code now} (ms, any steady clock). Returns the item that finished on this call, or
     * null. Starts the next item when nothing plays.
     */
    public T tick(long now) {
        T finished = null;
        if (current != null) {
            timeline = Math.round((now - start) * (double) speed);
            if (SynergySlamMath.done(timeline)) {
                finished = current;
                current = null;
            }
        }
        if (current == null && !waiting.isEmpty()) {
            current = waiting.poll();
            start = now;
            speed = SynergySlamMath.speed(waiting.size());
            timeline = 0;
        }
        return finished;
    }

    /** The playing item, or null. */
    public T current() {
        return current;
    }

    /** Milliseconds into the playing item's timeline (already scaled), as of the last {@link #tick}. */
    public long timeline() {
        return timeline;
    }

    public int waiting() {
        return waiting.size();
    }

    public boolean idle() {
        return current == null && waiting.isEmpty();
    }

    public void clear() {
        waiting.clear();
        current = null;
        timeline = 0;
    }
}
