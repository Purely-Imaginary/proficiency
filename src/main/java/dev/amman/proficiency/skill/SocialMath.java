package dev.amman.proficiency.skill;

/**
 * The numbers behind the company bonus and the Social skill. Pure: no Minecraft types, so every
 * rule here is unit tested. {@link CompanyBonus} looks at the world and {@link SocialService}
 * pays; this class only does arithmetic.
 *
 * <p>Social earns only from XP the company bonus added. A grant multiplied by 1.15 for company
 * added 0.15/1.15 of its final amount, and Social gets {@code share} of that. Standing next to
 * someone while doing nothing makes no grant, so it pays nothing; there is no separate AFK rule
 * because there is nothing to farm.
 */
public final class SocialMath {

    /** Open Circle: the company radius grows by this many blocks per rank. */
    public static final double RADIUS_PER_RANK = 4.0;
    /** Old Friends: camaraderie grows by this per rank. */
    public static final double CAMARADERIE_PER_RANK = 0.05;
    /** Wise Counsel: the mentor bonus grows by this per rank. */
    public static final double MENTOR_PER_RANK = 0.05;
    /** Patient Mentor: a mentor needs this many fewer levels of lead per rank. */
    public static final int GAP_PER_RANK = 4;
    /** Stay a While: the bonus lasts this long per rank after the others leave (10 s). */
    public static final long LINGER_TICKS_PER_RANK = 200L;
    /** Strength in Numbers: this per rank for every extra player near you. */
    public static final double CROWD_PER_RANK = 0.02;
    /** Strength in Numbers counts at most this many players past the first. */
    public static final int CROWD_MAX_EXTRA = 3;
    /** Teaching: a better Social player near you adds this per rank of their Teaching. */
    public static final double TEACHING_PER_RANK = 0.04;

    /** Good Company: how much more XP the proc gives, before proc power, and for how long. */
    public static final double GOOD_COMPANY_XP = 0.20;
    public static final int GOOD_COMPANY_TICKS = 300;
    /** Social XP is collected and paid out at most this often (5 s), so it is one log line. */
    public static final long PAY_INTERVAL_TICKS = 100L;
    /** The proc rolls at most once a minute, or it would be up all the time. */
    public static final long PROC_GATE_TICKS = 1200L;

    private SocialMath() {
    }

    /** Who is near, as the company bonus sees it. */
    public record Company(int others, boolean mentor, double teaching) {
        public static final Company NONE = new Company(0, false, 0.0);
    }

    /** One player's Social talents that change the bonus, read once per computation. */
    public record Talents(int radius, int camaraderie, int mentor, int gap, int linger, int crowd,
            boolean heart) {
        public static final Talents NONE = new Talents(0, 0, 0, 0, 0, 0, false);
    }

    public static double radius(double base, Talents talents) {
        return base + RADIUS_PER_RANK * talents.radius();
    }

    /**
     * Levels of lead someone needs to count as your mentor. Heart of the Group makes it 0: anyone
     * at least as good as you counts. Otherwise never below 1.
     */
    public static int mentorGap(int base, Talents talents) {
        if (talents.heart()) {
            return 0;
        }
        return Math.max(1, base - GAP_PER_RANK * talents.gap());
    }

    /**
     * The extra XP fraction company adds: 0.15 means x1.15. Zero with nobody near.
     *
     * @param passive the Social passive, as a fraction; it scales the whole extra
     */
    public static double extra(Company company, double camaraderie, double mentor, Talents talents,
            double passive) {
        if (company.others() <= 0) {
            return 0.0;
        }
        double extra = company.mentor()
                ? mentor + MENTOR_PER_RANK * talents.mentor()
                : camaraderie + CAMARADERIE_PER_RANK * talents.camaraderie();
        int crowd = Math.min(CROWD_MAX_EXTRA, company.others() - 1);
        extra += CROWD_PER_RANK * talents.crowd() * crowd;
        extra += Math.max(0.0, company.teaching());
        return Math.max(0.0, extra) * (1.0 + Math.max(0.0, passive));
    }

    /** What a teacher with {@code rank} ranks of Teaching adds for a student near them. */
    public static double teaching(int rank) {
        return TEACHING_PER_RANK * Math.max(0, rank);
    }

    /** Whether the last company, seen at {@code lastSeen}, still counts at {@code now}. */
    public static boolean lingers(long now, long lastSeen, int rank) {
        return rank > 0 && lastSeen >= 0 && now >= lastSeen
                && now - lastSeen <= LINGER_TICKS_PER_RANK * rank;
    }

    /**
     * Base Social XP from one grant: {@code share} of what the company factor added to it.
     * {@code amount} is the grant after every multiplier. Nothing when company was 1.0 or less.
     */
    public static double share(double amount, double companyFactor, double share) {
        if (!(amount > 0) || !(companyFactor > 1.0) || !(share > 0)
                || !Double.isFinite(amount) || !Double.isFinite(companyFactor)) {
            return 0.0;
        }
        return amount * (1.0 - 1.0 / companyFactor) * share;
    }

    /** Good Company's XP multiplier for a given proc power. */
    public static double goodCompany(double power) {
        return 1.0 + GOOD_COMPANY_XP * Math.max(1.0, power);
    }
}
