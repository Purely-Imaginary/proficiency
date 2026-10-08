package dev.amman.proficiency.client;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.amman.proficiency.skill.Skill;
import org.junit.jupiter.api.Test;

/** The talent tree's unlock timing and the skills panel's activity ring. */
class ScreenFxTest {

    private static final long T0 = 10_000_000L;

    @Test
    void firstSightingAnimatesNothing() {
        UnlockFx fx = new UnlockFx(30, 2);
        fx.observe(3, 2, 3, T0);
        assertEquals(2, fx.shownRank(3, T0));
        assertEquals(-1f, fx.sweep(3, T0));
        assertEquals(-1f, fx.travel(3, T0 + 100));
    }

    @Test
    void oneRankSweepsThenSettles() {
        UnlockFx fx = new UnlockFx(30, 2);
        fx.observe(0, 0, 2, T0);
        fx.observe(0, 1, 2, T0 + 1000);
        assertEquals(0, fx.shownRank(0, T0 + 1000));
        assertTrue(fx.sweep(0, T0 + 1000 + UnlockFx.SWEEP_MS / 2) > 0.4f);
        assertEquals(1, fx.shownRank(0, T0 + 1000 + UnlockFx.SWEEP_MS));
        assertEquals(-1f, fx.sweep(0, T0 + 1000 + UnlockFx.SWEEP_MS));
        // Not full: the lines stay as they were.
        assertEquals(-1f, fx.travel(0, T0 + 1000 + UnlockFx.SWEEP_MS + 50));
    }

    @Test
    void linesTravelOnlyAfterTheNodeIsFull() {
        UnlockFx fx = new UnlockFx(30, 0);
        fx.observe(1, 0, 1, T0);
        fx.observe(1, 1, 1, T0 + 500);
        long done = T0 + 500 + UnlockFx.SWEEP_MS;
        assertTrue(fx.linesPending(1, done - 10));
        assertEquals(-1f, fx.travel(1, done - 10));
        assertEquals(0f, fx.travel(1, done));
        assertTrue(fx.travel(1, done + UnlockFx.TRAVEL_MS / 2) > 0.4f);
        assertEquals(-1f, fx.travel(1, done + UnlockFx.TRAVEL_MS));
        assertTrue(!fx.linesPending(1, done + UnlockFx.TRAVEL_MS));
    }

    @Test
    void shiftFillPlaysEveryRankWithinTheCap() {
        UnlockFx fx = new UnlockFx(30, 0);
        fx.observe(2, 0, 5, T0);
        fx.observe(2, 5, 5, T0 + 100);
        long start = T0 + 100;
        assertEquals(0, fx.shownRank(2, start));
        int previous = 0;
        for (long t = start; t <= start + UnlockFx.TOTAL_CAP_MS + 5; t += 10) {
            int shown = fx.shownRank(2, t);
            assertTrue(shown >= previous, "ranks only rise");
            previous = shown;
        }
        assertEquals(5, previous);
        assertEquals(5, fx.shownRank(2, start + UnlockFx.TOTAL_CAP_MS));
        assertTrue(fx.sweep(2, start + 5 * 30) >= 0f);
    }

    @Test
    void ranksArrivingOneByOneKeepOneContinuousRun() {
        UnlockFx fx = new UnlockFx(30, 0);
        fx.observe(4, 0, 3, T0);
        fx.observe(4, 1, 3, T0 + 100);
        float before = fx.sweep(4, T0 + 200);
        fx.observe(4, 2, 3, T0 + 200);
        float after = fx.sweep(4, T0 + 200);
        assertEquals(before, after, 0.01f, "the sweep in flight carries on");
        fx.observe(4, 3, 3, T0 + 300);
        assertEquals(3, fx.shownRank(4, T0 + 300 + UnlockFx.TOTAL_CAP_MS));
    }

    @Test
    void respecSnaps() {
        UnlockFx fx = new UnlockFx(30, 0);
        fx.observe(5, 3, 3, T0);
        fx.observe(5, 1, 3, T0 + 10);
        assertEquals(1, fx.shownRank(5, T0 + 10));
        assertEquals(-1f, fx.sweep(5, T0 + 10));
    }

    @Test
    void synergyPulsesOnceOnSwitchOn() {
        UnlockFx fx = new UnlockFx(1, 2);
        fx.observeSynergy(0, false, T0);
        fx.observeSynergy(1, true, T0);
        assertEquals(-1f, fx.pulse(1, T0 + 10), "already on at the first look: no pulse");
        fx.observeSynergy(0, true, T0 + 1000);
        assertTrue(fx.pulse(0, T0 + 1000 + UnlockFx.PULSE_MS / 2) > 0.4f);
        assertEquals(-1f, fx.pulse(0, T0 + 1000 + UnlockFx.PULSE_MS));
        fx.observeSynergy(0, true, T0 + 5000);
        assertEquals(-1f, fx.pulse(0, T0 + 5000));
    }

