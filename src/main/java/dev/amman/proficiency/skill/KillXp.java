package dev.amman.proficiency.skill;

/**
 * The kill bonus on its own: no game objects, so it is unit tested. A kill pays
 * {@code clamp(base + maxHealth / divisor, base, cap)}, times the boss multiplier for a notable
 * boss. The cap is applied before the boss multiplier, so a boss tops out at cap x multiplier.
 */
public final class KillXp {

    /** The XP-log source of a kill is this prefix plus the victim's entity description id. */
    public static final String KILL_PREFIX = "kill|";

    private KillXp() {
    }

    public static double bonus(double maxHealth, boolean boss, double base, double divisor,
            double cap, double bossMultiplier) {
        if (!Double.isFinite(maxHealth) || maxHealth < 0) {
            maxHealth = 0;
        }
        double safeBase = Math.max(0.0, base);
        double raw = safeBase + (divisor > 0 ? maxHealth / divisor : 0.0);
        double capped = Math.max(safeBase, Math.min(Math.max(cap, safeBase), raw));
        return boss ? capped * Math.max(0.0, bossMultiplier) : capped;
    }
}
