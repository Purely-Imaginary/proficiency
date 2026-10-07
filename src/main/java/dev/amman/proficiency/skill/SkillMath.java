package dev.amman.proficiency.skill;

import dev.amman.proficiency.config.ProficiencyConfig;

public final class SkillMath {

    public static final int MAX_LEVEL = 100;

    private SkillMath() {
    }

    /**
     * XP needed to go from {@code level} to {@code level + 1}: {@code floor + base * (L+1)^exp}.
     * With the defaults (8, 2, 1.35) the first level costs 10 XP, level 10 about 53 and the hundredth
     * about 1,010, which puts a maxed skill near 44,000 XP.
     */
    public static float xpToNext(int level) {
        if (level >= MAX_LEVEL) {
            return Float.MAX_VALUE;
        }
        double cost = ProficiencyConfig.curveFloor() + ProficiencyConfig.curveBase()
                * Math.pow(level + 1, ProficiencyConfig.curveExponent());
        return (float) Math.max(1.0, cost);
    }

    /** Fraction of the way from {@code level} to {@code level + 1}, in 0..1. */
    public static float progress(int level, float xp) {
        if (level >= MAX_LEVEL) {
            return 1.0f;
        }
        float need = xpToNext(level);
        return need <= 0 ? 0f : Math.min(1.0f, xp / need);
    }

    /** The passive effect of a skill at its current level, as a fraction. Linear, like Valheim. */
    public static double bonus(Skill skill, int level) {
        if (!ProficiencyConfig.enabled(skill)) {
            return 0.0;
        }
        return ProficiencyConfig.maxBonus(skill) * (level / (double) MAX_LEVEL);
    }

    /**
     * How often a skill's signature proc fires at this level. Nothing at all below the unlock
     * level, then a straight climb from the floor to the skill's own maximum at 100. The cliff at
     * the unlock level is deliberate: crossing 25 in a skill should feel like something happened,
     * not like a number went up by one again.
     */
    public static double procChance(Skill skill, int level) {
        if (!ProficiencyConfig.procsEnabled() || !ProficiencyConfig.enabled(skill)) {
            return 0.0;
        }
        int unlock = ProficiencyConfig.procUnlockLevel();
        if (level < unlock) {
            return 0.0;
        }
        double floor = ProficiencyConfig.procFloor();
        double ceiling = ProficiencyConfig.procChance(skill);
        if (level >= MAX_LEVEL || unlock >= MAX_LEVEL) {
            return ceiling;
        }
        double climb = (level - unlock) / (double) (MAX_LEVEL - unlock);
        return floor + (ceiling - floor) * climb;
    }

    /** Total XP from zero to {@code level}, for /proficiency set and the panel's tooltip. */
    public static float totalXpFor(int level) {
        float total = 0;
        for (int i = 0; i < Math.min(level, MAX_LEVEL); i++) {
            total += xpToNext(i);
        }
        return total;
    }
}
