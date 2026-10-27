package dev.amman.proficiency.xp;

/**
 * Spellcasting XP from the mana a real cast costs (user, 2026-10-14: "connected to amount of mana consumed").
 * The cast rule's xp is the pay for {@link #REFERENCE_MANA} mana; a cheaper spell pays less, a bigger one
 * more, up to {@link #MAX_MULTIPLIER} times the rule. The spell's base cost is used (before the caster's
 * discounts), so gear that makes spells cheaper does not make them pay less.
 */
public final class ManaXp {

    /** Mana that earns exactly the cast rule's xp (a typical small Ars Nouveau spell). */
    public static final double REFERENCE_MANA = 20.0;
    /** The biggest spell pays at most this many times the rule (240 mana at the reference). */
    public static final double MAX_MULTIPLIER = 12.0;
    /** A cast that cost something never pays less than this. */
    public static final double MIN_XP = 0.05;

    private ManaXp() {
    }

    /** XP for one cast that cost {@code manaCost}; 0 for a free cast (nothing was consumed). */
    public static double forCost(double ruleXp, int manaCost) {
        if (manaCost <= 0 || ruleXp <= 0) {
            return 0.0;
        }
        double xp = ruleXp * Math.min(MAX_MULTIPLIER, manaCost / REFERENCE_MANA);
        return Math.max(MIN_XP, xp);
    }
}
