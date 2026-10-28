package dev.amman.proficiency.skill;

/**
 * One player's live record tracking in one skill: the rolling XP window, the kill and catch counts, and what was
 * last announced. Not saved (a relog starts a new tracker; the bests live in {@link PlayerRecords}). The game side
 * calls {@link #onGrant} after each paid grant.
 */
public final class RecordTracker {

    /** What one grant did to the records. */
    public record Outcome(boolean changed, RecordMetric toast, float toastValue) {
        public static final Outcome NOTHING = new Outcome(false, null, 0f);
    }

    private final RateWindow xp = new RateWindow(RecordRules.WINDOW_TICKS);
    private final RateWindow catches = new RateWindow(RecordRules.WINDOW_TICKS);
    private final float[] announced = new float[RecordMetric.VALUES.length];
    private long firstTick = Long.MIN_VALUE;
    private long lastToast = Long.MIN_VALUE / 2;
    private long lastTick = Long.MIN_VALUE;
    private int chainKills;
    private boolean seeded;

    /**
     * @param records      the player's stored bests (raised in place)
     * @param now          game time
     * @param amount       the XP the grant paid, after every multiplier
     * @param chainActions actions in the current Tempo chain of this skill (0 when the skill keeps no chain)
     * @param kill         the grant is a kill bonus
     * @param catchLike    the grant is a fishing catch (not a first-time bonus)
     */
    public Outcome onGrant(PlayerRecords records, Skill skill, long now, float amount, int chainActions,
            boolean kill, boolean catchLike) {
        if (!Float.isFinite(amount) || amount <= 0f) {
            return Outcome.NOTHING;
        }
        if (now < lastTick) {
            xp.clear();
            catches.clear();
            firstTick = now;
        }
        lastTick = now;
        if (!seeded) {
            seeded = true;
            for (RecordMetric metric : RecordMetric.VALUES) {
                announced[metric.ordinal()] = records.value(skill, metric);
            }
        }
        if (firstTick == Long.MIN_VALUE) {
            firstTick = now;
        }

        xp.add(now, amount);
        float[] now4 = new float[RecordMetric.VALUES.length];
        now4[RecordMetric.RATE.ordinal()] = xp.sum(now);
        now4[RecordMetric.CHAIN.ordinal()] = chainActions;
        now4[RecordMetric.GRANT.ordinal()] = amount;

        if (chainActions <= 1) {
            chainKills = 0;
        }
        RecordRules.ExtraKind kind = RecordRules.extraKind(skill);
        if (kind == RecordRules.ExtraKind.KILLS_PER_CHAIN) {
            if (kill) {
                chainKills++;
            }
            now4[RecordMetric.EXTRA.ordinal()] = chainKills;
        } else if (kind == RecordRules.ExtraKind.CATCHES_PER_MINUTE) {
            if (catchLike) {
                catches.add(now, 1f);
            }
            now4[RecordMetric.EXTRA.ordinal()] = catches.sum(now);
        }

        boolean changed = false;
        for (RecordMetric metric : RecordMetric.VALUES) {
            changed |= records.raise(skill, metric, now4[metric.ordinal()]);
        }

        if (!RecordRules.lifeOver(firstTick, now)) {
            // The first minute of play: records rise but nothing is announced, and what was reached is the baseline.
            for (RecordMetric metric : RecordMetric.VALUES) {
                announced[metric.ordinal()] = Math.max(announced[metric.ordinal()], records.value(skill, metric));
            }
            return new Outcome(changed, null, 0f);
        }
        if (!RecordRules.cooledDown(lastToast, now)) {
            return new Outcome(changed, null, 0f);
        }
        for (RecordMetric metric : RecordMetric.VALUES) {
            float best = records.value(skill, metric);
            if (announced[metric.ordinal()] <= 0f) {
                // A first value is a baseline, not a "new best".
                announced[metric.ordinal()] = best;
                continue;
            }
            if (RecordRules.worthAToast(metric, announced[metric.ordinal()], best)) {
                announced[metric.ordinal()] = best;
                lastToast = now;
                return new Outcome(changed, metric, best);
            }
        }
        return new Outcome(changed, null, 0f);
    }
}
