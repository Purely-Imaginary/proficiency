package dev.amman.proficiency.hearth.effects;

/**
 * Workshop's station speed (docs/HEARTH-SPEC.md, the boons table): furnaces, smokers, blast
 * furnaces and brewing stands in range run {@code speed} faster (0.40 = 40% faster) while a player
 * is in range.
 *
 * <p>The station keeps its own vanilla tick; each tick that really cooked (or brewed) one step adds
 * {@code speed} to a carry, and whole steps of the carry are taken as extra progress. The finishing
 * step is always vanilla's own (vanilla finishes on an exact match: a furnace on
 * {@code progress == total}, a brewing stand on {@code brewTime == 0}), so extra progress stops one
 * short of it. The carry never holds more than one step, so a blocked station does not bank speed.
 * A furnace burns its fuel as much faster as it cooks, so fuel per item stays vanilla's.
 */
public final class StationMath {

    private StationMath() {
    }

    /** The carry after this tick's share of speed. */
    public static float carryAfter(float carry, double speed) {
        if (!(speed > 0)) {
            return 0f;
        }
        float c = Float.isNaN(carry) ? 0f : Math.max(0f, carry);
        return (float) (c + speed);
    }

    /** Extra whole steps to take now: the carry's whole part, never more than {@code room}. */
    public static int extra(float carry, int room) {
        if (room <= 0 || !(carry >= 1f)) {
            return 0;
        }
        return Math.min(room, (int) Math.floor(carry));
    }

    /** What is left of the carry once {@code taken} steps were used; at most one step. */
    public static float carryLeft(float carry, int taken) {
        float left = carry - Math.max(0, taken);
        return Math.max(0f, Math.min(1f, left));
    }

    /**
     * Room for extra furnace progress this tick: up to one short of the total (the craft stays on
     * vanilla's tick) and no more than the fuel left beyond its last tick.
     */
    public static int furnaceRoom(int progress, int total, int litTime) {
        return Math.max(0, Math.min(total - 1 - progress, litTime - 1));
    }

    /** Room for extra brewing: the brew time may come down to 1, never to 0 (vanilla's last tick brews). */
    public static int brewRoom(int brewTime) {
        return Math.max(0, brewTime - 1);
    }
}
