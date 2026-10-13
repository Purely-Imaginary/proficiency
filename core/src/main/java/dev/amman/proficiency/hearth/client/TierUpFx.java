package dev.amman.proficiency.hearth.client;

import dev.amman.proficiency.client.XpGainDots;

/**
 * The tier-up moment of one Hearth tracker row, without any Minecraft rendering in it: a flash on
 * the row's bar, the roman numeral rolling over and a ring leaving it, gold at tier VI. It copies
 * the skill HUD's {@code LevelUpFx} (same timings and curves), but it is an instance, one per row,
 * because two pools can rise in the same second and {@code LevelUpFx} is static and keyed by skill.
 * The tracker draws it; the unit tests drive it with a fake clock.
 */
public final class TierUpFx {

    /** The ring, flash and numeral roll of a tier-up. */
    public static final long NORMAL_MS = 700;
    /** The longer gold one, for tier VI. */
    public static final long GOLD_MS = 1400;
    /** How long the numeral takes to turn over. */
    public static final long ROLL_MS = 380;
    /** The tier that gets the gold moment. */
    public static final int GOLD_TIER = 6;

    private int oldTier;
    private int newTier;
    private long start = Long.MIN_VALUE / 2;
    private boolean started;

    /** Tier VI is the big one. */
    public static boolean isGold(int tier) {
        return tier >= GOLD_TIER;
    }

    public static long durationMs(int tier) {
        return isGold(tier) ? GOLD_MS : NORMAL_MS;
    }

    /** Starts the moment for a rise from {@code oldTier} to {@code newTier}. A fall or no change does nothing. */
    public void start(int oldTier, int newTier, long now) {
        if (newTier <= oldTier) {
            return;
        }
        this.oldTier = oldTier;
        this.newTier = newTier;
        this.start = now;
        this.started = true;
    }

    /** Forget it (the row went, the world was left, the switch is off). */
    public void reset() {
        started = false;
        start = Long.MIN_VALUE / 2;
    }

    public int oldTier() {
        return oldTier;
    }

    public int newTier() {
        return newTier;
    }

    public boolean gold() {
        return isGold(newTier);
    }

    public long startedAt() {
        return start;
    }

    /** 0 at the start to 1 at the end; above 1 once it is over, negative before it starts. */
    public float t(long now) {
        return (now - start) / (float) durationMs(newTier);
    }

    /** True while the flash, ring and roll still play. */
    public boolean active(long now) {
        return started && now >= start && now - start < durationMs(newTier);
    }

    /** How far the numeral has rolled, 0 to 1, eased; 1 once it has landed or when nothing plays. */
    public float roll(long now) {
        if (!active(now)) {
            return 1f;
        }
        float t = (now - start) / (float) ROLL_MS;
        return (float) XpGainDots.ease(Math.max(0f, Math.min(1f, t)));
    }

    /** Brightness of the bar flash: 1 at the start falling to 0 on a squared curve, so it snaps. */
    public float flash(long now) {
        if (!active(now)) {
            return 0f;
        }
        float left = 1f - Math.max(0f, Math.min(1f, t(now)));
        return left * left;
    }

    /**
     * The ring's growth, 0 to 1 on an ease-out cubic (fast, then settling), as the skill HUD's ring
     * grows; 1 once it is over.
     */
    public float ring(long now) {
        float t = Math.max(0f, Math.min(1f, t(now)));
        return 1f - (1f - t) * (1f - t) * (1f - t);
    }
}
