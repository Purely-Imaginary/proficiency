package dev.amman.proficiency.config;

import dev.amman.proficiency.skill.Skill;
import net.minecraftforge.common.ForgeConfigSpec;

import java.util.EnumMap;
import java.util.Map;

/**
 * Server-side config. Skills are server-authoritative, so every number that decides how fast you
 * level or how much a level is worth lives here and the client is only ever told the result.
 */
public final class ProficiencyConfig {

    public static final ForgeConfigSpec SPEC;

    public static final ForgeConfigSpec.DoubleValue XP_MULTIPLIER;
    public static final ForgeConfigSpec.DoubleValue CURVE_FLOOR;
    public static final ForgeConfigSpec.DoubleValue CURVE_BASE;
    public static final ForgeConfigSpec.DoubleValue CURVE_EXPONENT;
    public static final ForgeConfigSpec.BooleanValue ENABLE_PROCS;
    public static final ForgeConfigSpec.IntValue PROC_UNLOCK_LEVEL;
    public static final ForgeConfigSpec.DoubleValue PROC_FLOOR;
    public static final ForgeConfigSpec.BooleanValue SIGNATURE_NEEDS_ROOT;
    public static final ForgeConfigSpec.BooleanValue ANNOUNCE_MILESTONES;
    public static final ForgeConfigSpec.IntValue MILESTONE_INTERVAL;
    public static final ForgeConfigSpec.IntValue MILESTONE_MIN_LEVEL;
    public static final ForgeConfigSpec.DoubleValue MASTERY_STAR_FACTOR;
    public static final ForgeConfigSpec.IntValue MASTERY_MAX_STARS;
    public static final ForgeConfigSpec.DoubleValue BOSS_HEALTH;
    public static final ForgeConfigSpec.DoubleValue FIRST_TIME_XP;
    public static final ForgeConfigSpec.IntValue FIRST_TIME_BUILD_PER_DAY;
    public static final ForgeConfigSpec.BooleanValue FIRST_TIME_TIER_SCALING;
    public static final ForgeConfigSpec.BooleanValue PLACED_BLOCKS_PAY_XP;
    public static final ForgeConfigSpec.DoubleValue SPAWNER_MOB_XP;
    public static final ForgeConfigSpec.DoubleValue AOE_XP_SHARE;
    public static final ForgeConfigSpec.DoubleValue RANGED_XP_PER_DOUBLING;
    public static final ForgeConfigSpec.DoubleValue ARTIFICIAL_MOB_XP;
    public static final ForgeConfigSpec.DoubleValue KILL_BONUS_BASE;
    public static final ForgeConfigSpec.DoubleValue KILL_BONUS_HEALTH_DIVISOR;
    public static final ForgeConfigSpec.DoubleValue KILL_BONUS_CAP;
    public static final ForgeConfigSpec.DoubleValue KILL_BONUS_BOSS_MULTIPLIER;
    public static final ForgeConfigSpec.IntValue STRUCTURE_XP;
    public static final ForgeConfigSpec.IntValue GRAND_STRUCTURE_XP;
    public static final ForgeConfigSpec.IntValue STRUCTURE_ENTRY_COOLDOWN_MINUTES;
    public static final ForgeConfigSpec.BooleanValue ANNOUNCE_GRAND_DISCOVERIES;
    public static final ForgeConfigSpec.ConfigValue<java.util.List<? extends String>> XP_FEED_PLAYERS;
    public static final ForgeConfigSpec.BooleanValue TELEMETRY_ENABLED;
    public static final ForgeConfigSpec.BooleanValue HAUL_ENABLED;
    public static final ForgeConfigSpec.IntValue TELEMETRY_RETENTION_DAYS;
    public static final ForgeConfigSpec.BooleanValue EXPORT_ENABLED;
    public static final ForgeConfigSpec.IntValue EXPORT_INTERVAL_SECONDS;

    public static final ForgeConfigSpec.DoubleValue COMPANY_RADIUS;
    public static final ForgeConfigSpec.BooleanValue JADE_PLAYERS;
    public static final ForgeConfigSpec.BooleanValue RECORDS_ENABLED;
    public static final ForgeConfigSpec.BooleanValue COOP_STREAMS;
    public static final ForgeConfigSpec.IntValue MENTOR_GAP;
    public static final ForgeConfigSpec.DoubleValue MENTOR_BONUS;
    public static final ForgeConfigSpec.DoubleValue CAMARADERIE_BONUS;
    public static final ForgeConfigSpec.DoubleValue SOCIAL_SHARE;

    public static final ForgeConfigSpec.DoubleValue RESTED_CAP_FACTOR;
    public static final ForgeConfigSpec.DoubleValue RESTED_FULL_HOURS;
    public static final ForgeConfigSpec.DoubleValue RESTED_EXTRA;
    public static final ForgeConfigSpec.DoubleValue TEACH_FACTOR;
    public static final ForgeConfigSpec.DoubleValue TEACHER_SHARE;

    public static final ForgeConfigSpec.DoubleValue NIGHT_SHARE;
    public static final ForgeConfigSpec.DoubleValue NIGHT_TRICKLE_XP;

    public static final ForgeConfigSpec.DoubleValue COURAGE_XP_PER_DAMAGE;
    public static final ForgeConfigSpec.DoubleValue COURAGE_KILL_XP;
    public static final ForgeConfigSpec.IntValue COURAGE_FIGHT_WINDOW;

    public static final ForgeConfigSpec.DoubleValue GUARDIAN_COVER_XP;
    public static final ForgeConfigSpec.DoubleValue GUARDIAN_BLOCK_XP;
    public static final ForgeConfigSpec.DoubleValue GUARDIAN_AVENGER_XP;
    public static final ForgeConfigSpec.DoubleValue GUARDIAN_HEAL_XP;
    public static final ForgeConfigSpec.DoubleValue GUARDIAN_REVIVE_XP;

    public static final ForgeConfigSpec.DoubleValue CHARGER_FIRST_BLOOD_XP;
    public static final ForgeConfigSpec.DoubleValue CHARGER_CHARGE_XP;
    public static final ForgeConfigSpec.DoubleValue CHARGER_SPEARHEAD_KILL_XP;

    public static final ForgeConfigSpec.DoubleValue TACTICIAN_SUPPORT_XP;
    public static final ForgeConfigSpec.DoubleValue TACTICIAN_RESCUE_XP;
    public static final ForgeConfigSpec.DoubleValue TACTICIAN_OVERWATCH_XP;
    public static final ForgeConfigSpec.DoubleValue HUSBANDRY_BREED_XP;
    public static final ForgeConfigSpec.DoubleValue HUSBANDRY_TAME_XP;
    public static final ForgeConfigSpec.DoubleValue HUSBANDRY_SHEAR_XP;
    public static final ForgeConfigSpec.DoubleValue HUSBANDRY_MILK_XP;
    public static final ForgeConfigSpec.DoubleValue HUSBANDRY_BRUSH_XP;
    public static final ForgeConfigSpec.DoubleValue HUSBANDRY_FEED_XP;
    public static final ForgeConfigSpec.DoubleValue HUSBANDRY_HONEY_XP;
    public static final ForgeConfigSpec.ConfigValue<java.util.List<? extends String>> RIDING_RATES;
    public static final ForgeConfigSpec.DoubleValue COMMERCE_TRADE_FACTOR;
    public static final ForgeConfigSpec.DoubleValue COMMERCE_BARTER_XP;
    public static final ForgeConfigSpec.BooleanValue COMMERCE_DISCOUNT;

    public static final ForgeConfigSpec.IntValue STREAK_STEP_MINUTES;
    public static final ForgeConfigSpec.IntValue STREAK_MAX_STACKS;
    public static final ForgeConfigSpec.DoubleValue STREAK_XP_PER_STACK;
    public static final ForgeConfigSpec.IntValue STREAK_ACTIVE_WINDOW;

    public static final ForgeConfigSpec.IntValue TEMPO_WINDOW;
    public static final ForgeConfigSpec.DoubleValue TEMPO_STEP;
    public static final ForgeConfigSpec.DoubleValue TEMPO_CAP;
    public static final ForgeConfigSpec.IntValue TEMPO_COMBAT_WINDOW;
    public static final ForgeConfigSpec.DoubleValue TEMPO_COMBAT_STEP;
    public static final ForgeConfigSpec.DoubleValue TEMPO_COMBAT_CRIT_WINDOW_MUL;

