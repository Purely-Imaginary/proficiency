package dev.amman.proficiency.skill;

/**
 * A rolling sum over the last {@code windowTicks} game ticks: add (tick, amount) pairs, ask for the sum. Bounded
 * (the oldest entry is dropped when full, which only ever undercounts) and safe against a clock that goes back.
 */
public final class RateWindow {

    private static final int CAPACITY = 1024;

    private final long windowTicks;
    private final long[] ticks = new long[CAPACITY];
    private final float[] amounts = new float[CAPACITY];
    private int head;
    private int size;
    private double sum;
    private long last = Long.MIN_VALUE;

    public RateWindow(long windowTicks) {
        this.windowTicks = windowTicks;
    }

    public void add(long now, float amount) {
        if (now < last) {
            clear();
        }
        last = now;
        prune(now);
        if (size == CAPACITY) {
            dropOldest();
        }
        int at = (head + size) % CAPACITY;
        ticks[at] = now;
        amounts[at] = amount;
        size++;
        sum += amount;
    }

    /** The sum of everything added in the last {@code windowTicks} ticks up to {@code now}. */
    public float sum(long now) {
        if (now < last) {
            clear();
            return 0f;
        }
        prune(now);
        return (float) Math.max(0.0, sum);
    }

    public int size() {
        return size;
    }

    public void clear() {
        head = 0;
        size = 0;
        sum = 0;
        last = Long.MIN_VALUE;
    }

    private void prune(long now) {
        while (size > 0 && now - ticks[head] >= windowTicks) {
            dropOldest();
        }
        if (size == 0) {
            sum = 0;
        }
    }

    private void dropOldest() {
        sum -= amounts[head];
        head = (head + 1) % CAPACITY;
        size--;
    }
}
