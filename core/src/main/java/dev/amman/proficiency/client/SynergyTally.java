package dev.amman.proficiency.client;

/**
 * The "+1 synergy" counter at the bottom-right: each chip that lands ticks it up, it holds
 * {@link #HOLD_MS} after the last landing and then fades and starts again from zero. Pure, caller-clocked.
 */
public final class SynergyTally {

    public static final long HOLD_MS = 1700;
    public static final long FADE_IN_MS = 120;
    public static final long FADE_OUT_MS = 300;
    public static final long POP_MS = 260;

    private int count;
    private long lastAt = Long.MIN_VALUE;

    /** A chip landed at {@code now}. */
    public void land(long now) {
        if (!visible(now)) {
            count = 0;
        }
        count++;
        lastAt = now;
    }

    public boolean visible(long now) {
        return count > 0 && now - lastAt < HOLD_MS + FADE_OUT_MS;
    }

    /** What the counter shows ("+N"); 0 when it is not on screen. */
    public int count(long now) {
        return visible(now) ? count : 0;
    }

    /** Opacity 0..1. The first landing fades in; a later one only pops. */
    public float alpha(long now) {
        if (!visible(now)) {
            return 0f;
        }
        long since = now - lastAt;
        float out = since <= HOLD_MS ? 1f : 1f - (since - HOLD_MS) / (float) FADE_OUT_MS;
        float in = count == 1 ? Math.min(1f, since / (float) FADE_IN_MS) : 1f;
        return Math.max(0f, Math.min(in, out));
    }

    /** 1 at a landing, easing to 0 over {@link #POP_MS}: the number's scale kick. */
    public float pop(long now) {
        long since = now - lastAt;
        if (!visible(now) || since >= POP_MS) {
            return 0f;
        }
        float u = 1f - since / (float) POP_MS;
        return u * u;
    }

    public void clear() {
        count = 0;
        lastAt = Long.MIN_VALUE;
    }
}
