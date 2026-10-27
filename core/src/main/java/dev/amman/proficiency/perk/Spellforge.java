package dev.amman.proficiency.perk;

import java.util.function.DoubleSupplier;

/**
 * Spellforged (Smithing and Spellcasting): the Whetstone and Temper gifts on freshly crafted gear
 * come more often, and can come at a higher level than I. Each step up from I is its own roll, so
 * III is rarer than II; the step chance grows with Spellcasting, the hand that puts the weave in.
 *
 * <p>Gift chance x1.5 (Whetstone III: 45% to 67.5%, Temper III: 30% to 45%). A step up is 25% at
 * Spellcasting 0 and 50% at 100. A gift never goes past III or past the enchantment's own top.
 */
public final class Spellforge {

    public static final double CHANCE_MULTIPLIER = 1.5;
    public static final double STEP_BASE = 0.25;
    public static final double STEP_PER_LEVEL = 0.0025;
    public static final double STEP_CAP = 0.50;
    public static final int MAX_GIFT_LEVEL = 3;

    private Spellforge() {
    }

    /** A gift's chance with the synergy or without it, never above certain. */
    public static double giftChance(double base, boolean spellforged) {
        double chance = spellforged ? base * CHANCE_MULTIPLIER : base;
        return Math.max(0.0, Math.min(1.0, chance));
    }

    /** Chance of each step up from level I. */
    public static double stepChance(int spellcastingLevel) {
        return Math.min(STEP_CAP, STEP_BASE + Math.max(0, spellcastingLevel) * STEP_PER_LEVEL);
    }

    /**
     * The level a gift lands at: I without the synergy; with it, one more for each step roll in a
     * row that comes in under the step chance, up to III or the enchantment's top level.
     */
    public static int giftLevel(boolean spellforged, int spellcastingLevel, int maxLevel, DoubleSupplier roll) {
        int top = Math.min(MAX_GIFT_LEVEL, Math.max(1, maxLevel));
        if (!spellforged) {
            return 1;
        }
        double step = stepChance(spellcastingLevel);
        int level = 1;
        while (level < top && roll.getAsDouble() < step) {
            level++;
        }
        return level;
    }
}
