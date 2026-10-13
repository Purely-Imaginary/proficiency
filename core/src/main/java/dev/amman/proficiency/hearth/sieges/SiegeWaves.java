package dev.amman.proficiency.hearth.sieges;

import dev.amman.proficiency.hearth.HearthData;
import dev.amman.proficiency.hearth.SiegeRules;

import java.util.ArrayList;
import java.util.List;
import java.util.random.RandomGenerator;

/**
 * What one wave is made of and where it comes from (docs/HEARTH-SPEC.md, "The siege itself").
 *
 * <ul>
 * <li><b>Mix</b>: the ceiling's mobs ({@link SiegeRules#mobsFor}), picked evenly, except the ids in
 * {@code onePerWave} (the evoker of "VI + an evoker", the ravager of level V), each of which a wave
 * fields once, as long as at least one common mob still fits.</li>
 * <li><b>Gates</b>: a wave arrives from one side (two above 6 mobs, three above 12), so a group sees
 * where it comes from, each mob within {@link #GATE_SPREAD} radians of its gate.</li>
 * </ul>
 * Pure.
 */
public final class SiegeWaves {

    /** Half the width of a gate, in radians (about 34 degrees each way). */
    public static final double GATE_SPREAD = 0.6;

    /** The eight compass names, north first, clockwise (lang suffixes). */
    public static final String[] DIRECTIONS = {"n", "ne", "e", "se", "s", "sw", "w", "nw"};

    private SiegeWaves() {
    }

    /** The entity ids of one wave of {@code size} at {@code ceiling}. Empty when the mix is empty. */
    public static List<String> compose(int size, int ceiling, HearthData.Siege s, RandomGenerator random) {
        List<String> mix = SiegeRules.mobsFor(ceiling, s);
        List<String> out = new ArrayList<>();
        if (size <= 0 || mix.isEmpty()) {
            return out;
        }
        List<String> once = new ArrayList<>();
        List<String> common = new ArrayList<>();
        for (String id : mix) {
            (s.onePerWave().contains(id) ? once : common).add(id);
        }
        if (common.isEmpty()) {
            // Only leaders listed: they are all there is.
            for (int i = 0; i < size; i++) {
                out.add(once.get(random.nextInt(once.size())));
            }
            return out;
        }
        for (String leader : once) {
            if (out.size() + 1 < size) {
                out.add(leader);
            }
        }
        while (out.size() < size) {
            out.add(common.get(random.nextInt(common.size())));
        }
        return out;
    }

    /** How many sides a wave of {@code size} comes from. */
    public static int gates(int size) {
        return size > 12 ? 3 : size > 6 ? 2 : 1;
    }

    /** The gate angles of a wave, in radians, spread evenly around a random start. */
    public static double[] gateAngles(int size, RandomGenerator random) {
        int n = gates(size);
        double start = random.nextDouble() * Math.PI * 2;
        double[] out = new double[n];
        for (int i = 0; i < n; i++) {
            out[i] = start + i * (Math.PI * 2 / n);
        }
        return out;
    }

    /** An angle near a gate, at most {@link #GATE_SPREAD} away. */
    public static double near(double gate, RandomGenerator random) {
        return gate + (random.nextDouble() * 2 - 1) * GATE_SPREAD;
    }

    /**
     * The compass point an offset from the Hearth lies at, as an index into {@link #DIRECTIONS}
     * (Minecraft: north is -Z, east is +X).
     */
    public static int direction(double dx, double dz) {
        if (dx == 0 && dz == 0) {
            return 0;
        }
        double angle = Math.atan2(dx, -dz);
        return Math.floorMod((int) Math.round(angle / (Math.PI / 4)), 8);
    }

    /** The offset of an angle at {@code radius}: x = sin, z = -cos (0 is north, turning east). */
    public static double dx(double angle, double radius) {
        return Math.sin(angle) * radius;
    }

    public static double dz(double angle, double radius) {
        return -Math.cos(angle) * radius;
    }
}
