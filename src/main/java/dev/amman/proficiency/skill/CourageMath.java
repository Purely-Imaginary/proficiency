package dev.amman.proficiency.skill;

/**
 * The numbers behind Courage. Pure: no Minecraft types, so every rule here is unit tested.
 * {@code CourageEvents} looks at the fight and pays; this class only decides.
 *
 * <p>Courage pays for offense under pressure: damage you deal and kills you make, times how bad
 * the odds were at that moment. The odds come from four facts read at the hit:
 * <ul>
 * <li>how many mobs are after you within 12 blocks (outnumbered),</li>
 * <li>how much stronger the foe is than you (max health or attack; a boss is big),</li>
 * <li>how low your own health is (only on top of a crowd or a stronger foe),</li>
 * <li>and your armour, which only scales the other three (less armour, braver).</li>
 * </ul>
 * At even or favourable odds the pressure is zero and nothing is paid. Endurance pays for damage
 * taken; Courage never does.
 */
public final class CourageMath {

    /** Mobs further than this are not in your fight. */
    public static final double CROWD_RADIUS = 12.0;
    /** Pressure from each foe after you beyond the first. */
    public static final double PER_EXTRA_FOE = 0.5;
    /** Crowd pressure never goes past this (five foes). */
    public static final double CROWD_CAP = 2.0;
    /** Pressure per unit of "how many times stronger" past even. */
    public static final double PER_STRENGTH = 0.5;
    /** Strength pressure never goes past this. */
    public static final double STRENGTH_CAP = 1.5;
    /** A boss adds this on top, whatever its numbers. */
    public static final double BOSS_PRESSURE = 2.0;
    /** Your health counts once it is under this share of your maximum. */
    public static final double LOW_HEALTH = 0.5;
    /** Pressure at zero health; it grows in a straight line from {@link #LOW_HEALTH}. */
    public static final double LOW_HEALTH_CAP = 1.0;
    /** Armour past this is "fully armoured" and adds no bravery. Full diamond is 20. */
    public static final double FULL_ARMOUR = 20.0;
    /** With no armour at all the pressure counts this much more. */
    public static final double BARE_BONUS = 0.5;
    /** The odds factor never goes past this, whatever stacks up. */
    public static final double ODDS_CAP = 4.0;

    /** Base XP per point of damage dealt, before the odds, when the config is not loaded. */
    public static final double DEFAULT_XP_PER_DAMAGE = 0.2;
    /** Base XP for a kill, before the odds, when the config is not loaded. */
    public static final double DEFAULT_KILL_XP = 2.0;
    /** One hit never counts more damage than this. */
    public static final double MAX_DAMAGE_PER_HIT = 20.0;
    /** You are "in the fight" for this long after a mob last hurt you (20 s). */
    public static final long DEFAULT_FIGHT_WINDOW_TICKS = 400L;

    /** The passive reaches its full bonus at this many extra foes. */
    public static final int PASSIVE_FOES = 4;
    /** With Lionheart the same full bonus comes at 2 extra foes (3 after you). Never more bonus. */
    public static final int LIONHEART_FOES = 2;
    /** Lionheart: an outnumbered kill takes this much off Stand Your Ground's cooldown (5 s). */
    public static final long LIONHEART_COOLDOWN_TICKS = 100L;

    /** Stand Your Ground: extra damage per hostile mob within {@link #STAND_RADIUS}. */
    public static final double STAND_PER_FOE = 0.05;
    public static final int STAND_MAX_FOES = 6;
    public static final double STAND_RADIUS = 8.0;

    /** Rally: Strength I for this long, times proc power. */
    public static final int RALLY_STRENGTH_TICKS = 200;
    /** Rally: the short Speed burst. */
    public static final int RALLY_SPEED_TICKS = 60;
    /** Rallying Cry: players this close share a Rally. */
    public static final double RALLY_CRY_RADIUS = 8.0;

