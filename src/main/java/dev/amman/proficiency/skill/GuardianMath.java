package dev.amman.proficiency.skill;

/**
 * The numbers behind Guardian. Pure: no Minecraft types, so every rule here is unit tested.
 * {@code GuardianEvents} watches the world and pays; this class only decides.
 *
 * <p>Guardian pays only for protecting another player. There are five sources:
 * <ul>
 * <li>cover: damage you take from a mob that was after a player near you, or that you pulled
 * off them;</li>
 * <li>block: damage you block with a shield or soak with absorption hearts while a player is
 * within 4 blocks;</li>
 * <li>avenger: a kill on a mob that hurt a player in the last 5 seconds;</li>
 * <li>heal: health your thrown healing or regeneration potion gives back to a player;</li>
 * <li>revive: a player drops under 30% health and lives 10 more seconds with you next to them.</li>
 * </ul>
 * Each one is scaled by the ally's danger, which comes from their health: a healthy friend pays
 * a quarter, a friend near death pays double.
 */
public final class GuardianMath {

    /** Danger for an ally at full health. Shadowing a healthy friend pays little. */
    public static final double DANGER_FLOOR = 0.25;
    /** Danger for an ally at zero health. It grows in a straight line from the floor. */
    public static final double DANGER_CEILING = 2.0;

    /** Base XP per point of damage you take for an ally, before danger. */
    public static final double DEFAULT_COVER_XP_PER_DAMAGE = 1.0;
    /** Base XP per point of damage you block or absorb near an ally, before danger. */
    public static final double DEFAULT_BLOCK_XP_PER_DAMAGE = 0.5;
    /** Base XP for killing a mob that hurt an ally, before danger. */
    public static final double DEFAULT_AVENGER_XP = 4.0;
    /** Base XP per point of health your potion gives back to an ally, before danger. */
    public static final double DEFAULT_HEAL_XP_PER_HEALTH = 1.0;
    /** Base XP for staying next to an ally through a close call, before danger. */
    public static final double DEFAULT_REVIVE_XP = 20.0;

    /** One hit never counts more damage than this. */
    public static final double MAX_DAMAGE_PER_HIT = 20.0;
    /** One mob pays cover and block XP for at most this much damage in total. Saved on the mob. */
    public static final double MOB_CAP = 40.0;

    /** Cover: the ally a mob was after must be this close to you. */
    public static final double COVER_RADIUS = 8.0;
    /** Cover: a mob you pulled off an ally (you hit it, then it turned) may come from this far. */
    public static final double PULLED_RADIUS = 16.0;
    /** Cover: a mob remembers the ally it turned away from for this long (20 s). */
    public static final long PROTECT_MEMORY_TICKS = 400L;
    /** Cover: your hit must be this recent for the turn to count as "pulled" (2 s). */
    public static final long PULL_HIT_TICKS = 40L;
    /** Block: an ally must be this close when you block or absorb. */
    public static final double BLOCK_RADIUS = 4.0;
    /** Avenger: the mob hurt an ally within this long (5 s). */
    public static final long AVENGER_TICKS = 100L;
    /** Avenger: the ally must be this close to you at the kill. */
    public static final double AVENGER_RADIUS = 16.0;
    /** Heal: the ally must have been hurt by something that is not a player this recently (30 s). */
    public static final long HEAL_HURT_WINDOW_TICKS = 600L;
    /** Heal: a splash potion or a cloud this close to the ally is what healed them. */
    public static final double HEAL_SCAN_RADIUS = 5.0;
    /** Revive: the ally drops under this share of max health. */
    public static final double REVIVE_THRESHOLD = 0.30;
    /** Revive: you must be this close when they drop. */
    public static final double REVIVE_START_RADIUS = 4.0;
    /** Revive: and stay this close for the whole watch. */
    public static final double REVIVE_STAY_RADIUS = 6.0;
    /** Revive: how long the ally has to live (10 s). */
    public static final long REVIVE_TICKS = 200L;
    /** Revive: one payout per ally per guardian in this long (5 min). */
    public static final long REVIVE_COOLDOWN_TICKS = 6000L;

