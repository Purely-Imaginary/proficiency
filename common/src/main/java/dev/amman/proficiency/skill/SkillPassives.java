package dev.amman.proficiency.skill;

import dev.amman.proficiency.config.ProficiencyConfig;
import dev.amman.proficiency.event.TalentMath;
import dev.amman.proficiency.perk.TalentService;

import java.util.ArrayList;
import java.util.List;
import java.util.function.ToDoubleFunction;

/**
 * What each skill's passive actually changes in the game, as concrete numbers. This is the one
 * table the item tooltip, the skills panel and the tree header read ("Reach: 4.5 → 4.78"), and the
 * one place the gameplay code gets its passive formulas from, so the screen can never drift from
 * what the game does. Nothing here touches a registry or a player; it reads a {@link PlayerSkills}.
 *
 * <p>The base value of a stat is the same function run on a player with nothing: level 0, no
 * talents. It is the vanilla number where the passive changes a vanilla number (block reach 4.5,
 * maximum health 20) and 1 or 0 where it multiplies or adds a chance.
 */
public final class SkillPassives {

    /** Block interaction range in survival, the same on Forge 1.20.1 and NeoForge/Fabric 1.21.1. */
    public static final double VANILLA_BLOCK_REACH = 4.5;
    /** A player's maximum health before any modifier. */
    public static final double VANILLA_MAX_HEALTH = 20.0;
    /** How far Mining and Masonry can push block reach at level 100, in blocks, each. */
    public static final double REACH_AT_MAX = 2.0;
    /** A second drop from gathering is this share of the passive (Farming's is the whole of it). */
    public static final double DROP_SHARE = 0.25;
    /** A double catch is this share of the Fishing passive. */
    public static final double CATCH_SHARE = 0.5;
    /** Trailwise: the share of the Wayfaring passive that is walking speed. */
    public static final double WALK_SHARE = 0.5;

    /** How a stat value is written. */
    public enum Format {
        /** A multiplier: "×1.25". */
        MULT,
        /** A chance or share, 0 to 1: "7.2%". */
        PERCENT,
        /** A plain number with up to two decimals: "4.78", "24". */
        NUMBER
    }

    /** One line of the stat table: label key (and its arguments), base and current value. */
    public record Stat(String labelKey, Object[] labelArgs, Format format, double base, double current) {
    }

    private record Def(String key, Format format, ToDoubleFunction<PlayerSkills> value,
            java.util.function.Function<PlayerSkills, Object[]> args) {
        Def(String key, Format format, ToDoubleFunction<PlayerSkills> value) {
            this(key, format, value, null);
        }
    }

    private static final Object[] NO_ARGS = new Object[0];
    /** A player with nothing: level 0 everywhere, no talents. Never written to. */
    private static final PlayerSkills NOTHING = new PlayerSkills();

    private SkillPassives() {
    }

    // ---- Formulas the gameplay code reads ------------------------------------------------------

    /** Damage, break speed, spell power and the like: one plus the passive. */
    public static double more(double bonus) {
        return bonus > 0 ? 1.0 + bonus : 1.0;
    }

    /** Shield wear, shield knockback and anvil breakage: what is left after the passive, never past 90%. */
    public static double lessUpTo90(double bonus) {
        return bonus > 0 ? 1.0 - Math.min(0.9, bonus) : 1.0;
    }

    /** A second drop from gathering. Farming's chance is the passive itself, the others a share. */
    public static double dropChance(Skill skill, double bonus) {
        return skill == Skill.FARMING ? bonus : bonus * DROP_SHARE;
    }

    /** The chance a catch is doubled. */
    public static double doubleCatch(double bonus) {
        return bonus * CATCH_SHARE;
    }

    /** The share of a rod's wear the passive spares. */
    public static double rodSpared(double bonus) {
        return Math.min(0.9, bonus);
    }

    /** What the anvil asks, after Smithing haggles (never more than 75% off). */
    public static double anvilCostFactor(double bonus) {
        return bonus > 0 ? 1.0 - Math.min(0.75, bonus) : 1.0;
    }

    /** Fall distance that counts, after Jumping. */
    public static double fallFactor(double bonus) {
        return bonus > 0 ? 1.0 - Math.min(0.6, bonus * 0.5) : 1.0;
    }

    /** The chance a tick of drowning costs no air. */
    public static double airSkip(double bonus) {
        return bonus > 0 ? Math.min(0.9, bonus) : 0.0;
    }

    /** How much longer the breath lasts, from the skipped ticks. */
    public static double airLasts(double bonus) {
        return 1.0 / (1.0 - airSkip(bonus));
    }

    /** The multiplier on a jump's launch speed; height goes with its square, so height is {@link #more}. */
    public static double jumpLaunch(double bonus) {
        return Math.sqrt(more(bonus));
    }

    /** Wayfaring: the walking-speed share of the passive. */
    public static double walkShare(double bonus) {
        return WALK_SHARE * bonus;
    }

