package dev.amman.proficiency.client;

/**
 * How far the skill line (and the Hearth glyphs on its row) moves up to stay clear of Forge 1.20.1's
 * action bar. Forge draws the action bar (the overlay message) {@code max(leftHeight, rightHeight) + 9}
 * above the bottom, never lower than vanilla's 68, so it climbs with every extra row of bars on either
 * side of the hotbar: armour, a second row of hearts (the Hearth's own temp HP at full Warmth), a mount's
 * health. At 68 the action bar's text ends just below the skill line (label at 88, bar ending at 75);
 * every row above that ran the text into the line, and "Set out from the Hearth" printed through the
 * Provisioned glyph (polish pass, Forge 1.20.1 real client, Reclamation pack, 2026-10-10). NeoForge and
 * Fabric 1.21.1 keep the action bar at 68, so they need no lift. Pure: the Forge HUD feeds it the bar
 * heights of the frame.
 */
public final class LineLift {

    /** Vanilla's action bar row, measured up from the bottom; Forge never draws it lower. */
    public static final int ACTION_BAR = 68;
    /** Forge's gap between the top of the bar stacks and the action bar row (68 - 59, the item name's row). */
    public static final int FORGE_GAP = 9;
    /** How long the line takes to settle at a new height (milliseconds, about two thirds of the way). */
    public static final double EASE_MS = 60.0;

    private LineLift() {
    }

    /** GUI pixels the action bar sits above vanilla's row for these bar stacks (0 when it does not move). */
    public static int forLeftRight(int leftHeight, int rightHeight) {
        return Math.max(0, Math.max(leftHeight, rightHeight) + FORGE_GAP - ACTION_BAR);
    }

    /**
     * One easing step from {@code current} toward {@code target} after {@code elapsedMs}: a row of hearts
     * that comes or goes slides the line instead of snapping it. Lands exactly once within a twentieth of
     * a pixel.
     */
    public static double ease(double current, int target, long elapsedMs) {
        if (elapsedMs <= 0) {
            return current;
        }
        double next = current + (target - current) * (1.0 - Math.exp(-Math.min(elapsedMs, 1000) / EASE_MS));
        return Math.abs(target - next) < 0.05 ? target : next;
    }
}
