package dev.amman.proficiency.hearth;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

/**
 * What a trip away from the Hearth is remembered by, for the homecoming line (docs/HEARTH-SPEC.md,
 * "Setting out and coming home": "You told of 3 200 blocks walked and a stronghold"). A place is a
 * discovery the mod already pays Wayfaring for; its grant source is the language key of the place
 * ({@code structure.minecraft.stronghold}, {@code biome.minecraft.plains},
 * {@code dimension.minecraft.the_nether}).
 */
public final class TaleTrip {

    /** Most places a trip keeps; the line names fewer. */
    public static final int MAX_PLACES = 8;
    /** Most places the homecoming line names. */
    public static final int NAMED = 3;

    public enum Kind { STRUCTURE, DIMENSION, BIOME }

    private TaleTrip() {
    }

    /** The kind of place a grant source names, or null when it names none. */
    public static Kind kindOf(String source) {
        if (source == null) {
            return null;
        }
        if (source.startsWith("structure.")) {
            return Kind.STRUCTURE;
        }
        if (source.startsWith("dimension.")) {
            return Kind.DIMENSION;
        }
        if (source.startsWith("biome.")) {
            return Kind.BIOME;
        }
        return null;
    }

    /** What to do with a place found on a trip: add it, and which already kept place (or none) makes room. */
    public record Admission(boolean add, String drop) {
        static final Admission NO = new Admission(false, null);
    }

    /**
     * Whether a newly found place is kept. A trip keeps {@link #MAX_PLACES}; past that a better kind
     * takes the place of the weakest one kept (the latest found of the weakest kind), so a stronghold or
     * the Nether found after eight biomes still makes the line. The same or a lesser kind is dropped.
     */
    public static Admission admit(Collection<String> known, String place) {
        Kind kind = kindOf(place);
        if (kind == null || known.contains(place)) {
            return Admission.NO;
        }
        if (known.size() < MAX_PLACES) {
            return new Admission(true, null);
        }
        String weakest = null;
        int weakestRank = -1;
        for (String kept : known) {
            int rank = rank(kept);
            if (rank >= weakestRank) {
                weakestRank = rank;
                weakest = kept;
            }
        }
        return weakest != null && kind.ordinal() < weakestRank ? new Admission(true, weakest) : Admission.NO;
    }

    private static int rank(String place) {
        Kind kind = kindOf(place);
        return kind == null ? Kind.values().length : kind.ordinal();
    }

    /**
     * The places to name, best first: structures, then dimensions, then biomes, each kind in the order
     * the trip found them, at most {@code max}.
     */
    public static List<String> highlights(Collection<String> places, int max) {
        List<String> out = new ArrayList<>();
        for (Kind kind : Kind.values()) {
            for (String place : places) {
                if (out.size() >= max) {
                    return out;
                }
                if (kindOf(place) == kind) {
                    out.add(place);
                }
            }
        }
        return out;
    }

    /** Whole blocks between two readings of the player's walked-distance statistics (centimetres). */
    public static long blocks(long startCm, long nowCm) {
        return nowCm > startCm && startCm >= 0 ? (nowCm - startCm) / 100L : 0L;
    }

    /** {@code 3200} as {@code "3 200"} with a no-break space, the grouping the spec's example uses. */
    public static String group(long n) {
        String digits = Long.toString(Math.abs(n));
        StringBuilder out = new StringBuilder();
        for (int i = 0; i < digits.length(); i++) {
            if (i > 0 && (digits.length() - i) % 3 == 0) {
                out.append(' ');
            }
            out.append(digits.charAt(i));
        }
        return (n < 0 ? "-" : "") + out;
    }

    /** A charge amount as the chat shows it: a whole number, at least 1 for anything paid. */
    public static String charge(double amount) {
        if (!(amount > 0) || Double.isInfinite(amount)) {
            return "0";
        }
        return group(Math.max(1L, Math.round(amount)));
    }
}
