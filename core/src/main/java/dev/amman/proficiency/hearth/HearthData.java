package dev.amman.proficiency.hearth;

import dev.amman.proficiency.skill.Skill;

import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Every Hearth number, as datapack data: {@code data/<ns>/proficiency/hearth/*.json}, merged by
 * {@link HearthDataLoader} like the XP sources (the mod's own {@code default.json} first, later
 * files override, {@code "replace": true} drops what came before). The shipped defaults are the
 * numbers of docs/HEARTH-SPEC.md, "Data"; {@link #DEFAULTS} holds the same numbers in code, so the
 * mod works with no file at all and a test holds the file and the code together.
 *
 * <p>Immutable. The live copy is {@link Hearths#data()}; a reload swaps it whole. Lists stand in for
 * arrays so two copies compare by value. Tier-indexed lists have six entries, tier I first.
 */
public record HearthData(
        Map<HearthPool, PoolSpec> pools,
        List<Double> thresholds,
        double mirrorShare,
        double combatShare,
        double hourlyCap,
        Map<Integer, Integer> comfortForTier,
        Decay decay,
        Warmth warmth,
        double combatSeconds,
        Tales tales,
        Provisioned provisioned,
        CharacterRules character,
        Chronicle chronicle,
        Feast feast,
        Bell bell,
        List<Stage> stages,
        Comfort comfort,
        Siege siege) {

    /** Six tiers (I..VI), six stages, six boon values per pool. */
    public static final int TIERS = 6;

    /** One game hour in ticks, the unit of decay, the hourly cap and siege play time. */
    public static final long TICKS_PER_HOUR = 72_000L;

    /** One in-game day. */
    public static final long TICKS_PER_DAY = 24_000L;

    /** What feeds a pool: a skill id, a skill category, Tales, or Feasts. */
    public record Feed(Kind kind, String id) {

        public enum Kind { SKILL, CATEGORY, TALES, FEAST }

        public static final String TALES_TOKEN = "#tales";
        public static final String FEAST_TOKEN = "#feast";
        public static final String CATEGORY_PREFIX = "#category:";

        public boolean matches(Skill skill) {
            return switch (kind) {
                case SKILL -> skill.id().equals(id);
                case CATEGORY -> skill.category().id().equals(id);
                default -> false;
            };
        }

        /** As written in a data file. */
        public String token() {
            return switch (kind) {
                case SKILL -> id;
                case CATEGORY -> CATEGORY_PREFIX + id;
                case TALES -> TALES_TOKEN;
                case FEAST -> FEAST_TOKEN;
            };
        }

        public static Feed skill(String id) {
            return new Feed(Kind.SKILL, id);
        }

        public static Feed category(String id) {
            return new Feed(Kind.CATEGORY, id);
        }

        public static Feed tales() {
            return new Feed(Kind.TALES, "");
        }

        public static Feed feast() {
            return new Feed(Kind.FEAST, "");
        }
    }

    /** How Comfort caps a pool's tier: by the {@code comfortForTier} table, or {@code floor(comfort / 2)}. */
    public enum ComfortCap {
        TIERS("tiers"), HALF("half");

        private final String id;

        ComfortCap(String id) {
            this.id = id;
        }

        public String id() {
            return id;
        }

        public static ComfortCap byId(String id) {
            for (ComfortCap cap : values()) {
                if (cap.id.equals(id)) {
                    return cap;
                }
            }
            return null;
        }
    }

    /**
     * One pool. {@code values} are the boon numbers per tier (seconds per heart for regen,
     * fractions otherwise; empty for a pool whose boon reads other lists). {@code holdSeconds} above
     * 0 holds the pool's mirrored charge that long (Masonry and Decorating). {@code refund},
     * {@code warmthSeconds} and {@code provisionedMinutes} are empty unless the boon uses them.
     */
    public record PoolSpec(
            HearthPool pool,
            List<Feed> feeds,
            BoonType boon,
            List<Double> values,
            int holdSeconds,
            double absorptionPerTier,
            ComfortCap comfortCap,
            List<Double> refund,
            List<Double> warmthSeconds,
            List<Double> provisionedMinutes) {

        /** The boon value at a tier, 0 at tier 0 or when the list has no entry for it. */
        public double value(int tier) {
            return at(values, tier);
        }

        public double refundAt(int tier) {
            return at(refund, tier);
        }

        static double at(List<Double> list, int tier) {
            return tier >= 1 && tier <= list.size() ? list.get(tier - 1) : 0.0;
        }

        public boolean feeds(Skill skill) {
            for (Feed feed : feeds) {
                if (feed.matches(skill)) {
                    return true;
                }
            }
            return false;
        }

        public boolean hasFeed(Feed.Kind kind) {
            for (Feed feed : feeds) {
                if (feed.kind() == kind) {
                    return true;
                }
            }
            return false;
        }
    }

    /** Lazy decay: {@code perHour} of the charge per game hour since the last visit, tier floor held for {@code graceHours}. */
    public record Decay(double perHour, double graceHours) {
    }

    /** Warmth: 0 to 100% in {@code riseSeconds} in range, back to 0 in {@code drainSeconds} outside. */
    public record Warmth(double riseSeconds, double drainSeconds) {
    }

    /** Tales: {@code share} of the XP of these feeds earned away is banked; {@code feedAll} splits the payout over every pool. */
    public record Tales(double share, List<Feed> feeds, boolean feedAll) {

        public boolean banks(Skill skill) {
            for (Feed feed : feeds) {
                if (feed.matches(skill)) {
                    return true;
                }
            }
            return false;
        }
    }

    /** Provisioned: how long the Hearty value is carried after leaving at full Warmth, with no Homecoming. */
    public record Provisioned(double minutes) {
    }

    /** Character: distinct placed block ids, out of {@code max}; {@code forStage} is the Character a stage needs. */
    public record CharacterRules(int max, Map<Integer, Integer> forStage) {

        public int forStage(int stage) {
            Integer need = forStage.get(stage);
            return need == null ? 0 : need;
        }
    }

    /** The Hearth Chronicle: the flat write bonus (share of the Tales paid) and the rated burst per star. */
    public record Chronicle(double writeBonus, double perStar) {
    }

    /**
     * Feast: eaters within {@code windowSeconds} of each other (the dinner bell opens a
     * {@code dinnerBellSeconds} window), {@code chargePerExtraEater} Homecoming charge each; the
     * dinner bell answers a meal eaten within {@code dinnerTriggerSeconds}.
     */
    public record Feast(double windowSeconds, double dinnerBellSeconds, double chargePerExtraEater,
            double dinnerTriggerSeconds) {
    }

    /** The stage-V context bell's timings, all in seconds. */
    public record Bell(double alarmGlowSeconds, double alarmCooldown, double comeHomeSeconds,
            double comeHomeCooldown, double homewardChannelSeconds) {
    }

    /**
     * One stage: the item taken as its offering. {@code build} is legacy, read and ignored: what a stage is made
     * of is the stage designs (docs/HEARTH-DESIGNS-SPEC.md), not a block tag.
     */
    public record Stage(String build, String offering) {
    }

    /** Comfort: the kind ids (12 shipped), the cooldown between event-driven scans and the safety rescan. */
    public record Comfort(List<String> kinds, double scanCooldownSeconds, double rescanSeconds) {

        public int max() {
            return kinds.size();
        }
    }

    /**
     * Sieges. Times are play hours (server time with a non-AFK player online), in-game days, seconds
     * or ticks as named. {@code baseMobs} is keyed by ceiling (3..6); {@code mobs} lists what each
     * ceiling adds to the mix (a higher ceiling also fields every lower one's mobs).
     *
     * <p>Added by the Sieges lane (not in the spec's block, all <i>tune</i>): the douse meter
     * ({@code douseMax}, filled by {@code dousePerMobSecond} for each siege mob within
     * {@code douseRadius} blocks of the Hearth), {@code abandonGraceSeconds} (how long nobody may be
     * in range before a running siege counts as abandoned), {@code spawnClearance} (the blocks a
     * spawn keeps from every player when it can) and {@code onePerWave} (mob ids a wave fields at
     * most once: the spec's "VI + an evoker").
     */
    public record Siege(
            boolean requiresFirstHorn,
            double cooldownPlayHours,
            double cooldownDays,
            double rampHours,
            double pMax,
            double pityHours,
            double hornCooldownPlayHours,
            int postponeNights,
            int waves,
            Map<Integer, Integer> baseMobs,
            double perExtraPlayer,
            double burst,
            double streakBonus,
            int streakCap,
            double dousedLoss,
            long warningTicks,
            double hornMinDelaySeconds,
            double nextWaveAt,
            double rollSeconds,
            Map<Integer, List<String>> mobs,
            double douseRadius,
            double dousePerMobSecond,
            double douseMax,
            double abandonGraceSeconds,
            double spawnClearance,
            List<String> onePerWave) {

        public int baseMobs(int ceiling) {
            Integer count = baseMobs.get(ceiling);
            return count == null ? 0 : count;
        }
    }

    // ---- Lookups --------------------------------------------------------------------------------

    public PoolSpec pool(HearthPool pool) {
        return pools.get(pool);
    }

    /**
     * The pool a skill's mirrored XP feeds, or null for none (Movement, Expedition, Survival,
     * Mastery and Homebuilder by default). A pool naming the skill itself beats a pool naming its
     * category; among equals the first pool in {@link HearthPool} order wins.
     */
    public HearthPool poolFor(Skill skill) {
        for (HearthPool pool : HearthPool.VALUES) {
            PoolSpec spec = pools.get(pool);
            if (spec == null) {
                continue;
            }
            for (Feed feed : spec.feeds()) {
                if (feed.kind() == Feed.Kind.SKILL && feed.matches(skill)) {
                    return pool;
                }
            }
        }
        for (HearthPool pool : HearthPool.VALUES) {
            PoolSpec spec = pools.get(pool);
            if (spec == null) {
                continue;
            }
            for (Feed feed : spec.feeds()) {
                if (feed.kind() == Feed.Kind.CATEGORY && feed.matches(skill)) {
                    return pool;
                }
            }
        }
        return null;
    }

    /** The pool Tales pay into: the one listing {@code #tales}, else Homecoming. */
    public HearthPool talesPool() {
        return poolWithFeed(Feed.Kind.TALES, HearthPool.HOMECOMING);
    }

    /** The pool a Feast charges: the one listing {@code #feast}, else the Tales pool. */
    public HearthPool feastPool() {
        return poolWithFeed(Feed.Kind.FEAST, talesPool());
    }

    private HearthPool poolWithFeed(Feed.Kind kind, HearthPool fallback) {
        for (HearthPool pool : HearthPool.VALUES) {
            PoolSpec spec = pools.get(pool);
            if (spec != null && spec.hasFeed(kind)) {
                return pool;
            }
        }
        return fallback;
    }

    /** The first pool giving this boon, or null. */
    public HearthPool poolWithBoon(BoonType boon) {
        for (HearthPool pool : HearthPool.VALUES) {
            PoolSpec spec = pools.get(pool);
            if (spec != null && spec.boon() == boon) {
                return pool;
            }
        }
        return null;
    }

    /** The charge a tier starts at; 0 for tier 0. */
    public double threshold(int tier) {
        if (tier <= 0) {
            return 0.0;
        }
        return thresholds.get(Math.min(tier, thresholds.size()) - 1);
    }

    /** The Comfort a tier needs under the {@code comfortForTier} table; 0 when the table names none. */
    public int comfortForTier(int tier) {
        Integer need = comfortForTier.get(tier);
        return need == null ? 0 : need;
    }

    // ---- The shipped defaults (docs/HEARTH-SPEC.md, "Data") -------------------------------------

    private static List<Double> list(double... values) {
        List<Double> out = new ArrayList<>(values.length);
        for (double value : values) {
            out.add(value);
        }
        return Collections.unmodifiableList(out);
    }

    private static Map<Integer, Integer> tiers(int... keyValues) {
        Map<Integer, Integer> out = new LinkedHashMap<>();
        for (int i = 0; i + 1 < keyValues.length; i += 2) {
            out.put(keyValues[i], keyValues[i + 1]);
        }
        return Collections.unmodifiableMap(out);
    }

    /** The comfort kinds of docs/HEARTH-SPEC.md, "Comfort", in the order the panel lists them. */
    public static final List<String> DEFAULT_COMFORT_KINDS = List.of("bed", "seat", "carpet", "banner",
            "fire", "light", "plant", "art", "books", "music", "storage", "trophy");

    public static final HearthData DEFAULTS = defaults();

    private static HearthData defaults() {
        Map<HearthPool, PoolSpec> pools = new EnumMap<>(HearthPool.class);
        pools.put(HearthPool.HEARTY, new PoolSpec(HearthPool.HEARTY, List.of(Feed.category("gathering")),
                BoonType.HUNGER_SLOW, list(0.08, 0.15, 0.22, 0.28, 0.34, 0.40), 0, 0.0, ComfortCap.TIERS,
                List.of(), List.of(), List.of()));
        pools.put(HearthPool.REGEN, new PoolSpec(HearthPool.REGEN,
                List.of(Feed.skill("decorating"), Feed.skill("farming")),
                BoonType.REGEN_OUT_OF_COMBAT, list(14, 12, 10, 8, 6, 4), 600, 0.10, ComfortCap.HALF,
                List.of(), List.of(), List.of()));
        pools.put(HearthPool.FORTIFIED, new PoolSpec(HearthPool.FORTIFIED,
                List.of(Feed.skill("masonry"), Feed.skill("blocking"), Feed.skill("endurance")),
                BoonType.MOB_DAMAGE_REDUCTION, list(0.04, 0.07, 0.10, 0.13, 0.16, 0.20), 600, 0.0,
                ComfortCap.TIERS, List.of(), List.of(), List.of()));
        pools.put(HearthPool.WORKSHOP, new PoolSpec(HearthPool.WORKSHOP, List.of(Feed.category("crafting")),
                BoonType.STATION_SPEED, list(0.08, 0.15, 0.22, 0.28, 0.34, 0.40), 0, 0.0, ComfortCap.TIERS,
                list(0.02, 0.03, 0.04, 0.05, 0.06, 0.08), List.of(), List.of()));
        pools.put(HearthPool.HOMECOMING, new PoolSpec(HearthPool.HOMECOMING,
                List.of(Feed.skill("social"), Feed.tales()), BoonType.READINESS, List.of(), 0, 0.0,
                ComfortCap.HALF, List.of(), list(55, 50, 45, 40, 35, 30), list(16, 18, 20, 22, 25, 30)));
        Map<Integer, List<String>> mobs = new LinkedHashMap<>();
        mobs.put(3, List.of("minecraft:zombie", "minecraft:skeleton", "minecraft:spider"));
        mobs.put(4, List.of("minecraft:pillager"));
        mobs.put(5, List.of("minecraft:vindicator", "minecraft:witch"));
        mobs.put(6, List.of("minecraft:evoker"));
        // Key 7 is siege level V, called only by the Horn of the Warhost (SiegeRules.levelKey).
        mobs.put(7, List.of("minecraft:ravager"));
        return new HearthData(
                Collections.unmodifiableMap(pools),
                list(200, 600, 1400, 2800, 5000, 8000),
                0.10,
                0.03,
                300,
                tiers(5, 8, 6, 10),
                new Decay(0.02, 24),
                new Warmth(60, 30),
                5,
                new Tales(0.05, List.of(Feed.category("movement"), Feed.category("expedition")), false),
                new Provisioned(15),
                new CharacterRules(50, tiers(3, 8, 4, 14, 5, 24, 6, 40)),
                new Chronicle(0.25, 0.05),
                new Feast(30, 120, 20, 60),
                new Bell(10, 30, 60, 300, 5),
                List.of(new Stage("proficiency:hearth/stage/firepit", "minecraft:flint_and_steel"),
                        new Stage("proficiency:hearth/stage/hearth", "minecraft:copper_block"),
                        new Stage("proficiency:hearth/stage/homestead", "minecraft:iron_block"),
                        new Stage("proficiency:hearth/stage/hall", "minecraft:gold_block"),
                        new Stage("proficiency:hearth/stage/gathering_place", "minecraft:diamond_block"),
                        new Stage("proficiency:hearth/stage/heart_of_the_home", "minecraft:heart_of_the_sea")),
                new Comfort(DEFAULT_COMFORT_KINDS, 30, 300),
                new Siege(true, 4, 2, 6, 0.02, 16, 1, 2, 3, tiers(3, 4, 4, 6, 5, 9, 6, 12, 7, 15), 0.5, 0.10, 0.05, 5,
                        0.10, TICKS_PER_DAY, 120, 0.5, 60, Collections.unmodifiableMap(mobs),
                        3.0, 1.0, 100, 10, 6, List.of("minecraft:evoker", "minecraft:ravager")));
    }
}
