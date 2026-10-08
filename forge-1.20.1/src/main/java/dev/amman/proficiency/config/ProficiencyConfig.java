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
    public static final ForgeConfigSpec.IntValue TELEMETRY_RETENTION_DAYS;

    public static final ForgeConfigSpec.DoubleValue COMPANY_RADIUS;
    public static final ForgeConfigSpec.IntValue MENTOR_GAP;
    public static final ForgeConfigSpec.DoubleValue MENTOR_BONUS;
    public static final ForgeConfigSpec.DoubleValue CAMARADERIE_BONUS;
    public static final ForgeConfigSpec.DoubleValue SOCIAL_SHARE;

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

    public static final ForgeConfigSpec.IntValue STREAK_STEP_MINUTES;
    public static final ForgeConfigSpec.IntValue STREAK_MAX_STACKS;
    public static final ForgeConfigSpec.DoubleValue STREAK_XP_PER_STACK;
    public static final ForgeConfigSpec.IntValue STREAK_ACTIVE_WINDOW;

    public static final ForgeConfigSpec.IntValue TEMPO_WINDOW;
    public static final ForgeConfigSpec.DoubleValue TEMPO_STEP;
    public static final ForgeConfigSpec.DoubleValue TEMPO_CAP;

    public static final ForgeConfigSpec.IntValue ABILITY_UNLOCK_LEVEL;
    public static final ForgeConfigSpec.IntValue ABILITY_DURATION;
    public static final ForgeConfigSpec.IntValue ABILITY_COOLDOWN;

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
                .comment("Max health at which a kill is announced server-wide as a boss.",
                        "A heuristic, and a blunt one: prefer filling in the",
                        "proficiency:notable_bosses entity type tag, which this pack's owner",
                        "controls and which does not drift as mobs are added.")
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
                .comment("Multiplier on the kill bonus for mobs in the proficiency:notable_bosses tag",
                        "(or over bossHealthThreshold health). Applied after the cap.")
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
                .defineInRange("mentorLevelGap", 20, 1, 100);
        MENTOR_BONUS = b.defineInRange("mentorBonus", 0.50, 0.0, 10.0);
        CAMARADERIE_BONUS = b.defineInRange("camaraderieBonus", 0.15, 0.0, 10.0);
        SOCIAL_SHARE = b
                .comment("Social skill XP, as a share of the extra XP the company bonus added to a",
                        "grant. 0.5 with camaraderie +15% pays Social about 6.5% of what you earn",
                        "near others. Grants with no company bonus pay Social nothing.")
                .defineInRange("socialShare", 0.5, 0.0, 10.0);
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
                .comment("XP per block of distance on a ranged hit with a friend between you and the mob (6 to 30 blocks, 3 per mob).")
                .defineInRange("overwatchXpPerBlock", dev.amman.proficiency.skill.TacticianMath.DEFAULT_OVERWATCH_XP_PER_BLOCK, 0.0, 100.0);
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

        b.comment("Active abilities: twenty seconds where the signature proc stops rolling.")
                .push("abilities");
        ABILITY_UNLOCK_LEVEL = b.defineInRange("unlockLevel", 50, 1, 100);
        ABILITY_DURATION = b.defineInRange("durationTicks", 400, 20, 12000);
        ABILITY_COOLDOWN = b.defineInRange("cooldownTicks", 6000, 20, 432000);
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
        return SPEC.isLoaded() ? MENTOR_GAP.get() : 20;
    }

    public static double mentorBonus() {
        return safe(MENTOR_BONUS, 0.50);
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

    public static int tempoWindow() {
        return SPEC.isLoaded() ? TEMPO_WINDOW.get() : 60;
    }

    public static double tempoStep() {
        return safe(TEMPO_STEP, 0.02);
    }

    public static double tempoCap() {
        return safe(TEMPO_CAP, 0.50);
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

    public static double procFloor() {
        return safe(PROC_FLOOR, 0.05);
    }

    public static double procChance(Skill skill) {
        return safe(PROC_CHANCE.get(skill), skill.defaultProcChance());
    }
}
