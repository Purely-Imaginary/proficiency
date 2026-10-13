package dev.amman.proficiency.skill;

import dev.amman.proficiency.perk.Synergies;
import dev.amman.proficiency.perk.Talent;
import dev.amman.proficiency.perk.Talents;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * The numbers behind Homebuilder (docs/HOMEBUILDER-SPEC.md). Pure: no Minecraft types, so every
 * rule here is unit tested; the common code ({@code homebuilder} package) looks at the world and
 * pays. The talent tags live here too, so both Minecraft lines read them through one place.
 *
 * <p>Homebuilder earns like Nightwalker and cannot farm itself:
 * <ul>
 * <li>A share of every other skill's grant earned at home (in range of a lit Hearth that is not
 * smouldering), before multipliers. Its own grants, the other share pots' payouts (Social,
 * Nightwalker), an op's {@code addxp} and teaching never feed it.</li>
 * <li>The home's milestones, once each per Hearth and per player.</li>
 * </ul>
 */
public final class HomebuilderMath {

    // ---- XP ---------------------------------------------------------------------------------------

    /** Homebuilder's share of another skill's grant at home, before multipliers (spec, tune). */
    public static final double DEFAULT_SHARE = 0.15;
    /** The share is held and paid at most this often (5 s), as one "At home" line, like Nightwalker. */
    public static final long PAY_INTERVAL_TICKS = 100L;

    /** Lighting (stage 1) or sealing a stage pays this times the stage number. */
    public static final double DEFAULT_STAGE_XP = 150.0;
    /** A Comfort kind seen at a Hearth for the first time, placed by you. */
    public static final double DEFAULT_COMFORT_XP = 60.0;
    /** A new Character block id at a Hearth, placed by you. */
    public static final double DEFAULT_CHARACTER_XP = 10.0;
    /** A siege defended: this much, split by kills. */
    public static final double DEFAULT_SIEGE_XP = 300.0;
    /** Everyone present at a defended siege gets at least this share of it. */
    public static final double SIEGE_MIN_SHARE = 0.10;
    /** A Feast you were part of. */
    public static final double DEFAULT_FEAST_XP = 25.0;
    /** Tales paid in at a Hearth: this share of the Tales. */
    public static final double DEFAULT_TALES_SHARE = 0.10;
    /** A Chronicle page written (the flat part). */
    public static final double DEFAULT_CHRONICLE_XP = 20.0;

    // ---- The passive ------------------------------------------------------------------------------

    /** Reach at home: the passive times this, in blocks (+2 at 0.20). */
    public static final double REACH_PER_BONUS = 10.0;
    /** Magnet radius: this plus the passive times {@link #MAGNET_PER_BONUS} (6 blocks at 0.20). */
    public static final double MAGNET_BASE = 1.0;
    public static final double MAGNET_PER_BONUS = 25.0;
    /** Long Pull: blocks of magnet radius per rank. */
    public static final double LONG_PULL_PER_RANK = 1.0;
    /** The magnet runs every this many ticks (one area query per player at home). */
    public static final int MAGNET_INTERVAL_TICKS = 5;
    /** At most this many entities pulled per run, nearest first. */
    public static final int MAGNET_MAX_PER_PULL = 8;
    /** Another player's drop is left alone this long (5 s), so handing items over still works. */
    public static final int FRESH_DROP_TICKS = 100;
    /** How fast a pulled item flies, blocks per tick; Tidy Home rank 3 doubles it. */
    public static final double MAGNET_SPEED = 0.30;
    /** The little hop that makes a pulled item arc in instead of sliding along the floor. */
    public static final double MAGNET_HOP = 0.22;
    /** Inside this distance the magnet lets vanilla's own pickup take over. */
    public static final double MAGNET_DONE = 1.2;

    /** House Proud doubles the passive. */
    public static final double HOUSE_PROUD_FACTOR = 2.0;

