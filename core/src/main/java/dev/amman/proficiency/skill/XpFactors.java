package dev.amman.proficiency.skill;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * The multipliers one XP grant applied, as short id and value pairs. Only factors that are not
 * 1.0 are kept, so a plain gain carries an empty list and costs one byte on the wire. Pure
 * helpers, no game classes, so the encoding is unit tested.
 */
public final class XpFactors {

    /** Config rate: {@code xpRate} times {@code xpMultiplier}. */
    public static final String RATE = "rate";
    public static final String PERK = "perk";
    public static final String COMPANY = "company";
    public static final String TEMPO = "tempo";
    public static final String OVERFLOW = "overflow";
    public static final String INSPIRED = "inspired";
    public static final String STREAK = "streak";
    /** Rested XP spent on this grant: the grant with the extra, over the grant without it. */
    public static final String RESTED = "rested";
    /** The first-time tier scaling. */
    public static final String TIER = "tier";

    /** Most factors one gain can carry; the codec drops the rest. */
    public static final int MAX = 12;

    public record Factor(String id, float value) {
    }

    private XpFactors() {
    }

    /** Whether a value is worth listing: finite and not 1.0 to the precision shown. */
    public static boolean isNotable(double value) {
        return Double.isFinite(value) && Math.abs(value - 1.0) >= 0.0005;
    }

    /** Adds {@code id} to {@code into} when its value is notable. */
    public static void add(List<Factor> into, String id, double value) {
        if (isNotable(value) && into.size() < MAX) {
            into.add(new Factor(id, (float) value));
        }
    }

    /** {@code tempo=1.300;streak=1.120}, or an empty string. Ids never contain {@code ;} or {@code =}. */
    public static String encode(List<Factor> factors) {
        StringBuilder out = new StringBuilder();
        for (Factor factor : factors) {
            if (out.length() > 0) {
                out.append(';');
            }
            out.append(factor.id()).append('=')
                    .append(String.format(Locale.ROOT, "%.3f", factor.value()));
        }
        return out.toString();
    }

    /** The inverse of {@link #encode}; malformed pieces are skipped. */
    public static List<Factor> decode(String text) {
        List<Factor> out = new ArrayList<>();
        if (text == null || text.isEmpty()) {
            return out;
        }
        for (String piece : text.split(";")) {
            int eq = piece.indexOf('=');
            if (eq <= 0) {
                continue;
            }
            try {
                out.add(new Factor(piece.substring(0, eq), Float.parseFloat(piece.substring(eq + 1))));
            } catch (NumberFormatException ignored) {
                // A bad piece is a bad log line, not a reason to lose the rest.
            }
        }
        return out;
    }
}
