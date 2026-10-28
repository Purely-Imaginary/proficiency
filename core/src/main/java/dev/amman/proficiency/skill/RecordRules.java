package dev.amman.proficiency.skill;

/**
 * The numbers behind personal records. A record is stored the moment it is beaten; a toast is rarer: it needs a real
 * improvement over the last announced best, a skill that has been running a minute, and 30 s since the skill's last
 * toast. All pure so the thresholds are unit tested.
 */
public final class RecordRules {

    /** The rolling window the XP rate is measured over: 60 seconds. */
    public static final long WINDOW_TICKS = 1200;
    /** No toast in a skill's first minute of play in a session (the records still rise, silently). */
    public static final long MIN_LIFE_TICKS = 1200;
    /** At most one record toast per skill in 30 seconds. */
    public static final long TOAST_COOLDOWN_TICKS = 600;
    /** A toast needs the best to rise by at least this share of the last announced one... */
    public static final float MIN_RELATIVE = 0.10f;
    /** ... and by at least this much in its own unit (XP per minute, steps, XP, count). */
    private static final float[] MIN_ABSOLUTE = {10f, 3f, 5f, 2f};

    /** What a skill's extra record counts. */
    public enum ExtraKind {
        NONE, KILLS_PER_CHAIN, CATCHES_PER_MINUTE
    }

    private RecordRules() {
    }

    public static ExtraKind extraKind(Skill skill) {
        return switch (skill) {
            case BEASTSLAYING -> ExtraKind.KILLS_PER_CHAIN;
            case FISHING -> ExtraKind.CATCHES_PER_MINUTE;
            default -> ExtraKind.NONE;
        };
    }

    public static float minGain(RecordMetric metric, float announced) {
        return Math.max(MIN_ABSOLUTE[metric.ordinal()], announced * MIN_RELATIVE);
    }

    /** True when {@code best} beats the last announced value by enough to be worth a toast. */
    public static boolean worthAToast(RecordMetric metric, float announced, float best) {
        return best >= announced + minGain(metric, announced);
    }

    public static boolean lifeOver(long firstTick, long now) {
        return firstTick != Long.MIN_VALUE && now - firstTick >= MIN_LIFE_TICKS;
    }

    public static boolean cooledDown(long lastToast, long now) {
        return now < lastToast || now - lastToast >= TOAST_COOLDOWN_TICKS;
    }
}