    public static final ForgeConfigSpec.BooleanValue HEARTH_ENABLED;
    public static final ForgeConfigSpec.DoubleValue HEARTH_RANGE;
    public static final ForgeConfigSpec.IntValue HEARTH_RANGE_BELOW;
    public static final ForgeConfigSpec.IntValue HEARTH_RANGE_ABOVE;
    public static final ForgeConfigSpec.DoubleValue HEARTH_RANGE_MAX;
    public static final ForgeConfigSpec.DoubleValue HEARTH_REACH_FULL;
    public static final ForgeConfigSpec.IntValue HEARTH_MIN_SPACING;
    public static final ForgeConfigSpec.IntValue HEARTH_BUILD_SECONDS;
    public static final ForgeConfigSpec.BooleanValue HEARTH_BUILD_KEEPS_CHUNK;
    public static final ForgeConfigSpec.IntValue HEARTH_BUILD_CHUNK_WAIT;
    public static final ForgeConfigSpec.BooleanValue HEARTH_SIEGES;

    // Homebuilder (docs/HOMEBUILDER-SPEC.md): the share and the milestones, all tune numbers.
    public static final ForgeConfigSpec.DoubleValue HOME_SHARE;
    public static final ForgeConfigSpec.DoubleValue HOME_STAGE_XP;
    public static final ForgeConfigSpec.DoubleValue HOME_COMFORT_XP;
    public static final ForgeConfigSpec.DoubleValue HOME_CHARACTER_XP;
    public static final ForgeConfigSpec.DoubleValue HOME_SIEGE_XP;
    public static final ForgeConfigSpec.DoubleValue HOME_FEAST_XP;
    public static final ForgeConfigSpec.DoubleValue HOME_TALES_SHARE;
    public static final ForgeConfigSpec.DoubleValue HOME_CHRONICLE_XP;
    public static final ForgeConfigSpec.IntValue HOUSE_PROUD_TICKS;
    public static final ForgeConfigSpec.IntValue HOUSE_PROUD_COOLDOWN;

    public static final ForgeConfigSpec.IntValue ABILITY_UNLOCK_LEVEL;
    public static final ForgeConfigSpec.IntValue ABILITY_DURATION;
    public static final ForgeConfigSpec.IntValue ABILITY_COOLDOWN;

    // Masonry and Decorating free blocks (RefundMath).
    public static final ForgeConfigSpec.DoubleValue REFUND_FREE_CAP;
    public static final ForgeConfigSpec.DoubleValue REFUND_FREE_CAP_KEYSTONE;
    public static final ForgeConfigSpec.DoubleValue REFUND_KEYSTONE_CHANCE;

    private static final Map<Skill, ForgeConfigSpec.DoubleValue> MAX_BONUS = new EnumMap<>(Skill.class);
    private static final Map<Skill, ForgeConfigSpec.DoubleValue> XP_RATE = new EnumMap<>(Skill.class);
    private static final Map<Skill, ForgeConfigSpec.DoubleValue> PROC_CHANCE = new EnumMap<>(Skill.class);
    private static final Map<Skill, ForgeConfigSpec.BooleanValue> ENABLED = new EnumMap<>(Skill.class);

