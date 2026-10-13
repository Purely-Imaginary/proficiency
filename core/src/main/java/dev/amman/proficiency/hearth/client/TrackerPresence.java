package dev.amman.proficiency.hearth.client;

/**
 * When the Hearth tracker is on the screen (docs/HEARTH-SPEC.md, "Hearth tracker": at home only, fading
 * {@link #LINGER_MS} after you leave), with no Minecraft in it. It is told every frame whether the
 * player is home, drawn or not: with the GUI hidden (F1) the HUD pass still calls {@link #update}, so a
 * walk out while hidden is not replayed as a fresh "just left" when the GUI comes back.
 */
public final class TrackerPresence {

    public static final long FADE_IN_MS = 250;
    /** How long the tracker stays after you leave, before it fades. */
    public static final long LINGER_MS = 5000;
    public static final long FADE_OUT_MS = 500;

    private long homeSince = Long.MIN_VALUE / 2;
    private long leftAt = Long.MIN_VALUE / 2;
    private boolean wasHome;

    /** This frame's home flag: arriving starts the fade in, leaving starts the linger. */
    public void update(boolean home, long now) {
        if (home && !wasHome) {
            homeSince = now;
        } else if (!home && wasHome) {
            leftAt = now;
        }
        wasHome = home;
    }

    /** Whether the last {@link #update} said home. */
    public boolean home() {
        return wasHome;
    }

    /** Opacity 0..1 before easing: rising over {@link #FADE_IN_MS} at home, held, then falling after leaving. */
    public float opacity(long now) {
        if (wasHome) {
            return Math.max(0f, Math.min(1f, (now - homeSince) / (float) FADE_IN_MS));
        }
        long gone = now - leftAt - LINGER_MS;
        return gone < 0 ? 1f : 1f - Math.min(1f, gone / (float) FADE_OUT_MS);
    }

    /** Leaving a world: never home, nothing lingering. */
    public void reset() {
        homeSince = Long.MIN_VALUE / 2;
        leftAt = Long.MIN_VALUE / 2;
        wasHome = false;
    }
}
