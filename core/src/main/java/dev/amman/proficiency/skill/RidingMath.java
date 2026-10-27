package dev.amman.proficiency.skill;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * The numbers behind Riding (the 37th skill). Pure: no Minecraft types, so every rule here is unit
 * tested. {@code RidingEvents} watches the world and pays; {@link RidingTracker} keeps the anti-farm
 * state; this class only decides.
 *
 * <p>Riding is metres ridden on anything you sit in or on and steer: a horse, donkey, mule, camel,
 * pig, strider, any modded mount (anything {@code getVehicle()} returns), a boat, a minecart, and
 * elytra flight. The movement skills pay nothing while you ride; this fills that gap. XP per metre
 * is a rate per vehicle type ({@link #DEFAULT_RATES}, overridable in the server config). The passive
 * is a faster mount, a higher jump and a sparing elytra.
 */
public final class RidingMath {

    private RidingMath() {
    }

    // ---- What is being ridden ------------------------------------------------------------------

    /** The four ways to travel. They differ in what they need from the player. */
    public enum Kind {
        /** A living mount you steer: horse, camel, pig, strider, anything modded. Needs input. */
        MOUNT(true),
        /** A boat. Needs input (a boat in a current drifts by itself). */
        BOAT(true),
        /** A minecart. Rails do the steering, so no input; the ground rules stop loops. */
        MINECART(false),
        /** Elytra flight. Steering is the look direction, so no input is asked. */
        ELYTRA(false);

        private final boolean needsInput;

        Kind(boolean needsInput) {
            this.needsInput = needsInput;
        }

        public boolean needsInput() {
            return needsInput;
        }
    }

    // ---- XP rates ------------------------------------------------------------------------------

    /** Rate key for every boat and raft, chest boats included. */
    public static final String RATE_BOAT = "#boat";
    /** Rate key for every kind of minecart. */
    public static final String RATE_MINECART = "#minecart";
    /** Rate key for any other mount: a modded one, or a vanilla one this table does not name. */
    public static final String RATE_OTHER = "*";
    /** Elytra's rate key (the item). */
    public static final String RATE_ELYTRA = "minecraft:elytra";

    /** Base XP per metre by vehicle type. Running pays 0.05 per metre at about 5.6 m/s. */
    public static final Map<String, Double> DEFAULT_RATES;

    static {
        Map<String, Double> rates = new LinkedHashMap<>();
        rates.put("minecraft:horse", 0.030);
        rates.put("minecraft:donkey", 0.030);
        rates.put("minecraft:mule", 0.030);
        rates.put("minecraft:skeleton_horse", 0.030);
        rates.put("minecraft:zombie_horse", 0.030);
        rates.put("minecraft:camel", 0.035);
        rates.put("minecraft:pig", 0.045);
        rates.put("minecraft:strider", 0.045);
        rates.put(RATE_BOAT, 0.040);
        rates.put(RATE_MINECART, 0.012);
        rates.put(RATE_ELYTRA, 0.005);
        rates.put(RATE_OTHER, 0.030);
        DEFAULT_RATES = java.util.Collections.unmodifiableMap(rates);
    }

    /** The default table as "id=rate" lines, the shape the server config stores them in. */
    public static List<String> defaultRateLines() {
        return DEFAULT_RATES.entrySet().stream().map(e -> e.getKey() + "=" + e.getValue()).toList();
    }

    /**
     * Reads "id=rate" lines over the defaults. A line that does not parse, names a negative or
     * non-finite rate, or has no id is ignored, so a typo in the config cannot break the skill.
     */
    public static Map<String, Double> parseRates(List<? extends CharSequence> lines) {
        Map<String, Double> rates = new LinkedHashMap<>(DEFAULT_RATES);
        if (lines == null) {
            return rates;
        }
        for (CharSequence raw : lines) {
            if (raw == null) {
                continue;
            }
            String line = raw.toString().trim();
            int eq = line.lastIndexOf('=');
            if (eq <= 0 || eq == line.length() - 1) {
                continue;
            }
            String id = line.substring(0, eq).trim().toLowerCase(Locale.ROOT);
            try {
                double rate = Double.parseDouble(line.substring(eq + 1).trim());
                if (!id.isEmpty() && rate >= 0 && Double.isFinite(rate)) {
                    rates.put(id, rate);
                }
            } catch (NumberFormatException ignored) {
                // a typo: keep the default
            }
        }
        return rates;
    }

    /** The class of a vehicle from its type id, for the rate table and the player-input rule. */
    public static Kind kindOf(String entityTypeId) {
        if (RATE_ELYTRA.equals(entityTypeId)) {
            return Kind.ELYTRA;
        }
        if (isBoat(entityTypeId)) {
            return Kind.BOAT;
        }
        if (isMinecart(entityTypeId)) {
            return Kind.MINECART;
        }
        return Kind.MOUNT;
    }

    private static String path(String id) {
        int colon = id == null ? -1 : id.indexOf(':');
        return id == null ? "" : id.substring(colon + 1);
    }

    public static boolean isBoat(String entityTypeId) {
        String path = path(entityTypeId);
        // "oak_boat", "bamboo_chest_raft", a modded "steam_boat"; never "aircraft" or "spacecraft".
        return path.equals("boat") || path.equals("raft") || path.endsWith("_boat") || path.endsWith("_raft");
    }

    public static boolean isMinecart(String entityTypeId) {
        return path(entityTypeId).contains("minecart");
    }

    /** XP per metre for a vehicle type: its own entry, else boat or minecart, else the catch-all. */
    public static double rate(Map<String, Double> rates, String entityTypeId) {
        Double own = rates.get(entityTypeId);
        if (own != null) {
            return own;
        }
        String group = isBoat(entityTypeId) ? RATE_BOAT : isMinecart(entityTypeId) ? RATE_MINECART : RATE_OTHER;
        Double grouped = rates.get(group);
        if (grouped != null) {
            return grouped;
        }
        Double other = rates.get(RATE_OTHER);
        return other != null ? other : DEFAULT_RATES.get(RATE_OTHER);
    }

    // ---- Elytra --------------------------------------------------------------------------------

    /** Within this many blocks of the ground, a glide earns the full low-altitude bonus. */
    public static final double LOW_ALTITUDE = 4.0;
    /** Above this many blocks over the ground the low-altitude bonus is gone. */
    public static final double HIGH_ALTITUDE = 24.0;
    /** The low-altitude bonus at the ground, and the high-speed bonus at {@link #FAST}: +50% each. */
    public static final double ALTITUDE_BONUS = 0.5;
    public static final double SPEED_BONUS = 0.5;
    /** Below this many metres per second a glide earns no speed bonus... */
    public static final double SLOW = 10.0;
    /** ...and at this many (a firework flight) the full one. */
    public static final double FAST = 30.0;

    /**
     * The elytra multiplier on the base rate, 1.0 to 2.0: up to +50% for skimming the ground (a
     * linear fade from {@link #LOW_ALTITUDE} to {@link #HIGH_ALTITUDE}) and up to +50% for speed (a
     * linear rise from {@link #SLOW} to {@link #FAST} metres per second).
     *
     * @param aboveGround blocks of air under the player, a negative number or NaN counting as none
     */
    public static double elytraFactor(double aboveGround, double metresPerSecond) {
        double low = 0.0;
        if (aboveGround >= 0 && aboveGround < HIGH_ALTITUDE) {
            low = aboveGround <= LOW_ALTITUDE ? 1.0
                    : (HIGH_ALTITUDE - aboveGround) / (HIGH_ALTITUDE - LOW_ALTITUDE);
        }
        double fast = Math.max(0.0, Math.min(1.0, (metresPerSecond - SLOW) / (FAST - SLOW)));
        if (!Double.isFinite(fast)) {
            fast = 0.0;
        }
        return 1.0 + ALTITUDE_BONUS * low + SPEED_BONUS * fast;
    }

    // ---- The passive ---------------------------------------------------------------------------

    /** Mount speed: one plus the passive. */
    public static double speedMultiplier(double bonus) {
        return bonus > 0 ? 1.0 + bonus : 1.0;
    }

    /** The share of the passive that is jump height on a mount that jumps. */
    public static final double JUMP_SHARE = 0.5;

    public static double jumpMultiplier(double bonus) {
        return bonus > 0 ? 1.0 + bonus * JUMP_SHARE : 1.0;
    }

    /** The share of the passive that spares an elytra wear point, never past 90%. */
    public static final double ELYTRA_SHARE = 1.5;

    public static double elytraSpared(double bonus) {
        return bonus > 0 ? Math.min(0.9, bonus * ELYTRA_SHARE) : 0.0;
    }

    /**
     * The damage value an elytra should have after a tick in which its wear may have risen from
     * {@code damageBefore} to {@code damageNow}: one wear point taken is given back when the roll
     * (0 to 1) falls under the spared share. Never gives back more than one point per step, and
     * never from a damage value that did not rise (a repair, an anvil, a mending tick).
     */
    public static int elytraWear(int damageBefore, int damageNow, double bonus, double roll) {
        if (damageNow <= damageBefore || damageBefore < 0) {
            return damageNow;
        }
        return roll < elytraSpared(bonus) ? damageNow - 1 : damageNow;
    }

    // ---- The tree ------------------------------------------------------------------------------

    /** Couched Lance: melee damage while mounted, per rank... */
    public static final double LANCE_PER_RANK = 0.06;
    /** ...and as much again at a full gallop. */
    public static final double GALLOP_SPEED = 0.35;

    public static double lanceMultiplier(int rank, double blocksPerTick) {
        if (rank <= 0) {
            return 1.0;
        }
        double each = LANCE_PER_RANK * rank;
        return 1.0 + each + (blocksPerTick >= GALLOP_SPEED ? each : 0.0);
    }

    /** Barding: your mount takes this much less damage per rank (never past half). */
    public static final double BARDING_PER_RANK = 0.08;

    public static double bardingMultiplier(int rank) {
        return rank <= 0 ? 1.0 : 1.0 - Math.min(0.5, BARDING_PER_RANK * rank);
    }

    /** Saddle Archer: arrows and bolts loosed from a mount hit this much harder per rank. */
    public static final double ARCHER_PER_RANK = 0.08;

    public static double archerMultiplier(int rank) {
        return rank <= 0 ? 1.0 : 1.0 + ARCHER_PER_RANK * rank;
    }

    /** The Whistle: a goat horn calls a tamed mount from this far, per rank (rank 1: 64). */
    public static final double WHISTLE_RANGE_PER_RANK = 32.0;
    public static final long WHISTLE_BASE_COOLDOWN = 2400L;
    public static final long WHISTLE_COOLDOWN_STEP = 400L;

    public static double whistleRange(int rank) {
        return rank <= 0 ? 0.0 : WHISTLE_RANGE_PER_RANK * (1 + rank);
    }

    /** Ticks before the horn can call again: 2 minutes less 20 seconds per rank, so 100, 80, 60 s. */
    public static long whistleCooldown(int rank) {
        return Math.max(600L, WHISTLE_BASE_COOLDOWN - WHISTLE_COOLDOWN_STEP * Math.max(0, rank));
    }

    public static boolean whistleReady(long lastCall, long now, int rank) {
        return lastCall < 0 || now < lastCall || now - lastCall >= whistleCooldown(rank);
    }

    /** High Seat: while mounted, block and entity reach grow by this many blocks per rank. */
    public static final double REACH_PER_RANK = 0.5;

    public static double seatReach(int rank) {
        return Math.max(0, rank) * REACH_PER_RANK;
    }

    /** Rocket Saver: a firework rocket used in flight is not spent with this chance per rank. */
    public static final double ROCKET_SAVE_PER_RANK = 0.15;

    public static double rocketSaveChance(int rank) {
        return Math.min(0.9, Math.max(0, rank) * ROCKET_SAVE_PER_RANK);
    }

    /** Soft Landing: no fall damage for this long (4 s) after you leave a mount, once per 20 s. */
    public static final long SOFT_LANDING_TICKS = 80L;
    public static final long SOFT_LANDING_COOLDOWN = 400L;

    public static boolean softLandingActive(long dismountedAt, long now) {
        return dismountedAt >= 0 && now >= dismountedAt && now - dismountedAt <= SOFT_LANDING_TICKS;
    }

    /** True when a new dismount may start a Soft Landing: the last one was long enough ago. */
    public static boolean softLandingReady(long lastStart, long now) {
        return lastStart < 0 || now < lastStart || now - lastStart >= SOFT_LANDING_COOLDOWN;
    }

    /** Trample: a mount at this speed or more hurts what it runs into... */
    public static final double TRAMPLE_SPEED = 0.35;
    /** ...once per mob per second... */
    public static final long TRAMPLE_COOLDOWN = 20L;

    /** ...for 3 damage, one more per 20 levels of Riding (8 at level 100). */
    public static double trampleDamage(int level) {
        return 3.0 + Math.max(0, Math.min(100, level)) / 20;
    }

    // ---- Tailwind and Full Gallop --------------------------------------------------------------

    /** Tailwind on a living mount: Speed II and Jump Boost II for this many ticks, times the proc power. */
    public static final int TAILWIND_TICKS = 120;
    /** Tailwind on elytra: a push along the look direction, in blocks per tick, times the proc power. */
    public static final double TAILWIND_PUSH = 0.5;
    /** Tailwind in a boat or a minecart: this many ticks of Regeneration for the rider. */
    public static final int TAILWIND_REFRESH_TICKS = 80;

    public static int tailwindTicks(double power) {
        return (int) Math.max(20, Math.round(TAILWIND_TICKS * Math.max(0.0, power)));
    }

    public static double tailwindPush(double power) {
        return TAILWIND_PUSH * Math.max(1.0, Math.min(3.0, power));
    }

    /** Full Gallop's reach: tamed mounts of yours within this many blocks join in. */
    public static final double GALLOP_RADIUS = 24.0;
}
