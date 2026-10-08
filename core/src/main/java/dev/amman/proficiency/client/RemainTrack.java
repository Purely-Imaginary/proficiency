package dev.amman.proficiency.client;

/**
 * Turns "ticks remaining" into a 0 to 1 fraction of the whole, for a ring that drains or a sweep
 * that shrinks. The client is only told when a frenzy or cooldown ends, not how long it was, so
 * the whole is the biggest remaining value seen since it last read zero. One slot per skill.
 */
public final class RemainTrack {

    private final float[] total;

    public RemainTrack(int slots) {
        total = new float[slots];
    }

    /** Fraction left for {@code slot}, 0 when nothing remains. */
    public float fraction(int slot, float remaining) {
        if (!(remaining > 0f)) {
            total[slot] = 0f;
            return 0f;
        }
        if (remaining > total[slot]) {
            total[slot] = remaining;
        }
        return Math.min(1f, remaining / total[slot]);
    }

    /** The whole for {@code slot}: the biggest remaining value seen since it last read zero. */
    public float total(int slot) {
        return total[slot];
    }

    public void clear() {
        java.util.Arrays.fill(total, 0f);
    }
}