    static {
        ForgeConfigSpec.Builder b = new ForgeConfigSpec.Builder();

        b.comment("How quickly skills are earned and what a level is worth.").push("progression");

        XP_MULTIPLIER = b
                .comment("Global multiplier on all skill XP. 2.0 levels everything twice as fast.")
                .defineInRange("xpMultiplier", 1.0, 0.01, 100.0);

        // 2026-09-29: the curve was 1.0 * L^1.25 (Lv10 = 88 XP, Lv100 = 14k) and the owner found
        // the start far too fast: Woodcutting 10 inside five trees, Wayfaring 15 almost at once.
        // Chosen pace, about 3x slower: floor 8 + 2 * L^1.35, so Lv10 = 293, Lv25 = 1,919,
        // Lv50 = 8,964, Lv100 = 43,956. The floor is what fixes the start: every level costs at
        // least 8 XP more, which matters most where L^1.35 is tiny.
        CURVE_FLOOR = b
                .comment("XP added to every level's cost. Cost for level L is floor + base * L^exponent.",
                        "The floor is what keeps the first levels from arriving almost at once.")
                .defineInRange("curveFloor", 8.0, 0.0, 1000.0);

        CURVE_BASE = b
                .comment("Multiplier on the climbing part of the cost: floor + base * L^exponent.")
                .defineInRange("curveBase", 2.0, 0.1, 1000.0);

        CURVE_EXPONENT = b
                .comment("How sharply the cost per level climbs. With the default floor 8 and base 2,",
                        "1.35 puts level 10 at ~290 XP and level 100 near 44,000.")
                .defineInRange("curveExponent", 1.35, 1.0, 3.0);

        ENABLE_PROCS = b
                .comment("Signature moments: Motherlode, Timber, Perfect Strike and the rest.",
                        "Turn this off and the mod is a plain set of percentage bonuses.")
                .define("enableProcs", true);

        PROC_UNLOCK_LEVEL = b
                .comment("Level at which a skill's signature proc starts firing at all.")
                .defineInRange("procUnlockLevel", 25, 1, 100);

        PROC_FLOOR = b
                .comment("Proc chance the moment it unlocks, before it scales up to the per-skill maximum.")
                .defineInRange("procFloor", 0.05, 0.0, 1.0);

        SIGNATURE_NEEDS_ROOT = b
                .comment("The signature proc and the ability also wait for the skill tree's root node to be",
                        "bought in full, on top of their levels, so they only switch on for a player who chose them.")
                .define("signatureNeedsRoot", true);

        ANNOUNCE_MILESTONES = b
                .comment("Announce milestone levels to the whole server.")
                .define("announceMilestones", true);

        // New key names on purpose (were milestoneInterval 25 / milestoneMinLevel 50): a saved
        // config keeps its old values over new defaults, so renaming is what moves every existing
        // world and server to the 10, 20 ... 100 rule the owner asked for (2026-10-07).
        MILESTONE_INTERVAL = b
                .comment("Announce every Nth level of a skill to the whole server: 10 means 10, 20 ... 100.")
                .defineInRange("announceEveryLevels", 10, 1, 100);

        MILESTONE_MIN_LEVEL = b
                .comment("Do not announce levels below this one.")
                .defineInRange("announceFromLevel", 10, 1, 100);

        MASTERY_STAR_FACTOR = b
                .comment("Mastery stars: XP keeps flowing at level 100 into a bar that earns up to 5 cosmetic stars.",
                        "Star n costs n times (this factor times the XP of level 99 to 100).",
                        "1.0 makes all five stars cost about as much as the last 15 levels.")
                .defineInRange("masteryStarFactor", 1.0, 0.01, 100.0);

        MASTERY_MAX_STARS = b
                .comment("How many Mastery stars a skill can earn, 0 to 5. 0 turns the overflow bar off.")
                .defineInRange("masteryMaxStars", 5, 0, 5);

        BOSS_HEALTH = b
                .comment("Max health at which a kill is announced server-wide as a boss, for a mob",
                        "no boss rule names. A blunt backstop: the boss rules decide first. They follow",
                        "the proficiency:notable_bosses tag AND the common boss tags (c:bosses,",
                        "neoforge:bosses, forge:bosses) that mods fill in, and a datapack can add",
                        "or remove ids. Prefer those to this number.")
                .defineInRange("bossHealthThreshold", 150.0, 1.0, 10000.0);

        FIRST_TIME_XP = b
                .comment("One-time bonus XP, before multipliers, the first time each kind of thing",
                        "feeds a skill: a new ore for Mining, a new wood for Woodcutting, a new mob",
                        "for a weapon skill. 0 turns it off. Per player, per skill, per kind.")
                .defineInRange("firstTimeXp", 15.0, 0.0, 1000.0);

        FIRST_TIME_BUILD_PER_DAY = b
                .comment("Most first-time bonuses Masonry and Decorating each pay per real day",
                        "(the server's local date). Every new block is a new kind there, so without",
                        "a cap the creative palette pays out in one afternoon. Over the cap a kind",
                        "is not marked seen, so it still pays on another day. 0 pays none.")
                .defineInRange("firstTimeBuildPerDay", 10, 0, 10000);

        FIRST_TIME_TIER_SCALING = b
                .comment("Scale the first-time bonus by how rare the thing is: ores x2 (diamond,",
                        "emerald, ancient debris x5), bosses x10, strong mobs x3 to x5, uncommon,",
                        "rare and epic items x2, x3, x5. false pays firstTimeXp flat for everything.")
                .define("firstTimeTierScaling", true);

        PLACED_BLOCKS_PAY_XP = b
                .comment("false: breaking a block a player placed pays no gathering XP, first-time bonus,",
                        "proc or bonus drops (a ripe planted crop still pays). Blocks placed before this",
                        "existed count as natural. true restores the old behaviour.")
                .define("placedBlocksPayXp", false);

        AOE_XP_SHARE = b
                .comment("Share of the usual gathering XP each EXTRA block pays when an area tool or a",
                        "vein-mining mod breaks several blocks at once (a hammer, excavator or broadaxe,",
                        "a Meka-Tool, a vein miner). The first block you break pays in full; every other",
                        "block broken for you in the same tick pays this share, with no first-time bonus,",
                        "no proc roll and no tempo step of its own. 0 pays nothing for the extras, 1 pays",
                        "them in full.")
                .defineInRange("aoeXpShare", dev.amman.proficiency.skill.AoeBreaks.DEFAULT_SHARE, 0.0, 1.0);

        RANGED_XP_PER_DOUBLING = b
                .comment("Archery and Crossbows: extra hit XP for a long shot, logarithmic in the distance.",
                        "Every doubling of the distance past 8 blocks adds this share of the hit's XP,",
                        "counted up to 128 blocks. At 0.5: 16 blocks pay 1.5x, 32 blocks 2x, 64 blocks",
                        "2.5x, 128 blocks 3x. Closer than 8 pays the plain hit. 0 turns it off.")
                .defineInRange("rangedXpPerDoubling", dev.amman.proficiency.skill.RangeXp.DEFAULT_PER_DOUBLING, 0.0, 10.0);

        SPAWNER_MOB_XP = b
                .comment("Share of the usual combat XP a mob from a monster spawner pays (1.0 = all).")
                .defineInRange("spawnerMobXp", 0.25, 0.0, 1.0);

        ARTIFICIAL_MOB_XP = b
                .comment("Share of the usual combat XP a mob from a spawn egg, dispenser, command or",
                        "mob bucket pays. 0 pays nothing.")
                .defineInRange("artificialMobXp", 0.0, 0.0, 1.0);

        KILL_BONUS_BASE = b
                .comment("Kill bonus: XP a kill pays to the skill of the killing blow, before",
                        "multipliers, is clamp(killBonusBase + maxHealth / killBonusHealthDivisor,",
                        "killBonusBase, killBonusCap). Hits pay their normal XP and no first-time",
                        "bonus; the first-time bonus for a new mob is paid on its first kill.",
                        "Spawn-egg and spawner mobs scale it like any combat XP. 0 turns kills off.")
                .defineInRange("killBonusBase", 2.0, 0.0, 1000.0);

        KILL_BONUS_HEALTH_DIVISOR = b
                .comment("Max health per extra point of kill bonus (20 health pays 4 at the default 5).")
                .defineInRange("killBonusHealthDivisor", 5.0, 0.1, 10000.0);

        KILL_BONUS_CAP = b
                .comment("Most a single kill pays before the boss multiplier.")
                .defineInRange("killBonusCap", 20.0, 0.0, 10000.0);

        KILL_BONUS_BOSS_MULTIPLIER = b
                .comment("Multiplier on the kill bonus for bosses: mobs a boss rule names (the",
                        "proficiency:notable_bosses tag and the common boss tags in the shipped rules),",
                        "or over bossHealthThreshold health when no rule applies. Applied after the cap.")
                .defineInRange("killBonusBossMultiplier", 2.0, 0.0, 100.0);

        STRUCTURE_XP = b
                .comment("Wayfaring XP, before multipliers, for the first visit to each kind of",
                        "structure. Variants count once: every village is a village.")
                .defineInRange("structureXp", 40, 0, 100000);

        GRAND_STRUCTURE_XP = b
                .comment("The same for the structures in the proficiency:grand_structures tag",
                        "(Ancient City, Stronghold, End City and the like).")
                .defineInRange("grandStructureXp", 150, 0, 100000);

        STRUCTURE_ENTRY_COOLDOWN_MINUTES = b
                .comment("Minutes before the small re-entry banner shows again for the same",
                        "structure. 0 turns the re-entry banner off. Per player, in memory only.")
                .defineInRange("structureEntryCooldownMinutes", 10, 0, 1440);

        ANNOUNCE_GRAND_DISCOVERIES = b
                .comment("Tell everyone else on the server, in quiet grey chat, when a player",
                        "first finds a grand structure type or first enters a dimension.")
                .define("announceGrandDiscoveries", true);

        XP_FEED_PLAYERS = b
                .comment("Player names whose debug XP feed is switched on at every login.",
                        "The same as running /skills xpfeed on, but it works for someone offline.")
                .defineListAllowEmpty("xpFeedPlayers", java.util.List.<String>of(),
                        name -> name instanceof String);

        b.pop();

        b.comment("Working near other people. Nothing here gates anything: it only changes the",
                        "rate, because one player has to be able to finish every tree alone.")
                .push("company");
        COMPANY_RADIUS = b.defineInRange("radius", 24.0, 1.0, 256.0);
        MENTOR_GAP = b
                .comment("How many levels ahead someone must be to count as your mentor.")
                .defineInRange("mentorLevelGap", 5, 1, 100);
        MENTOR_BONUS = b
                .comment("The XP bonus for working near a mentor. It was 0.50 before rested XP and",
                        "teaching; it is 0.25 now because a student also gets rested XP from a teacher.",
                        "An existing config file keeps its old value until someone edits it.")
                .defineInRange("mentorBonus", 0.25, 0.0, 10.0);
        CAMARADERIE_BONUS = b.defineInRange("camaraderieBonus", 0.15, 0.0, 10.0);
        SOCIAL_SHARE = b
                .comment("Social skill XP, as a share of the extra XP the company bonus added to a",
                        "grant. 0.5 with camaraderie +15% pays Social about 6.5% of what you earn",
                        "near others. Grants with no company bonus pay Social nothing.")
                .defineInRange("socialShare", 0.5, 0.0, 10.0);
        JADE_PLAYERS = b
                .comment("Jade (optional): looking at another player shows their title, their three best",
                        "skills and what the two of you gain together. false hides all of it, so a",
                        "server can keep levels private.")
                .define("jadePlayers", true);
        COOP_STREAMS = b
                .comment("Co-op XP streams (1.45.0): once a second each player is sent the XP of the players",
                        "within company range (their dots stream in on the client, ui.coopStreams) and who is in",
                        "company with them. Cosmetic and optional; false sends nothing.")
                .define("coopStreams", true);
        b.pop();

        b.comment("Rested XP: a pool per skill that fills while you are active and away from that",
                        "skill, and is spent as a bonus on later XP in it. Teaching, in Social, fills a",
                        "student's pool faster. See docs/RESTED-AND-TEACHING.md.")
                .push("rested");
        RESTED_CAP_FACTOR = b
                .comment("The pool holds this many times what the skill's current level costs.",
                        "0 switches rested XP and teaching off.")
                .defineInRange("capFactor", dev.amman.proficiency.skill.RestedMath.DEFAULT_CAP_FACTOR, 0.0, 10.0);
        RESTED_FULL_HOURS = b
                .comment("Active hours (not AFK) a skill must rest to fill its empty pool.")
                .defineInRange("fullHours", dev.amman.proficiency.skill.RestedMath.DEFAULT_FULL_HOURS, 0.1, 1000.0);
        RESTED_EXTRA = b
                .comment("Each grant in the skill gains this times itself from the pool, until the",
                        "pool is empty. 1.0 doubles it. It adds to the survival bonus, it does not multiply it.")
                .defineInRange("extra", dev.amman.proficiency.skill.RestedMath.DEFAULT_EXTRA, 0.0, 10.0);
        TEACH_FACTOR = b
                .comment("A teacher near a student fills the student's pool this many times faster",
                        "than resting does.")
                .defineInRange("teachFactor", dev.amman.proficiency.skill.RestedMath.DEFAULT_TEACH_FACTOR, 0.0, 20.0);
        TEACHER_SHARE = b
                .comment("The share of the XP a student spends from a teacher's part of the pool that",
                        "the teacher earns as Social XP (before Social's own multipliers).")
                .defineInRange("teacherShare", dev.amman.proficiency.skill.RestedMath.DEFAULT_TEACHER_SHARE, 0.0, 5.0);
        b.pop();

        b.comment("Nightwalker: being active in the dark. It has no source of its own: it earns a",
                        "share of other skills' XP earned in the dark, plus a trickle for active time",
                        "outdoors at night.")
                .push("nightwalker");
        NIGHT_SHARE = b
                .comment("Nightwalker XP as a share of another skill's grant, before multipliers,",
                        "when you stand at block light 0-3 (and, by day, low sky light too).")
                .defineInRange("share", dev.amman.proficiency.skill.NightwalkerMath.DEFAULT_SHARE, 0.0, 10.0);
        NIGHT_TRICKLE_XP = b
                .comment("Base XP for each full minute of active play outdoors at night, away from",
                        "light. A second counts only while you move and look around, not in bed,",
                        "in water or riding. 0 turns it off.")
                .defineInRange("trickleXp", dev.amman.proficiency.skill.NightwalkerMath.DEFAULT_TRICKLE_XP, 0.0, 1000.0);
        b.pop();

        b.comment("Courage: fighting when the odds are against you. It pays for damage dealt and",
                        "kills, times an odds factor (foes after you, a stronger foe or a boss, low",
                        "health, less armour). At even or favourable odds it pays nothing.")
                .push("courage");
        COURAGE_XP_PER_DAMAGE = b
                .comment("Base Courage XP per point of damage dealt, before the odds factor.")
                .defineInRange("xpPerDamage", dev.amman.proficiency.skill.CourageMath.DEFAULT_XP_PER_DAMAGE, 0.0, 100.0);
        COURAGE_KILL_XP = b
                .comment("Base Courage XP for a kill, before the odds factor.")
                .defineInRange("killXp", dev.amman.proficiency.skill.CourageMath.DEFAULT_KILL_XP, 0.0, 1000.0);
        COURAGE_FIGHT_WINDOW = b
                .comment("Courage pays only while you are in a fight: a mob hurt you within this many",
                        "ticks. Stops a mob farm (mobs that see you but cannot reach you) from paying.")
                .defineInRange("fightWindowTicks", (int) dev.amman.proficiency.skill.CourageMath.DEFAULT_FIGHT_WINDOW_TICKS, 20, 12000);
        b.pop();

        b.comment("Guardian: protecting other players. Every source needs another player and is",
                        "scaled by their danger (x0.25 at full health, x2 near death). Nothing is paid",
                        "with no player near, and nothing for damage a player deals you.")
                .push("guardian");
        GUARDIAN_COVER_XP = b
                .comment("XP per point of damage you take from a mob that was after a player near you.")
                .defineInRange("coverXpPerDamage", dev.amman.proficiency.skill.GuardianMath.DEFAULT_COVER_XP_PER_DAMAGE, 0.0, 100.0);
        GUARDIAN_BLOCK_XP = b
                .comment("XP per point of damage you block or absorb with a player within 4 blocks.")
                .defineInRange("blockXpPerDamage", dev.amman.proficiency.skill.GuardianMath.DEFAULT_BLOCK_XP_PER_DAMAGE, 0.0, 100.0);
        GUARDIAN_AVENGER_XP = b
                .comment("XP for killing a mob that hurt another player in the last 5 seconds.")
                .defineInRange("avengerXp", dev.amman.proficiency.skill.GuardianMath.DEFAULT_AVENGER_XP, 0.0, 1000.0);
        GUARDIAN_HEAL_XP = b
                .comment("XP per point of health your thrown healing or regeneration potion gives back.")
                .defineInRange("healXpPerHealth", dev.amman.proficiency.skill.GuardianMath.DEFAULT_HEAL_XP_PER_HEALTH, 0.0, 100.0);
        GUARDIAN_REVIVE_XP = b
                .comment("XP for staying next to a player who dropped under 30% health until they live 10 s.")
                .defineInRange("reviveXp", dev.amman.proficiency.skill.GuardianMath.DEFAULT_REVIVE_XP, 0.0, 1000.0);
        b.pop();

        b.comment("Charger: first in, always moving forward. Melee only. First blood pays once per",
                        "mob, a charge needs 5 blocks closed on the target in 2 s (no sprint needed),",
                        "and one spot (12 blocks) pays at most 16 times in 5 minutes.")
                .push("charger");
        CHARGER_FIRST_BLOOD_XP = b
                .comment("XP for a first blood on a 20-health mob; x0.5 to x3 by the mob's max health.")
                .defineInRange("firstBloodXp", dev.amman.proficiency.skill.ChargerMath.DEFAULT_FIRST_BLOOD_XP, 0.0, 1000.0);
        CHARGER_CHARGE_XP = b
                .comment("XP per block closed on a charge hit (at most 12 blocks); x1.5 for a sprint attack.")
                .defineInRange("chargeXpPerBlock", dev.amman.proficiency.skill.ChargerMath.DEFAULT_CHARGE_XP_PER_BLOCK, 0.0, 100.0);
        CHARGER_SPEARHEAD_KILL_XP = b
                .comment("XP for a kill in Spearhead (a friend behind you, mobs ahead), for a 20-health mob.")
                .defineInRange("spearheadKillXp", dev.amman.proficiency.skill.ChargerMath.DEFAULT_SPEARHEAD_KILL_XP, 0.0, 1000.0);
        b.pop();

        b.comment("Tactician: the back line. Ranged only, and only for mobs that are fighting another",
                        "player (after them, or hurt them in the last 5 s). A mob after nobody or only",
                        "you pays nothing, and one spot (12 blocks) pays at most 48 XP in 5 minutes.")
                .push("tactician");
        TACTICIAN_SUPPORT_XP = b
                .comment("XP per point of ranged damage on a mob that is after another player (20 per hit, 40 per mob).")
                .defineInRange("supportXpPerDamage", dev.amman.proficiency.skill.TacticianMath.DEFAULT_SUPPORT_XP_PER_DAMAGE, 0.0, 100.0);
        TACTICIAN_RESCUE_XP = b
                .comment("XP for a ranged kill of a mob that hurt another player in the last 5 s, for a 20-health mob.")
                .defineInRange("rescueXp", dev.amman.proficiency.skill.TacticianMath.DEFAULT_RESCUE_XP, 0.0, 1000.0);
        TACTICIAN_OVERWATCH_XP = b
                .comment("Scale of the Overwatch XP for a ranged hit with a friend between you and the mob, 3 per mob.",
                        "Logarithmic in the distance from 6 to 30 blocks: 6 blocks pay 6x this, 30 blocks 30x,",
                        "15 blocks about 20x.")
                .defineInRange("overwatchXpPerBlock", dev.amman.proficiency.skill.TacticianMath.DEFAULT_OVERWATCH_XP_PER_BLOCK, 0.0, 100.0);
        b.pop();

        b.comment("Husbandry: looking after animals. Survival only, through a real player. A crowded pen",
                        "(more than 8 animals within 8 blocks) pays less, and one spot (12 blocks) pays at most",
                        "12 jobs in 5 minutes. Every value is the base XP before the species' worth (x1.5 for",
                        "the rarer ones), the pen's density and your multipliers.")
                .push("husbandry");
        HUSBANDRY_BREED_XP = b
                .comment("XP for a breeding you caused by feeding two animals (once per pair per 5 minutes).")
                .defineInRange("breedXp", dev.amman.proficiency.skill.HusbandryMath.Job.BREED.defaultXp(), 0.0, 1000.0);
        HUSBANDRY_TAME_XP = b
                .comment("XP for taming a wolf, cat, parrot or horse (8 per 30 minutes; each species also pays the first-time bonus once).")
                .defineInRange("tameXp", dev.amman.proficiency.skill.HusbandryMath.Job.TAME.defaultXp(), 0.0, 1000.0);
        HUSBANDRY_SHEAR_XP = b
                .comment("XP for shearing a sheep or mooshroom that had regrown its coat.")
                .defineInRange("shearXp", dev.amman.proficiency.skill.HusbandryMath.Job.SHEAR.defaultXp(), 0.0, 1000.0);
        HUSBANDRY_MILK_XP = b
                .comment("XP for milking a cow or goat (once per animal per 2 minutes).")
                .defineInRange("milkXp", dev.amman.proficiency.skill.HusbandryMath.Job.MILK.defaultXp(), 0.0, 1000.0);
        HUSBANDRY_BRUSH_XP = b
                .comment("XP for brushing a scute off an armadillo (once per animal per 5 minutes).")
                .defineInRange("brushXp", dev.amman.proficiency.skill.HusbandryMath.Job.BRUSH.defaultXp(), 0.0, 1000.0);
        HUSBANDRY_FEED_XP = b
                .comment("XP for feeding a baby animal so it grows up.")
                .defineInRange("feedXp", dev.amman.proficiency.skill.HusbandryMath.Job.FEED.defaultXp(), 0.0, 1000.0);
        HUSBANDRY_HONEY_XP = b
                .comment("XP for taking honey or honeycomb from a hive that was full.")
                .defineInRange("honeyXp", dev.amman.proficiency.skill.HusbandryMath.Job.HONEY.defaultXp(), 0.0, 1000.0);
        b.pop();

        b.comment("Riding: metres ridden on a mount, in a boat, in a minecart or under elytra, survival only.",
                        "Each line is 'entity id=XP per metre'. '#boat' is every boat and raft, '#minecart' every",
                        "minecart, 'minecraft:elytra' is gliding (paid up to twice this for skimming the ground fast)",
                        "and '*' is any other mount, a modded one included. A rate of 0 switches a vehicle off.",
                        "A boat or a mount you steer pays only while you steer it (a leash-dragged mount and a boat",
                        "adrift pay nothing); a ride that does not get anywhere (loops, shuttles) and ground you rode",
                        "through in the last 10 minutes pay nothing; a teleport or portal step of 10 blocks is skipped.")
                .push("riding");
        RIDING_RATES = b
                .comment("Base XP per metre by vehicle type.")
                .defineListAllowEmpty("rates", dev.amman.proficiency.skill.RidingMath.defaultRateLines(),
                        entry -> entry instanceof String);
        b.pop();

        b.comment("Commerce: trades with villagers, wandering traders and any modded trader that uses the vanilla",
                        "merchant screen, and bartering gold with a piglin. Survival only, through a real player.",
                        "The same offer pays less each time within a day, more than 20 trades in 5 minutes fade, and a",
                        "piglin pays only when a player gave it the ingot (a dispenser or a hopper farm pays nothing).")
                .push("commerce");
        COMMERCE_TRADE_FACTOR = b
                .comment("A multiplier on the XP of every trade (the formula is 2 + 1.6 x sqrt(emeralds), more for a higher trader level).")
                .defineInRange("tradeXpFactor", 1.0, 0.0, 100.0);
        COMMERCE_BARTER_XP = b
                .comment("XP per gold ingot a piglin takes from you (the first 12 in 10 minutes pay in full).")
                .defineInRange("barterXp", dev.amman.proficiency.skill.CommerceMath.BARTER_XP, 0.0, 1000.0);
        COMMERCE_DISCOUNT = b
                .comment("Whether the skill's discount (and Haggle) is written into the traders' offers as a special price.",
                        "Turn it off if another mod rewrites trade prices; the XP and the rest of the tree stay.")
                .define("discount", true);
        b.pop();

        b.comment("What a death costs, and what staying alive pays. A death wipes every skill's",
                        "progress toward its next level (never the level itself) and the whole",
                        "survival streak. The streak grows one stack per step of active play alive,",
                        "and every stack adds to all skill XP.")
                .push("survival");
        STREAK_STEP_MINUTES = b
                .comment("Minutes of active play alive per stack.")
                .defineInRange("stepMinutes", 60, 1, 1440);
        STREAK_MAX_STACKS = b
                .comment("Stack cap. 0 switches the streak off and leaves only the progress wipe.")
                .defineInRange("maxStacks", 50, 0, 1000);
        STREAK_XP_PER_STACK = b
                .comment("Skill XP bonus per stack, as a fraction. 0.01 with 50 stacks tops out at +50%.")
                .defineInRange("xpPerStack", 0.01, 0.0, 1.0);
        STREAK_ACTIVE_WINDOW = b
                .comment("Time only counts while the player has earned skill XP within this many",
                        "minutes, so standing AFK does not build a streak.")
                .defineInRange("activeWindowMinutes", 5, 1, 60);
        b.pop();

        b.comment("Rhythm. Repeated actions in one skill build a multiplier that decays on a pause.")
                .push("tempo");
        TEMPO_WINDOW = b
                .comment("Ticks of silence that break the chain.")
                .defineInRange("windowTicks", 60, 1, 1200);
        TEMPO_STEP = b.defineInRange("perStep", 0.02, 0.0, 1.0);
        TEMPO_CAP = b.defineInRange("cap", 0.50, 0.0, 10.0);
        b.comment("The weapon skills (Swords, Axes, Maces, Tridents, Unarmed, Archery, Crossbows, Beastslaying).",
                        "A fight has pauses, so their chain survives a longer silence and climbs in bigger steps",
                        "to the same cap. Courage, Charger, Guardian, Tactician and Blocking own no chain: they",
                        "pay what the live weapon chain pays. Endurance has no Tempo.")
                .push("combat");
        TEMPO_COMBAT_WINDOW = b
                .comment("Ticks of silence that break a weapon skill's chain.")
                .defineInRange("windowTicks", 140, 1, 1200);
        TEMPO_COMBAT_STEP = b.defineInRange("perStep", 0.05, 0.0, 1.0);
        TEMPO_COMBAT_CRIT_WINDOW_MUL = b
                .comment("A vanilla jump crit with a weapon (falling, not sprinting, full swing) multiplies the window",
                        "of the next gap only by this. Proficiency's own crits do not. 1.0 turns it off.")
                .defineInRange("critWindowMul", 2.0, 1.0, 10.0);
        b.pop();
        b.pop();

        b.comment("Personal records per skill: best XP per minute, longest Tempo chain, biggest single grant and",
                        "the skill's own best. Saved with the player, never wiped by a death.")
                .push("records");
        RECORDS_ENABLED = b
                .comment("false stops tracking, toasts and the Records line. Saved records stay as they are.")
                .define("enabled", true);
        b.pop();

        b.comment("Balance telemetry: per player, skill and source kind, XP and active time are",
                        "counted in memory and appended to <world>/proficiency/telemetry/YYYY-MM-DD.jsonl",
                        "every five minutes and on stop. No packets, no per-grant disk writes.",
                        "tools/balance_report.py turns the files into a report.")
                .push("telemetry");
        TELEMETRY_ENABLED = b.define("enabled", true);
        TELEMETRY_RETENTION_DAYS = b
                .comment("Day files older than this are deleted at server start. 0 keeps everything.")
                .defineInRange("retentionDays", 60, 0, 36500);
        b.pop();

        b.comment("Today's Haul (1.44.0): a per-player summary of what was earned since the last haul (XP per skill,",
                        "levels, stars, talent points, best Tempo chain, discoveries, synergies, Hearth charge), saved",
                        "with the player and shown as a card in bed or with /proficiency haul. false: nothing is",
                        "recorded or sent and the command says it is off.")
                .push("haul");
        HAUL_ENABLED = b.define("enabled", true);
        b.pop();

        b.comment("Dashboard export: every intervalSeconds and when a player leaves, the server writes",
                        "<world>/proficiency/export/players.json (levels, bar fractions, stars, synergies,",
                        "Expertise, top skills, deaths this week and every Hearth) through a temporary file and a",
                        "rename, for a home dashboard to read. It only reads game state; no packets.")
                .push("export");
        EXPORT_ENABLED = b.define("enabled", true);
        EXPORT_INTERVAL_SECONDS = b.defineInRange("intervalSeconds", 300, 30, 86400);
        b.pop();

        b.comment("Active abilities: twenty seconds where the signature proc stops rolling.")
                .push("abilities");
        ABILITY_UNLOCK_LEVEL = b.defineInRange("unlockLevel", 50, 1, 100);
        ABILITY_DURATION = b.defineInRange("durationTicks", 400, 20, 12000);
        ABILITY_COOLDOWN = b.defineInRange("cooldownTicks", 6000, 20, 432000);
        b.pop();

        b.comment("Masonry and Decorating: a placed block can come back, which makes it free. A placement gives",
                        "back at most the one block. Every source (the signature proc, the passive, talents) is a",
                        "chance that the block is free; together they never pass the cap. During the ability every",
                        "proc lands, so a block is free at the cap. Storage blocks, ores, rare items and",
                        "#proficiency:no_refund never come back.")
                .push("refunds");
        REFUND_FREE_CAP = b
                .comment("The most a placed block can be free, as a fraction (0.55: a block costs at least 0.45).")
                .defineInRange("freeCap", dev.amman.proficiency.skill.RefundMath.DEFAULT_FREE_CAP, 0.0, 1.0);
        REFUND_FREE_CAP_KEYSTONE = b
                .comment("The cap with Master Mason or Master Decorator.")
                .defineInRange("freeCapKeystone", dev.amman.proficiency.skill.RefundMath.DEFAULT_FREE_CAP_KEYSTONE, 0.0, 1.0);
        REFUND_KEYSTONE_CHANCE = b
                .comment("Master Mason's and Master Decorator's own chance that a block is free.")
                .defineInRange("keystoneChance", dev.amman.proficiency.skill.RefundMath.DEFAULT_KEYSTONE_CHANCE, 0.0, 1.0);
        b.pop();

        b.comment("The Hearth: a base block that mirrors nearby XP into boons (docs/HEARTH-SPEC.md).",
                        "Its numbers (pools, tiers, decay, sieges) are datapack data, proficiency/hearth/*.json.")
                .push("hearth");
        HEARTH_ENABLED = b
                .comment("Master switch. false: Hearths do nothing (no charge, boons, Warmth or sieges).")
                .define("enabled", true);
        HEARTH_RANGE = b
                .comment("Range radius in blocks, horizontally around the campfire, before any Reach. The range is a cylinder.",
                        "The house itself (Comfort scan, siege ring, bell) always uses this radius.")
                .defineInRange("range", 24.0, 4.0, 128.0);
        HEARTH_RANGE_BELOW = b
                .comment("How far the range reaches below the campfire, so the mine under the base is not home.")
                .defineInRange("rangeBelow", 8, 0, 128);
        HEARTH_RANGE_ABOVE = b
                .comment("How far the range reaches above the campfire.")
                .defineInRange("rangeAbove", 16, 0, 256);
        HEARTH_RANGE_MAX = b
                .comment("Reach: the range radius a Hearth grows to as Homebuilder XP is earned at it (docs/HEARTH-REACH-SPEC.md).",
                        "From 'range' with no Reach up to this when Reach is full. Equal to 'range' turns Reach off.")
                .defineInRange("rangeMax", 64.0, 4.0, 512.0);
        HEARTH_REACH_FULL = b
                .comment("Reach: the Homebuilder XP, earned in range of one Hearth, that grows its range all the way to rangeMax.",
                        "0 = every Hearth is at rangeMax at once.")
                .defineInRange("reachFullXp", 5000.0, 0.0, 10_000_000.0);
        HEARTH_MIN_SPACING = b
                .comment("No other Hearth may be lit closer than this, horizontally, in the same dimension.",
                        "Ranges grown by Reach may overlap; a block then belongs to the nearest Hearth.")
                .defineInRange("minSpacing", 64, 0, 4096);
        HEARTH_BUILD_SECONDS = b
                .comment("Self-build: seconds between two blocks the Hearth places by itself. It pays no XP.")
                .defineInRange("buildSecondsPerBlock", 60, 1, 3600);
        HEARTH_BUILD_KEEPS_CHUNK = b
                .comment("Self-build: while a build runs, the Hearth's chunk (that one chunk) stays loaded, so the build goes on",
                        "while nobody is near. Released when the build ends, is stopped, the Hearth is broken, or waits too long.")
                .define("buildKeepsChunkLoaded", true);
        HEARTH_BUILD_CHUNK_WAIT = b
                .comment("Self-build: minutes a build may wait for a missing material (or a blocked cell) before it lets the chunk go.",
                        "It takes the chunk again as soon as it places a block. 0 = let go at the first wait.")
                .defineInRange("buildChunkWaitLimitMinutes", 30, 0, 10080);
        HEARTH_SIEGES = b
                .comment("Sieges at Hearths. false: no rolls and the Siege Horn does nothing.")
                .define("sieges", true);
        b.pop();

        b.comment("Homebuilder: the home skill. It has no source of its own: it earns a share of other",
                        "skills' XP earned at a Hearth, plus the home's milestones, once each per Hearth and",
                        "per player. The passive (speed, reach, magnet) works only at home.")
                .push("homebuilder");
        HOME_SHARE = b
                .comment("Homebuilder XP as a share of another skill's grant at home, before multipliers.")
                .defineInRange("share", dev.amman.proficiency.skill.HomebuilderMath.DEFAULT_SHARE, 0.0, 10.0);
        HOME_STAGE_XP = b
                .comment("Base XP for lighting a Hearth or sealing a stage, times the stage number (I..VI).")
                .defineInRange("stageXp", dev.amman.proficiency.skill.HomebuilderMath.DEFAULT_STAGE_XP, 0.0, 100000.0);
        HOME_COMFORT_XP = b
                .comment("Base XP when a Comfort kind you placed appears at a Hearth for the first time.")
                .defineInRange("comfortXp", dev.amman.proficiency.skill.HomebuilderMath.DEFAULT_COMFORT_XP, 0.0, 100000.0);
        HOME_CHARACTER_XP = b
                .comment("Base XP for a new Character block id you placed at a Hearth.")
                .defineInRange("characterXp", dev.amman.proficiency.skill.HomebuilderMath.DEFAULT_CHARACTER_XP, 0.0, 100000.0);
        HOME_SIEGE_XP = b
                .comment("Base XP for a defended siege, split by kills; everyone present gets at least 10%.")
                .defineInRange("siegeXp", dev.amman.proficiency.skill.HomebuilderMath.DEFAULT_SIEGE_XP, 0.0, 100000.0);
        HOME_FEAST_XP = b
                .comment("Base XP for a Feast you were part of.")
                .defineInRange("feastXp", dev.amman.proficiency.skill.HomebuilderMath.DEFAULT_FEAST_XP, 0.0, 100000.0);
        HOME_TALES_SHARE = b
                .comment("Tales paid in at a Hearth pay this share of the Tales as Homebuilder XP.")
                .defineInRange("talesShare", dev.amman.proficiency.skill.HomebuilderMath.DEFAULT_TALES_SHARE, 0.0, 10.0);
        HOME_CHRONICLE_XP = b
                .comment("Base XP for a Hearth Chronicle page written (the flat part).")
                .defineInRange("chronicleXp", dev.amman.proficiency.skill.HomebuilderMath.DEFAULT_CHRONICLE_XP, 0.0, 100000.0);
        HOUSE_PROUD_TICKS = b
                .comment("How long House Proud lasts, in ticks, before Open House and the duration talents.")
                .defineInRange("houseProudTicks", dev.amman.proficiency.skill.HomebuilderMath.DEFAULT_HOUSE_PROUD_TICKS, 20, 12000);
        HOUSE_PROUD_COOLDOWN = b
                .comment("House Proud's cooldown, in ticks, before the capstone's cut.")
                .defineInRange("houseProudCooldownTicks",
                        dev.amman.proficiency.skill.HomebuilderMath.DEFAULT_HOUSE_PROUD_COOLDOWN_TICKS, 20, 432000);
        b.pop();

        b.comment("Per-skill tuning. maxBonus is the size of the passive effect at level 100,",
                        "as a fraction: 0.80 means +80%. xpRate scales only that skill's XP, and",
                        "procChance is how often the signature moment fires once the skill is at 100.")
                .push("skills");
        for (Skill skill : Skill.VALUES) {
            b.push(skill.id());
            ENABLED.put(skill, b.define("enabled", true));
            MAX_BONUS.put(skill, b.defineInRange("maxBonus", skill.defaultMaxBonus(), 0.0, 100.0));
            XP_RATE.put(skill, b.defineInRange("xpRate", 1.0, 0.0, 100.0));
            PROC_CHANCE.put(skill,
                    b.defineInRange("procChance", skill.defaultProcChance(), 0.0, 1.0));
            b.pop();
        }
        b.pop();

        SPEC = b.build();
        // The pure skill maths in core reads its numbers through this.
        dev.amman.proficiency.skill.SkillTuning.install(new Tuning());
    }

