package dev.amman.proficiency.perk;

/**
 * What a talent rank actually changes. Every node in every tree is some mix of these numbers, which
 * keeps three hundred and fifty nodes from turning into three hundred and fifty special cases. The
 * handful that genuinely need bespoke behaviour carry a {@link Talent#special()} tag instead and are
 * read directly where they matter.
 *
 * <p>Within one tree, ranks of the same effect add: two nodes at +5% per rank, five ranks each, make
 * +50%, not +61%. Synergies then multiply on top of that sum, because a synergy is a different kind
 * of thing from a rank and should read as its own step up.
 */
public enum PerkEffect {
    /** Multiplies XP earned in the skill. */
    XP_RATE,
    /** Multiplies the linear passive bonus. */
    BONUS,
    /** Multiplies how often the signature proc fires. */
    PROC_CHANCE,
    /** Multiplies how hard the proc hits when it does. */
    PROC_POWER,
    /** Multiplies how long the active ability lasts. */
    ABILITY_DURATION,
    /** Multiplies the active ability's cooldown. Its ranks are negative; it never falls below a quarter. */
    ABILITY_COOLDOWN,
    /**
     * Fraction of this skill's death penalty that is forgiven. Summed and clamped to 0..1 rather than
     * used as a multiplier, because "100% warded" has to mean exactly nothing is lost.
     */
    DEATH_WARD,
    /** Multiplies the tempo streak's bonus, not the base: a bigger reward for the same rhythm. */
    TEMPO;

    public String translationKey() {
        return "proficiency.effect." + name().toLowerCase(java.util.Locale.ROOT);
    }
}
