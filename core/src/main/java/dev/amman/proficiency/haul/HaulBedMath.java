package dev.amman.proficiency.haul;

/**
 * Where the "Today's Haul" button sits on the bed's chat screen ("Leave Bed"), and its soft pulse. Vanilla puts
 * "Leave Bed" at (width/2 - 100, height - 40) as 200 x 20 and the chat input along the bottom edge (2, height - 12,
 * width - 4, 12). The button goes centred just above "Leave Bed", one gap clear of it, so it covers neither at any
 * GUI scale; when the screen is too short for that it is simply left out ({@link #place} returns null).
 */
public final class HaulBedMath {

    /** Vanilla's "Leave Bed" button. */
    public static final int LEAVE_W = 200;
    public static final int LEAVE_H = 20;
    public static final int LEAVE_BOTTOM_MARGIN = 40;
    /** Vanilla's chat input line. */
    public static final int INPUT_H = 12;

    public static final int BUTTON_H = 20;
    public static final int GAP = 4;
    public static final int MIN_W = 96;
    public static final int MAX_W = 200;
    /** Width of the icon (16) with its padding, before the label. */
    public static final int ICON_PAD = 26;

    private HaulBedMath() {
    }

    /** {x, y, w, h} of the button, or null when it does not fit. {@code labelW} is the label's width in GUI px. */
    public static int[] place(int screenW, int screenH, int labelW) {
        int w = Math.min(MAX_W, Math.min(screenW - 8, Math.max(MIN_W, labelW + ICON_PAD + 10)));
        int y = screenH - LEAVE_BOTTOM_MARGIN - GAP - BUTTON_H;
        if (w < 40 || y < 2) {
            return null;
        }
        return new int[] {(screenW - w) / 2, y, w, BUTTON_H};
    }

    /** True when the two rectangles {x, y, w, h} share a pixel. */
    public static boolean overlaps(int[] a, int[] b) {
        return a[0] < b[0] + b[2] && b[0] < a[0] + a[2] && a[1] < b[1] + b[3] && b[1] < a[1] + a[3];
    }

    /** The glow's strength, 0 to 1: a slow breath, one cycle every 2.4 s, from 0.25 up to 1. */
    public static float pulse(long ms) {
        double phase = (ms % 2400L) / 2400.0 * Math.PI * 2;
        return (float) (0.625 - 0.375 * Math.cos(phase));
    }
}
