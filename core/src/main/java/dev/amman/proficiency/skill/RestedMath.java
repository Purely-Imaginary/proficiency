package dev.amman.proficiency.skill;

/**
 * The numbers behind rested XP and teaching. Pure: no Minecraft types, so every rule is unit
 * tested. The services in common/ look at the world; this class does the arithmetic. The design is
 * in docs/RESTED-AND-TEACHING.md.
 *
 * <p>A skill's rested pool fills while its owner is active (not AFK) and does not use that skill.
 * It is capped at {@link SkillTuning#restedCapFactor()} times what the current level costs, so the
 * cap grows with the skill. Spending doubles each grant in the skill (the extra is
 * {@link SkillTuning#restedExtra()} times the grant) until the pool is empty.
 */
public final class RestedMath {

    /** Real days a skill may sit unused before its tooltip calls it rusty. A flavour line only. */
    public static final int RUSTY_DAYS = 7;

    /** A skill counts as being used for this long after its last grant, so a rest is a whole quiet minute. */
    public static final long RESTING_TICKS = 1200L;

    /** A teacher counts as using a skill for this long after their last grant in it (5 s). */
    public static final long TEACHING_WINDOW_TICKS = 100L;

    /** Wide Classroom: the teaching radius grows by this many blocks per rank. */
    public static final double RADIUS_PER_RANK = 6.0;
    /** Quick Study: the teaching fill rate grows by this fraction per rank. */
    public static final double FILL_PER_RANK = 0.15;
    /** Teacher's Pride: the teacher's cut grows by this much per rank (0.05 is 5 points). */
    public static final double CUT_PER_RANK = 0.05;

    /** Where the defaults live, so config and tests agree. */
    public static final double DEFAULT_CAP_FACTOR = 1.5;
    public static final double DEFAULT_FULL_HOURS = 9.0;
    public static final double DEFAULT_EXTRA = 1.0;
    public static final double DEFAULT_TEACH_FACTOR = 3.0;
    public static final double DEFAULT_TEACHER_SHARE = 0.25;

    private static final long MS_PER_DAY = 86_400_000L;

    private RestedMath() {
    }

    /** Whether rested XP is on at all: it needs a cap and a fill time. */
    public static boolean enabled() {
        SkillTuning tuning = SkillTuning.current();
        return tuning.restedCapFactor() > 0 && tuning.restedFullHours() > 0
                && finite(tuning.restedCapFactor()) && finite(tuning.restedFullHours());
    }

    private static boolean finite(double value) {
        return !Double.isNaN(value) && !Double.isInfinite(value);
    }

    /**
     * The XP the bar in front of the player costs: the level's cost, or at level 100 the next
     * star's. Zero when nothing more can be earned (the last star, or Mastery off), so there is
     * nothing for a pool to be spent on.
     */
    public static float need(int level, int stars) {
        if (level < SkillMath.MAX_LEVEL) {
            return SkillMath.xpToNext(Math.max(0, level));
        }
        if (Mastery.maxStars() <= 0 || stars >= Mastery.maxStars()) {
            return 0f;
        }
        return Mastery.starCost(stars + 1);
    }

    /** The most a skill's pool can hold at this level. Zero when rested XP is off or the skill is done. */
    public static float cap(int level, int stars) {
        if (!enabled()) {
            return 0f;
        }
        return (float) (need(level, stars) * SkillTuning.current().restedCapFactor());
    }

    /** XP per second a resting skill gains: the cap spread over the hours to fill it. */
    public static double idlePerSecond(float cap) {
        if (!enabled() || !(cap > 0)) {
            return 0.0;
        }
        return cap / (SkillTuning.current().restedFullHours() * 3600.0);
    }

    /** XP per second a teacher adds to a student's skill, with Quick Study at {@code fillRank}. */
    public static double teachPerSecond(float cap, int fillRank) {
        double factor = Math.max(0.0, SkillTuning.current().restedTeachFactor());
        return idlePerSecond(cap) * factor * (1.0 + FILL_PER_RANK * Math.max(0, fillRank));
    }

    /** How far a teacher reaches: their company radius plus Wide Classroom. */
    public static double teachRadius(double companyRadius, int radiusRank) {
        return companyRadius + RADIUS_PER_RANK * Math.max(0, radiusRank);
    }

    /**
     * The XP a spend adds to one grant. {@code amount} is the grant after every multiplier,
     * survival streak included; {@code streak} is the streak's own multiplier (1.0 or more).
     * The extra is worked out from the grant without the streak, so the two add up
     * ({@code plain x (streak + 1)}) instead of multiplying ({@code plain x streak x 2}).
     * It is capped by {@code pool}. Zero for a broken number.
     */
    public static float extra(float amount, double streak, float pool) {
        if (!enabled() || !(amount > 0) || !(pool > 0) || !finite(amount) || !finite(pool)) {
            return 0f;
        }
        double factor = Math.max(1.0, finite(streak) ? streak : 1.0);
        double plain = amount / factor;
        double want = plain * Math.max(0.0, SkillTuning.current().restedExtra());
        return (float) Math.max(0.0, Math.min(want, pool));
    }

    /** The teacher's share of what a student spent: 25% by default, plus Teacher's Pride. */
    public static double teacherShare(int cutRank) {
        return Math.max(0.0, SkillTuning.current().restedTeacherShare()) + CUT_PER_RANK * Math.max(0, cutRank);
    }

    /** Base Social XP owed to a teacher for {@code spent} XP of their part of a student's pool. */
    public static double credit(float spent, int cutRank) {
        if (!(spent > 0) || !finite(spent)) {
            return 0.0;
        }
        return spent * teacherShare(cutRank);
    }