    /** These numbers, for {@link dev.amman.proficiency.skill.SkillMath} in core. */
    private static final class Tuning implements dev.amman.proficiency.skill.SkillTuning {
        @Override
        public double curveFloor() {
            return ProficiencyConfig.curveFloor();
        }

        @Override
        public double curveBase() {
            return ProficiencyConfig.curveBase();
        }

        @Override
        public double curveExponent() {
            return ProficiencyConfig.curveExponent();
        }

        @Override
        public boolean enabled(Skill skill) {
            return ProficiencyConfig.enabled(skill);
        }

        @Override
        public double maxBonus(Skill skill) {
            return ProficiencyConfig.maxBonus(skill);
        }

        @Override
        public boolean procsEnabled() {
            return ProficiencyConfig.procsEnabled();
        }

        @Override
        public int procUnlockLevel() {
            return ProficiencyConfig.procUnlockLevel();
        }

        @Override
        public double procFloor() {
            return ProficiencyConfig.procFloor();
        }

        @Override
        public boolean signatureNeedsRoot() {
            return ProficiencyConfig.signatureNeedsRoot();
        }

        @Override
        public double procChance(Skill skill) {
            return ProficiencyConfig.procChance(skill);
        }

        @Override
        public double masteryStarFactor() {
            return ProficiencyConfig.masteryStarFactor();
        }

