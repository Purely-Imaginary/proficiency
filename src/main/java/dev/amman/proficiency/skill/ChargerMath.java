package dev.amman.proficiency.skill;

/**
 * The numbers behind Charger. Pure: no Minecraft types, so every rule here is unit tested.
 * {@code ChargerEvents} watches the world and pays; this class only decides.
 *
 * <p>Charger is initiative and momentum: first in, always moving forward. Three XP sources:
 * <ul>
 * <li>first blood: the first melee hit on a hostile mob that no player hit in the last 10 s,
 * once per mob;</li>
 * <li>charge hit: a melee hit after you closed at least 5 blocks on the target in the last 2 s,
 * by any means (walking, falling, riding), scaled by the distance closed. No sprint needed; a
 * sprint attack pays more;</li>
 * <li>Spearhead kill: a melee kill while a friend is behind you and hostile mobs are ahead.</li>
 * </ul>
 */
public final class ChargerMath {

    // ---- XP --------------------------------------------------------------------------------

    /** Base XP for a first blood on a 20-health mob, before its worth. */
    public static final double DEFAULT_FIRST_BLOOD_XP = 3.0;
    /** Base XP per block closed on a charge hit. */
    public static final double DEFAULT_CHARGE_XP_PER_BLOCK = 0.4;
    /** Base XP for a Spearhead kill of a 20-health mob, before its worth. */
    public static final double DEFAULT_SPEARHEAD_KILL_XP = 2.0;

    /** A charge hit pays for at most this many blocks closed. */
    public static final double MAX_CHARGE_BLOCKS = 12.0;
    /** A sprint attack on a charge hit pays this much more XP. */
    public static final double SPRINT_XP = 1.5;
    /** A sprint attack on a charge hit makes Breach this much more likely. */
    public static final double SPRINT_BREACH = 1.5;

    /** Worth: a mob's max health over this, so a zombie is 1.0. */
    public static final double WORTH_HEALTH = 20.0;
    public static final double WORTH_MIN = 0.5;
    public static final double WORTH_MAX = 3.0;

    // ---- Anti-farm ---------------------------------------------------------------------------

    /** First blood: no player hit the mob in this long (10 s). */
    public static final long FIRST_BLOOD_QUIET_TICKS = 200L;
    /** First blood pays only on a fresh mob: at least this share of its max health left. */
    public static final double FRESH_SHARE = 0.9;
    /** A charge hit pays at most this many times on one mob. Saved on the mob. */
    public static final int CHARGE_PAYS_PER_MOB = 3;
    /** The spot rule: payouts this close to each other count as one spot. */
    public static final double SPOT_RADIUS = 12.0;
    /** The spot rule: payouts are remembered this long (5 min). */
    public static final long SPOT_WINDOW_TICKS = 6000L;
    /** The spot rule: at most this many Charger payouts in one spot inside the window. */
    public static final int SPOT_LIMIT = 16;

    // ---- The charge --------------------------------------------------------------------------

    /** A charge closes at least this many blocks on the target... */
    public static final double CHARGE_MIN_BLOCKS = 5.0;
    /** ...in this long (2 s). */
    public static final long CHARGE_WINDOW_TICKS = 40L;
    /** Head Start: a charge needs this many blocks less per rank. */
    public static final double HEAD_START_PER_RANK = 0.5;
    /** The trail keeps a sample every this many ticks. */
    public static final int TRAIL_STEP_TICKS = 2;
    /** Lifesteal works this long after a charge hit (5 s). */
    public static final long LIFESTEAL_TICKS = 100L;
    /** Lifesteal: this share of the damage dealt comes back as health. */
    public static final double LIFESTEAL_SHARE = 0.10;
    /** Red Harvest: this much more share per rank. */
    public static final double RED_HARVEST_PER_RANK = 0.05;
    /** Lifesteal: at most this much health per hit, plus half a point per Red Harvest rank. */
    public static final double LIFESTEAL_CAP = 1.0;

    // ---- First blood's shield ----------------------------------------------------------------

    /** Absorption from a first blood lasts this long (8 s), plus Crash In. */
    public static final int ABSORB_TICKS = 160;
    /** Crash In: this much longer per rank (4 s). */
    public static final int CRASH_IN_TICKS_PER_RANK = 80;
    /** At most one first-blood shield in this long (10 s), or a crowd would keep it full. */
    public static final long ABSORB_COOLDOWN_TICKS = 200L;

    // ---- Spearhead ---------------------------------------------------------------------------

