package dev.amman.proficiency.client;

import dev.amman.proficiency.skill.MilestoneTiers;

import java.util.ArrayList;
import java.util.List;

/**
 * The milestone ceremony's script, with no Minecraft types in it: how long each tier runs, when its
 * sounds play and at what pitch, when and where each firework goes off, and the easing curves of the
 * banner. {@code MilestoneCeremony} (game side) reads the script against a wall clock, so it is the
 * same at any frame rate. Tiers are indexed 0 to 4 for levels 10, 25, 50, 75 and 100; every tier
 * is bigger than the one before it (more bursts, more notes, longer, wider).
 */
public final class MilestoneFx {

    /** The vanilla sound each cue maps to (see {@code MilestoneCeremony}). */
    public enum Sound { CHIME, LEVELUP, TOAST, BELL, CROWD, LAUNCH }

    public record Cue(long atMs, Sound sound, float pitch, float volume) {
    }

    /**
     * One firework. {@code shape} follows the vanilla order (0 small ball, 1 large ball, 2 star, 3
     * creeper, 4 burst). {@code angle} is degrees round the player from where they look, {@code
     * radius} blocks out, {@code height} blocks above the eyes.
     */
    public record Burst(long atMs, int shape, float angle, float radius, float height, boolean gold) {
    }

    private static final long[] DURATION = {4200, 4800, 5600, 6400, 9000};
    private static final long[] FADE_IN = {320, 340, 360, 400, 600};
    public static final long FADE_OUT_MS = 800;
    /** Most fireworks any tier fires: the particle budget. */
    public static final int MAX_BURSTS = 12;
    /** The script's radii and heights are in these units; the game multiplies them so a burst is a sight, not a wall of sparks. */
    public static final float DISTANCE = 2.1f;

    private static final float[] SCALE = {0.50f, 0.61f, 0.76f, 0.88f, 1.00f};
    /** A pentatonic ladder of note-block pitches (F# to F# two octaves, in vanilla's pitch ratios). */
    private static final float[] LADDER = {0.707f, 0.794f, 0.891f, 1.0f, 1.122f, 1.260f, 1.414f, 1.587f, 1.782f, 2.0f};

    private MilestoneFx() {
    }

    public static int index(int tier) {
        return Math.max(0, MilestoneTiers.index(tier));
    }

    public static long durationMs(int tier) {
        return DURATION[index(tier)];
    }

    /** 0 while nothing shows, 1 across the hold, easing in and out at the ends. */
    public static float alpha(int tier, long age) {
        long dur = durationMs(tier);
        if (age <= 0 || age >= dur) {
            return 0f;
        }
        float in = clamp01(age / (float) FADE_IN[index(tier)]);
        float out = clamp01((dur - age) / (float) FADE_OUT_MS);
        return in * out;
    }

    /** 0 to 1 as the banner drops into place (ease-out, a small overshoot back at the end). */
    public static float slide(int tier, long age) {
        float t = clamp01(age / (float) (FADE_IN[index(tier)] + 80));
        float e = 1f - (1f - t) * (1f - t) * (1f - t);
        return e;
    }

    /** How big the banner is as a fraction of the biggest (the 100): width and type scale with it. */
    public static float scale(int tier) {
        return SCALE[index(tier)];
    }

    /**
     * The shine's position across the banner, 0 to 1, or -1 when none shows. Tiers below 100 get one
     * quick sweep; the Mastered banner sweeps slowly, again and again.
     */
    public static float shine(int tier, long age) {
        if (age < 0) {
            return -1f;
        }
        if (index(tier) < 4) {
            float t = (age - 450) / 700f;
            return t < 0f || t > 1f ? -1f : t;
        }
        long period = 3200;
        long start = 800;
        if (age < start) {
            return -1f;
        }
        float t = ((age - start) % period) / 2400f;
        return t > 1f ? -1f : t;
    }

    /**
     * The skill icon stamped onto the Mastered banner: 1 at rest, bigger (down to 1) as it lands at
     * {@link #STAMP_AT}. Before it lands the icon is not drawn ({@link #stampVisible}).
     */
    public static final long STAMP_AT = 900;
    private static final long STAMP_MS = 260;

    public static boolean stampVisible(long age) {
        return age >= STAMP_AT - STAMP_MS;
    }

