package dev.amman.proficiency.client;

import java.util.ArrayList;
import java.util.List;

/**
 * The XP-gain animation on the skill HUD bar, without any Minecraft in it: a gain launches a few
 * dots from the right edge of the screen, each carrying a share of the gain, and the bar only
 * fills by a share when its dot lands. {@link SkillHud} draws it; the unit tests drive it with a
 * fake clock.
 *
 * <p>Values are in "level units": level + progress, so 7.5 is half way through level 7 and a gain
 * that crosses a level runs the fill to the end and wraps. Cheap on purpose (the owner watches GPU
 * heat): at most {@link #MAX_LIVE} dots, three small rectangles each, and nothing at all once the
 * last one has landed.
 */
public final class XpGainDots {

    /** Most dots one gain launches. A full level's worth or more gets all of them. */
    public static final int MAX_PER_GAIN = 8;
    /** Most dots in the air at once, across gains. Anything over it fills the bar directly. */
    public static final int MAX_LIVE = 14;
    /** Time from the right edge to the bar. */
    public static final long FLIGHT_MS = 560;
    /** Gap between two launches, so a burst reads as a stream rather than one blob. */
    public static final long STAGGER_MS = 45;
    /** How long the fill edge glows after a dot lands. */
    public static final long FLASH_MS = 140;
    /** Time constant of the fill catching up with the dots that have landed. */
    public static final double FILL_TAU_MS = 70;

    /** One dot in flight. {@code lane} in [-1, 1] spreads start heights and arcs. */
    public record Dot(long launch, long flight, float lane, double share) {
        public long lands() {
            return launch + flight;
        }
    }

    private final List<Dot> dots = new ArrayList<>();
    private double shown = -1;
    private long lastFrame;
    private long lastLaunch;
    private long flashUntil;
    private int launched;

    /**
     * How many dots a gain of {@code gain} bars launches: two for a sliver, one more per 4% of a
     * bar, {@link #MAX_PER_GAIN} at most. Zero for nothing (or a loss).
     */
    public static int dotCount(double gain) {
        if (!(gain > 0)) {
            return 0;
        }
        long count = 2 + Math.round(gain * 25);
        return (int) Math.min(MAX_PER_GAIN, count);
    }

    /** Ease in and out (cubic): leaves the edge gently, rushes, settles into the bar. */
    public static double ease(double t) {
        if (t <= 0) {
            return 0;
        }
        if (t >= 1) {
            return 1;
        }
        return t < 0.5 ? 4 * t * t * t : 1 - Math.pow(-2 * t + 2, 3) / 2;
    }

    /** Forget every dot and show {@code value} as it is. For a new skill, a loss or the toggle off. */
    public void snap(double value, long now) {
        dots.clear();
        shown = value;
        lastFrame = now;
        flashUntil = 0;
    }

    /**
     * The value moved up from {@code from} to {@code to}: launch dots for the difference. A
     * first observation or a move down snaps instead.
     */
    public void gain(double from, double to, long now) {
        if (shown < 0 || !(to > from)) {
            snap(to, now);
            return;
        }
        double gain = to - from;
        int count = Math.min(dotCount(gain), MAX_LIVE - dots.size());
        if (count <= 0) {
            // The sky is full: let the bar take this one directly through the fill lerp.
            return;
        }
        double share = gain / count;
        long start = Math.max(now, lastLaunch + STAGGER_MS);
        for (int i = 0; i < count; i++) {
            long launch = start + i * STAGGER_MS;
            // Golden-ratio spread: deterministic, never two dots on the same lane in a row.
            float lane = (float) (((launched++ * 0.6180339887) % 1.0) * 2 - 1);
            long flight = FLIGHT_MS + (long) (lane * 60);
            dots.add(new Dot(launch, flight, lane, share));
            lastLaunch = launch;
        }
    }

    /**
     * Advances to {@code now} towards {@code real}, the value the server last sent: lands dots,
     * moves the fill. Returns the value the bar should show this frame.
     */
    public double update(double real, long now) {
        if (shown < 0) {
            snap(real, now);
            return shown;
        }
        double pending = 0;
        for (int i = dots.size() - 1; i >= 0; i--) {
            Dot dot = dots.get(i);
            if (now >= dot.lands()) {
                dots.remove(i);
                flashUntil = now + FLASH_MS;
            } else {
                pending += dot.share();
            }
        }
        double target = Math.max(0, real - pending);
        long dt = Math.max(0, now - lastFrame);
        lastFrame = now;
        double k = 1 - Math.exp(-dt / FILL_TAU_MS);
        shown += (target - shown) * k;
        if (Math.abs(target - shown) < 1e-4) {
            shown = target;
        }
        return shown;
    }

    /** The dots still in the air (including ones waiting for their launch slot). */
    public List<Dot> dots() {
        return dots;
    }

    /** Anything still moving: dots in flight, the fill catching up, or the edge glow. */
    public boolean busy(double real, long now) {
        return !dots.isEmpty() || Math.abs(real - shown) > 1e-4 || now < flashUntil;
    }

    /** 0..1, how bright the fill edge glows this frame. */
    public float flash(long now) {
        return now >= flashUntil ? 0f : (flashUntil - now) / (float) FLASH_MS;
    }

    /** 0..1 along the flight, before easing; negative while the dot waits for launch. */
    public static double progress(Dot dot, long now) {
        return (now - dot.launch()) / (double) dot.flight();
    }
}
