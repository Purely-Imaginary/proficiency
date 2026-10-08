package dev.amman.proficiency.skill;

/**
 * Mastery stars: what XP does once a skill is level 100. It fills an overflow bar that earns up to
 * {@link #MAX_STARS} stars. Stars are cosmetic: no stat, no talent point and no proc reads them.
 *
 * <p>Star {@code n} costs {@code starBase * n}, and {@code starBase} is the cost of the last level
 * (99 to 100) times a config factor. The factor defaults to 1, not 3: the five stars then cost
 * 15 times the last level, about 15,000 XP, which is what the last 15 levels cost (levels 85 to
 * 100). A factor of 3 would be 45,000 XP, as much as the whole climb from 0 to 100.
 */
public final class Mastery {

    public static final int MAX_STARS = 5;

    private Mastery() {
    }

    /** Stars a player can earn, per config: 0 switches the whole feature off, never above 5. */
    public static int maxStars() {
        double factor = SkillTuning.current().masteryStarFactor();
        if (!(factor > 0) || !Double.isFinite(factor)) {
            return 0;
        }
        return Math.max(0, Math.min(MAX_STARS, SkillTuning.current().masteryMaxStars()));
    }

    /** The star that ends the chase: the configured cap, or 5 when Mastery is off but stars were earned. */
    public static int lastStar() {
        int cap = maxStars();
        return cap > 0 ? cap : MAX_STARS;
    }

    /**
     * Earned stars as they are shown: never above {@link #lastStar()}. A server that lowers the cap
     * after players earned more stars would otherwise show "5/3"; the stored count is left alone.
     */
    public static int shownStars(int earned) {
        return Math.max(0, Math.min(earned, lastStar()));
    }

    /** What an operator's star count becomes: never below 0 and never above the configured cap. */
    public static int clampOpStars(int requested) {
        return Math.max(0, Math.min(requested, maxStars()));
    }

    /** The cost of star 1. */
    public static float starBase() {
        double base = SkillMath.xpToNext(SkillMath.MAX_LEVEL - 1) * SkillTuning.current().masteryStarFactor();
        return (float) Math.max(1.0, base);
    }

    /** XP to fill the bar for star {@code n} (1 to 5). */
    public static float starCost(int n) {
        return starBase() * Math.max(1, Math.min(MAX_STARS, n));
    }

    /** XP from the moment a skill reaches 100 to the {@code stars}-th star. */
    public static float totalCost(int stars) {
        float total = 0f;
        for (int n = 1; n <= Math.min(stars, MAX_STARS); n++) {
            total += starCost(n);
        }
        return total;
    }

    /**
     * What one XP grant did: levels and stars it earned, and how much of it was banked. Past the
     * last star the rest of a grant is dropped, so {@code banked} can be less than the grant.
     */
    public record Result(int levels, int stars, float banked) {
        public static final Result NONE = new Result(0, 0, 0f);

        public Result(int levels, int stars) {
            this(levels, stars, Float.NaN);
        }

        /** The XP that stayed, given what was paid: the whole grant unless the result says less. */
        public float bankedOf(float paid) {
            return Float.isNaN(banked) ? paid : Math.max(0f, Math.min(paid, banked));
        }
    }
}
