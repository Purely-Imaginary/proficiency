package dev.amman.proficiency.client;

import dev.amman.proficiency.skill.LodeMath;

/**
 * Seam Sense's Motherlode charge under the Mining bar: one pip per charge the player can bank,
 * side by side across the bar's width. A banked charge is a solid pip, the one being built fills
 * from the left, and the rest are empty track. A pip that just filled flashes for a moment, which
 * is the HUD's half of the "Motherlode charged" knock.
 */
public final class LodePips {

    /** Pixels between two pips. */
    public static final int GAP = 2;
    /** Pixels between the bottom of the bar and the top of the pips. */
    public static final int BELOW_BAR = 1;
    public static final int HEIGHT = 2;
    /** How long a freshly filled pip flashes. */
    public static final long FLASH_MS = 600;

    /** The charge being built, a dim stone; a banked charge, bright stone. */
    public static final int BUILDING = 0xFF7C7871;
    public static final int BANKED = 0xFFD8D3C8;

    private LodePips() {
    }

    /** One pip: its pixel columns from the bar's left edge, and how much of it is filled. */
    public record Pip(int left, int right, int filled, boolean banked) {
    }

    /**
     * The pips for a {@code width} pixel bar, or an empty array when there is nothing to show.
     * {@code capacity} is clamped to 1..{@link LodeMath#MAX_CAPACITY}; the charge to 0..capacity.
     */
    public static Pip[] of(double lode, int capacity, int width) {
        int count = Math.max(1, Math.min(LodeMath.MAX_CAPACITY, capacity));
        int each = (width - GAP * (count - 1)) / count;
        if (each <= 0) {
            return new Pip[0];
        }
        double charge = Double.isFinite(lode) ? Math.max(0.0, Math.min(count, lode)) : 0.0;
        int full = LodeMath.full(charge);
        // The pips share out the bar's leftover pixels from the left, so the row is as wide as the bar.
        int spare = width - GAP * (count - 1) - each * count;
        Pip[] pips = new Pip[count];
        int x = 0;
        for (int i = 0; i < count; i++) {
            int w = each + (i < spare ? 1 : 0);
            boolean banked = i < full;
            int filled;
            if (banked) {
                filled = w;
            } else if (i == full) {
                double part = charge - full;
                // Any charge at all shows one pixel, so the first stone after a spent ore is seen.
                filled = part > 0 ? Math.max(1, Math.min(w, (int) Math.floor(w * part))) : 0;
            } else {
                filled = 0;
            }
            pips[i] = new Pip(x, x + w, filled, banked);
            x += w + GAP;
        }
        return pips;
    }

    /** The flash strength (1 down to 0) of a pip that filled {@code sinceMs} ago. */
    public static float flash(long sinceMs) {
        if (sinceMs < 0 || sinceMs >= FLASH_MS) {
            return 0f;
        }
        float t = sinceMs / (float) FLASH_MS;
        return (1f - t) * (1f - t);
    }
}