    /** Hold the Line: less damage per rank while this many foes are after you. */
    public static final int HOLD_FOES = 3;
    public static final double HOLD_PER_RANK = 0.05;
    /** Giant Slayer: more damage per rank to bosses and elites. */
    public static final double GIANT_PER_RANK = 0.08;
    /** An elite: this much max health or more. */
    public static final double ELITE_HEALTH = 40.0;
    /** Spoils of Valor: health back per rank on a kill against a stronger foe. */
    public static final float VALOR_HEAL_PER_RANK = 2.0f;
    /** Unshaken: a mob's Slowness or Weakness is a third shorter per rank; gone at rank 3. */
    public static final int UNSHAKEN_RANKS = 3;
    /** Challenge: pulls a mob off a friend this many blocks from you, per rank. */
    public static final double CHALLENGE_PER_RANK = 4.0;
    /** Fearless Heart: a Grit gives Strength I for this long. */
    public static final int FEARLESS_HEART_TICKS = 100;

    private CourageMath() {
    }

    /**
     * One moment of a fight, as the handler reads it.
     *
     * @param foes          mobs after you within 12 blocks that attacked you in the fight window,
     *                      the target included if it is after you
     * @param foeMaxHealth  the target's maximum health
     * @param foeAttack     the target's attack damage (0 if it has none)
     * @param boss          the target is a boss
     * @param myMaxHealth   your maximum health
     * @param myAttack      your attack damage, weapon included
     * @param myHealth      your health now
     * @param armour        your armour value
     */
    public record Fight(int foes, double foeMaxHealth, double foeAttack, boolean boss,
            double myMaxHealth, double myAttack, double myHealth, double armour) {
    }

    /** Crowd pressure: half a point for each foe after you beyond the first, up to five foes. */
    public static double crowd(int foes) {
        return Math.min(CROWD_CAP, PER_EXTRA_FOE * Math.max(0, foes - 1));
    }

    /**
     * How many times stronger the foe is: the larger of max health and attack against yours. Below
     * 1 means weaker. A zero or broken "yours" reads as even, never as infinitely stronger.
     */
    public static double strengthRatio(double foeMaxHealth, double foeAttack, double myMaxHealth,
            double myAttack) {
        double health = ratio(foeMaxHealth, myMaxHealth);
        double attack = ratio(foeAttack, myAttack);
        return Math.max(health, attack);
    }

    private static double ratio(double theirs, double mine) {
        if (!(theirs > 0) || !(mine > 0) || !Double.isFinite(theirs) || !Double.isFinite(mine)) {
            return 0.0;
        }
        return theirs / mine;
    }

    /** Strength pressure: half a point per "times stronger" past even, capped. A boss adds 2. */
    public static double strength(double ratio, boolean boss) {
        double value = ratio > 1.0 ? Math.min(STRENGTH_CAP, PER_STRENGTH * (ratio - 1.0)) : 0.0;
        return value + (boss ? BOSS_PRESSURE : 0.0);
    }

    /** Low-health pressure: 0 at half health or more, 1 at zero. */
    public static double lowHealth(double health, double maxHealth) {
        if (!(maxHealth > 0)) {
            return 0.0;
        }
        double fraction = Math.max(0.0, Math.min(1.0, health / maxHealth));
        if (fraction >= LOW_HEALTH) {
            return 0.0;
        }
        return LOW_HEALTH_CAP * (LOW_HEALTH - fraction) / LOW_HEALTH;
    }

    /** The armour scale on the pressure: 1.0 in full armour, 1.5 bare. */
    public static double armourScale(double armour) {
        double worn = Math.max(0.0, Math.min(FULL_ARMOUR, armour));
        return 1.0 + BARE_BONUS * (1.0 - worn / FULL_ARMOUR);
    }

    /**
     * The pressure alone, before armour. Zero means even or favourable odds. Low health only adds
     * to a fight that is already uneven (a crowd or a stronger foe): a player who let a weak mob
     * chip them down must not farm Courage on single weak mobs.
     */
    public static double pressure(Fight f) {
        double ratio = strengthRatio(f.foeMaxHealth(), f.foeAttack(), f.myMaxHealth(), f.myAttack());
        double uneven = crowd(f.foes()) + strength(ratio, f.boss());
        if (!(uneven > 0)) {
            return 0.0;
        }
        return uneven + lowHealth(f.myHealth(), f.myMaxHealth());
    }

    /**
     * The odds factor XP is multiplied by. 0 at even or favourable odds: armour on its own never
     * makes a fight brave, it only scales a fight that already is.
     */
    public static double odds(Fight f) {
        double pressure = pressure(f);
        if (!(pressure > 0) || !Double.isFinite(pressure)) {
            return 0.0;
        }
        return Math.min(ODDS_CAP, pressure * armourScale(f.armour()));
    }

