package dev.amman.proficiency.skill;

/**
 * The numbers behind Nightwalker. Pure: no Minecraft types, so every rule here is unit tested.
 * {@link NightwalkerService} looks at the world and pays; this class only decides.
 *
 * <p>Nightwalker earns in two ways, and neither can be farmed on its own:
 * <ul>
 * <li>A share of every other skill's grant earned while you stand in the dark. No other grant,
 * no Nightwalker XP.</li>
 * <li>A small trickle for each full minute of active play outdoors at night, away from light.
 * A second counts only if you moved and looked around lately, and not in bed, in water, or
 * riding something.</li>
 * </ul>
 */
public final class NightwalkerMath {

    /** Block light at or below this is dark. Monsters spawn at 0; 3 is a dim cave edge. */
    public static final int DARK_BLOCK_LIGHT = 3;
    /** In daylight, sky light must be this low too (a cave, a closed room). At night it is ignored. */
    public static final int DARK_SKY_LIGHT = 3;

    /** Nightwalker's share of another skill's grant, before multipliers, when the config is not loaded. */
    public static final double DEFAULT_SHARE = 0.20;
    /** Base XP for each full minute of active play outdoors at night. */
    public static final double DEFAULT_TRICKLE_XP = 5.0;
    /** Active seconds that make one trickle payment. */
    public static final int TRICKLE_SECONDS = 60;
    /** A second counts only if you moved at least this far (blocks, horizontally) since the last one. */
    public static final double MIN_MOVE = 1.0;
    /** And only if you turned your head within this many seconds. */
    public static final int LOOK_WINDOW_SECONDS = 30;
    /** Smallest head turn (degrees, yaw plus pitch) that counts as looking around. */
    public static final float MIN_LOOK_DEGREES = 2.0f;

    /** Share-of-grant XP is held and paid at most this often (5 s), as one log line. */
    public static final long PAY_INTERVAL_TICKS = 100L;

    /** Moonlit: Night Vision and Speed for this long, times proc power. */
    public static final int MOONLIT_TICKS = 200;
    /** Eclipse: hostile mobs in the dark further than this lose you. */
    public static final double ECLIPSE_RANGE = 8.0;

    /** Far Sight: extra Dark Sight per rank, as a share of Night Vision's light. */
    public static final double FAR_SIGHT_PER_RANK = 0.02;
    /** Dark Sight never goes past this, so it stays weaker than a real Night Vision potion. */
    public static final double DARK_SIGHT_CAP = 0.20;
    /** Night Owl: less hunger per rank at night or in the dark. */
    public static final double NIGHT_OWL_PER_RANK = 0.10;
    /** Darkborn Bane: more damage per rank to a mob that spawned in the dark. */
    public static final double DARKBORN_PER_RANK = 0.06;
    /** Phantom Ward: less damage from Phantoms per rank. */
    public static final double PHANTOM_WARD_PER_RANK = 0.20;
    /** Deep Calm: a shorter Darkness effect per rank. */
    public static final double DEEP_CALM_PER_RANK = 0.25;
    /** Sanctuary: no natural monster spawns within this many blocks of your respawn point. */
    public static final double SANCTUARY_RADIUS = 16.0;

    private NightwalkerMath() {
    }

    /**
     * Whether a spot is dark: block light 0-3, and either it is night (sky light ignored) or the
     * sky light is low too.
     *
     * @param blockLight light from torches, lava and the like, 0-15
     * @param skyLight   raw sky light at the spot, 0-15 (0 where the dimension has no sky)
     * @param night      whether the level says it is night
     */
    public static boolean isDark(int blockLight, int skyLight, boolean night) {
        return blockLight <= DARK_BLOCK_LIGHT && (night || skyLight <= DARK_SKY_LIGHT);
    }

    /**
     * Vanilla's night test ({@code Level.isNight()}: sky darkness 4 or more), computed from the
     * inputs instead of the level's cached sky darkness, which a client only computes once.
     *
     * @param fixedTime  the dimension has no day cycle (the Nether, the End): never night
     * @param timeOfDay  0-1, as {@code Level.getTimeOfDay}; 0.5 is midnight
     * @param rain       rain level 0-1
     * @param thunder    thunder level 0-1
     */
    public static boolean isNight(boolean fixedTime, float timeOfDay, float rain, float thunder) {
        if (fixedTime) {
            return false;
        }
        double rainFactor = 1.0 - rain * 5.0f / 16.0;
        double thunderFactor = 1.0 - thunder * 5.0f / 16.0;
        double sun = 0.5 + 2.0 * Math.max(-0.25, Math.min(0.25, Math.cos(timeOfDay * (float) (Math.PI * 2))));
        int skyDarken = (int) ((1.0 - sun * rainFactor * thunderFactor) * 11.0);
        return skyDarken >= 4;
    }