    /** Spearhead's damage and knockback resistance start at this Charger level. Its XP does not wait. */
    public static final int SPEARHEAD_LEVEL = 10;
    /** First blood's shield and the charge's lifesteal start at this level, with the signature. */
    public static final int SUSTAIN_LEVEL = 25;
    /** Spearhead: a friend behind you this close, and a hostile ahead this close. */
    public static final double SPEARHEAD_RADIUS = 16.0;
    /** Spearpoint: friends count from this far behind. */
    public static final double SPEARPOINT_RADIUS = 24.0;
    /** Spearhead: this much more melee damage on hostile mobs. */
    public static final double SPEARHEAD_DAMAGE = 0.10;
    /** Spearpoint: this much more on top. */
    public static final double SPEARPOINT_DAMAGE = 0.10;
    /** Spearhead: knockback resistance while it holds. */
    public static final double SPEARHEAD_KNOCKBACK = 0.5;
    /** Trust the Line: damage from friends cut by this share at rank 1, 2, 3. Only in Spearhead. */
    private static final double[] TRUST_CUT = {0.0, 0.50, 0.65, 0.80};

    // ---- Shield and Spear (with Guardian) ----------------------------------------------------

    /** A friend behind you counts as a Guardian from this Guardian level. */
    public static final int GUARDIAN_LEVEL = 10;
    /** Shield and Spear: Spearhead's damage bonus times this, and full knockback resistance. */
    public static final double PAIR_DAMAGE = 1.5;
    /** The Shield and Spear synergy (one of the pair trained both trees): the pair bonus again. */
    public static final double PAIR_SYNERGY_DAMAGE = 2.0;

    // ---- Breach, Charge!, Warbringer ---------------------------------------------------------

    /** Breach: mobs this close in front of you are hit. */
    public static final double BREACH_RANGE = 4.0;
    /** Breach: the cone's half angle, as the cosine (45 degrees). */
    public static final double BREACH_COS = 0.7071;
    /** Breach: the push, times proc power. */
    public static final double BREACH_KNOCKBACK = 0.8;
    /** Breach: Slowness II this long (1.5 s), times proc power. */
    public static final int BREACH_STAGGER_TICKS = 30;
    /** Charge!: the dash's speed in blocks per tick. About 6 blocks on flat ground. */
    public static final double DASH_SPEED = 1.2;
    /** Warbringer: the dash goes this much further. */
    public static final double WARBRINGER_DASH = 2.0;
    /** Charge!: friends behind you get Speed I this long (5 s). */
    public static final int WAR_CRY_TICKS = 100;
    /** Charge!'s promise that the next target is first blood lasts this long (20 s). */
    public static final long FORCED_FIRST_BLOOD_TICKS = 400L;
    /** Onslaught: a kill this soon after your first blood makes the next target first blood (3 s). */
    public static final long ONSLAUGHT_TICKS = 60L;
    /** Warbringer: each first blood takes this much off Charge!'s cooldown (3 s). */
    public static final long WARBRINGER_COOLDOWN_TICKS = 60L;

    private ChargerMath() {
    }

    /** What a mob is worth: its max health over 20, from 0.5 to 3. A boss is worth 3. */
    public static double worth(double maxHealth, boolean boss) {
        if (boss) {
            return WORTH_MAX;
        }
        if (!(maxHealth > 0) || !Double.isFinite(maxHealth)) {
            return WORTH_MIN;
        }
        return Math.max(WORTH_MIN, Math.min(WORTH_MAX, maxHealth / WORTH_HEALTH));
    }

    /**
     * A first blood: no player hit the mob in the last 10 s, and the mob is fresh (90% of its max
     * health or more: a mob a drop tower softened is not a first blood). {@code lastHitAt} below
     * zero means no player ever hit it.
     */
    public static boolean firstBlood(long lastHitAt, long now, double health, double maxHealth) {
        boolean quiet = lastHitAt < 0 || now < lastHitAt || now - lastHitAt > FIRST_BLOOD_QUIET_TICKS;
        return quiet && fresh(health, maxHealth);
    }

    /** At least 90% of max health left. A broken maximum is never fresh. */
    public static boolean fresh(double health, double maxHealth) {
        return maxHealth > 0 && health >= maxHealth * FRESH_SHARE;
    }

    /** First blood XP: the base times the mob's worth. */
    public static double firstBloodXp(double base, double worth) {
        return base > 0 && worth > 0 ? base * worth : 0.0;
    }

    /** The blocks a charge needs, after Head Start. Never under 2. */
    public static double chargeNeeds(int headStart) {
        return Math.max(2.0, CHARGE_MIN_BLOCKS - HEAD_START_PER_RANK * Math.max(0, headStart));
    }

