package dev.amman.proficiency.skill;

import java.util.Set;

/**
 * The numbers behind Husbandry (the 36th skill). Pure: no Minecraft types, so every rule here is
 * unit tested. {@code HusbandryEvents} watches the world and pays; this class only decides.
 *
 * <p>Husbandry is looking after animals. XP sources, all survival only and all through a real
 * player (a dispenser or a machine's fake player is paid nothing):
 * <ul>
 * <li>breeding two animals by feeding them, once per pair per cooldown, less in a crowded pen;</li>
 * <li>taming (a wolf, a cat, a parrot, a horse), with the usual first-time bonus for each species;</li>
 * <li>shearing a sheep or a mooshroom that had regrown, milking a cow or a goat, brushing a scute
 * off an armadillo, and taking honey from a hive;</li>
 * <li>feeding a baby so it grows up faster.</li>
 * </ul>
 * The passive is faster growing for the babies you breed and hardier pets.
 */
public final class HusbandryMath {

    private HusbandryMath() {
    }

    // ---- XP ------------------------------------------------------------------------------------

    /** What an animal job is, for its payout and its cooldown. */
    public enum Job {
        BREED(10.0, 6000L),
        TAME(20.0, 0L),
        SHEAR(5.0, 0L),
        MILK(4.0, 2400L),
        BRUSH(6.0, 6000L),
        FEED(3.0, 1200L),
        HONEY(5.0, 0L);

        private final double xp;
        private final long cooldown;

        Job(double xp, long cooldown) {
            this.xp = xp;
            this.cooldown = cooldown;
        }

        /** Base XP for one job, before the species' worth and the pen's density. */
        public double defaultXp() {
            return xp;
        }

        /** The same animal (or pair) pays this job at most once in this many ticks. */
        public long cooldownTicks() {
            return cooldown;
        }
    }

    /** Species that are harder to keep: they pay half again. Everything else is 1.0. */
    private static final Set<String> RARE = Set.of(
            "minecraft:panda", "minecraft:axolotl", "minecraft:fox", "minecraft:ocelot", "minecraft:cat",
            "minecraft:wolf", "minecraft:horse", "minecraft:donkey", "minecraft:mule", "minecraft:llama",
            "minecraft:trader_llama", "minecraft:goat", "minecraft:frog", "minecraft:turtle",
            "minecraft:armadillo", "minecraft:camel", "minecraft:hoglin", "minecraft:strider",
            "minecraft:parrot", "minecraft:bee", "minecraft:sniffer", "minecraft:mooshroom");

    public static final double RARE_WORTH = 1.5;

    /** What a species pays, as a multiplier on the job's XP. */
    public static double speciesWorth(String entityTypeId) {
        return entityTypeId != null && RARE.contains(entityTypeId) ? RARE_WORTH : 1.0;
    }

    // ---- Anti-farm -----------------------------------------------------------------------------

    /** Animals within this many blocks of the job count as its pen. */
    public static final double PEN_RADIUS = 8.0;
    /** Up to this many animals in the pen pay in full. */
    public static final int PEN_FREE = 8;
    /** A crowded pen never pays less than this share. */
    public static final double PEN_FLOOR = 0.10;

    /**
     * The pen's density factor: 1 up to {@link #PEN_FREE} animals, then {@code PEN_FREE / animals},
     * never below {@link #PEN_FLOOR}. A farm of 40 animals pays a fifth per job, a 100-animal
     * breeder 10%.
     */
    public static double penFactor(int animalsInPen) {
        if (animalsInPen <= PEN_FREE) {
            return 1.0;
        }
        return Math.max(PEN_FLOOR, (double) PEN_FREE / animalsInPen);
    }

    /** The spot rule: payouts this close to each other count as one spot. */
    public static final double SPOT_RADIUS = 12.0;
    /** The spot rule: payouts are remembered this long (5 min). */
    public static final long SPOT_WINDOW_TICKS = 6000L;
    /** The spot rule: at most this many Husbandry payouts in one spot inside the window. */
    public static final int SPOT_LIMIT = 12;

    public static boolean spotAllows(int recentNear) {
        return recentNear < SPOT_LIMIT;
    }

    public static boolean inSpotWindow(long paidAt, long now) {
        return paidAt >= 0 && now >= paidAt && now - paidAt < SPOT_WINDOW_TICKS;
    }

    /** Taming: at most this many payouts in this long (30 min), so no taming farm. */
    public static final int TAME_LIMIT = 8;
    public static final long TAME_WINDOW_TICKS = 36000L;