    /**
     * Base XP for a hit: the damage that counts times the rate times the odds. The damage that
     * counts is capped per hit and by what this foe still has left to pay ({@code paidBudget}: its
     * max health minus what it already paid for), so a trapped mob that heals is worth one health
     * bar, not a farm.
     */
    public static double hitXp(double damage, double paidBudget, double odds, double xpPerDamage) {
        double counted = countedDamage(damage, paidBudget);
        double value = counted * xpPerDamage * odds;
        return value > 0 && Double.isFinite(value) ? value : 0.0;
    }

    /** The damage a hit may still count, from 0 to {@link #MAX_DAMAGE_PER_HIT}. */
    public static double countedDamage(double damage, double paidBudget) {
        if (!(damage > 0) || !(paidBudget > 0) || !Double.isFinite(damage)) {
            return 0.0;
        }
        return Math.min(MAX_DAMAGE_PER_HIT, Math.min(damage, paidBudget));
    }

    /** Base XP for a kill: the kill rate times the odds. */
    public static double killXp(double odds, double killXp) {
        double value = killXp * odds;
        return value > 0 && Double.isFinite(value) ? value : 0.0;
    }

    /** Whether the player is still in the fight: a mob hurt them within the window. */
    public static boolean inFight(long lastHurtByMob, long now, long window) {
        return lastHurtByMob >= 0 && now - lastHurtByMob >= 0 && now - lastHurtByMob <= window;
    }

    /** Outnumbered, for Rally and Lionheart: two or more foes after you, the target included. */
    public static boolean outnumbered(int foes) {
        return foes >= 2;
    }

    /**
     * The passive's damage multiplier: {@code passive} (0.40 at level 100, more with Hot Blood and
     * Lionheart) grows with each extra foe and never goes past {@code passive}. The full bonus
     * needs five foes after you, or three with Lionheart.
     */
    public static double passiveMultiplier(double passive, int foes, boolean lionheart) {
        if (!(passive > 0) || !Double.isFinite(passive)) {
            return 1.0;
        }
        int full = lionheart ? LIONHEART_FOES : PASSIVE_FOES;
        int extra = Math.max(0, Math.min(full, foes - 1));
        return 1.0 + passive * extra / full;
    }

    /** Stand Your Ground: +5% per hostile mob within 8 blocks, up to +30%. */
    public static double standMultiplier(int nearby) {
        return 1.0 + STAND_PER_FOE * Math.max(0, Math.min(STAND_MAX_FOES, nearby));
    }

    /** Hold the Line: the damage multiplier while three or more foes are after you. */
    public static double holdTheLine(int rank, int foes) {
        if (rank <= 0 || foes < HOLD_FOES) {
            return 1.0;
        }
        return Math.max(0.5, 1.0 - HOLD_PER_RANK * rank);
    }

    /** Giant Slayer: the damage multiplier against a boss or an elite. */
    public static double giantSlayer(int rank, boolean boss, double maxHealth) {
        if (rank <= 0 || !(boss || maxHealth >= ELITE_HEALTH)) {
            return 1.0;
        }
        return 1.0 + GIANT_PER_RANK * rank;
    }

    /** Spoils of Valor: health back on a kill, only against a stronger foe or a boss. */
    public static float valorHeal(int rank, double strengthRatio, boolean boss) {
        if (rank <= 0 || !(boss || strengthRatio > 1.0)) {
            return 0f;
        }
        return VALOR_HEAL_PER_RANK * rank;
    }

    /** Unshaken: a mob's Slowness or Weakness, its new length. 0 means it never lands. */
    public static int unshakenTicks(int duration, int rank) {
        if (rank <= 0 || duration <= 0) {
            return duration;
        }
        if (rank >= UNSHAKEN_RANKS) {
            return 0;
        }
        return (int) Math.round(duration * (1.0 - (double) rank / UNSHAKEN_RANKS));
    }

    /** Challenge: how far from you a friend can be for you to pull their mob. */
    public static double challengeRadius(int rank) {
        return CHALLENGE_PER_RANK * Math.max(0, rank);
    }

    /** Rally's Strength length for a given proc power. */
    public static int rallyTicks(double power) {
        return (int) Math.round(RALLY_STRENGTH_TICKS * Math.max(1.0, power));
    }
}