    /** Whether a charge closed enough. */
    public static boolean isCharge(double closed, int headStart) {
        return closed >= chargeNeeds(headStart);
    }

    /** Charge hit XP: per block closed (at most 12), times 1.5 for a sprint attack. */
    public static double chargeXp(double closed, boolean sprint, double perBlock) {
        if (!(closed > 0) || !(perBlock > 0)) {
            return 0.0;
        }
        return Math.min(closed, MAX_CHARGE_BLOCKS) * perBlock * (sprint ? SPRINT_XP : 1.0);
    }

    /** Spearhead kill XP: the base times the mob's worth. */
    public static double spearheadKillXp(double base, double worth) {
        return base > 0 && worth > 0 ? base * worth : 0.0;
    }

    /**
     * The spot rule: how many payouts may still happen here. {@code recentNear} is how many
     * Charger payouts this player got within 12 blocks of here in the last 5 minutes.
     */
    public static boolean spotAllows(int recentNear) {
        return recentNear < SPOT_LIMIT;
    }

    /** A remembered payout still counts for the spot rule. */
    public static boolean inSpotWindow(long paidAt, long now) {
        return paidAt >= 0 && now >= paidAt && now - paidAt < SPOT_WINDOW_TICKS;
    }

    /** First blood's melee damage: the passive (its bonus) and any boosts, as one multiplier. */
    public static double firstBloodMultiplier(double passive, double boosts) {
        double base = 1.0 + Math.max(0.0, passive);
        return base * (boosts > 0 && Double.isFinite(boosts) ? boosts : 1.0);
    }

    /**
     * Spearhead's damage bonus: 10%, plus 10% with Spearpoint, times 1.5 with a Guardian behind
     * you (Shield and Spear) and times 2 instead when one of you has the synergy.
     */
    public static double spearheadBonus(boolean spearpoint, boolean guardianBehind, boolean synergy) {
        double bonus = SPEARHEAD_DAMAGE + (spearpoint ? SPEARPOINT_DAMAGE : 0.0);
        if (guardianBehind) {
            bonus *= synergy ? PAIR_SYNERGY_DAMAGE : PAIR_DAMAGE;
        }
        return bonus;
    }

    /** Spearhead's knockback resistance: half, or all of it with a Guardian behind. */
    public static double spearheadKnockback(boolean guardianBehind) {
        return guardianBehind ? 1.0 : SPEARHEAD_KNOCKBACK;
    }

    /** Trust the Line: what is left of a friend's hit on you in Spearhead. */
    public static double trustMultiplier(int rank, boolean spearhead) {
        if (!spearhead || rank <= 0) {
            return 1.0;
        }
        return 1.0 - TRUST_CUT[Math.min(rank, TRUST_CUT.length - 1)];
    }

    /** Whether a lifesteal window from a charge hit at {@code chargedAt} is still open. */
    public static boolean lifestealOpen(long chargedAt, long now) {
        return chargedAt >= 0 && now >= chargedAt && now - chargedAt <= LIFESTEAL_TICKS;
    }

    /** Lifesteal: 10% of the damage (+5% per Red Harvest rank), at most 1 (+0.5 per rank). */
    public static float lifesteal(double damage, int redHarvest) {
        if (!(damage > 0)) {
            return 0f;
        }
        int rank = Math.max(0, redHarvest);
        double share = LIFESTEAL_SHARE + RED_HARVEST_PER_RANK * rank;
        double cap = LIFESTEAL_CAP + 0.5 * rank;
        return (float) Math.min(damage * share, cap);
    }

    /** First blood's Absorption: how long, with Crash In. */
    public static int absorbTicks(int crashIn) {
        return ABSORB_TICKS + CRASH_IN_TICKS_PER_RANK * Math.max(0, crashIn);
    }

    /** First blood's Absorption: Absorption I, or II with Crash In at rank 3. */
    public static int absorbAmplifier(int crashIn) {
        return crashIn >= 3 ? 1 : 0;
    }

    /** The first-blood shield is ready again. */
    public static boolean absorbReady(long lastAt, long now) {
        return lastAt < 0 || now < lastAt || now - lastAt >= ABSORB_COOLDOWN_TICKS;
    }

    /** Breach's push, with proc power. */
    public static double breachKnockback(double power) {
        return BREACH_KNOCKBACK * Math.max(1.0, power);
    }

    /** Breach's stagger (Slowness II), with proc power. */
    public static int breachStaggerTicks(double power) {
        return (int) Math.round(BREACH_STAGGER_TICKS * Math.max(1.0, power));
    }