    /** A job is paid again only once its cooldown has passed. */
    public static boolean cooledDown(Job job, long lastPaid, long now) {
        return lastPaid < 0 || now < lastPaid || now - lastPaid >= job.cooldownTicks();
    }

    /** A taming watch gives up this long after the first click (3 min: a horse takes a few rides). */
    public static final long TAME_WATCH_TICKS = 3600L;

    /** The XP of one job. */
    public static double payout(Job job, String entityTypeId, int animalsInPen) {
        return job.defaultXp() * speciesWorth(entityTypeId) * penFactor(animalsInPen);
    }

    // ---- The passive ---------------------------------------------------------------------------

    /** A baby's usual age at birth, in ticks. */
    public static final int BABY_AGE = -24000;

    /** Growth speed: one plus the passive. */
    public static double growthMultiplier(double bonus) {
        return bonus > 0 ? 1.0 + bonus : 1.0;
    }

    /** Hardier pets: one plus the passive. */
    public static double petHealthMultiplier(double bonus) {
        return bonus > 0 ? 1.0 + bonus : 1.0;
    }

    /**
     * The age a bred baby starts at: the usual (a negative number of ticks to wait) shortened by
     * the passive, and halved again for Herd Master. Never past -1, so it is still a baby.
     */
    public static int bredBabyAge(int usualAge, double bonus, boolean headStart) {
        if (usualAge >= 0) {
            return usualAge;
        }
        double age = usualAge / growthMultiplier(bonus);
        if (headStart) {
            age *= HEAD_START;
        }
        return Math.min(-1, (int) Math.round(age));
    }

    /** Herd Master: babies are born with this share of the wait still to go. */
    public static final double HEAD_START = 0.5;

    // ---- Twins ---------------------------------------------------------------------------------

    /** Big Litter: a third baby, this chance per rank. */
    public static final double TRIPLETS_PER_RANK = 0.10;

    public static double tripletsChance(int rank) {
        return Math.max(0, rank) * TRIPLETS_PER_RANK;
    }

    // ---- The tree ------------------------------------------------------------------------------

    /** Gentle Hands: mounting an untamed horse adds this much temper per rank (of a max of 100). */
    public static final int GENTLE_TEMPER_PER_RANK = 6;

    public static int gentleTemper(int rank) {
        return Math.max(0, rank) * GENTLE_TEMPER_PER_RANK;
    }

    /** Wool Gatherer: this many extra wool per rank when you shear. */
    public static int extraWool(int rank) {
        return Math.max(0, rank);
    }

    /** Fresh Pail: this chance per rank of a second bucket of milk. */
    public static final double PAIL_PER_RANK = 0.20;

    public static double pailChance(int rank) {
        return Math.max(0, rank) * PAIL_PER_RANK;
    }

    /**
     * Apprentice Wolf: a tamed wolf adds this share per rank of its owner's best weapon passive to
     * its own damage ({@code bonus} is the weapon skill's passive, e.g. 0.8 for swords at 100).
     * At rank 3 the wolf hits as hard as its owner's weapon training.
     */
    public static double wolfLearns(double ownerWeaponBonus, int rank) {
        if (!(ownerWeaponBonus > 0) || rank <= 0) {
            return 1.0;
        }
        return 1.0 + ownerWeaponBonus * Math.min(rank, 3) / 3.0;
    }

    /** Shared Strength: your pet hits this much harder per rank on a mob you hit in the last 5 s. */
    public static final double SHARED_PER_RANK = 0.10;
    public static final long SHARED_WINDOW_TICKS = 100L;

    public static double sharedStrength(int rank) {
        return 1.0 + Math.max(0, rank) * SHARED_PER_RANK;
    }

    /** Faithful Friend: a pet that would die at a lit Hearth survives, at this share of its health... */
    public static final double FAITHFUL_HEALTH = 0.25;
    /** ...once in this long per owner (10 min). */
    public static final long FAITHFUL_COOLDOWN_TICKS = 12000L;

    public static boolean faithfulReady(long lastUsed, long now) {
        return lastUsed < 0 || now < lastUsed || now - lastUsed >= FAITHFUL_COOLDOWN_TICKS;
    }

    // ---- The ability ---------------------------------------------------------------------------

    /** Pasture Blessing: pets within this many blocks of you get Regeneration for the duration. */
    public static final double BLESSING_RADIUS = 24.0;
}