        @Override
        public int masteryMaxStars() {
            return ProficiencyConfig.masteryMaxStars();
        }

        @Override
        public double restedCapFactor() {
            return ProficiencyConfig.restedCapFactor();
        }

        @Override
        public double restedFullHours() {
            return ProficiencyConfig.restedFullHours();
        }

        @Override
        public double restedExtra() {
            return ProficiencyConfig.restedExtra();
        }

        @Override
        public double restedTeachFactor() {
            return ProficiencyConfig.teachFactor();
        }

        @Override
        public double restedTeacherShare() {
            return ProficiencyConfig.teacherShare();
        }
    }

    private ProficiencyConfig() {
    }

    private static double safe(ForgeConfigSpec.DoubleValue value, double fallback) {
        return SPEC.isLoaded() ? value.get() : fallback;
    }

    public static double xpMultiplier() {
        return safe(XP_MULTIPLIER, 1.0);
    }

    public static double curveFloor() {
        return safe(CURVE_FLOOR, 8.0);
    }

    public static double curveBase() {
        return safe(CURVE_BASE, 2.0);
    }

    public static double curveExponent() {
        return safe(CURVE_EXPONENT, 1.35);
    }

    /** Ticks per stack. Real time at 20 ticks a second. */
    public static long streakStepTicks() {
        return (SPEC.isLoaded() ? STREAK_STEP_MINUTES.get() : 60) * 1200L;
    }