    // ---- Hearthglow, House Proud, Homeward ----------------------------------------------------------

    /** Hearthglow rolls at most once per 10 s. */
    public static final long HEARTHGLOW_COOLDOWN_TICKS = 200L;
    /** House Proud lasts 30 s (tune) before Open House and the ability duration effects. */
    public static final int DEFAULT_HOUSE_PROUD_TICKS = 600;
    /** House Proud's cooldown, 6 minutes (tune), before the capstone's cut. */
    public static final int DEFAULT_HOUSE_PROUD_COOLDOWN_TICKS = 7200;
    /** Open House: friends this many blocks past the Hearth's range (horizontally) still share, per rank. */
    public static final double OPEN_HOUSE_REACH_PER_RANK = 8.0;
    /** Heart of the Home: a Feast counts as a House Proud for everyone at it, this long. */
    public static final int HEART_OF_HOME_TICKS = 600;

    /** Homeward's channel, 10 s. */
    public static final int HOMEWARD_TICKS = 200;
    /** Moving further than this from where the channel started breaks it, in blocks. */
    public static final double HOMEWARD_MOVE_TOLERANCE = 0.5;
    /** One in-game day, the Homeward cooldown. */
    public static final long TICKS_PER_DAY = 24_000L;

    /** Scaffolder: extra blocks of safe fall per rank (ranks 1, 2, 3). */
    private static final int[] SCAFFOLDER_BLOCKS = {0, 3, 5, 8};
    /** Warm Welcome: Warmth fills this much faster per rank. */
    public static final double WARM_WELCOME_PER_RANK = 0.10;
    /** Storyteller: this many more Tales banked per rank. */
    public static final double STORYTELLER_PER_RANK = 0.10;
    /** Night Watch: the extra warning, one in-game hour before the siege's dusk. */
    public static final long NIGHT_WATCH_EARLY_TICKS = 1000L;
    /** The day time a due siege starts at (the transition into night). */
    public static final long DUSK = 13_000L;

    // ---- Talent and synergy tags (tools/talents_spec.py) --------------------------------------------

    public static final String TIDY_HOME = "tidy_home";
    public static final String SCAFFOLDER = "scaffolder";
    public static final String LONG_PULL = "long_pull";
    public static final String WARM_WELCOME = "warm_welcome";
    public static final String STORYTELLER = "storyteller";
    public static final String OPEN_HOUSE = "open_house";
    public static final String NIGHT_WATCH = "night_watch";
    public static final String HOMEWARD = "homeward";
    /** The synergy with Social's capstone. */
    public static final String HEART_OF_HOME = "heart_of_home";

    private HomebuilderMath() {
    }

    // ---- XP ---------------------------------------------------------------------------------------

    /**
     * Homebuilder's base XP from another skill's grant at home: the grant before any player
     * multiplier, at that skill's own config rate, times the share. An area tool's extra block passes
     * its reduced share in {@code aoeShare} (1.0 for an ordinary grant).
     */
    public static double share(double baseAmount, double skillRate, double aoeShare, double share) {
        if (!(baseAmount > 0) || !(skillRate > 0) || !(aoeShare > 0) || !(share > 0)) {
            return 0.0;
        }
        double xp = baseAmount * skillRate * aoeShare * share;
        return Double.isFinite(xp) ? xp : 0.0;
    }

    /** The XP for lighting (stage 1) or sealing a stage. */
    public static double stageXp(int stage, double perStage) {
        return stage < 1 || !(perStage > 0) ? 0.0 : perStage * Math.min(stage, 6);
    }

