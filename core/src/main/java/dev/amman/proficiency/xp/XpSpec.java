package dev.amman.proficiency.xp;

/**
 * An XP amount: {@code clamp(base + perHardness * hardness + perHealth * maxHealth, min, max)}.
 * A flat number is just a base. A block with negative hardness (bedrock) pays nothing when the
 * spec depends on hardness.
 */
public record XpSpec(double base, double perHardness, double perHealth, double min, double max) {

    public static XpSpec flat(double amount) {
        return new XpSpec(amount, 0, 0, 0, Double.POSITIVE_INFINITY);
    }

    public double eval(double hardness, double maxHealth) {
        if (perHardness != 0 && hardness < 0) {
            return 0;
        }
        double value = base + perHardness * Math.max(0, hardness) + perHealth * Math.max(0, maxHealth);
        double upper = Math.max(max, min);
        return Math.max(min, Math.min(upper, value));
    }

    public double eval() {
        return eval(0, 0);
    }
}