    public static int streakMaxStacks() {
        return SPEC.isLoaded() ? STREAK_MAX_STACKS.get() : 50;
    }

    public static double streakXpPerStack() {
        return safe(STREAK_XP_PER_STACK, 0.01);
    }

    public static long streakActiveWindowTicks() {
        return (SPEC.isLoaded() ? STREAK_ACTIVE_WINDOW.get() : 5) * 1200L;
    }

    public static boolean announceMilestones() {
        return !SPEC.isLoaded() || ANNOUNCE_MILESTONES.get();
    }

    public static int milestoneInterval() {
        return SPEC.isLoaded() ? MILESTONE_INTERVAL.get() : 10;
    }

    public static double masteryStarFactor() {
        return SPEC.isLoaded() ? MASTERY_STAR_FACTOR.get() : 1.0;
    }

    public static int masteryMaxStars() {
        return SPEC.isLoaded() ? MASTERY_MAX_STARS.get() : 5;
    }

    public static int milestoneMinLevel() {
        return SPEC.isLoaded() ? MILESTONE_MIN_LEVEL.get() : 10;
    }

    public static double firstTimeXp() {
        return safe(FIRST_TIME_XP, 15.0);
    }

    /** The share of a bow or crossbow hit's XP each doubling of its distance past 8 blocks adds. */
    public static double rangedXpPerDoubling() {
        return safe(RANGED_XP_PER_DOUBLING, dev.amman.proficiency.skill.RangeXp.DEFAULT_PER_DOUBLING);
    }

