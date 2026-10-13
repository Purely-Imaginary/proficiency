package dev.amman.proficiency.client;

/**
 * Where the HUD draws the rested segment while the XP fill is still animating. The far end is
 * fixed to the real value plus the pool's reach, and the near end is the animated fill, so
 * incoming XP eats into the blue instead of pushing it along. While the fill is still finishing
 * an earlier bar (a level or star rolling over), there is no segment: the pool belongs to the bar
 * that comes next.
 */
public final class RestedSpan {

    private RestedSpan() {
    }

    /**
     * The segment as {@code {start, end}} pixels of a {@code width} pixel bar, or {@code null}
     * when there is nothing to draw. {@code shown} and {@code real} are {@link BarValue} values,
     * {@code reach} is the pool's reach as a fraction of the bar from the real fill. A pool that
     * reaches anything shows at least one pixel, as long as the bar has room.
     */
    public static int[] of(double shown, double real, float reach, int width) {
        if (!(reach > 0f) || width <= 0) {
            return null;
        }
        if (Math.floor(shown + 1e-6) < Math.floor(real + 1e-6) && real < BarValue.top()) {
            return null;
        }
        int start = (int) Math.round(width * BarValue.fill(shown));
        int realFill = (int) Math.round(width * BarValue.fill(real));
        if (realFill >= width) {
            return null;
        }
        int end = Math.min(width, realFill + Math.max(1, Math.round(width * reach)));
        return end > start ? new int[] {start, end} : null;
    }
}
