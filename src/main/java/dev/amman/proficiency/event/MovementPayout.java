package dev.amman.proficiency.event;

/**
 * Turns metres into small, frequent XP grants for one movement skill.
 *
 * <p>Movement earns a trickle: a sprint is about 0.28 XP a second. Paying whole points only meant
 * one grant every 3 to 4 seconds, which is longer than the tempo window, so the tempo chain broke
 * between grants and never built. Now a grant is paid as soon as {@link #STEP} XP has piled up,
 * which is about one a second on a steady sprint. The XP per metre is unchanged: the whole pile,
 * fraction included, is paid, so nothing is lost or invented.
 *
 * <p>The tempo bonus on a steady sprint (up to +50%) is the intended reward for keeping moving.
 *
 * <p>Proc rolls stay at one per whole point of XP earned, as before, so smaller grants do not
 * make procs more frequent. Pure Java, so the maths is unit tested.
 */
final class MovementPayout {

    /** Smallest grant. At 0.05 XP per metre a sprint pays every ~0.9 s, a sneak every ~1.9 s. */
    static final double STEP = 0.25;

    private double carry;
    private double procCarry;

    /** Adds earned XP. Returns what to grant now, or 0 if the pile is still under {@link #STEP}. */
    double add(double xp) {
        if (!(xp > 0) || !Double.isFinite(xp)) {
            return 0;
        }
        carry += xp;
        if (carry < STEP) {
            return 0;
        }
        double pay = carry;
        carry = 0;
        return pay;
    }

    /** How many proc rolls a grant of {@code paid} XP earns: one per whole point, remainder kept. */
    int procRolls(double paid) {
        procCarry += paid;
        int rolls = (int) Math.floor(procCarry);
        procCarry -= rolls;
        return rolls;
    }

    /** XP waiting to be paid. */
    double pending() {
        return carry;
    }
}