    /**
     * A defended siege, split by kills: each killer gets {@code total} times their share of the
     * kills, and everyone present (killers included) at least {@code minShare} of {@code total}.
     * Players who killed but left range are still paid for their kills. No kills at all: everyone
     * present gets an equal split, never less than the minimum.
     */
    public static Map<UUID, Double> siegeSplit(Map<UUID, Integer> kills, Collection<UUID> present, double total,
            double minShare) {
        Map<UUID, Double> paid = new LinkedHashMap<>();
        if (!(total > 0)) {
            return paid;
        }
        int sum = 0;
        for (int n : kills.values()) {
            sum += Math.max(0, n);
        }
        List<UUID> everyone = new ArrayList<>(present);
        for (UUID killer : kills.keySet()) {
            if (!everyone.contains(killer) && kills.get(killer) != null && kills.get(killer) > 0) {
                everyone.add(killer);
            }
        }
        double floor = total * Math.max(0.0, minShare);
        for (UUID player : everyone) {
            double share;
            if (sum > 0) {
                Integer n = kills.get(player);
                share = total * Math.max(0, n == null ? 0 : n) / sum;
            } else {
                share = everyone.isEmpty() ? 0.0 : total / everyone.size();
            }
            paid.put(player, Math.max(floor, share));
        }
        return paid;
    }

    // ---- The passive ------------------------------------------------------------------------------

    /** Movement speed at home, a multiplier part (0.20 is +20%). */
    public static double speed(double bonus) {
        return Math.max(0.0, bonus);
    }

    /** Extra block reach at home, in blocks. */
    public static double reach(double bonus) {
        return Math.max(0.0, bonus) * REACH_PER_BONUS;
    }

    /** The magnet radius in blocks: 1 + p x 25, plus Long Pull. */
    public static double magnetRadius(double bonus, int longPull) {
        return MAGNET_BASE + Math.max(0.0, bonus) * MAGNET_PER_BONUS + LONG_PULL_PER_RANK * Math.max(0, longPull);
    }

    /** The passive a player uses at home: their own, doubled by House Proud, or a friend's shared one if larger. */
    public static double effectiveBonus(double own, boolean houseProud, double shared) {
        double mine = Math.max(0.0, own) * (houseProud ? HOUSE_PROUD_FACTOR : 1.0);
        return Math.max(mine, Math.max(0.0, shared));
    }

    /**
     * The velocity that carries a pulled item towards the player: straight at them, a little hop up
     * so it arcs in over the floor, never faster than {@code speed}. {@code dx, dy, dz} run from the
     * item to the player's waist.
     */
    public static double[] pull(double dx, double dy, double dz, boolean fast, boolean onGround) {
        double distance = Math.sqrt(dx * dx + dy * dy + dz * dz);
        if (!(distance > 1.0e-6)) {
            return new double[] {0.0, 0.0, 0.0};
        }
        double speed = MAGNET_SPEED * (fast ? 2.0 : 1.0);
        // Slow down near the player so it does not overshoot; vanilla's own pickup takes it there.
        double step = Math.min(speed, distance / 4.0);
        double vx = dx / distance * step;
        double vz = dz / distance * step;
        double vy = dy / distance * step + (onGround ? MAGNET_HOP : 0.04);
        return new double[] {vx, vy, vz};
    }

    // ---- Scaffolder, Warm Welcome, Storyteller ------------------------------------------------------

    /** Scaffolder: extra blocks a fall at home may be before it hurts. */
    public static int scaffolderBlocks(int rank) {
        return SCAFFOLDER_BLOCKS[Math.max(0, Math.min(3, rank))];
    }

    /**
     * Fall damage at home with Scaffolder: each extra safe block takes one point off (a fall's
     * damage is one per block past the safe distance), never below 0.
     */
    public static float scaffolderDamage(float damage, int rank) {
        return Math.max(0f, damage - scaffolderBlocks(rank));
    }

    /** Warm Welcome: how much faster Warmth fills, as a fraction (0.30 at rank 3). */
    public static double warmWelcome(int rank) {
        return WARM_WELCOME_PER_RANK * Math.max(0, Math.min(3, rank));
    }

    /** Storyteller: how many more Tales are banked, as a fraction (0.30 at rank 3). */
    public static double storyteller(int rank) {
        return STORYTELLER_PER_RANK * Math.max(0, Math.min(3, rank));
    }

