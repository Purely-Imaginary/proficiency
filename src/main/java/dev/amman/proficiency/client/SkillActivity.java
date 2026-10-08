package dev.amman.proficiency.client;

import dev.amman.proficiency.skill.PlayerSkills;
import dev.amman.proficiency.skill.Skill;

/**
 * What the skills panel shows about recent XP, kept on the client. Every frame the HUD hands this
 * the synced skills, and any rise in a skill's level plus progress lands in a ring of
 * {@link #BUCKETS} buckets of five minutes (two hours), one ring per skill. The panel reads a row's
 * glow (any gain in the last ten minutes, stronger the fresher) and its sparkline from here.
 *
 * <p>Gains are counted in level units (level plus progress), so a sparkline compares one skill with
 * itself and nothing else. A drop (a death wipes progress) is not a gain and only moves the
 * baseline. The first {@link #WARMUP_MS} after the first frame in a world do not count either:
 * the full sync arrives just after the join and would read as one huge gain. No Minecraft in the
 * maths; {@link HudState} resets it on logout.
 */
public final class SkillActivity {

    public static final int BUCKETS = 24;
    public static final long BUCKET_MS = 5 * 60_000L;
    public static final long GLOW_MS = 10 * 60_000L;
    public static final long WARMUP_MS = 3_000L;

    private static final int COUNT = Skill.VALUES.length;
    private static final float[][] RING = new float[COUNT][BUCKETS];
    /** Absolute bucket number (time / BUCKET_MS) of each skill's newest slot, or -1 for none. */
    private static final long[] HEAD = new long[COUNT];
    private static final long[] LAST_GAIN = new long[COUNT];
    private static final float[] LAST_VALUE = new float[COUNT];
    private static long firstFrame = -1;
    private static final float[] VALUES = new float[COUNT];
    private static Object lastOwner;
    private static float lastTotal;

    static {
        reset();
    }

    private SkillActivity() {
    }

    public static void reset() {
        for (int i = 0; i < COUNT; i++) {
            java.util.Arrays.fill(RING[i], 0f);
            HEAD[i] = -1;
            LAST_GAIN[i] = -1;
            LAST_VALUE[i] = -1f;
        }
        firstFrame = -1;
        lastOwner = null;
        lastTotal = 0f;
    }

    /**
     * Called once a frame with the player's synced skills and a monotonic clock. Allocates nothing.
     * A different {@code PlayerSkills} object (respawn, dimension change, a new world) starts over
     * with a fresh warm-up, and so does a whole snapshot jumping from nothing to a level or more
     * (a sync that lands late): neither is XP.
     */
    public static void observe(PlayerSkills skills, long now) {
        float total = 0f;
        for (int i = 0; i < COUNT; i++) {
            Skill skill = Skill.VALUES[i];
            VALUES[i] = skills.level(skill) + skills.progress(skill);
            total += VALUES[i];
        }
        observeAll(skills, VALUES, total, now);
    }

    static void observeAll(Object owner, float[] values, float total, long now) {
        boolean fresh = owner != lastOwner || (lastTotal <= 0f && total >= 1f);
        lastOwner = owner;
        lastTotal = total;
        if (fresh || firstFrame < 0) {
            java.util.Arrays.fill(LAST_VALUE, -1f);
            firstFrame = now;
        }
        boolean warm = now - firstFrame >= WARMUP_MS;
        for (int i = 0; i < COUNT; i++) {
            observe(i, values[i], warm, now);
        }
    }

    static void observe(int skill, float value, boolean counts, long now) {
        float before = LAST_VALUE[skill];
        LAST_VALUE[skill] = value;
        if (before >= 0 && counts && value > before + 1e-6f) {
            add(skill, value - before, now);
        }
    }

    /** Records a gain of {@code amount} level units for {@code skill} at {@code now}. */
    static void add(int skill, float amount, long now) {
        long bucket = Math.floorDiv(now, BUCKET_MS);
        long head = HEAD[skill];
        if (head < bucket) {
            // Clear the slots the clock has moved through, at most the whole ring.
            long from = Math.max(head + 1, bucket - BUCKETS + 1);
            for (long b = from; b <= bucket; b++) {
                RING[skill][(int) Math.floorMod(b, (long) BUCKETS)] = 0f;
            }
            HEAD[skill] = bucket;
        } else if (bucket < head - BUCKETS + 1) {
            return;
        }
        RING[skill][(int) Math.floorMod(bucket, (long) BUCKETS)] += amount;
        LAST_GAIN[skill] = Math.max(LAST_GAIN[skill], now);
    }

    /** 0 for no gain in the last ten minutes, up to 1 for one just now. Linear in between. */
    public static float glow(Skill skill, long now) {
        long last = LAST_GAIN[skill.ordinal()];
        if (last < 0) {
            return 0f;
        }
        long age = now - last;
        return age >= GLOW_MS ? 0f : Math.min(1f, Math.max(0f, 1f - age / (float) GLOW_MS));
    }

    /**
     * Fills {@code out} (length {@link #BUCKETS}) with the skill's gains, oldest first, the newest
     * slot being the current five minutes. Returns the largest value, 0 when there is nothing.
     */
    public static float series(Skill skill, long now, float[] out) {
        int s = skill.ordinal();
        long current = Math.floorDiv(now, BUCKET_MS);
        long head = HEAD[s];
        float max = 0f;
        for (int i = 0; i < BUCKETS; i++) {
            long b = current - (BUCKETS - 1) + i;
            float v = head >= 0 && b <= head && b > head - BUCKETS
                    ? RING[s][(int) Math.floorMod(b, (long) BUCKETS)] : 0f;
            out[i] = v;
            if (v > max) {
                max = v;
            }
        }
        return max;
    }
}