    /** The share of its normal XP an area tool's extra block pays. */
    public static double aoeXpShare() {
        return safe(AOE_XP_SHARE, dev.amman.proficiency.skill.AoeBreaks.DEFAULT_SHARE);
    }

    public static boolean placedBlocksPayXp() {
        return SPEC.isLoaded() && PLACED_BLOCKS_PAY_XP.get();
    }

    public static double spawnerMobXp() {
        return safe(SPAWNER_MOB_XP, 0.25);
    }

    public static double killBonusBase() {
        return safe(KILL_BONUS_BASE, 2.0);
    }

    public static double killBonusHealthDivisor() {
        return safe(KILL_BONUS_HEALTH_DIVISOR, 5.0);
    }

    public static double killBonusCap() {
        return safe(KILL_BONUS_CAP, 20.0);
    }

    public static double killBonusBossMultiplier() {
        return safe(KILL_BONUS_BOSS_MULTIPLIER, 2.0);
    }

    public static double artificialMobXp() {
        return safe(ARTIFICIAL_MOB_XP, 0.0);
    }

    public static int firstTimeBuildPerDay() {
        return SPEC.isLoaded() ? FIRST_TIME_BUILD_PER_DAY.get() : 10;
    }

    public static boolean firstTimeTierScaling() {
        return !SPEC.isLoaded() || FIRST_TIME_TIER_SCALING.get();
    }

    public static int structureXp() {
        return SPEC.isLoaded() ? STRUCTURE_XP.get() : 40;
    }

    public static int grandStructureXp() {
        return SPEC.isLoaded() ? GRAND_STRUCTURE_XP.get() : 150;
    }

    public static int structureEntryCooldownMinutes() {
        return SPEC.isLoaded() ? STRUCTURE_ENTRY_COOLDOWN_MINUTES.get() : 10;
    }

    public static boolean announceGrandDiscoveries() {
        return SPEC.isLoaded() ? ANNOUNCE_GRAND_DISCOVERIES.get() : true;
    }

    public static boolean telemetryEnabled() {
        return !SPEC.isLoaded() || TELEMETRY_ENABLED.get();
    }

    public static boolean exportEnabled() {
        return !SPEC.isLoaded() || EXPORT_ENABLED.get();
    }

    public static int exportIntervalSeconds() {
        return SPEC.isLoaded() ? EXPORT_INTERVAL_SECONDS.get() : 300;
    }

    public static int telemetryRetentionDays() {
        return SPEC.isLoaded() ? TELEMETRY_RETENTION_DAYS.get() : 60;
    }

    public static boolean xpFeedAtLogin(String name) {
        if (!SPEC.isLoaded()) {
            return false;
        }
        for (String listed : XP_FEED_PLAYERS.get()) {
            if (listed.equalsIgnoreCase(name)) {
                return true;
            }
        }
        return false;
    }

    public static double bossHealth() {
        return safe(BOSS_HEALTH, 150.0);
    }

    public static double companyRadius() {
        return safe(COMPANY_RADIUS, 24.0);
    }

    public static int mentorGap() {
        return SPEC.isLoaded() ? MENTOR_GAP.get() : 5;
    }

    public static double mentorBonus() {
        return safe(MENTOR_BONUS, 0.25);
    }

    public static double restedCapFactor() {
        return safe(RESTED_CAP_FACTOR, dev.amman.proficiency.skill.RestedMath.DEFAULT_CAP_FACTOR);
    }

    public static double restedFullHours() {
        return safe(RESTED_FULL_HOURS, dev.amman.proficiency.skill.RestedMath.DEFAULT_FULL_HOURS);
    }

    public static double restedExtra() {
        return safe(RESTED_EXTRA, dev.amman.proficiency.skill.RestedMath.DEFAULT_EXTRA);
    }

    public static double teachFactor() {
        return safe(TEACH_FACTOR, dev.amman.proficiency.skill.RestedMath.DEFAULT_TEACH_FACTOR);
    }

    public static double teacherShare() {
        return safe(TEACHER_SHARE, dev.amman.proficiency.skill.RestedMath.DEFAULT_TEACHER_SHARE);
    }

    public static double refundFreeCap() {
        return safe(REFUND_FREE_CAP, dev.amman.proficiency.skill.RefundMath.DEFAULT_FREE_CAP);
    }

    public static double refundFreeCapKeystone() {
        return safe(REFUND_FREE_CAP_KEYSTONE, dev.amman.proficiency.skill.RefundMath.DEFAULT_FREE_CAP_KEYSTONE);
    }

    public static double refundKeystoneChance() {
        return safe(REFUND_KEYSTONE_CHANCE, dev.amman.proficiency.skill.RefundMath.DEFAULT_KEYSTONE_CHANCE);
    }

    public static boolean jadePlayers() {
        return !SPEC.isLoaded() || JADE_PLAYERS.get();
    }

    public static boolean coopStreams() {
        return !SPEC.isLoaded() || COOP_STREAMS.get();
    }

    public static double camaraderieBonus() {
        return safe(CAMARADERIE_BONUS, 0.15);
    }

    public static double socialShare() {
        return safe(SOCIAL_SHARE, 0.5);
    }

    public static double nightShare() {
        return safe(NIGHT_SHARE, dev.amman.proficiency.skill.NightwalkerMath.DEFAULT_SHARE);
    }

    public static double nightTrickleXp() {
        return safe(NIGHT_TRICKLE_XP, dev.amman.proficiency.skill.NightwalkerMath.DEFAULT_TRICKLE_XP);
    }

    public static double courageXpPerDamage() {
        return safe(COURAGE_XP_PER_DAMAGE, dev.amman.proficiency.skill.CourageMath.DEFAULT_XP_PER_DAMAGE);
    }

    public static double courageKillXp() {
        return safe(COURAGE_KILL_XP, dev.amman.proficiency.skill.CourageMath.DEFAULT_KILL_XP);
    }

    public static long courageFightWindow() {
        return SPEC.isLoaded() ? COURAGE_FIGHT_WINDOW.get()
                : dev.amman.proficiency.skill.CourageMath.DEFAULT_FIGHT_WINDOW_TICKS;
    }

    public static double guardianCoverXp() {
        return safe(GUARDIAN_COVER_XP, dev.amman.proficiency.skill.GuardianMath.DEFAULT_COVER_XP_PER_DAMAGE);
    }

    public static double guardianBlockXp() {
        return safe(GUARDIAN_BLOCK_XP, dev.amman.proficiency.skill.GuardianMath.DEFAULT_BLOCK_XP_PER_DAMAGE);
    }

    public static double guardianAvengerXp() {
        return safe(GUARDIAN_AVENGER_XP, dev.amman.proficiency.skill.GuardianMath.DEFAULT_AVENGER_XP);
    }

    public static double guardianHealXp() {
        return safe(GUARDIAN_HEAL_XP, dev.amman.proficiency.skill.GuardianMath.DEFAULT_HEAL_XP_PER_HEALTH);
    }

    public static double guardianReviveXp() {
        return safe(GUARDIAN_REVIVE_XP, dev.amman.proficiency.skill.GuardianMath.DEFAULT_REVIVE_XP);
    }

    public static double chargerFirstBloodXp() {
        return safe(CHARGER_FIRST_BLOOD_XP, dev.amman.proficiency.skill.ChargerMath.DEFAULT_FIRST_BLOOD_XP);
    }

    public static double chargerChargeXp() {
        return safe(CHARGER_CHARGE_XP, dev.amman.proficiency.skill.ChargerMath.DEFAULT_CHARGE_XP_PER_BLOCK);
    }