    // ---- Night Watch ------------------------------------------------------------------------------

    /**
     * Whether a Night Watch player hears the early horn now: the siege is due (its warning day has
     * passed), and the day time is within the hour before the dusk it starts at.
     *
     * @param now     game time
     * @param due     the siege's due game time
     * @param dayTime the level's day time (any day; only the time within the day counts)
     */
    public static boolean nightWatchWarns(long now, long due, long dayTime) {
        if (due == Long.MIN_VALUE || now + NIGHT_WATCH_EARLY_TICKS < due) {
            return false;
        }
        long inDay = Math.floorMod(dayTime, TICKS_PER_DAY);
        return inDay >= DUSK - NIGHT_WATCH_EARLY_TICKS && inDay < DUSK;
    }

    // ---- Homeward ---------------------------------------------------------------------------------

    /** The in-game day number Homeward's once-a-day rule counts in (sleeping moves it on). */
    public static long day(long dayTime) {
        return Math.floorDiv(dayTime, TICKS_PER_DAY);
    }

    /** Whether Homeward is ready: not used yet on this in-game day. */
    public static boolean homewardReady(long lastDay, long today) {
        return lastDay == Long.MIN_VALUE || today != lastDay;
    }

    /**
     * As {@link #homewardReady(long, long)}, and also ready once a full day of game time has passed
     * since the last use ({@code lastAt}, {@code Long.MIN_VALUE} when unknown). A world whose clock
     * stands still ({@code doDaylightCycle false}) never reaches a new day number, and Homeward
     * would otherwise be used once and never again.
     */
    public static boolean homewardReady(long lastDay, long today, long lastAt, long now) {
        if (homewardReady(lastDay, today)) {
            return true;
        }
        return lastAt != Long.MIN_VALUE && now - lastAt >= TICKS_PER_DAY;
    }

    /** How a channel step ended. */
    public enum Step { RUNNING, ARRIVED, MOVED, HURT }

    /** A Homeward channel in progress: where it started and when it lands. */
    public static final class Channel {
        private final double x;
        private final double y;
        private final double z;
        private final long start;
        private final int ticks;

        public Channel(double x, double y, double z, long start, int ticks) {
            this.x = x;
            this.y = y;
            this.z = z;
            this.start = start;
            this.ticks = Math.max(1, ticks);
        }

        public long start() {
            return start;
        }

        public int ticks() {
            return ticks;
        }

        /** Fraction done at {@code now}, 0 to 1. */
        public double progress(long now) {
            return Math.max(0.0, Math.min(1.0, (now - start) / (double) ticks));
        }

        /** One check: damage or moving away breaks it, otherwise it lands when the time is up. */
        public Step step(long now, double px, double py, double pz, boolean hurt) {
            if (hurt) {
                return Step.HURT;
            }
            double dx = px - x;
            double dy = py - y;
            double dz = pz - z;
            if (dx * dx + dz * dz > HOMEWARD_MOVE_TOLERANCE * HOMEWARD_MOVE_TOLERANCE
                    || Math.abs(dy) > 1.0) {
                return Step.MOVED;
            }
            return now - start >= ticks ? Step.ARRIVED : Step.RUNNING;
        }
    }

    // ---- Ranks ------------------------------------------------------------------------------------

    /** Ranks on the Homebuilder nodes carrying {@code special}; 0 when none. */
    public static int rank(PlayerSkills skills, String special) {
        int ranks = 0;
        for (Talent talent : Talents.of(Skill.HOMEBUILDER)) {
            if (special.equals(talent.special())) {
                ranks += skills.rank(talent);
            }
        }
        return ranks;
    }

    /** Heart of the Home (Open House + Social's capstone) is awake for this player. */
    public static boolean heartOfHome(PlayerSkills skills) {
        return Synergies.hasSpecial(skills, HEART_OF_HOME);
    }
}