    public static float stampScale(long age) {
        if (age >= STAMP_AT) {
            // A short settle: a 6% squash that eases back.
            float t = clamp01((age - STAMP_AT) / 220f);
            return 1f - 0.06f * (1f - t) * (1f - t);
        }
        float t = clamp01((age - (STAMP_AT - STAMP_MS)) / (float) STAMP_MS);
        return 1f + 1.6f * (1f - t) * (1f - t);
    }

    /** A ring that spreads from the stamp the moment it lands: 0 to 1 over 700 ms, then -1. */
    public static float stampRing(long age) {
        float t = (age - STAMP_AT) / 700f;
        return t < 0f || t > 1f ? -1f : t;
    }

    public static List<Cue> cues(int tier) {
        int i = index(tier);
        List<Cue> out = new ArrayList<>();
        // A rising run of chimes: more notes at each tier, each higher than the last.
        int notes = 3 + i;
        int start = Math.min(i, 3);
        for (int n = 0; n < notes; n++) {
            out.add(new Cue(80L + n * 95L, Sound.CHIME, LADDER[Math.min(LADDER.length - 1, start + n)],
                    0.55f + 0.04f * i));
        }
        out.add(new Cue(80L + notes * 95L, Sound.LEVELUP, 0.85f + 0.09f * i, 0.6f + 0.08f * i));
        if (i >= 2) {
            out.add(new Cue(150, Sound.TOAST, 0.8f + 0.1f * (i - 2), 0.7f));
        }
        if (i == 3) {
            out.add(new Cue(520, Sound.BELL, 1.0f, 0.9f));
        }
        if (i == 4) {
            // The crowd and the bells: three bells climbing, villagers cheering over them.
            out.add(new Cue(520, Sound.BELL, 0.8f, 1.0f));
            out.add(new Cue(1150, Sound.BELL, 1.0f, 1.0f));
            out.add(new Cue(1800, Sound.BELL, 1.25f, 1.0f));
            out.add(new Cue(900, Sound.CROWD, 0.9f, 0.9f));
            out.add(new Cue(1450, Sound.CROWD, 1.05f, 0.9f));
            out.add(new Cue(2050, Sound.CROWD, 1.2f, 0.9f));
            out.add(new Cue(2700, Sound.LAUNCH, 0.7f, 0.8f));
        }
        out.sort((a, b) -> Long.compare(a.atMs(), b.atMs()));
        return out;
    }

    public static List<Burst> bursts(int tier) {
        int i = index(tier);
        List<Burst> out = new ArrayList<>();
        switch (i) {
            case 0 -> out.add(new Burst(350, 0, 0, 3.5f, 2.0f, false));
            case 1 -> {
                out.add(new Burst(350, 1, 0, 4.0f, 2.4f, false));
                out.add(new Burst(650, 0, -35, 3.6f, 1.8f, false));
                out.add(new Burst(750, 0, 35, 3.6f, 1.8f, false));
            }
            case 2 -> {
                out.add(new Burst(350, 2, 0, 4.5f, 2.8f, false));
                out.add(new Burst(700, 1, -40, 4.0f, 2.2f, false));
                out.add(new Burst(800, 1, 40, 4.0f, 2.2f, false));
                out.add(new Burst(1100, 0, -20, 3.5f, 3.4f, true));
                out.add(new Burst(1200, 0, 20, 3.5f, 3.4f, true));
            }
            case 3 -> {
                out.add(new Burst(350, 4, 0, 5.0f, 3.2f, false));
                out.add(new Burst(700, 2, -45, 4.5f, 2.4f, false));
                out.add(new Burst(800, 2, 45, 4.5f, 2.4f, false));
                out.add(new Burst(1150, 1, -25, 4.5f, 3.8f, true));
                out.add(new Burst(1250, 1, 25, 4.5f, 3.8f, true));
                out.add(new Burst(1650, 3, 0, 5.0f, 3.0f, false));
                out.add(new Burst(1900, 4, 0, 5.0f, 4.4f, true));
            }
            default -> {
                // The ring: eight bursts spaced round the player, a bell apart, then the crown.
                for (int k = 0; k < 8; k++) {
                    out.add(new Burst(400L + k * 260L, k % 2 == 0 ? 1 : 2, k * 45f, 5.0f, 2.6f + (k % 2) * 0.8f,
                            k % 3 == 0));
                }
                out.add(new Burst(2700, 4, 0, 0.5f, 5.6f, true));
                out.add(new Burst(3100, 4, 180, 0.5f, 5.0f, true));
            }
        }
        return out;
    }

    public static float clamp01(float v) {
        return v < 0f ? 0f : Math.min(1f, v);
    }
}