    /** Maximum health with the Endurance passive on top of the base 20. */
    public static double maxHealth(double bonus) {
        return VANILLA_MAX_HEALTH * (1.0 + Math.max(0.0, bonus));
    }

    /** Blocks of reach Mining adds at {@code level}. */
    public static double miningReach(int level) {
        return REACH_AT_MAX * (level / 100.0);
    }

    /** Blocks of reach Masonry adds at {@code level}, before its talents. */
    public static double masonryReach(int level) {
        return REACH_AT_MAX * (level / 100.0);
    }

    /** Long Arm and Grand Architect: the reach Masonry's talents add. */
    public static double masonryTalentReach(PlayerSkills skills) {
        return 0.5 * TalentService.rank(skills, Skill.MASONRY, "long_arm")
                + (TalentService.rank(skills, Skill.MASONRY, "architect_reach") > 0 ? 2.0 : 0.0);
    }

    /** Foes after you that the Courage passive needs for its whole bonus (the target counts). */
    public static int courageFoes(PlayerSkills skills) {
        int full = TalentService.rank(skills, Skill.COURAGE, "lionheart") > 0
                ? CourageMath.LIONHEART_FOES : CourageMath.PASSIVE_FOES;
        return full + 1;
    }

    /** The Social talents that change the company bonus, read from a player's synced skills. */
    public static SocialMath.Talents socialTalents(PlayerSkills skills) {
        return new SocialMath.Talents(
                TalentService.rank(skills, Skill.SOCIAL, "company_radius"),
                TalentService.rank(skills, Skill.SOCIAL, "camaraderie_up"),
                TalentService.rank(skills, Skill.SOCIAL, "mentor_up"),
                TalentService.rank(skills, Skill.SOCIAL, "mentor_gap"),
                TalentService.rank(skills, Skill.SOCIAL, "company_linger"),
                TalentService.rank(skills, Skill.SOCIAL, "company_crowd"),
                TalentService.rank(skills, Skill.SOCIAL, "heart_of_group") > 0);
    }

    /** The extra XP one friend nearby adds (0.15 is 15%), with the Social passive and talents. */
    public static double companyBonus(PlayerSkills skills) {
        return SocialMath.extra(new SocialMath.Company(1, false, 0.0), ProficiencyConfig.camaraderieBonus(),
                ProficiencyConfig.mentorBonus(), socialTalents(skills), skills.bonus(Skill.SOCIAL));
    }

    // ---- The table -----------------------------------------------------------------------------

    /** One stat: the skill's passive run through a function. */
    private static Def of(String key, Format format, Skill skill, java.util.function.DoubleUnaryOperator fn) {
        return new Def(key, format, skills -> fn.applyAsDouble(skills.bonus(skill)));
    }

    private static final java.util.Map<Skill, List<Def>> TABLE = new java.util.EnumMap<>(Skill.class);

