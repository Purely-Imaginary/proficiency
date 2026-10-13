package dev.amman.proficiency.skill;

/**
 * Critical hits: what a melee crit pays, and the numbers of the talents that use one (Lunge,
 * Rend, Flow, Leaping Blade). Pure, so the rules are unit tested without a game.
 *
 * <p>Only a melee swing counts. A bow's "crit arrow" is just a full draw, which Deadeye and
 * Hanging Shot read on their own, and every crossbow bolt is one.
 */
public final class CritRules {

    /** Hit XP for a critical melee hit, against a plain one. The kill bonus is not scaled. */
    public static final double XP_MULTIPLIER = 1.5;
    /** The signature proc's chance on a critical melee hit, against a plain one. */
    public static final double PROC_CHANCE_MULTIPLIER = 2.0;
    /** Vanilla's crit damage. A forced crit (Lunge) gets at least this. */
    public static final float VANILLA_DAMAGE = 1.5f;
    /** Leaping Blade: a sword crit's damage. */
    public static final float LEAPING_BLADE_DAMAGE = 1.75f;
    /** Lunge: 9 s at rank 1, 3 s sooner per rank. */
    public static final long LUNGE_BASE_TICKS = 180;
    public static final long LUNGE_STEP_TICKS = 60;
    /** Rend: how long the wound stays open, and how much more the target takes. */
    public static final long REND_TICKS = 80;
    public static final float REND_DAMAGE = 1.15f;
    /** Flow: Speed and Haste per rank. */
    public static final int FLOW_TICKS_PER_RANK = 20;

    private CritRules() {
    }

    /** The skills a melee swing trains; only these pay or roll more on a crit. */
    public static boolean isMelee(Skill skill) {
        return skill == Skill.SWORDS || skill == Skill.AXES || skill == Skill.MACES
                || skill == Skill.TRIDENTS || skill == Skill.UNARMED;
    }

    /** The XP factor of one hit: {@link #XP_MULTIPLIER} on a melee crit, else 1. */
    public static double xpFactor(Skill skill, boolean crit) {
        return crit && isMelee(skill) ? XP_MULTIPLIER : 1.0;
    }

    /** The proc chance scale of one hit: {@link #PROC_CHANCE_MULTIPLIER} on a melee crit, else 1. */
    public static double procChanceScale(Skill skill, boolean crit) {
        return crit && isMelee(skill) ? PROC_CHANCE_MULTIPLIER : 1.0;
    }

    /** A chance scaled for a crit, never above certain. */
    public static double scaledChance(double chance, double scale) {
        if (!(chance > 0)) {
            return 0.0;
        }
        return Math.min(1.0, chance * Math.max(0.0, scale));
    }

    /** Lunge's cooldown in ticks for a rank: 180, 120, 60. Zero or less means no Lunge. */
    public static long lungeCooldownTicks(int rank) {
        if (rank <= 0) {
            return 0;
        }
        return Math.max(LUNGE_STEP_TICKS, LUNGE_BASE_TICKS - LUNGE_STEP_TICKS * (Math.min(rank, 3) - 1));
    }

    /** Whether a Lunge is ready: never used, or its cooldown has run out. */
    public static boolean lungeReady(long now, long readyAt) {
        return now >= readyAt;
    }

    /** The crit damage after the crit talents: Leaping Blade raises a sword crit, never lowers one. */
    public static float critDamage(float current, boolean leapingBlade) {
        float floor = leapingBlade ? LEAPING_BLADE_DAMAGE : VANILLA_DAMAGE;
        return Math.max(current, floor);
    }

    /** Flow's Speed and Haste length in ticks for a rank. */
    public static int flowTicks(int rank) {
        return rank <= 0 ? 0 : FLOW_TICKS_PER_RANK * Math.min(rank, 3);
    }

    /** Rend's damage multiplier while the wound is open at {@code now}. */
    public static float rendMultiplier(long now, long openUntil) {
        return now <= openUntil ? REND_DAMAGE : 1.0f;
    }
}