    /**
     * Base Nightwalker XP from one grant of another skill: {@code share} of its size at that
     * skill's own rate, before any player multiplier (company, tempo, streak and the rest apply
     * again on the Nightwalker grant, so they are left out here).
     */
    public static double share(double baseAmount, double skillRate, double tier, double share) {
        double value = baseAmount * skillRate * tier * share;
        return value > 0 && Double.isFinite(value) ? value : 0.0;
    }

    /** One second of outdoor night play, as the tick sees it. */
    public record Second(boolean outdoors, boolean night, boolean dark, boolean sleeping,
            boolean riding, boolean inWater, double x, double z, float yaw, float pitch) {
    }

    /**
     * Counts active seconds outdoors at night for one player. It keeps the last position and head
     * direction, so it knows whether the player moved and looked around. Not thread safe; one per
     * player, driven by the server's once-a-second tick.
     */
    public static final class Trickle {
        private boolean seen;
        private double lastX;
        private double lastZ;
        private float lastYaw;
        private float lastPitch;
        /** Seconds since the head last turned; starts past the window, so standing still never counts. */
        private int sinceLook = LOOK_WINDOW_SECONDS + 1;
        private int active;

        /** Active seconds counted toward the next payment. */
        public int active() {
            return active;
        }

        /** Feeds one second. Returns true when it completes a full active minute (and restarts the count). */
        public boolean step(Second s) {
            boolean moved = false;
            if (seen) {
                double dx = s.x() - lastX;
                double dz = s.z() - lastZ;
                moved = dx * dx + dz * dz >= MIN_MOVE * MIN_MOVE;
                float turn = Math.abs(wrap(s.yaw() - lastYaw)) + Math.abs(s.pitch() - lastPitch);
                sinceLook = turn >= MIN_LOOK_DEGREES ? 0 : Math.min(sinceLook + 1, LOOK_WINDOW_SECONDS + 1);
            }
            seen = true;
            lastX = s.x();
            lastZ = s.z();
            lastYaw = s.yaw();
            lastPitch = s.pitch();
            if (!counts(s, moved, sinceLook)) {
                return false;
            }
            if (++active >= TRICKLE_SECONDS) {
                active = 0;
                return true;
            }
            return false;
        }

        /** A death or a relog: the partial minute goes. */
        public void reset() {
            active = 0;
        }
    }

    /** The anti-AFK rule for one second, on its own. */
    public static boolean counts(Second s, boolean moved, int secondsSinceLook) {
        return s.outdoors() && s.night() && s.dark() && !s.sleeping() && !s.riding() && !s.inWater()
                && moved && secondsSinceLook <= LOOK_WINDOW_SECONDS;
    }

    private static float wrap(float degrees) {
        float d = degrees % 360f;
        if (d >= 180f) {
            d -= 360f;
        }
        if (d < -180f) {
            d += 360f;
        }
        return d;
    }

    /**
     * Dark Sight: how much of Night Vision's light you get in the dark, 0 to {@link #DARK_SIGHT_CAP}.
     *
     * @param passive  the Nightwalker passive, as a fraction (0.10 at level 100 before talents)
     * @param farSight ranks of Far Sight
     */
    public static double darkSight(double passive, int farSight) {
        double value = Math.max(0.0, passive) + FAR_SIGHT_PER_RANK * Math.max(0, farSight);
        return Math.min(DARK_SIGHT_CAP, value);
    }

    /** Moonlit's length in ticks for a given proc power. */
    public static int moonlitTicks(double power) {
        return (int) Math.round(MOONLIT_TICKS * Math.max(1.0, power));
    }

    /** Night Owl: the share of new hunger handed back. Never all of it. */
    public static double nightOwlRefund(int rank) {
        return Math.min(0.5, NIGHT_OWL_PER_RANK * Math.max(0, rank));
    }

    /** Darkborn Bane: the damage multiplier against a mob that spawned in the dark. */
    public static double darkbornMultiplier(int rank) {
        return 1.0 + DARKBORN_PER_RANK * Math.max(0, rank);
    }

    /** Phantom Ward: the damage multiplier for a Phantom's hit. Never below 0.2. */
    public static double phantomWard(int rank) {
        return Math.max(0.2, 1.0 - PHANTOM_WARD_PER_RANK * Math.max(0, rank));
    }

    /** Deep Calm: a Darkness effect's new length. At least one second. */
    public static int darknessTicks(int duration, int rank) {
        if (rank <= 0 || duration <= 0) {
            return duration;
        }
        double kept = Math.max(0.0, 1.0 - DEEP_CALM_PER_RANK * rank);
        return Math.max(20, (int) Math.round(duration * kept));
    }

    /** Eclipse: whether a hostile mob this far away (squared blocks) loses track of you. */
    public static boolean eclipseHides(double distanceSquared, boolean mobInDark) {
        return mobInDark && distanceSquared > ECLIPSE_RANGE * ECLIPSE_RANGE;
    }

    /** Sanctuary: whether a spawn this far (squared blocks) from a respawn point is blocked. */
    public static boolean inSanctuary(double distanceSquared) {
        return distanceSquared <= SANCTUARY_RADIUS * SANCTUARY_RADIUS;
    }
}
