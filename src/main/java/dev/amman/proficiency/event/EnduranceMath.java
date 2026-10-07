package dev.amman.proficiency.event;

import java.util.ArrayDeque;
import java.util.Set;

/**
 * What a hit is worth to Endurance, kept free of registries so it can be unit tested. The handler
 * in {@link EnduranceEvents} turns a DamageSource into the three plain facts this reads: was it a
 * living attacker, was it yourself, and what damage type was it.
 *
 * <p>The rule: Endurance pays for health you actually lost and lived through. A fight pays in full,
 * because nobody stands in front of a zombie for the XP for long. The world pays half, and through
 * a rolling budget, because a cactus, a two-block drop and a berry bush will hurt you forever for
 * free and an AFK farm would otherwise be the fastest way to level. Some damage pays nothing at
 * all: the void and /kill are not a hardship you survive, starvation is a choice, and generic
 * damage is what commands and mods deal when they mean "take this", not "you were hurt".
 */
public final class EnduranceMath {

    /** XP per point of health lost to anything alive. A zombie hit on Normal is about 3. */
    public static final double ATTACKER_XP_PER_HP = 1.0;

    /** One hit never pays more than this, whatever it was. Ten hearts is a whole health bar. */
    public static final double MAX_XP_PER_HIT = 20.0;

    /** XP per point of health lost to the world: falls, fire, lava, cactus, drowning, magic. */
    public static final double ENVIRONMENT_XP_PER_HP = 0.5;

    /**
     * The world pays at most this much XP per player in any {@link #ENVIRONMENT_WINDOW_TICKS}.
     * Ten XP a minute is twenty health lost to the world every minute, which is more than any real
     * adventure costs and far less than a cactus loop wants: 600 XP an hour at most, where the same
     * hour of fighting pays several times that and the curve asks 14,000 for level 100.
     */
    public static final double ENVIRONMENT_XP_CAP = 10.0;

    /** Sixty seconds of game time. */
    public static final long ENVIRONMENT_WINDOW_TICKS = 1200L;

    /**
     * Damage types that pay nothing, by registry path. The void, /kill and the world border are
     * not hits you survive; starvation is a choice; {@code generic} is what commands and other
     * mods deal when they mean "take this", not "something hurt you".
     */
    public static final Set<String> NEVER_PAYS =
            Set.of("fell_out_of_world", "generic_kill", "outside_border", "starve", "generic");

    public enum Kind {
        /** A mob or a player hit you. Full pay. */
        ATTACKER,
        /** The world hurt you. Half pay, through the rolling budget. */
        ENVIRONMENT,
        /** Pays nothing. */
        NONE
    }

    private EnduranceMath() {
    }

    /**
     * @param livingAttacker the damage has a living source entity that is not you
     * @param selfInflicted  the source entity is you: your own arrow, your own TNT, your firework
     * @param damageType     the damage type's registry path, e.g. {@code fall}; null if unknown
     */
    public static Kind classify(boolean livingAttacker, boolean selfInflicted, String damageType) {
        if (selfInflicted || (damageType != null && NEVER_PAYS.contains(damageType))) {
            return Kind.NONE;
        }
        return livingAttacker ? Kind.ATTACKER : Kind.ENVIRONMENT;
    }

    /**
     * The XP a hit is worth before the environmental budget: health actually lost times the rate
     * for its kind, capped per hit. Zero for a hit you did not survive, for a creative or spectator
     * player (the caller passes {@code counts = false}), and for anything that is not a finite
     * positive number.
     */
    public static double xpFor(Kind kind, double healthLost, boolean survived, boolean counts) {
        if (!counts || !survived || kind == Kind.NONE || !Double.isFinite(healthLost) || healthLost <= 0) {
            return 0.0;
        }
        double rate = kind == Kind.ATTACKER ? ATTACKER_XP_PER_HP : ENVIRONMENT_XP_PER_HP;
        return Math.min(MAX_XP_PER_HIT, healthLost * rate);
    }

    // ---- The tree's arithmetic ---------------------------------------------------------------

    /** Scar Tissue: scars counted, and how long after the last hit they all fade. */
    public static final int MAX_SCARS = 5;
    public static final long SCAR_FADE_TICKS = 100L;
    public static final double SCAR_PER_RANK = 0.02;

    /** Indomitable: one heart at a time, four at most. */
    public static final float INDOMITABLE_HEART = 2.0f;
    public static final float INDOMITABLE_CAP = 8.0f;

    /** Scar Tissue: 2% per rank per scar off the next hit. Five scars at rank 3 is 30%. */
    public static double scarMultiplier(int scars, int rank) {
        int counted = Math.max(0, Math.min(MAX_SCARS, scars));
        return Math.max(0.0, 1.0 - SCAR_PER_RANK * Math.max(0, rank) * counted);
    }

    /** Scars standing at {@code now}: the old count if the last hit was recent, none otherwise. */
    public static int scarsAt(int scars, long lastHit, long now) {
        return now - lastHit <= SCAR_FADE_TICKS ? Math.max(0, Math.min(MAX_SCARS, scars)) : 0;
    }

    /** Lean Times: the share of new exhaustion handed back, 15% per rank, never all of it. */
    public static double leanRefund(int rank) {
        return Math.min(0.9, Math.max(0, rank) * 0.15);
    }

    /** Indomitable: one more heart, never past four, never taking away what is already there. */
    public static float indomitableAbsorption(float current) {
        if (current >= INDOMITABLE_CAP) {
            return current;
        }
        return Math.min(INDOMITABLE_CAP, Math.max(0f, current) + INDOMITABLE_HEART);
    }

    /**
     * One player's rolling budget for environmental XP. It remembers what it paid and when, and
     * forgets anything older than the window, so the cap is "this much in any sixty seconds" rather
     * than a bucket that refills all at once on the minute and could be timed.
     */
    public static final class Budget {

        private record Paid(long tick, double xp) {
        }

        private final ArrayDeque<Paid> paid = new ArrayDeque<>();
        private final double cap;
        private final long window;

        public Budget() {
            this(ENVIRONMENT_XP_CAP, ENVIRONMENT_WINDOW_TICKS);
        }

        public Budget(double cap, long window) {
            this.cap = cap;
            this.window = window;
        }

        /** How much of {@code wanted} the budget allows at {@code now}, and books it as paid. */
        public double take(long now, double wanted) {
            if (!Double.isFinite(wanted) || wanted <= 0) {
                return 0.0;
            }
            while (!paid.isEmpty() && now - paid.peekFirst().tick() >= window) {
                paid.pollFirst();
            }
            double spent = 0.0;
            for (Paid entry : paid) {
                spent += entry.xp();
            }
            double granted = Math.min(wanted, Math.max(0.0, cap - spent));
            if (granted > 0) {
                paid.addLast(new Paid(now, granted));
            }
            return granted;
        }
    }
}