    /** True when a skill has had no grant for {@link #RESTING_TICKS} (or never in this session). */
    public static boolean resting(long now, long lastUsed) {
        return lastUsed == Long.MIN_VALUE || now < lastUsed || now - lastUsed > RESTING_TICKS;
    }

    /** True when a teacher used the skill within {@link #TEACHING_WINDOW_TICKS}. */
    public static boolean usedLately(long now, long lastUsed) {
        return lastUsed != Long.MIN_VALUE && now >= lastUsed && now - lastUsed <= TEACHING_WINDOW_TICKS;
    }

    /** Today as a count of real days, from the server's wall clock. */
    public static long today() {
        return Math.floorDiv(System.currentTimeMillis(), MS_PER_DAY);
    }

    /** Whole real days since {@code usedDay}; 0 when it was never set. */
    public static int daysSince(long usedDay, long today) {
        if (usedDay <= 0 || today <= usedDay) {
            return 0;
        }
        return (int) Math.min(Integer.MAX_VALUE, today - usedDay);
    }

    public static boolean rusty(int daysIdle) {
        return daysIdle >= RUSTY_DAYS;
    }

    /**
     * How far the pool reaches along the bar: the pool as a fraction of the bar in front of the
     * player, never more than 1. The HUD paints that much blue after the filled part.
     */
    public static float reach(float pool, int level, int stars) {
        float need = need(level, stars);
        if (!(need > 0) || !(pool > 0)) {
            return 0f;
        }
        return Math.min(1f, pool / need);
    }

    // ---- The rules that touch a player's skills --------------------------------------------------

    /** A teacher at least {@code gap} levels above a student counts as their teacher in that skill. */
    public static boolean canTeach(int teacherLevel, int studentLevel, int gap) {
        return teacherLevel >= studentLevel + Math.max(0, gap);
    }

    /**
     * One step of resting for a player: every skill that has not been used for a whole quiet minute
     * fills by its idle rate over {@code seconds}. Nothing at all unless the player is active
     * (the survival streak's own not-AFK test: XP earned within {@code windowTicks}). Returns the
     * XP added across all skills.
     */
    public static float rest(PlayerSkills skills, long now, long windowTicks, double seconds) {
        return rest(skills, now, windowTicks, seconds, java.util.EnumSet.noneOf(Skill.class));
    }

    /** As {@link #rest(PlayerSkills, long, long, double)}, leaving out {@code skip}: skills a teacher is filling. */
    public static float rest(PlayerSkills skills, long now, long windowTicks, double seconds,
            java.util.Set<Skill> skip) {
        if (!enabled() || !(seconds > 0) || !skills.isActive(now, windowTicks)) {
            return 0f;
        }
        float added = 0f;
        for (Skill skill : Skill.VALUES) {
            if (skip.contains(skill) || !SkillTuning.current().enabled(skill)
                    || !resting(now, skills.usedAt(skill))) {
                continue;
            }
            float cap = skills.restedCap(skill);
            if (cap > 0f && skills.rested(skill) < cap) {
                added += skills.fillRested(skill, (float) (idlePerSecond(cap) * seconds));
            }
        }
        return added;
    }

    /**
     * One step of teaching: {@code teacher} is using {@code skill} near this student, so the
     * student's pool in it fills at the teaching rate over {@code seconds}, and the part is
     * remembered against the teacher. The caller has already checked who may teach whom and that
     * both are active. Returns the XP added.
     */
    public static float teach(PlayerSkills student, Skill skill, java.util.UUID teacher, int fillRank,
            double seconds) {
        if (!enabled() || !(seconds > 0) || teacher == null) {
            return 0f;
        }
        float cap = student.restedCap(skill);
        if (!(cap > 0f) || student.rested(skill) >= cap) {
            return 0f;
        }
        return student.fillRestedTaught(skill, teacher, (float) (teachPerSecond(cap, fillRank) * seconds));
    }

    /** The XP owed to one teacher for a spend, in base Social XP. */
    public record Credit(java.util.UUID teacher, double socialXp) {
    }

    /** What spending the pool on one grant did: the extra XP, and who is owed what. */
    public record Spend(float extra, java.util.List<Credit> credits) {
        public static final Spend NONE = new Spend(0f, java.util.List.of());
    }

    /**
     * Spends the pool on one grant in {@code skill}: takes the extra out of the pool and works out
     * the teachers' credit from the taught parts it used. {@code amount} is the grant after every
     * multiplier and {@code streak} the survival streak's multiplier within it (see {@link #extra}).
     * {@code cutRankOf} gives a teacher's Teacher's Pride rank, since the cut is theirs.
     */
    public static Spend spend(PlayerSkills skills, Skill skill, float amount, double streak,
            java.util.function.ToIntFunction<java.util.UUID> cutRankOf) {
        float want = extra(amount, streak, skills.rested(skill));
        if (!(want > 0f)) {
            return Spend.NONE;
        }
        RestedPool.Spent spent = skills.spendRested(skill, want);
        if (!(spent.total() > 0f)) {
            return Spend.NONE;
        }
        java.util.List<Credit> credits = new java.util.ArrayList<>(spent.taught().size());
        for (RestedPool.Part part : spent.taught()) {
            double owed = credit(part.xp(), cutRankOf == null ? 0 : cutRankOf.applyAsInt(part.teacher()));
            if (owed > 0) {
                credits.add(new Credit(part.teacher(), owed));
            }
        }
        return new Spend(spent.total(), credits);
    }
}
