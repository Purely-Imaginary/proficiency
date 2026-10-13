package dev.amman.proficiency.hearth;

import java.util.ArrayList;
import java.util.List;

/**
 * The rolling 60-minute cap of one pool of one Hearth: at most {@code hourlyCap} charge in any 60
 * game minutes. Sixty one-minute buckets of game time, so a restart (game time stops) neither
 * resets nor spends the window. Bursts outside the cap (a siege victory, a rated tale) do not go
 * through here.
 */
public final class HourlyCap {

    /** One bucket is one game minute. */
    public static final long BUCKET_TICKS = 1200L;
    public static final int BUCKETS = 60;

    private final long[] minute = new long[BUCKETS];
    private final double[] amount = new double[BUCKETS];

    public HourlyCap() {
        java.util.Arrays.fill(minute, Long.MIN_VALUE);
    }

    /** Charge taken in the 60 minutes up to and including {@code gameTime}'s minute. */
    public double used(long gameTime) {
        long now = Math.floorDiv(gameTime, BUCKET_TICKS);
        double sum = 0.0;
        for (int i = 0; i < BUCKETS; i++) {
            if (minute[i] != Long.MIN_VALUE && minute[i] > now - BUCKETS && minute[i] <= now) {
                sum += amount[i];
            }
        }
        return sum;
    }

    /** What is left under {@code cap} right now. */
    public double room(long gameTime, double cap) {
        return Math.max(0.0, cap - used(gameTime));
    }

    /**
     * Takes as much of {@code wanted} as the cap leaves and records it. Returns what was taken
     * (0 for a negative, NaN or infinite amount).
     */
    public double take(long gameTime, double wanted, double cap) {
        if (!(wanted > 0) || Double.isInfinite(wanted)) {
            return 0.0;
        }
        double taken = Math.min(wanted, room(gameTime, cap));
        if (taken <= 0) {
            return 0.0;
        }
        long now = Math.floorDiv(gameTime, BUCKET_TICKS);
        int slot = (int) Math.floorMod(now, (long) BUCKETS);
        if (minute[slot] != now) {
            minute[slot] = now;
            amount[slot] = 0.0;
        }
        amount[slot] += taken;
        return taken;
    }

    public void clear() {
        java.util.Arrays.fill(minute, Long.MIN_VALUE);
        java.util.Arrays.fill(amount, 0.0);
    }

    /** The live buckets as (minute, amount) pairs, for the save. */
    public List<long[]> encode() {
        List<long[]> out = new ArrayList<>();
        for (int i = 0; i < BUCKETS; i++) {
            if (minute[i] != Long.MIN_VALUE && amount[i] > 0) {
                out.add(new long[] {minute[i], Double.doubleToRawLongBits(amount[i])});
            }
        }
        return out;
    }

    public void decode(List<long[]> buckets) {
        clear();
        for (long[] bucket : buckets) {
            if (bucket.length < 2) {
                continue;
            }
            int slot = (int) Math.floorMod(bucket[0], (long) BUCKETS);
            double value = Double.longBitsToDouble(bucket[1]);
            if (Double.isFinite(value) && value > 0 && (minute[slot] == Long.MIN_VALUE || bucket[0] > minute[slot])) {
                minute[slot] = bucket[0];
                amount[slot] = value;
            }
        }
    }
}