    /** Intercept: the base reach, before Bodyguard. */
    public static final double INTERCEPT_RADIUS = 6.0;
    /** Intercept: you take this share of the hit, before proc power. */
    public static final double INTERCEPT_SHARE = 0.5;
    /** Shield Wall: the base reach, before Bodyguard. */
    public static final double SHIELD_WALL_RADIUS = 8.0;
    /** Bodyguard: Intercept and Shield Wall reach this much further per rank. */
    public static final double BODYGUARD_PER_RANK = 2.0;
    /** The passive never takes more than this share off a hit. */
    public static final double PASSIVE_CAP = 0.5;

    /** Taunt: a hit mob stays on you this long (5 s). */
    public static final long TAUNT_TICKS = 100L;
    /** Mending Guard: health back per rank for the most hurt ally, per block. */
    public static final float BLOCK_HEAL_PER_RANK = 1.0f;
    /** Mending Guard: at most one heal in this long. */
    public static final long BLOCK_HEAL_COOLDOWN_TICKS = 10L;
    /** Mending Guard, Heartshare and Sworn Shield: the ally must be this close. */
    public static final double ALLY_RADIUS = 8.0;
    /** Heartshare: ranks to share all of the time. */
    public static final int HEARTSHARE_RANKS = 3;
    /** Sworn Shield: once in this long (10 min). */
    public static final long SWORN_COOLDOWN_TICKS = 12000L;
    /** Sworn Shield: you take this share of the killing blow. */
    public static final double SWORN_SHARE = 0.5;
    /** Sentinel: each hit you take for an ally takes this much off Shield Wall's cooldown (2 s). */
    public static final long SENTINEL_COOLDOWN_TICKS = 40L;
    /** Watchful Compass: at most one pulse per friend in this long (3 s). */
    public static final long PULSE_COOLDOWN_TICKS = 60L;

    private GuardianMath() {
    }

    /**
     * How much danger the ally is in, from their health share: 0.25 at full health, 1.125 at half,
     * 2.0 at zero. A broken share reads as full health, never as danger.
     */
    public static double danger(double healthShare) {
        if (!Double.isFinite(healthShare)) {
            return DANGER_FLOOR;
        }
        double share = Math.max(0.0, Math.min(1.0, healthShare));
        return DANGER_FLOOR + (DANGER_CEILING - DANGER_FLOOR) * (1.0 - share);
    }

    /** The share of max health, safe against a zero or broken maximum. */
    public static double share(double health, double maxHealth) {
        if (!(maxHealth > 0) || !Double.isFinite(health)) {
            return 1.0;
        }
        return Math.max(0.0, Math.min(1.0, health / maxHealth));
    }

    /** How much of one hit counts: at most 20, and never past what the mob has left to pay. */
    public static double countedDamage(double damage, double paidOnMob) {
        if (!(damage > 0)) {
            return 0.0;
        }
        double left = MOB_CAP - Math.max(0.0, paidOnMob);
        return Math.max(0.0, Math.min(Math.min(damage, MAX_DAMAGE_PER_HIT), left));
    }

    /** XP for damage counted (cover or block), at a rate and a danger. */
    public static double damageXp(double counted, double rate, double danger) {
        if (!(counted > 0) || !(rate > 0) || !(danger > 0)) {
            return 0.0;
        }
        return counted * rate * danger;
    }

    /** XP for a heal: only the health actually given back counts, never the overheal. */
    public static double healXp(double amount, double health, double maxHealth, double rate) {
        double given = healed(amount, health, maxHealth);
        if (!(given > 0) || !(rate > 0)) {
            return 0.0;
        }
        return given * rate * danger(share(health, maxHealth));
    }

