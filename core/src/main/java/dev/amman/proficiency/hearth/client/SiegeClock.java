package dev.amman.proficiency.hearth.client;

/**
 * The siege panel's clock and fades (docs/HEARTH-SPEC.md, "Warning: a day ahead, a countdown on
 * the left"), with no Minecraft in it. The server sends the game time a siege is due once; the
 * client counts down from it, so nothing streams every second.
 */
public final class SiegeClock {

    /** How long an outcome stays up before the panel goes. */
    public static final long OUTCOME_MS = 10_000;
    public static final long FADE_IN_MS = 250;
    public static final long FADE_OUT_MS = 700;
    /** The warning the bar drains over when the server's number is unknown: one in-game day. */
    public static final long DEFAULT_WARNING_TICKS = 24_000;

    /**
     * How far a countdown's start may move and still be the siege already announced. The server sends the
     * expected start (the first dusk after the due time) again when Warm n/m changes and on a postponement:
     * a slept night moves that dusk up to half a day earlier or, across a dusk, up to a day later, and a
     * postponement a night later. A new siege at the same Hearth comes at least a horn's cooldown after the
     * last one, so the window stops at two days.
     */
    public static final long SAME_SIEGE_EARLIER = 24_000;
    public static final long SAME_SIEGE_LATER = 48_000;

    private SiegeClock() {
    }

    /**
     * Whether a countdown for the Hearth at {@code pos} starting at {@code start} is the one last announced
     * ({@code announcedPos}, {@code announcedStart}; {@link Long#MIN_VALUE} for none): then it gets the panel
     * only, not the banner again.
     */
    public static boolean sameSiege(long pos, long start, long announcedPos, long announcedStart) {
        if (announcedPos == Long.MIN_VALUE || pos != announcedPos) {
            return false;
        }
        long shift = start - announcedStart;
        return shift > -SAME_SIEGE_EARLIER && shift <= SAME_SIEGE_LATER;
    }

    /** Game ticks left until {@code due}, never below 0. */
    public static long ticksLeft(long due, long now) {
        return Math.max(0L, due - now);
    }

    /**
     * "18:20" for ticks left (real time, 20 ticks a second), "1:02:05" past an hour. Rounded up, so
     * the clock reads 0:00 only when it is due.
     */
    public static String clock(long ticksLeft) {
        long seconds = (Math.max(0L, ticksLeft) + 19) / 20;
        long hours = seconds / 3600;
        long minutes = (seconds % 3600) / 60;
        long secs = seconds % 60;
        if (hours > 0) {
            return String.format(java.util.Locale.ROOT, "%d:%02d:%02d", hours, minutes, secs);
        }
        return String.format(java.util.Locale.ROOT, "%d:%02d", minutes, secs);
    }

    /** The countdown bar over the whole warning: full while the start is a warning or more away. */
    public static float bar(long due, long now, long warningTicks) {
        return bar(due, now, warningTicks, 0L);
    }

    /**
     * The countdown bar, 1 when announced draining to 0 at the due time. {@code spanTicks} is the time
     * that was left when this siege was first seen ({@link #span}): a horn's siege a few minutes off starts
     * full and drains to zero, where the warning alone would show it nearly empty from the start. 0 drains
     * over the warning.
     */
    public static float bar(long due, long now, long warningTicks, long spanTicks) {
        long warning = warningTicks > 0 ? warningTicks : DEFAULT_WARNING_TICKS;
        long span = spanTicks > 0 ? Math.min(spanTicks, warning) : warning;
        return Math.max(0f, Math.min(1f, ticksLeft(due, now) / (float) span));
    }

    /**
     * The span a countdown's bar drains over: the most time left it was ever seen with ({@code previous},
     * 0 for a new siege, and {@code ticksLeft} now), never more than the warning. A start moved later (a
     * postponement) widens it, so the bar never jumps above full.
     */
    public static long span(long previous, long ticksLeft, long warningTicks) {
        long warning = warningTicks > 0 ? warningTicks : DEFAULT_WARNING_TICKS;
        return Math.max(1L, Math.min(warning, Math.max(previous, ticksLeft)));
    }

    /** What the panel leaves free at the bottom of the screen for the hotbar, hearts, food and XP bar, as the XP feed does. */
    public static final int HUD_CLEARANCE = 48;

    /**
     * Where the top of a siege panel {@code height} tall goes on a screen {@code guiHeight} high: at
     * {@code fraction} of the height (the hearth.siegePanelY setting), or just under a discovery banner
     * ({@code bannerTop}..{@code bannerBottom}; {@code bannerTop} 0 for none) that would cover it, as the XP
     * feed and the death recap make room for it. Never lower than {@link #HUD_CLEARANCE} above the bottom,
     * so a high setting on a short screen (0.9 at GUI scale 4) stops above the hotbar, and never above
     * {@code margin}.
     */
    public static int panelTop(int guiHeight, int height, float fraction, int margin, int bannerTop, int bannerBottom) {
        int lowest = guiHeight - HUD_CLEARANCE - height;
        int base = Math.min(Math.max(margin, Math.round(guiHeight * fraction)), lowest);
        // Two pixels of slack: the banner's kicker line starts below its top, so touching is not covering.
        int target = bannerTop > 0 && base + height - 2 > bannerTop ? bannerBottom + 4 : base;
        target = Math.min(Math.max(base, target), lowest);
        return Math.max(margin, target);
    }

    /**
     * Panel opacity, 0..1: a quick fade in from {@code shownAt}, then steady; with an end time
     * ({@code endsAt} not {@link Long#MAX_VALUE}) a slower fade out to it. The death recap's curve.
     */
    public static float opacity(long shownAt, long endsAt, long now) {
        if (now < shownAt) {
            return 0f;
        }
        float in = Math.min(1f, (now - shownAt) / (float) FADE_IN_MS);
        if (endsAt == Long.MAX_VALUE) {
            return in;
        }
        if (now >= endsAt) {
            return 0f;
        }
        float out = Math.min(1f, (endsAt - now) / (float) FADE_OUT_MS);
        return Math.min(in, out);
    }
}
