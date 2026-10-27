package dev.amman.proficiency.skill;

/**
 * Trial Chambers' pure numbers (1.21 only): the factor a trial mob's Courage and Charger XP is
 * multiplied by, what a cleared wave pays to the combat skill used most in it, and what a vault
 * pays Wayfaring once. No Minecraft here, so it is unit tested; the game side is
 * {@code TrialEvents} and the trial spawner and vault mixins.
 */
public final class TrialRules {

    public static final double DEFAULT_MOB_FACTOR = 1.5;
    public static final double DEFAULT_OMINOUS_MOB_FACTOR = 2.0;
    public static final double DEFAULT_WAVE_XP = 15.0;
    public static final double DEFAULT_VAULT_XP = 30.0;
    /** An ominous wave or vault pays this much more. */
    public static final double OMINOUS_MULTIPLIER = 2.0;
    /** Kills of the wave's mobs by one player that earn the whole wave bonus; fewer pay a share. */
    public static final int FULL_SHARE_KILLS = 6;

    private TrialRules() {
    }

    /** The XP factor of a trial mob for Courage and Charger; 1 for any other mob. */
    public static double mobFactor(boolean trialMob, boolean ominous, double normal, double ominousFactor) {
        if (!trialMob) {
            return 1.0;
        }
        double factor = ominous ? ominousFactor : normal;
        return Double.isFinite(factor) && factor > 1.0 ? factor : 1.0;
    }

    /** What a player's cleared wave pays: the base, in proportion to the kills they made. */
    public static double waveXp(double base, int kills, boolean ominous) {
        if (!(base > 0) || kills <= 0) {
            return 0.0;
        }
        double share = Math.min(1.0, kills / (double) FULL_SHARE_KILLS);
        return base * share * (ominous ? OMINOUS_MULTIPLIER : 1.0);
    }

    /** What opening a vault the first time pays. */
    public static double vaultXp(double base, boolean ominous) {
        if (!(base > 0)) {
            return 0.0;
        }
        return base * (ominous ? OMINOUS_MULTIPLIER : 1.0);
    }

    /**
     * The index of the largest count (the skill used most), or -1 when all are zero. A tie goes to the
     * lowest index, so the answer never depends on iteration order.
     */
    public static int mostUsed(int[] counts) {
        int best = -1;
        int most = 0;
        for (int i = 0; i < counts.length; i++) {
            if (counts[i] > most) {
                most = counts[i];
                best = i;
            }
        }
        return best;
    }
}