    /** The health a heal really gives back: the missing health, no more. */
    public static double healed(double amount, double health, double maxHealth) {
        if (!(amount > 0) || !(maxHealth > 0)) {
            return 0.0;
        }
        return Math.max(0.0, Math.min(amount, maxHealth - health));
    }

    /** A cover pays for this mob and this ally only if the memory is fresh enough. */
    public static boolean remembered(long since, long now) {
        return since >= 0 && now >= since && now - since <= PROTECT_MEMORY_TICKS;
    }

    /** Your hit on the mob was recent enough that its turn onto you is your doing. */
    public static boolean pulled(long hitAt, long turnAt) {
        return hitAt >= 0 && turnAt >= hitAt && turnAt - hitAt <= PULL_HIT_TICKS;
    }

    /** The mob hurt an ally recently enough for its death to be an avenger kill. */
    public static boolean avenges(long hurtAt, long now) {
        return hurtAt >= 0 && now >= hurtAt && now - hurtAt <= AVENGER_TICKS;
    }

    /** The ally was hurt by the world (not a player) recently enough for a heal to count. */
    public static boolean healCounts(long worldHurtAt, long now) {
        return worldHurtAt >= 0 && now >= worldHurtAt && now - worldHurtAt <= HEAL_HURT_WINDOW_TICKS;
    }

    /** A close call starts when a hit takes the ally from 30% or more to under 30%, still alive. */
    public static boolean closeCall(double before, double after, double maxHealth) {
        if (!(maxHealth > 0) || !(after > 0)) {
            return false;
        }
        double line = maxHealth * REVIVE_THRESHOLD;
        return before >= line && after < line;
    }

    /** The watch has lasted long enough to pay. */
    public static boolean reviveDone(long started, long now) {
        return now - started >= REVIVE_TICKS;
    }

    /** Intercept's reach with Bodyguard. */
    public static double interceptRadius(int bodyguard) {
        return INTERCEPT_RADIUS + BODYGUARD_PER_RANK * Math.max(0, bodyguard);
    }

    /** Shield Wall's reach with Bodyguard. */
    public static double shieldWallRadius(int bodyguard) {
        return SHIELD_WALL_RADIUS + BODYGUARD_PER_RANK * Math.max(0, bodyguard);
    }

    /** The damage you take on an Intercept: half the hit, less with more proc power. */
    public static float interceptDamage(float amount, double power) {
        if (!(amount > 0)) {
            return 0f;
        }
        return (float) (amount * INTERCEPT_SHARE / Math.max(1.0, power));
    }

    /** What is left of a hit on an ally after the strongest Guardian passive near them. */
    public static double passiveMultiplier(double bestBonus) {
        if (!(bestBonus > 0)) {
            return 1.0;
        }
        return 1.0 - Math.min(PASSIVE_CAP, bestBonus);
    }

    /** Mending Guard's heal. */
    public static float blockHeal(int rank) {
        return BLOCK_HEAL_PER_RANK * Math.max(0, rank);
    }

    /** Heartshare: the ally's share of your Absorption's time. */
    public static int shareTicks(int duration, int rank) {
        if (duration <= 0 || rank <= 0) {
            return 0;
        }
        return (int) ((long) duration * Math.min(rank, HEARTSHARE_RANKS) / HEARTSHARE_RANKS);
    }

    /** Sworn Shield: whether it is ready again. */
    public static boolean swornReady(long lastUsed, long now) {
        return lastUsed < 0 || now < lastUsed || now - lastUsed >= SWORN_COOLDOWN_TICKS;
    }

    /**
     * Sworn Shield: whether the guardian lives through taking {@code half}. With less than that
     * left (plus half a heart), the blow is not redirected: it would only trade one death for another.
     */
    public static boolean swornSurvives(float half, float health, float absorption) {
        return half >= 0 && half < health + absorption - 0.5f;
    }

        /** Sworn Shield: the share of the killing blow you take. */
    public static float swornDamage(float amount) {
        return amount > 0 ? (float) (amount * SWORN_SHARE) : 0f;
    }
}
