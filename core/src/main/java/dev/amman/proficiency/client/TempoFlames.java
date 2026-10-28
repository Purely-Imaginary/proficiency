package dev.amman.proficiency.client;

/**
 * The Tempo fire's rules: pixel flames that burn on top of the HUD skill bar, hotter and taller as the
 * chain climbs (five heats, the fifth at the cap), a flare when it steps up, and a die-down to smoke when
 * the window lapses. Pure numbers and the frame clock; {@code TempoBadge} draws them from
 * {@code textures/gui/tempo_flames.png} ({@code tools/gen_tempo_flames.py}).
 */
public final class TempoFlames {

    /** Heats 1 (a few embers) to 5 (the cap, yellow-white). */
    public static final int HEATS = 5;
    /** How long the flare burns after a step up. */
    public static final long FLARE_MS = 350;
    /** How long the white flare of a vanilla jump crit burns (1.49.0): longer than a step's, it is the event. */
    public static final long CRIT_FLARE_MS = 900;
    /** How long the fire takes to die down to smoke and go, once the window has lapsed. */
    public static final long DIE_MS = 1800;
    /** The part of the die-down in which the fire sinks, one heat at a time, to nothing. */
    public static final float SINK = 0.35f;
    /** Animation frames in the strip, and how long each shows: the loop is the same at any frame rate. */
    public static final int FRAMES = 16;
    public static final long FRAME_MS = 90;
    /** Rows the label rises while the fire burns, so the tallest flame never reaches the name or the level. */
    public static final int LIFT = 5;
    /** Time constant of the label's rise and fall. */
    public static final long LIFT_MS = 70;

    private static final String GLYPHS = "x0123456789.";

    private TempoFlames() {
    }

    /** Heat for {@code steps} of {@code maxSteps}: 0 for no chain, 1 from the first step, {@link #HEATS} at the cap. */
    public static int heat(int steps, int maxSteps) {
        if (steps <= 0 || maxSteps <= 0) {
            return 0;
        }
        int heat = (int) Math.ceil(Math.min(steps, maxSteps) * (double) HEATS / maxSteps);
        return Math.max(1, Math.min(HEATS, heat));
    }

    /**
     * 0 while the window is open, then rising to 1 over {@link #DIE_MS}; at 1 the fire is gone.
     * {@code sinceUpdateMs} is the time since the server last said the chain was alive.
     */
    public static float lapse(long sinceUpdateMs, long windowMs) {
        if (sinceUpdateMs < 0 || sinceUpdateMs <= windowMs) {
            return 0f;
        }
        float t = (sinceUpdateMs - windowMs) / (float) DIE_MS;
        return Math.min(1f, t);
    }

    /** The heat drawn while dying: the full heat, then one step down at a time over the {@link #SINK} part. */
    public static int shownHeat(int heat, float lapse) {
        if (lapse <= 0f) {
            return heat;
        }
        if (lapse >= SINK) {
            return 0;
        }
        return Math.max(0, (int) Math.ceil(heat * (1f - lapse / SINK)));
    }

    /** How strongly the smoke shows: none while the fire lives, up quickly as it sinks, gone at the end. */
    public static float smoke(float lapse) {
        if (lapse <= 0f || lapse >= 1f) {
            return 0f;
        }
        if (lapse < 0.15f) {
            return lapse / 0.15f;
        }
        if (lapse < 0.6f) {
            return 1f;
        }
        return (1f - lapse) / 0.4f;
    }

    /** The multiplier's strength: whole while the fire lives, gone with the flames. */
    public static float numberFade(float lapse) {
        return lapse <= 0f ? 1f : Math.max(0f, 1f - lapse / SINK);
    }

    /** 1 at a step up falling to 0 over {@link #FLARE_MS}. */
    public static float flare(long sinceStepMs) {
        if (sinceStepMs < 0 || sinceStepMs >= FLARE_MS) {
            return 0f;
        }
        float t = sinceStepMs / (float) FLARE_MS;
        return (1f - t) * (1f - t);
    }

    /** 1 at a jump crit, falling to 0 over {@link #CRIT_FLARE_MS}; held full for the first tenth so it reads. */
    public static float critFlare(long sinceCritMs) {
        if (sinceCritMs < 0 || sinceCritMs >= CRIT_FLARE_MS) {
            return 0f;
        }
        float hold = CRIT_FLARE_MS * 0.1f;
        if (sinceCritMs <= hold) {
            return 1f;
        }
        float t = (sinceCritMs - hold) / (CRIT_FLARE_MS - hold);
        return (1f - t) * (1f - t);
    }

    /** The strip's frame at {@code nowMs}: from the clock, never counted per drawn frame. */
    public static int frame(long nowMs) {
        return (int) Math.floorMod(nowMs / FRAME_MS, (long) FRAMES);
    }

    /** One step of the label's rise towards {@code target}, {@code dtMs} after the last; a long gap lands at once. */
    public static float ease(float current, float target, long dtMs) {
        if (dtMs <= 0) {
            return current;
        }
        if (dtMs > 500) {
            return target;
        }
        float k = 1f - (float) Math.exp(-dtMs / (double) LIFT_MS);
        float next = current + (target - current) * k;
        return Math.abs(target - next) < 0.05f ? target : next;
    }

    /** {@code x1.30} from a multiplier in thousandths. */
    public static String text(int permille) {
        int hundredths = Math.round(permille / 10f);
        return "x" + (hundredths / 100) + "." + (hundredths % 100 < 10 ? "0" : "") + (hundredths % 100);
    }

    /** Index of {@code c} in the strip's small pixel font, or -1 when it has no glyph. */
    public static int glyph(char c) {
        return GLYPHS.indexOf(c);
    }

    /** Pixels {@code c} moves the pen in the small font: the dot is narrow. */
    public static int advance(char c) {
        return c == '.' ? 2 : 4;
    }

    /** Width of {@code text} in the small font, without the gap after the last glyph. */
    public static int width(String text) {
        int w = 0;
        for (int i = 0; i < text.length(); i++) {
            w += advance(text.charAt(i));
        }
        return text.isEmpty() ? 0 : w - 1;
    }
}