    /**
     * Whether a point is in Breach's cone: within 4 blocks, and within 45 degrees of where you
     * face. {@code fx, fz} is your horizontal facing (need not be unit length).
     */
    public static boolean inCone(double fx, double fz, double dx, double dz) {
        double dist = Math.sqrt(dx * dx + dz * dz);
        double facing = Math.sqrt(fx * fx + fz * fz);
        if (dist > BREACH_RANGE || !(facing > 0)) {
            return false;
        }
        if (dist < 1e-6) {
            return true;
        }
        return (fx * dx + fz * dz) / (dist * facing) >= BREACH_COS;
    }

    /** Ahead or behind must be at least this far along your facing, so beside you is neither. */
    public static final double SIDE_MARGIN = 0.5;

    /** In front of you (the half plane you face, past half a block), for Spearhead. */
    public static boolean ahead(double fx, double fz, double dx, double dz) {
        return along(fx, fz, dx, dz) > SIDE_MARGIN;
    }

    /** Behind you (the half plane at your back, past half a block), for Spearhead. */
    public static boolean behind(double fx, double fz, double dx, double dz) {
        return along(fx, fz, dx, dz) < -SIDE_MARGIN;
    }

    /** How far the offset reaches along your facing, in blocks. */
    private static double along(double fx, double fz, double dx, double dz) {
        double length = Math.sqrt(fx * fx + fz * fz);
        return length > 0 ? (fx * dx + fz * dz) / length : 0.0;
    }

    /** Charge!'s dash speed, with Warbringer. */
    public static double dashSpeed(boolean warbringer) {
        return DASH_SPEED * (warbringer ? WARBRINGER_DASH : 1.0);
    }

    /**
     * Where a player has been, for the charge. A small ring of positions, one every 2 ticks.
     * {@link #closed} says how far the player closed on a target in the last 2 s. Only your own
     * movement counts: a mob that walks up to you is not your charge.
     */
    public static final class Trail {
        private static final int SIZE = (int) (CHARGE_WINDOW_TICKS / TRAIL_STEP_TICKS) + 2;
        private final long[] ticks = new long[SIZE];
        private final double[] xs = new double[SIZE];
        private final double[] ys = new double[SIZE];
        private final double[] zs = new double[SIZE];
        private int next;
        private int count;

        public void add(long tick, double x, double y, double z) {
            ticks[next] = tick;
            xs[next] = x;
            ys[next] = y;
            zs[next] = z;
            next = (next + 1) % SIZE;
            count = Math.min(SIZE, count + 1);
        }

        /** Forget the trail: a charge hit spends the charge. */
        public void clear() {
            count = 0;
            next = 0;
        }

        public int size() {
            return count;
        }

        /**
         * How far you closed on the target (at {@code tx, ty, tz}) in the last 2 s, by your own
         * movement. For each recent position of yours, two numbers, and the smaller one counts:
         * <ul>
         * <li>the gap: how much nearer the target's spot you are now than you were then;</li>
         * <li>your push: how far you moved toward the target, measured along the line from you
         * now to the target (your move projected on it).</li>
         * </ul>
         * The push alone stops a mob that walks up to you from counting. Retreating from a mob that
         * follows you, then turning to hit it, has a big gap but a negative push: the mob is on
         * the side you came from. Never below 0.
         */
        public double closed(long now, double x, double y, double z, double tx, double ty, double tz) {
            double here = dist(x, y, z, tx, ty, tz);
            double ux = 0.0;
            double uy = 0.0;
            double uz = 0.0;
            if (here > 1e-6) {
                ux = (tx - x) / here;
                uy = (ty - y) / here;
                uz = (tz - z) / here;
            }
            double best = 0.0;
            for (int i = 0; i < count; i++) {
                long age = now - ticks[i];
                if (age < 0 || age > CHARGE_WINDOW_TICKS) {
                    continue;
                }
                double gap = dist(xs[i], ys[i], zs[i], tx, ty, tz) - here;
                // Standing on the target's spot: the gap alone is all there is to go on.
                double push = here > 1e-6
                        ? (x - xs[i]) * ux + (y - ys[i]) * uy + (z - zs[i]) * uz
                        : gap;
                best = Math.max(best, Math.min(gap, push));
            }
            return best;
        }

        private static double dist(double ax, double ay, double az, double bx, double by, double bz) {
            double dx = ax - bx;
            double dy = ay - by;
            double dz = az - bz;
            return Math.sqrt(dx * dx + dy * dy + dz * dz);
        }
    }
}