    static {
        for (Skill skill : new Skill[] {Skill.SWORDS, Skill.AXES, Skill.MACES, Skill.TRIDENTS, Skill.UNARMED,
                Skill.ARCHERY, Skill.CROSSBOWS}) {
            TABLE.put(skill, List.of(of("damage", Format.MULT, skill, SkillPassives::more)));
        }
        TABLE.put(Skill.BLOCKING, List.of(
                of("shield_wear", Format.MULT, Skill.BLOCKING, SkillPassives::lessUpTo90),
                of("block_knockback", Format.MULT, Skill.BLOCKING, SkillPassives::lessUpTo90)));
        TABLE.put(Skill.ENDURANCE, List.of(
                of("max_health", Format.NUMBER, Skill.ENDURANCE, SkillPassives::maxHealth)));
        TABLE.put(Skill.COURAGE, List.of(new Def("courage_damage", Format.MULT,
                skills -> CourageMath.passiveMultiplier(skills.bonus(Skill.COURAGE), courageFoes(skills),
                        TalentService.rank(skills, Skill.COURAGE, "lionheart") > 0),
                skills -> new Object[] {courageFoes(skills)})));
        TABLE.put(Skill.GUARDIAN, List.of(
                of("guardian_ally_damage", Format.MULT, Skill.GUARDIAN, GuardianMath::passiveMultiplier)));
        TABLE.put(Skill.CHARGER, List.of(
                of("first_blood_damage", Format.MULT, Skill.CHARGER, bonus -> ChargerMath.firstBloodMultiplier(bonus, 0))));
        TABLE.put(Skill.TACTICIAN, List.of(
                of("tactician_damage", Format.MULT, Skill.TACTICIAN, TacticianMath::passiveMultiplier)));

        TABLE.put(Skill.MINING, List.of(
                of("break_speed", Format.MULT, Skill.MINING, SkillPassives::more),
                new Def("double_drop", Format.PERCENT,
                        skills -> dropChance(Skill.MINING, skills.bonus(Skill.MINING))),
                new Def("block_reach", Format.NUMBER,
                        skills -> VANILLA_BLOCK_REACH + miningReach(skills.level(Skill.MINING)))));
        for (Skill skill : new Skill[] {Skill.WOODCUTTING, Skill.EXCAVATION}) {
            TABLE.put(skill, List.of(
                    of("break_speed", Format.MULT, skill, SkillPassives::more),
                    new Def("double_drop", Format.PERCENT, skills -> dropChance(skill, skills.bonus(skill)))));
        }
        TABLE.put(Skill.FARMING, List.of(
                new Def("double_harvest", Format.PERCENT,
                        skills -> dropChance(Skill.FARMING, skills.bonus(Skill.FARMING)))));
        TABLE.put(Skill.FISHING, List.of(
                of("rod_wear", Format.MULT, Skill.FISHING, bonus -> 1.0 - rodSpared(bonus)),
                of("double_catch", Format.PERCENT, Skill.FISHING, SkillPassives::doubleCatch)));

        TABLE.put(Skill.RUNNING, List.of(of("sprint_speed", Format.MULT, Skill.RUNNING, SkillPassives::more)));
        TABLE.put(Skill.SNEAKING, List.of(of("sneak_speed", Format.MULT, Skill.SNEAKING, SkillPassives::more)));
        TABLE.put(Skill.JUMPING, List.of(
                of("jump_height", Format.MULT, Skill.JUMPING, SkillPassives::more),
                of("fall_distance", Format.MULT, Skill.JUMPING, SkillPassives::fallFactor)));
        TABLE.put(Skill.SWIMMING, List.of(
                of("swim_speed", Format.MULT, Skill.SWIMMING, SkillPassives::more),
                of("air_lasts", Format.MULT, Skill.SWIMMING, SkillPassives::airLasts)));

        TABLE.put(Skill.SMITHING, List.of(
                of("anvil_cost", Format.MULT, Skill.SMITHING, SkillPassives::anvilCostFactor),
                of("anvil_break", Format.MULT, Skill.SMITHING, SkillPassives::lessUpTo90)));
        TABLE.put(Skill.COOKING, List.of(of("second_helping", Format.PERCENT, Skill.COOKING, bonus -> bonus)));
        TABLE.put(Skill.ALCHEMY, List.of(of("potion_duration", Format.MULT, Skill.ALCHEMY, SkillPassives::more)));

        TABLE.put(Skill.SPELLCASTING, List.of(of("spell_damage", Format.MULT, Skill.SPELLCASTING, SkillPassives::more)));
        TABLE.put(Skill.ENGINEERING, List.of(of("machine_bonus", Format.PERCENT, Skill.ENGINEERING, bonus -> bonus)));

        TABLE.put(Skill.BEASTSLAYING, List.of(of("pack_damage", Format.MULT, Skill.BEASTSLAYING, SkillPassives::more)));
        TABLE.put(Skill.WAYFARING, List.of(
                of("walk_speed", Format.MULT, Skill.WAYFARING, bonus -> 1.0 + walkShare(bonus))));
        TABLE.put(Skill.SPELUNKING, List.of(
                of("cave_damage", Format.MULT, Skill.SPELUNKING, bonus -> 1.0 - TalentMath.undergroundReduction(bonus))));

        TABLE.put(Skill.MASONRY, List.of(
                new Def("placement_reach", Format.NUMBER, skills -> VANILLA_BLOCK_REACH
                        + masonryReach(skills.level(Skill.MASONRY)) + masonryTalentReach(skills)),
                of("refund_chance", Format.PERCENT, Skill.MASONRY, bonus -> bonus)));
        TABLE.put(Skill.DECORATING, List.of(of("refund_chance", Format.PERCENT, Skill.DECORATING, bonus -> bonus)));

        TABLE.put(Skill.SOCIAL, List.of(new Def("company_bonus", Format.PERCENT, SkillPassives::companyBonus)));
        TABLE.put(Skill.NIGHTWALKER, List.of(new Def("dark_sight", Format.PERCENT,
                skills -> NightwalkerMath.darkSight(skills.bonus(Skill.NIGHTWALKER),
                        TalentService.rank(skills, Skill.NIGHTWALKER, "dark_sight")))));
    }

    /**
     * The stat lines of {@code skill}'s passive for this player: label, base (a level-0 player with
     * no talents) and current value. Always at least one line.
     */
    public static List<Stat> of(PlayerSkills skills, Skill skill) {
        List<Stat> stats = new ArrayList<>(3);
        for (Def def : TABLE.get(skill)) {
            Object[] args = def.args() == null ? NO_ARGS : def.args().apply(skills);
            stats.add(new Stat("proficiency.stat." + def.key(), args, def.format(),
                    def.value().applyAsDouble(NOTHING), def.value().applyAsDouble(skills)));
        }
        return stats;
    }
}