    @Test
    void shimmerPassesThenRests() {
        assertEquals(0f, UnlockFx.shimmer(UnlockFx.SHIMMER_PERIOD_MS * 7, 0));
        assertEquals(-1f, UnlockFx.shimmer(UnlockFx.SHIMMER_PASS_MS + 1, 0));
    }

    @Test
    void activityBucketsGlowAndAgeOut() {
        SkillActivity.reset();
        long now = SkillActivity.BUCKET_MS * 1000 + 1000;
        float[] out = new float[SkillActivity.BUCKETS];
        assertEquals(0f, SkillActivity.series(Skill.MINING, now, out));
        assertEquals(0f, SkillActivity.glow(Skill.MINING, now));

        SkillActivity.add(Skill.MINING.ordinal(), 0.5f, now);
        SkillActivity.add(Skill.MINING.ordinal(), 0.25f, now + 1000);
        assertEquals(0.75f, SkillActivity.series(Skill.MINING, now + 1000, out), 1e-5f);
        assertEquals(0.75f, out[SkillActivity.BUCKETS - 1], 1e-5f);
        assertTrue(SkillActivity.glow(Skill.MINING, now + 1000) > 0.99f);

        // Ten minutes on: the glow is gone, the gain moved two buckets back.
        long later = now + 1000 + SkillActivity.GLOW_MS;
        assertEquals(0f, SkillActivity.glow(Skill.MINING, later));
        SkillActivity.series(Skill.MINING, later, out);
        assertEquals(0.75f, out[SkillActivity.BUCKETS - 3], 1e-5f);

        // Two hours on: out of the ring.
        long gone = now + SkillActivity.BUCKET_MS * SkillActivity.BUCKETS;
        assertEquals(0f, SkillActivity.series(Skill.MINING, gone, out));
        assertEquals(0f, SkillActivity.series(Skill.FARMING, now, out));
    }

    @Test
    void drops_and_warmup_are_not_gains() {
        SkillActivity.reset();
        long now = SkillActivity.BUCKET_MS * 500;
        int s = Skill.MINING.ordinal();
        float[] out = new float[SkillActivity.BUCKETS];
        SkillActivity.observe(s, 5.0f, false, now);
        SkillActivity.observe(s, 9.0f, false, now + 1);
        assertEquals(0f, SkillActivity.series(Skill.MINING, now + 1, out), "warm-up jump");
        SkillActivity.observe(s, 9.5f, true, now + 2);
        SkillActivity.observe(s, 9.0f, true, now + 3);
        SkillActivity.observe(s, 9.2f, true, now + 4);
        assertEquals(0.7f, SkillActivity.series(Skill.MINING, now + 5, out), 1e-4f);
    }

    private static float[] values(float mining) {
        float[] v = new float[Skill.VALUES.length];
        v[Skill.MINING.ordinal()] = mining;
        return v;
    }

    @Test
    void respawnAndLateSyncAreBaselinesNotGains() {
        SkillActivity.reset();
        float[] out = new float[SkillActivity.BUCKETS];
        long t = SkillActivity.BUCKET_MS * 800;
        Object first = new Object();
        SkillActivity.observeAll(first, values(40.2f), 40.2f, t);
        t += SkillActivity.WARMUP_MS + 10;
        SkillActivity.observeAll(first, values(40.2f), 40.2f, t);
        SkillActivity.observeAll(first, values(40.5f), 40.5f, t + 1);
        assertEquals(0.3f, SkillActivity.series(Skill.MINING, t + 1, out), 1e-4f);

        // Respawn: a new object holding an empty snapshot, then the real values after warm-up.
        Object second = new Object();
        t += 5000;
        SkillActivity.observeAll(second, values(0f), 0f, t);
        t += SkillActivity.WARMUP_MS + 10;
        SkillActivity.observeAll(second, values(40.5f), 40.5f, t);
        assertEquals(0.3f, SkillActivity.series(Skill.MINING, t, out), 1e-4f, "no jump recorded");

        // A sync that lands late on the same object: zero to a full snapshot.
        SkillActivity.reset();
        t += SkillActivity.BUCKET_MS * 10;
        SkillActivity.observeAll(first, values(0f), 0f, t);
        t += SkillActivity.WARMUP_MS + 10;
        SkillActivity.observeAll(first, values(0f), 0f, t);
        SkillActivity.observeAll(first, values(60f), 60f, t + 1);
        assertEquals(0f, SkillActivity.series(Skill.MINING, t + 2, out));
        t += SkillActivity.WARMUP_MS + 10;
        SkillActivity.observeAll(first, values(60f), 60f, t);
        SkillActivity.observeAll(first, values(60.4f), 60.4f, t + 1);
        assertEquals(0.4f, SkillActivity.series(Skill.MINING, t + 2, out), 1e-4f);
    }

    @Test
    void glowStaysInRangeWhenTheClockStepsBack() {
        SkillActivity.reset();
        long now = SkillActivity.BUCKET_MS * 1000;
        SkillActivity.add(Skill.MINING.ordinal(), 1f, now);
        assertEquals(1f, SkillActivity.glow(Skill.MINING, now - 5 * 60_000L));
    }
}