    public static double chargerSpearheadKillXp() {
        return safe(CHARGER_SPEARHEAD_KILL_XP, dev.amman.proficiency.skill.ChargerMath.DEFAULT_SPEARHEAD_KILL_XP);
    }

    public static double tacticianSupportXp() {
        return safe(TACTICIAN_SUPPORT_XP, dev.amman.proficiency.skill.TacticianMath.DEFAULT_SUPPORT_XP_PER_DAMAGE);
    }

    public static double tacticianRescueXp() {
        return safe(TACTICIAN_RESCUE_XP, dev.amman.proficiency.skill.TacticianMath.DEFAULT_RESCUE_XP);
    }

    public static double tacticianOverwatchXp() {
        return safe(TACTICIAN_OVERWATCH_XP, dev.amman.proficiency.skill.TacticianMath.DEFAULT_OVERWATCH_XP_PER_BLOCK);
    }

    /** The configured base XP of one Husbandry job. */
    public static double husbandryXp(dev.amman.proficiency.skill.HusbandryMath.Job job) {
        return switch (job) {
            case BREED -> safe(HUSBANDRY_BREED_XP, job.defaultXp());
            case TAME -> safe(HUSBANDRY_TAME_XP, job.defaultXp());
            case SHEAR -> safe(HUSBANDRY_SHEAR_XP, job.defaultXp());
            case MILK -> safe(HUSBANDRY_MILK_XP, job.defaultXp());
            case BRUSH -> safe(HUSBANDRY_BRUSH_XP, job.defaultXp());
            case FEED -> safe(HUSBANDRY_FEED_XP, job.defaultXp());
            case HONEY -> safe(HUSBANDRY_HONEY_XP, job.defaultXp());
        };
    }

    private static volatile java.util.List<? extends String> ridingRatesSource;
    private static volatile java.util.Map<String, Double> ridingRatesParsed;

    /** The XP per metre table: the defaults with the config's lines laid over them (parsed once per change). */
    public static java.util.Map<String, Double> ridingRates() {
        if (!SPEC.isLoaded()) {
            return dev.amman.proficiency.skill.RidingMath.DEFAULT_RATES;
        }
        java.util.List<? extends String> lines = RIDING_RATES.get();
        java.util.Map<String, Double> parsed = ridingRatesParsed;
        if (parsed == null || !lines.equals(ridingRatesSource)) {
            parsed = dev.amman.proficiency.skill.RidingMath.parseRates(lines);
            ridingRatesParsed = parsed;
            ridingRatesSource = java.util.List.copyOf(lines);
        }
        return parsed;
    }

    public static double commerceTradeFactor() {
        return safe(COMMERCE_TRADE_FACTOR, 1.0);
    }

    public static double commerceBarterXp() {
        return safe(COMMERCE_BARTER_XP, dev.amman.proficiency.skill.CommerceMath.BARTER_XP);
    }

    public static boolean commerceDiscount() {
        return !SPEC.isLoaded() || COMMERCE_DISCOUNT.get();
    }

    public static boolean recordsEnabled() {
        return !SPEC.isLoaded() || RECORDS_ENABLED.get();
    }

    public static int tempoWindow() {
        return SPEC.isLoaded() ? TEMPO_WINDOW.get() : 60;
    }

    public static double tempoStep() {
        return safe(TEMPO_STEP, 0.02);
    }

    public static int tempoCombatWindow() {
        return SPEC.isLoaded() ? TEMPO_COMBAT_WINDOW.get() : 140;
    }

    public static double tempoCombatStep() {
        return safe(TEMPO_COMBAT_STEP, 0.05);
    }

    public static double tempoCombatCritWindowMul() {
        return safe(TEMPO_COMBAT_CRIT_WINDOW_MUL, 2.0);
    }

    public static double tempoCap() {
        return safe(TEMPO_CAP, 0.50);
    }

    public static boolean haulEnabled() {
        return !SPEC.isLoaded() || HAUL_ENABLED.get();
    }

    public static boolean hearthEnabled() {
        return !SPEC.isLoaded() || HEARTH_ENABLED.get();
    }

    public static double hearthRange() {
        return safe(HEARTH_RANGE, 24.0);
    }

    public static int hearthRangeBelow() {
        return SPEC.isLoaded() ? HEARTH_RANGE_BELOW.get() : 8;
    }

    /** Reach's top radius; never below the base range. */
    public static double hearthRangeMax() {
        return Math.max(hearthRange(), safe(HEARTH_RANGE_MAX, 64.0));
    }

    public static double hearthReachFullXp() {
        return safe(HEARTH_REACH_FULL, 5000.0);
    }

    public static int hearthRangeAbove() {
        return SPEC.isLoaded() ? HEARTH_RANGE_ABOVE.get() : 16;
    }

    public static int hearthMinSpacing() {
        return SPEC.isLoaded() ? HEARTH_MIN_SPACING.get() : 64;
    }

    public static int hearthBuildSeconds() {
        return SPEC.isLoaded() ? HEARTH_BUILD_SECONDS.get() : 60;
    }

    public static boolean hearthBuildKeepsChunk() {
        return !SPEC.isLoaded() || HEARTH_BUILD_KEEPS_CHUNK.get();
    }

    public static int hearthBuildChunkWaitMinutes() {
        return SPEC.isLoaded() ? HEARTH_BUILD_CHUNK_WAIT.get() : 30;
    }

    public static boolean hearthSieges() {
        return !SPEC.isLoaded() || HEARTH_SIEGES.get();
    }

    public static double homeShare() {
        return safe(HOME_SHARE, dev.amman.proficiency.skill.HomebuilderMath.DEFAULT_SHARE);
    }

    public static double homeStageXp() {
        return safe(HOME_STAGE_XP, dev.amman.proficiency.skill.HomebuilderMath.DEFAULT_STAGE_XP);
    }

    public static double homeComfortXp() {
        return safe(HOME_COMFORT_XP, dev.amman.proficiency.skill.HomebuilderMath.DEFAULT_COMFORT_XP);
    }

    public static double homeCharacterXp() {
        return safe(HOME_CHARACTER_XP, dev.amman.proficiency.skill.HomebuilderMath.DEFAULT_CHARACTER_XP);
    }

    public static double homeSiegeXp() {
        return safe(HOME_SIEGE_XP, dev.amman.proficiency.skill.HomebuilderMath.DEFAULT_SIEGE_XP);
    }

    public static double homeFeastXp() {
        return safe(HOME_FEAST_XP, dev.amman.proficiency.skill.HomebuilderMath.DEFAULT_FEAST_XP);
    }

    public static double homeTalesShare() {
        return safe(HOME_TALES_SHARE, dev.amman.proficiency.skill.HomebuilderMath.DEFAULT_TALES_SHARE);
    }

    public static double homeChronicleXp() {
        return safe(HOME_CHRONICLE_XP, dev.amman.proficiency.skill.HomebuilderMath.DEFAULT_CHRONICLE_XP);
    }

    public static int houseProudTicks() {
        return SPEC.isLoaded() ? HOUSE_PROUD_TICKS.get()
                : dev.amman.proficiency.skill.HomebuilderMath.DEFAULT_HOUSE_PROUD_TICKS;
    }

    public static int houseProudCooldownTicks() {
        return SPEC.isLoaded() ? HOUSE_PROUD_COOLDOWN.get()
                : dev.amman.proficiency.skill.HomebuilderMath.DEFAULT_HOUSE_PROUD_COOLDOWN_TICKS;
    }

    public static int abilityUnlockLevel() {
        return SPEC.isLoaded() ? ABILITY_UNLOCK_LEVEL.get() : 50;
    }

    public static int abilityDuration() {
        return SPEC.isLoaded() ? ABILITY_DURATION.get() : 400;
    }

    public static int abilityCooldown() {
        return SPEC.isLoaded() ? ABILITY_COOLDOWN.get() : 6000;
    }

    public static boolean enabled(Skill skill) {
        return !SPEC.isLoaded() || ENABLED.get(skill).get();
    }

    public static double maxBonus(Skill skill) {
        return safe(MAX_BONUS.get(skill), skill.defaultMaxBonus());
    }

    public static double xpRate(Skill skill) {
        return safe(XP_RATE.get(skill), 1.0);
    }

    public static boolean procsEnabled() {
        return !SPEC.isLoaded() || ENABLE_PROCS.get();
    }

    public static int procUnlockLevel() {
        return SPEC.isLoaded() ? PROC_UNLOCK_LEVEL.get() : 25;
    }

    public static boolean signatureNeedsRoot() {
        return !SPEC.isLoaded() || SIGNATURE_NEEDS_ROOT.get();
    }

    public static double procFloor() {
        return safe(PROC_FLOOR, 0.05);
    }

    public static double procChance(Skill skill) {
        return safe(PROC_CHANCE.get(skill), skill.defaultProcChance());
    }
}
