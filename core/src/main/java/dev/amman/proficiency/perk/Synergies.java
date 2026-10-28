package dev.amman.proficiency.perk;

import dev.amman.proficiency.perk.Synergy.Grant;
import dev.amman.proficiency.perk.Synergy.Need;
import dev.amman.proficiency.skill.PlayerSkills;
import dev.amman.proficiency.skill.Skill;
import dev.amman.proficiency.skill.SkillCategory;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static dev.amman.proficiency.perk.PerkEffect.ABILITY_DURATION;
import static dev.amman.proficiency.perk.PerkEffect.BONUS;
import static dev.amman.proficiency.perk.PerkEffect.PROC_CHANCE;
import static dev.amman.proficiency.perk.PerkEffect.PROC_POWER;
import static dev.amman.proficiency.perk.PerkEffect.TEMPO;
import static dev.amman.proficiency.perk.PerkEffect.XP_RATE;
import static dev.amman.proficiency.skill.Skill.*;

/**
 * Every synergy between trees, plus Discipline, the quiet one underneath them.
 *
 * <p>Needs are pitched at "a full node", never "a full tree", so a synergy is something you reach on
 * the way rather than a reward for having finished. Most need a level-10 or level-30 node in each of
 * two skills; the few that hang off a Mastery are the late-game ones, and they are the ones with the
 * bespoke behaviour.
 *
 * <p>Discipline: every twenty points spent anywhere in a category's trees makes every skill in that
 * category's passive 1% bigger, up to 10%. A fighter who has put points into swords, shields and
 * bows hits a little harder with all three, which is how practice actually works.
 */
public final class Synergies {

    public static final int DISCIPLINE_POINTS_PER_STEP = 20;
    public static final double DISCIPLINE_STEP = 0.01;
    public static final double DISCIPLINE_CAP = 0.10;

    private static final List<Synergy> ALL = new ArrayList<>();
    private static final Map<String, Synergy> BY_ID = new HashMap<>();

    private Synergies() {
    }

    static {
        add("prospectors_forge", needs(MINING, "smelter", 1, SMITHING, "fine_edge", 3),
                grants(SMITHING, XP_RATE, 1.15), "forge_smelt");
        add("woodsmans_edge", needs(WOODCUTTING, "timber_call", 5, AXES, "bloodlust", 5),
                grants(AXES, BONUS, 1.10, WOODCUTTING, PROC_CHANCE, 1.10), "woodsman");
        add("deep_delver", needs(MINING, "efficiency", 5, SPELUNKING, "cave_lore", 5),
                grants(MINING, BONUS, 1.10, SPELUNKING, XP_RATE, 1.20), "deep_delver");
        add("silent_hunter", needs(SNEAKING, "vanish", 5, ARCHERY, "piercing", 3),
                grants(SNEAKING, XP_RATE, 1.10), "ambush");
        add("juggernaut", needs(BLOCKING, "tenacity", 3, MACES, "crushing", 5),
                grants(BLOCKING, BONUS, 1.10), "juggernaut");
        // A shield arm and a thick hide: what gets past the one trains the other.
        add("stalwart", needs(BLOCKING, "reinforced", 5, ENDURANCE, "iron_constitution", 5),
                grants(ENDURANCE, BONUS, 1.10, BLOCKING, XP_RATE, 1.10), null);
        add("tidecaller", needs(TRIDENTS, "tidal_edge", 5, SWIMMING, "streamline", 5, FISHING, "lucky_line", 5),
                grants(TRIDENTS, BONUS, 1.15, SWIMMING, BONUS, 1.15), "tidecaller");
        add("hearth_and_harvest", needs(FARMING, "bountiful", 5, COOKING, "seasoning", 5),
                grants(COOKING, PROC_CHANCE, 1.20, FARMING, XP_RATE, 1.15), null);
        add("apothecary", needs(ALCHEMY, "potency", 5, COOKING, "second_helping", 5),
                grants(ALCHEMY, BONUS, 1.15, COOKING, XP_RATE, 1.15), null);
        add("arcane_engineering", needs(SPELLCASTING, "spell_surge", 5, ENGINEERING, "precision_parts", 5),
                grants(SPELLCASTING, XP_RATE, 1.15, ENGINEERING, XP_RATE, 1.15), null);
        add("siege_engineer", needs(CROSSBOWS, "heavy_bolts", 5, ENGINEERING, "spare_parts", 5),
                grants(CROSSBOWS, BONUS, 1.15, ENGINEERING, PROC_CHANCE, 1.10), null);
        add("bowyer", needs(ARCHERY, "steady_aim", 5, WOODCUTTING, "sharp_axe", 5),
                grants(ARCHERY, BONUS, 1.10, WOODCUTTING, XP_RATE, 1.10), null);
        add("monster_hunter", needs(BEASTSLAYING, "trophy", 3, SWORDS, "crosscut", 3),
                grants(BEASTSLAYING, BONUS, 1.15, SWORDS, PROC_POWER, 1.15), null);
        add("brawler", needs(UNARMED, "haymaker", 5, RUNNING, "long_legs", 5),
                grants(UNARMED, PROC_CHANCE, 1.20, RUNNING, XP_RATE, 1.10), null);
        add("pathfinder", needs(WAYFARING, "far_horizons", 5, RUNNING, "second_wind", 5, JUMPING, "high_jump", 5),
                grants(RUNNING, BONUS, 1.15, JUMPING, BONUS, 1.15, WAYFARING, XP_RATE, 1.20), null);
        add("earthshaper", needs(EXCAVATION, "wide_shovel", 1, MASONRY, "thrifty", 5),
                grants(EXCAVATION, BONUS, 1.15, MASONRY, XP_RATE, 1.15), null);
        // Travelling together: the road trains the company, and the company finds more road.
        add("caravan", needs(WAYFARING, "far_horizons", 5, SOCIAL, "open_circle", 3),
                grants(SOCIAL, XP_RATE, 1.10, WAYFARING, XP_RATE, 1.10), null);
        // Hunting in the dark: Sneaking gets you close, Nightwalker sees the kill. Not a second
        // stealth: a crouched kill in the dark is always a Moonlit (see NightwalkerEvents).
        add("night_hunter", needs(SNEAKING, "backstab", 3, NIGHTWALKER, "darkborn_bane", 3),
                grants(SNEAKING, XP_RATE, 1.10, NIGHTWALKER, PROC_CHANCE, 1.15), "night_hunter");
        // Endurance pays for the hits you take, Courage for the hits you give back while the fight
        // is against you. Fearless Heart is the bridge: a Grit (Endurance) gives Strength I.
        add("fearless_heart", needs(ENDURANCE, "stubborn", 5, COURAGE, "hold_the_line", 3),
                grants(ENDURANCE, XP_RATE, 1.10, COURAGE, PROC_CHANCE, 1.10), "fearless_heart");
        // Blocking owns the shield (Bastion shelters players near a raised shield). Bulwark is the
        // bridge: a hit you Intercept while your shield is up lands on the shield, not on you.
        add("bulwark", needs(BLOCKING, "reinforced", 5, GUARDIAN, "bodyguard", 3),
                grants(BLOCKING, XP_RATE, 1.10, GUARDIAN, BONUS, 1.10), "bulwark");
        // Courage fights the odds, Guardian keeps the oath. Killing a mob that just hurt a friend
        // (an avenger kill) always gives Courage's Rally.
        add("oathkeeper", needs(COURAGE, "hot_blood", 5, GUARDIAN, "warding", 5),
                grants(COURAGE, XP_RATE, 1.10, GUARDIAN, PROC_CHANCE, 1.10), "oathkeeper");
        // A Guardian behind a Charger boosts both (Shield and Spear): the pair bonus is in
        // ChargerEvents and needs no tree, only a Guardian at level 10 among the friends behind a
        // Charger in Spearhead. This entry is for one player trained in both roles: when either
        // player of such a pair has it, the pair's damage bonus is doubled instead of x1.5.
        add("shield_and_spear", needs(CHARGER, "trust_the_line", 3, GUARDIAN, "bodyguard", 3),
                grants(CHARGER, BONUS, 1.10, GUARDIAN, XP_RATE, 1.10), "shield_and_spear");
        // Hammer and Anvil (Charger and Tactician): a Charger's first blood on a mob another player
        // marked hits x1.5 harder, and a Tactician's hits on a mob fighting a Charger pay double XP.
        // Both need no tree (TacticianEvents). This entry is for one player trained in both roles:
        // when either player of such a pair has it, the first blood is x2 instead of x1.5.
        add("hammer_and_anvil", needs(TACTICIAN, "clear_line", 1, CHARGER, "first_in", 5),
                grants(TACTICIAN, XP_RATE, 1.10, CHARGER, BONUS, 1.10), "hammer_and_anvil");
        // Covering Fire (Guardian and Tactician): a Tactician's ranged hit on a mob that is after
        // a Guardian makes it hit players 25% softer for 3 s (TacticianEvents, no tree needed).
        // With this synergy on either of the two, 40% for 6 s.
        add("covering_fire", needs(TACTICIAN, "crossfire", 3, GUARDIAN, "cover", 3),
                grants(TACTICIAN, PROC_CHANCE, 1.10, GUARDIAN, BONUS, 1.10), "covering_fire");
        add("master_builder", needs(MASONRY, "master_mason", 1, DECORATING, "master_decorator", 1),
                grants(MASONRY, BONUS, 1.20, DECORATING, BONUS, 1.20), null);
        // Heart of the Home (docs/HOMEBUILDER-SPEC.md): the keeper of the home and the heart of the
        // group. A Feast at a Hearth counts as a House Proud for everyone at it (the shared passive
        // for 30 s, no cooldown used); see homebuilder.HouseProud.
        add("heart_of_home", needs(HOMEBUILDER, "open_house", 3, SOCIAL, "heart_of_the_group", 1),
                grants(HOMEBUILDER, BONUS, 1.10, SOCIAL, XP_RATE, 1.10), "heart_of_home");

        // Synergies 2 (docs/SYNERGIES-2-SPEC.md): about ten per tree. `null /* Bn:id */` marks a bespoke one
        // whose behaviour a later batch wires in; its special tag equals its id.
        add("turn_and_thrust", needs(SWORDS, "parry", 3, BLOCKING, "counter", 5),
                grants(SWORDS, PROC_CHANCE, 1.10, BLOCKING, XP_RATE, 1.10), "turn_and_thrust");
        add("cavalry_blade", needs(SWORDS, "keen_edge", 5, CHARGER, "first_in", 5),
                grants(SWORDS, BONUS, 1.10, CHARGER, XP_RATE, 1.10), null);
        add("bloodied_steel", needs(SWORDS, "vampiric_edge", 3, ENDURANCE, "adrenal_mend", 3),
                grants(SWORDS, XP_RATE, 1.10, ENDURANCE, XP_RATE, 1.10), null);
        add("single_combat", needs(SWORDS, "duelist", 1, COURAGE, "challenge", 3),
                grants(SWORDS, PROC_POWER, 1.10, COURAGE, XP_RATE, 1.10), "single_combat");
        add("quiet_blade", needs(SNEAKING, "backstab", 3, SWORDS, "opening", 5),
                grants(SWORDS, PROC_CHANCE, 1.10, SNEAKING, BONUS, 1.05), null);
        add("bladesmith", needs(SMITHING, "weaponwright", 1, SWORDS, "keen_edge", 3),
                grants(SWORDS, BONUS, 1.05, SMITHING, XP_RATE, 1.05), null);
        add("moonblade", needs(SWORDS, "hemorrhage", 3, NIGHTWALKER, "silver_light", 3),
                grants(SWORDS, PROC_POWER, 1.10, NIGHTWALKER, PROC_CHANCE, 1.10), null);
        add("armsmaster", needs(SWORDS, "battle_trance", 2, AXES, "rampage", 2, MACES, "bell_ringer", 2),
                grants(SWORDS, ABILITY_DURATION, 1.10, AXES, ABILITY_DURATION, 1.10, MACES, ABILITY_DURATION, 1.10), null);
        add("berserkergang", needs(AXES, "berserker", 3, ENDURANCE, "scar_tissue", 3),
                grants(AXES, PROC_POWER, 1.10, ENDURANCE, BONUS, 1.05), null);
        add("red_mist", needs(AXES, "bloodrush", 3, COURAGE, "hot_blood", 5),
                grants(AXES, PROC_CHANCE, 1.10, COURAGE, BONUS, 1.10), null);
        add("reavers_rush", needs(AXES, "sunder", 3, CHARGER, "breakthrough", 5),
                grants(AXES, BONUS, 1.05, CHARGER, PROC_CHANCE, 1.10), "reavers_rush");
        add("shieldbreaker", needs(AXES, "heavy_blows", 5, BLOCKING, "shield_bash", 3),
                grants(AXES, XP_RATE, 1.10, BLOCKING, PROC_CHANCE, 1.10), null);
        add("headtaker", needs(AXES, "headsman", 3, BEASTSLAYING, "trophy_hunter", 5),
                grants(AXES, PROC_POWER, 1.10, BEASTSLAYING, PROC_CHANCE, 1.10), null);
        add("wolfs_hour", needs(AXES, "bloodlust", 5, NIGHTWALKER, "moonrise", 5),
                grants(AXES, PROC_CHANCE, 1.05, NIGHTWALKER, XP_RATE, 1.10), null);
        add("raider", needs(AXES, "bloodrush", 2, RUNNING, "adrenaline", 3),
                grants(AXES, XP_RATE, 1.10, RUNNING, PROC_POWER, 1.10), null);
        add("falling_hammer", needs(MACES, "meteor", 3, JUMPING, "stomp", 3),
                grants(MACES, PROC_POWER, 1.10, JUMPING, XP_RATE, 1.10), "falling_hammer");
        add("anvil_body", needs(MACES, "crushing", 5, ENDURANCE, "iron_constitution", 5),
                grants(MACES, BONUS, 1.05, ENDURANCE, ABILITY_DURATION, 1.10), null);
        add("quarry_hammer", needs(MACES, "tremor", 5, MINING, "efficiency", 3),
                grants(MACES, PROC_CHANCE, 1.10, MINING, XP_RATE, 1.10), null);
        add("battering_ram", needs(MACES, "concussion", 3, CHARGER, "crash_in", 3),
                grants(MACES, XP_RATE, 1.10, CHARGER, PROC_POWER, 1.10), null);
        add("war_bell", needs(MACES, "bell_ringer", 3, COURAGE, "war_drums", 3),
                grants(MACES, PROC_POWER, 1.05, COURAGE, PROC_CHANCE, 1.10), null);
        add("smiths_maul", needs(MACES, "aftershock", 3, SMITHING, "temper", 3),
                grants(MACES, XP_RATE, 1.10, SMITHING, BONUS, 1.10), null);
        add("earthbound_oath", needs(MACES, "cratermaker", 1, GUARDIAN, "hold_fast", 2),
                grants(GUARDIAN, XP_RATE, 1.10, MACES, ABILITY_DURATION, 1.10), "earthbound_oath");
        add("harpooneer", needs(TRIDENTS, "undertow", 3, FISHING, "deep_waters", 3),
                grants(TRIDENTS, PROC_POWER, 1.10, FISHING, XP_RATE, 1.10), "harpooneer");
        add("undercurrent", needs(TRIDENTS, "riptide_surge", 2, SWIMMING, "riptide_kick", 2),
                grants(TRIDENTS, ABILITY_DURATION, 1.10, SWIMMING, ABILITY_DURATION, 1.10), null);
        add("stormcaller", needs(TRIDENTS, "conductor", 1, SPELLCASTING, "overcharge", 3),
                grants(TRIDENTS, PROC_CHANCE, 1.10, SPELLCASTING, PROC_POWER, 1.10), null);
        add("sea_wolf", needs(TRIDENTS, "impaler", 3, COURAGE, "unshaken", 3),
                grants(TRIDENTS, XP_RATE, 1.10, COURAGE, BONUS, 1.05), null);
        add("deep_sea_slayer", needs(TRIDENTS, "skewer", 3, BEASTSLAYING, "giant_slayer", 2),
                grants(TRIDENTS, PROC_POWER, 1.05, BEASTSLAYING, BONUS, 1.10), null);
        add("boarding_party", needs(TRIDENTS, "true_aim", 5, CHARGER, "head_start", 3),
                grants(TRIDENTS, BONUS, 1.05, CHARGER, XP_RATE, 1.10), null);
        add("spear_volley", needs(TRIDENTS, "tidal_edge", 3, TACTICIAN, "spotter", 5),
                grants(TACTICIAN, BONUS, 1.10, TRIDENTS, XP_RATE, 1.10), null);
        add("mariner", needs(TRIDENTS, "deep_breath", 1, WAYFARING, "trailwise", 5, FISHING, "rain_dancer", 2),
                grants(TRIDENTS, XP_RATE, 1.05, WAYFARING, BONUS, 1.10, FISHING, XP_RATE, 1.05), null);
        add("iron_monk", needs(UNARMED, "iron_skin", 3, ENDURANCE, "roll_with_it", 3),
                grants(UNARMED, BONUS, 1.05, ENDURANCE, PROC_CHANCE, 1.10), null);
        add("nerve_strike", needs(SNEAKING, "backstab", 2, UNARMED, "one_inch_punch", 1),
                grants(UNARMED, PROC_POWER, 1.10, SNEAKING, XP_RATE, 1.10), "nerve_strike");
        add("flying_kick", needs(UNARMED, "haymaker", 5, JUMPING, "leap", 3),
                grants(UNARMED, PROC_CHANCE, 1.10, JUMPING, BONUS, 1.05), null);
        // A lunge off a jump: the crit Lunge forces lands harder still.
        add("leaping_blade", needs(SWORDS, "lunge", 1, JUMPING, "high_jump", 3),
                grants(SWORDS, PROC_POWER, 1.05, JUMPING, XP_RATE, 1.10), "leaping_blade");
        add("tavern_hero", needs(UNARMED, "combo", 3, COURAGE, "battle_cry", 5),
                grants(UNARMED, TEMPO, 1.15, COURAGE, XP_RATE, 1.10), null);
        add("kitchen_brawler", needs(UNARMED, "iron_fist", 5, COOKING, "hearty", 3),
                grants(UNARMED, XP_RATE, 1.10, COOKING, BONUS, 1.05), null);
        add("drunken_fist", needs(UNARMED, "stagger", 3, ALCHEMY, "strong_brew", 3),
                grants(UNARMED, PROC_CHANCE, 1.05, ALCHEMY, XP_RATE, 1.10), null);
        add("open_palm", needs(UNARMED, "dragon_palm", 3, GUARDIAN, "mending_guard", 3),
                grants(GUARDIAN, BONUS, 1.05, UNARMED, PROC_POWER, 1.10), null);
        add("shoulder_charge", needs(UNARMED, "iron_fist", 3, CHARGER, "crash_in", 2),
                grants(UNARMED, BONUS, 1.05, CHARGER, XP_RATE, 1.05), null);
        add("pavise", needs(BLOCKING, "thorns", 3, CROSSBOWS, "kickback", 3),
                grants(BLOCKING, XP_RATE, 1.10, CROSSBOWS, PROC_CHANCE, 1.10), "pavise");
        add("unbroken_line", needs(BLOCKING, "tenacity", 3, COURAGE, "unyielding", 2),
                grants(BLOCKING, BONUS, 1.05, COURAGE, ABILITY_DURATION, 1.10), null);
        add("shieldwright", needs(BLOCKING, "spiked_rim", 2, SMITHING, "whetstone", 3),
                grants(BLOCKING, PROC_POWER, 1.10, SMITHING, XP_RATE, 1.10), null);
        add("shield_rush", needs(BLOCKING, "shield_bash", 2, CHARGER, "trust_the_line", 3),
                grants(BLOCKING, PROC_CHANCE, 1.05, CHARGER, BONUS, 1.05), null);
        add("hardtack", needs(COOKING, "comfort_food", 3, ENDURANCE, "lean_times", 2),
                grants(ENDURANCE, XP_RATE, 1.05, COOKING, PROC_CHANCE, 1.05), null);
        add("marathoner", needs(RUNNING, "endurance", 3, ENDURANCE, "deep_reserves", 3),
                grants(RUNNING, BONUS, 1.05, ENDURANCE, PROC_POWER, 1.10), null);
        add("play_dead", needs(SNEAKING, "shadowmeld", 2, ENDURANCE, "last_stand", 1),
                grants(SNEAKING, PROC_POWER, 1.10, ENDURANCE, XP_RATE, 1.10), "play_dead");
        add("moonshot", needs(ARCHERY, "eagle_eye", 5, NIGHTWALKER, "far_sight", 3),
                grants(ARCHERY, PROC_CHANCE, 1.05, NIGHTWALKER, BONUS, 1.10), null);
        add("shared_quarry", needs(ARCHERY, "hunters_mark", 3, TACTICIAN, "painted_target", 1),
                grants(ARCHERY, XP_RATE, 1.10, TACTICIAN, PROC_CHANCE, 1.10), "shared_quarry");
        add("hanging_shot", needs(ARCHERY, "skyhunter", 2, JUMPING, "feather_weight", 3),
                grants(ARCHERY, BONUS, 1.05, JUMPING, XP_RATE, 1.10), "hanging_shot");
        add("big_game_hunter", needs(ARCHERY, "longshot", 3, BEASTSLAYING, "monster_lore", 5),
                grants(ARCHERY, PROC_POWER, 1.10, BEASTSLAYING, XP_RATE, 1.10), null);
        add("ranger", needs(ARCHERY, "quiver", 3, WAYFARING, "pathfinder", 3),
                grants(ARCHERY, XP_RATE, 1.05, WAYFARING, PROC_CHANCE, 1.10), "ranger");
        add("twin_strings", needs(ARCHERY, "rain_of_arrows", 2, CROSSBOWS, "hair_trigger", 5),
                grants(ARCHERY, ABILITY_DURATION, 1.10, CROSSBOWS, PROC_CHANCE, 1.05), null);
        add("arcane_arrow", needs(ARCHERY, "deadeye", 1, SPELLCASTING, "arcane_focus", 5),
                grants(ARCHERY, PROC_POWER, 1.05, SPELLCASTING, XP_RATE, 1.10), null);
        add("boltwright", needs(SMITHING, "masterwork", 5, CROSSBOWS, "armor_piercer", 3),
                grants(CROSSBOWS, PROC_POWER, 1.10, SMITHING, PROC_CHANCE, 1.05), null);
        add("firing_line", needs(CROSSBOWS, "siege_crew", 2, TACTICIAN, "suppression", 2),
                grants(CROSSBOWS, XP_RATE, 1.10, TACTICIAN, ABILITY_DURATION, 1.10), null);
        add("arrow_slit", needs(CROSSBOWS, "quickload", 2, MASONRY, "cornerstone", 2, TACTICIAN, "headshot", 3),
                grants(CROSSBOWS, BONUS, 1.05, MASONRY, XP_RATE, 1.10, TACTICIAN, PROC_POWER, 1.10), null);
        add("snipers_nest", needs(SNEAKING, "unseen", 3, CROSSBOWS, "salvage", 3),
                grants(CROSSBOWS, XP_RATE, 1.05, SNEAKING, PROC_CHANCE, 1.10), null);
        add("sentry_post", needs(CROSSBOWS, "armor_piercer", 2, GUARDIAN, "taunt", 1),
                grants(GUARDIAN, PROC_CHANCE, 1.05, CROSSBOWS, BONUS, 1.05), null);
        add("dragonbolt", needs(CROSSBOWS, "ballista", 1, BEASTSLAYING, "boss_bane", 1),
                grants(CROSSBOWS, PROC_POWER, 1.10, BEASTSLAYING, XP_RATE, 1.15), "dragonbolt");
        add("tunneler", needs(MINING, "vein_miner", 3, EXCAVATION, "mole", 2),
                grants(MINING, BONUS, 1.05, EXCAVATION, XP_RATE, 1.10), null);
        add("steam_drill", needs(MINING, "rich_veins", 2, ENGINEERING, "overclock", 3),
                grants(MINING, PROC_POWER, 1.10, ENGINEERING, XP_RATE, 1.10), null);
        add("quarryman", needs(MINING, "prospector", 5, MASONRY, "stonecutter", 3),
                grants(MINING, XP_RATE, 1.10, MASONRY, PROC_CHANCE, 1.10), null);
        add("mineral_tincture", needs(MINING, "gem_cutter", 3, ALCHEMY, "brewers_eye", 3),
                grants(MINING, PROC_POWER, 1.05, ALCHEMY, BONUS, 1.10), null);
        add("lightless_seam", needs(MINING, "dark_sight", 1, NIGHTWALKER, "dark_adapted", 5),
                grants(MINING, XP_RATE, 1.05, NIGHTWALKER, XP_RATE, 1.10), null);
        add("seismic_survey", needs(MINING, "geologist", 3, SPELUNKING, "echolocation", 3, EXCAVATION, "archaeologist", 3),
                grants(MINING, PROC_CHANCE, 1.05, SPELUNKING, XP_RATE, 1.10, EXCAVATION, PROC_CHANCE, 1.05), "seismic_survey");
        add("orchard_keeper", needs(WOODCUTTING, "sapling_keeper", 3, FARMING, "fertile_soil", 3),
                grants(WOODCUTTING, XP_RATE, 1.10, FARMING, BONUS, 1.05), "orchard_keeper");
        add("charcoal_burner", needs(WOODCUTTING, "sawmill", 1, SMITHING, "forgefire", 2),
                grants(SMITHING, PROC_CHANCE, 1.05, WOODCUTTING, PROC_POWER, 1.05), "charcoal_burner");
        add("joinery", needs(WOODCUTTING, "heartwood", 3, DECORATING, "thrift_shop", 5),
                grants(WOODCUTTING, BONUS, 1.05, DECORATING, XP_RATE, 1.10), null);
        add("greenway", needs(WOODCUTTING, "woodland_stride", 2, WAYFARING, "fresh_air", 3),
                grants(WAYFARING, PROC_CHANCE, 1.05, WOODCUTTING, ABILITY_DURATION, 1.10), null);
        add("full_woodpile", needs(WOODCUTTING, "seasoned", 2, HOMEBUILDER, "kindling", 5),
                grants(HOMEBUILDER, PROC_CHANCE, 1.10, WOODCUTTING, XP_RATE, 1.05), null);
        add("smokehouse", needs(WOODCUTTING, "forager", 3, COOKING, "camp_kitchen", 1),
                grants(COOKING, XP_RATE, 1.10, WOODCUTTING, PROC_CHANCE, 1.05), null);
        add("timber_frame", needs(WOODCUTTING, "canopy", 1, MASONRY, "long_arm", 3),
                grants(MASONRY, BONUS, 1.05, WOODCUTTING, PROC_POWER, 1.05), null);
        add("topsoil", needs(EXCAVATION, "sifter", 3, FARMING, "seed_saver", 3),
                grants(EXCAVATION, PROC_POWER, 1.10, FARMING, XP_RATE, 1.10), null);
        add("potters_clay", needs(EXCAVATION, "quick_dig", 5, DECORATING, "inspiration", 5),
                grants(EXCAVATION, XP_RATE, 1.10, DECORATING, PROC_CHANCE, 1.10), null);
        add("groundwork", needs(EXCAVATION, "earthmover", 2, HOMEBUILDER, "scaffolder", 3),
                grants(EXCAVATION, BONUS, 1.05, HOMEBUILDER, XP_RATE, 1.10), null);
        add("deep_strata", needs(EXCAVATION, "fossil_hunter", 1, SPELUNKING, "deep_dweller", 3),
                grants(EXCAVATION, XP_RATE, 1.10, SPELUNKING, BONUS, 1.10), null);
        add("earthworks", needs(EXCAVATION, "landslide_lore", 5, ENGINEERING, "wrench_wizard", 3),
                grants(EXCAVATION, PROC_CHANCE, 1.10, ENGINEERING, BONUS, 1.05), null);
        add("dredger", needs(EXCAVATION, "wide_shovel", 1, SWIMMING, "aqua_eyes", 1),
                grants(EXCAVATION, ABILITY_DURATION, 1.10, SWIMMING, XP_RATE, 1.10), "dredger");
        add("herbalist", needs(FARMING, "harvest_moon", 5, ALCHEMY, "catalyst", 3),
                grants(ALCHEMY, XP_RATE, 1.10, FARMING, PROC_CHANCE, 1.05), null);
        add("barn_raising", needs(FARMING, "husbandry", 3, SOCIAL, "kinship", 2),
                grants(FARMING, XP_RATE, 1.10, SOCIAL, BONUS, 1.05), null);
        add("moon_garden", needs(FARMING, "harvest_festival", 2, NIGHTWALKER, "night_owl", 3),
                grants(FARMING, PROC_POWER, 1.10, NIGHTWALKER, XP_RATE, 1.05), "moon_garden");
        add("irrigation", needs(FARMING, "scythe", 1, ENGINEERING, "redstone_savant", 3),
                grants(FARMING, ABILITY_DURATION, 1.10, ENGINEERING, PROC_CHANCE, 1.10), null);
        add("flower_beds", needs(FARMING, "druid", 1, DECORATING, "feng_shui", 3),
                grants(DECORATING, PROC_CHANCE, 1.10, FARMING, XP_RATE, 1.05), null);
        add("shore_supper", needs(FISHING, "fresh_catch", 3, COOKING, "banquet", 3),
                grants(FISHING, PROC_CHANCE, 1.05, COOKING, XP_RATE, 1.10), "shore_supper");
        add("riverborn", needs(FISHING, "patient", 5, SWIMMING, "dolphin", 5),
                grants(FISHING, BONUS, 1.05, SWIMMING, PROC_CHANCE, 1.10), null);
        add("wandering_angler", needs(FISHING, "rain_dancer", 2, WAYFARING, "globetrotter", 2),
                grants(FISHING, XP_RATE, 1.10, WAYFARING, ABILITY_DURATION, 1.10), null);
        add("fishing_trip", needs(FISHING, "double_hook", 3, SOCIAL, "stay_a_while", 3),
                grants(FISHING, PROC_CHANCE, 1.05, SOCIAL, XP_RATE, 1.10), "fishing_trip");
        add("night_line", needs(FISHING, "old_salt", 2, NIGHTWALKER, "long_night", 2),
                grants(FISHING, ABILITY_DURATION, 1.10, NIGHTWALKER, ABILITY_DURATION, 1.10), null);
        add("brine_and_bottle", needs(FISHING, "sunken_treasure", 1, ALCHEMY, "distillery", 1),
                grants(ALCHEMY, PROC_CHANCE, 1.10, FISHING, PROC_POWER, 1.05), null);
        add("fish_story", needs(FISHING, "lucky_line", 3, HOMEBUILDER, "storyteller", 2),
                grants(HOMEBUILDER, BONUS, 1.05, FISHING, XP_RATE, 1.05), null);
        add("cutpurses_flight", needs(RUNNING, "escape_artist", 2, SNEAKING, "cat_landing", 3),
                grants(SNEAKING, XP_RATE, 1.10, RUNNING, PROC_CHANCE, 1.05), null);
        add("running_start", needs(RUNNING, "roadrunner", 2, JUMPING, "stomp", 2, CHARGER, "rolling_thunder", 3),
                grants(CHARGER, PROC_POWER, 1.05, RUNNING, XP_RATE, 1.10, JUMPING, PROC_POWER, 1.10), null);
        add("freerunner", needs(RUNNING, "parkour", 1, JUMPING, "bounce", 1),
                grants(RUNNING, BONUS, 1.05, JUMPING, PROC_CHANCE, 1.10), "freerunner");
        add("running_mates", needs(RUNNING, "slipstream", 1, SOCIAL, "in_step", 1),
                grants(SOCIAL, ABILITY_DURATION, 1.10, RUNNING, ABILITY_DURATION, 1.10), null);
        add("first_responder", needs(RUNNING, "windwalker", 1, GUARDIAN, "watchful_eye", 5),
                grants(GUARDIAN, XP_RATE, 1.10, RUNNING, PROC_POWER, 1.05), "first_responder");
        add("hidden_spotter", needs(SNEAKING, "quiet_feet", 5, TACTICIAN, "clear_line", 1),
                grants(TACTICIAN, XP_RATE, 1.10, SNEAKING, BONUS, 1.05), null);
        add("hush_of_the_deep", needs(SNEAKING, "assassin", 1, SPELUNKING, "warden_whisper", 2),
                grants(SPELUNKING, PROC_CHANCE, 1.10, SNEAKING, PROC_POWER, 1.05), null);
        add("high_diver", needs(JUMPING, "safe_landing", 5, SWIMMING, "deep_lungs", 3),
                grants(JUMPING, XP_RATE, 1.05, SWIMMING, BONUS, 1.05), "high_diver");
        add("steeplejack", needs(JUMPING, "cat_reflexes", 2, MASONRY, "safety_harness", 3),
                grants(MASONRY, XP_RATE, 1.10, JUMPING, ABILITY_DURATION, 1.10), "steeplejack");
        add("pounce", needs(JUMPING, "stomp", 3, CHARGER, "unstoppable", 2),
                grants(CHARGER, ABILITY_DURATION, 1.10, JUMPING, PROC_POWER, 1.05), null);
        add("chasm_leaper", needs(SPELUNKING, "rope_climber", 1, JUMPING, "skywalker", 1),
                grants(SPELUNKING, BONUS, 1.05, JUMPING, BONUS, 1.05), null);
        add("lifeguard", needs(SWIMMING, "current_rider", 3, GUARDIAN, "warding", 5),
                grants(SWIMMING, XP_RATE, 1.10, GUARDIAN, BONUS, 1.05), "lifeguard");
        add("sump_diver", needs(SWIMMING, "pearl_diver", 1, SPELUNKING, "cave_sense", 5),
                grants(SPELUNKING, PROC_CHANCE, 1.05, SWIMMING, PROC_POWER, 1.10), null);
        add("island_hopper", needs(SWIMMING, "leviathan_blood", 1, WAYFARING, "world_walker", 1),
                grants(WAYFARING, BONUS, 1.05, SWIMMING, ABILITY_DURATION, 1.10), null);
        add("salt_cure", needs(SWIMMING, "current_rider", 2, ALCHEMY, "antidote", 3),
                grants(ALCHEMY, ABILITY_DURATION, 1.10, SWIMMING, BONUS, 1.05), null);
        add("gearwright", needs(SMITHING, "reforge", 3, ENGINEERING, "cogsmith", 3),
                grants(SMITHING, PROC_POWER, 1.10, ENGINEERING, XP_RATE, 1.05), null);
        add("runesmith", needs(SMITHING, "disenchanter", 1, ALCHEMY, "runescribe", 1),
                grants(SMITHING, ABILITY_DURATION, 1.10, ALCHEMY, PROC_POWER, 1.10), null);
        // The smith's eye for a good piece and the caster's feel for the weave: Whetstone and
        // Temper gifts come more often and can land at II or III (Spellforge, TalentCraftingEvents).
        add("spellforged", needs(SMITHING, "temper", 3, SPELLCASTING, "spell_surge", 5),
                grants(SMITHING, PROC_CHANCE, 1.05, SPELLCASTING, XP_RATE, 1.05), "spellforged");
        add("guild_prices", needs(SMITHING, "haggler", 5, SOCIAL, "good_word", 2),
                grants(SMITHING, BONUS, 1.05, SOCIAL, PROC_CHANCE, 1.10), null);
        add("wrought_iron", needs(SMITHING, "legendary", 1, DECORATING, "curator", 3),
                grants(DECORATING, PROC_CHANCE, 1.05, SMITHING, TEMPO, 1.15), null);
        add("potluck", needs(COOKING, "shared_meal", 2, SOCIAL, "old_friends", 3),
                grants(COOKING, PROC_POWER, 1.10, SOCIAL, XP_RATE, 1.05), null);
        add("sunday_roast", needs(COOKING, "family_recipe", 1, HOMEBUILDER, "warm_welcome", 2, FARMING, "green_thumb", 1),
                grants(COOKING, BONUS, 1.05, HOMEBUILDER, PROC_CHANCE, 1.05, FARMING, BONUS, 1.05), "sunday_roast");
        add("trail_rations", needs(WAYFARING, "campfire", 1, COOKING, "hearty", 3),
                grants(WAYFARING, XP_RATE, 1.05, COOKING, PROC_CHANCE, 1.05), null);
        add("mana_tonic", needs(ALCHEMY, "elixir_lore", 2, SPELLCASTING, "mana_font", 3),
                grants(SPELLCASTING, BONUS, 1.05, ALCHEMY, TEMPO, 1.15), null);
        add("field_medic", needs(ALCHEMY, "panacea", 1, GUARDIAN, "heartshare", 2),
                grants(GUARDIAN, PROC_CHANCE, 1.10, ALCHEMY, XP_RATE, 1.10), "field_medic");
        add("starward", needs(SPELLCASTING, "spell_ward", 3, NIGHTWALKER, "phantom_ward", 2),
                grants(SPELLCASTING, XP_RATE, 1.10, NIGHTWALKER, BONUS, 1.05), null);
        add("ley_hall", needs(SPELLCASTING, "deep_well", 2, DECORATING, "lamplighter", 3),
                grants(DECORATING, BONUS, 1.05, SPELLCASTING, PROC_CHANCE, 1.05), null);
        add("arcane_circle", needs(SPELLCASTING, "arcane_rhythm", 2, SOCIAL, "teaching", 3),
                grants(SOCIAL, BONUS, 1.05, SPELLCASTING, TEMPO, 1.15), null);
        add("battlemage", needs(SPELLCASTING, "echo_cast", 1, COURAGE, "giant_slayer", 2),
                grants(COURAGE, PROC_POWER, 1.10, SPELLCASTING, PROC_POWER, 1.05), null);
        add("hexbane", needs(BEASTSLAYING, "bane", 3, SPELLCASTING, "overcharge", 2),
                grants(BEASTSLAYING, PROC_POWER, 1.10, SPELLCASTING, BONUS, 1.05), null);
        add("fortifier", needs(ENGINEERING, "blueprint", 1, MASONRY, "bulk_order", 2),
                grants(ENGINEERING, BONUS, 1.05, MASONRY, PROC_CHANCE, 1.10), null);
        add("clockwork_home", needs(ENGINEERING, "assembly_line", 2, HOMEBUILDER, "long_pull", 3),
                grants(ENGINEERING, TEMPO, 1.15, HOMEBUILDER, XP_RATE, 1.05), null);
        add("field_engineer", needs(ENGINEERING, "chief_engineer", 1, TACTICIAN, "quartermaster", 2),
                grants(ENGINEERING, PROC_POWER, 1.10, TACTICIAN, XP_RATE, 1.05), null);
        add("bounty_road", needs(BEASTSLAYING, "scavenger", 3, WAYFARING, "inspired", 2),
                grants(BEASTSLAYING, XP_RATE, 1.10, WAYFARING, PROC_POWER, 1.10), null);
        add("witching_hour", needs(BEASTSLAYING, "hunters_instinct", 2, NIGHTWALKER, "darkborn_bane", 2),
                grants(BEASTSLAYING, ABILITY_DURATION, 1.10, NIGHTWALKER, PROC_POWER, 1.10), null);
        add("colossus_bane", needs(BEASTSLAYING, "giant_slayer", 3, COURAGE, "giant_slayer", 3),
                grants(COURAGE, XP_RATE, 1.10, BEASTSLAYING, BONUS, 1.05), "colossus_bane");
        add("pathscout", needs(WAYFARING, "seasoned_traveler", 3, TACTICIAN, "quick_call", 5),
                grants(TACTICIAN, PROC_CHANCE, 1.05, WAYFARING, XP_RATE, 1.05), null);
        add("glowworm_grotto", needs(SPELUNKING, "glowstone_heart", 3, DECORATING, "curator", 2),
                grants(DECORATING, TEMPO, 1.15, SPELUNKING, PROC_POWER, 1.05), null);
        add("shoring", needs(SPELUNKING, "cave_in", 1, MASONRY, "safety_harness", 2),
                grants(SPELUNKING, ABILITY_DURATION, 1.10, MASONRY, PROC_CHANCE, 1.05), null);
        add("root_cellar", needs(SPELUNKING, "cave_lore", 3, HOMEBUILDER, "night_watch", 1),
                grants(HOMEBUILDER, ABILITY_DURATION, 1.10, SPELUNKING, XP_RATE, 1.05), null);
        add("hearthstone", needs(MASONRY, "rapid_build", 5, HOMEBUILDER, "ember_heart", 3),
                grants(HOMEBUILDER, PROC_POWER, 1.10, MASONRY, TEMPO, 1.15), null);
        add("housewarming", needs(DECORATING, "hygge", 3, HOMEBUILDER, "light_step", 5),
                grants(DECORATING, XP_RATE, 1.05, HOMEBUILDER, BONUS, 1.05), "housewarming");

        // Husbandry (docs/batch-log/husbandry.md): ten pairings with the trees it works beside.
        add("farm_to_table", needs(FARMING, "green_thumb", 1, HUSBANDRY, "good_stock", 3),
                grants(HUSBANDRY, XP_RATE, 1.10, FARMING, BONUS, 1.05), null);
        add("feed_the_herd", needs(FARMING, "seed_saver", 2, HUSBANDRY, "gentle_hands", 2),
                grants(FARMING, XP_RATE, 1.05, HUSBANDRY, XP_RATE, 1.10), null);
        add("packmaster", needs(BEASTSLAYING, "tracking", 3, HUSBANDRY, "wolf_learns", 2),
                grants(HUSBANDRY, BONUS, 1.10, BEASTSLAYING, XP_RATE, 1.05), null);
        add("fresh_dairy", needs(COOKING, "second_helping", 3, HUSBANDRY, "fresh_pail", 2),
                grants(COOKING, BONUS, 1.05, HUSBANDRY, XP_RATE, 1.05), null);
        add("stable_roof", needs(HOMEBUILDER, "light_step", 3, HUSBANDRY, "good_stock", 3),
                grants(HOMEBUILDER, XP_RATE, 1.05, HUSBANDRY, PROC_CHANCE, 1.10), null);
        add("village_herd", needs(SOCIAL, "fellowship", 3, HUSBANDRY, "twin_luck", 3),
                grants(HUSBANDRY, PROC_CHANCE, 1.05, SOCIAL, XP_RATE, 1.10), null);
        add("watchdog", needs(GUARDIAN, "bodyguard", 2, HUSBANDRY, "shared_strength", 2),
                grants(GUARDIAN, BONUS, 1.05, HUSBANDRY, BONUS, 1.10), null);
        add("trail_companion", needs(WAYFARING, "trailwise", 3, HUSBANDRY, "gentle_hands", 3),
                grants(WAYFARING, XP_RATE, 1.05, HUSBANDRY, XP_RATE, 1.05), null);
        add("brave_hounds", needs(COURAGE, "battle_cry", 3, HUSBANDRY, "shared_strength", 3),
                grants(COURAGE, BONUS, 1.05, HUSBANDRY, PROC_POWER, 1.10), null);
        add("honey_tonic", needs(ALCHEMY, "potency", 3, HUSBANDRY, "long_summer", 2),
                grants(HUSBANDRY, ABILITY_DURATION, 1.10, ALCHEMY, XP_RATE, 1.05), null);
        // Riding (docs/batch-log/riding.md): ten pairings with the trees it rides beside.
        add("open_country", needs(RUNNING, "second_wind", 3, RIDING, "fair_wind", 3),
                grants(RIDING, XP_RATE, 1.05, RUNNING, XP_RATE, 1.05), null);
        add("ferryman", needs(SWIMMING, "streamline", 3, RIDING, "barding", 2),
                grants(RIDING, XP_RATE, 1.05, SWIMMING, BONUS, 1.05), null);
        add("heavy_cavalry", needs(CHARGER, "head_start", 2, RIDING, "couched_lance", 2),
                grants(CHARGER, BONUS, 1.05, RIDING, PROC_POWER, 1.10), null);
        add("horse_whisperer", needs(HUSBANDRY, "gentle_hands", 2, RIDING, "whistle", 2),
                grants(RIDING, BONUS, 1.05, HUSBANDRY, XP_RATE, 1.05), null);
        add("horse_archer", needs(ARCHERY, "steady_aim", 3, RIDING, "saddle_archer", 2),
                grants(ARCHERY, BONUS, 1.05, RIDING, PROC_CHANCE, 1.10), null);
        add("mounted_hunt", needs(BEASTSLAYING, "monster_lore", 3, RIDING, "couched_lance", 3),
                grants(BEASTSLAYING, XP_RATE, 1.05, RIDING, PROC_POWER, 1.10), null);
        add("rocketeer", needs(ENGINEERING, "spare_parts", 3, RIDING, "rocket_saver", 2),
                grants(RIDING, ABILITY_DURATION, 1.10, ENGINEERING, XP_RATE, 1.05), null);
        add("hard_rider", needs(ENDURANCE, "iron_constitution", 3, RIDING, "good_seat", 3),
                grants(ENDURANCE, BONUS, 1.05, RIDING, XP_RATE, 1.10), null);
        add("outrider", needs(TACTICIAN, "spotter", 3, RIDING, "fair_wind", 3),
                grants(TACTICIAN, BONUS, 1.05, RIDING, PROC_CHANCE, 1.05), null);
        add("cavalry_shield", needs(BLOCKING, "reinforced", 3, RIDING, "high_seat", 2),
                grants(BLOCKING, BONUS, 1.05, RIDING, BONUS, 1.10), null);
        // Commerce (docs/batch-log/commerce.md): ten pairings with the trees that sell to the villagers.
        add("market_day", needs(SOCIAL, "good_word", 2, COMMERCE, "fair_dealing", 3),
                grants(COMMERCE, XP_RATE, 1.05, SOCIAL, BONUS, 1.05), null);
        add("fresh_stock", needs(FISHING, "fresh_catch", 2, COMMERCE, "last_piece", 2),
                grants(FISHING, XP_RATE, 1.05, COMMERCE, BONUS, 1.05), null);
        add("book_deal", needs(SPELLCASTING, "arcane_focus", 3, COMMERCE, "silver_tongue", 3),
                grants(SPELLCASTING, XP_RATE, 1.05, COMMERCE, PROC_CHANCE, 1.10), null);
        add("pantry_account", needs(COOKING, "seasoning", 3, COMMERCE, "sweetener", 2),
                grants(COOKING, BONUS, 1.05, COMMERCE, XP_RATE, 1.05), null);
        add("apothecary_trade", needs(ALCHEMY, "potency", 3, COMMERCE, "regulars", 2),
                grants(ALCHEMY, XP_RATE, 1.05, COMMERCE, PROC_POWER, 1.10), null);
        add("stone_contract", needs(MASONRY, "thrifty", 3, COMMERCE, "fair_dealing", 4),
                grants(MASONRY, XP_RATE, 1.05, COMMERCE, BONUS, 1.05), null);
        add("good_furnishings", needs(DECORATING, "thrift_shop", 3, COMMERCE, "silver_tongue", 2),
                grants(DECORATING, BONUS, 1.05, COMMERCE, PROC_CHANCE, 1.05), null);
        add("dig_for_gold", needs(EXCAVATION, "quick_dig", 3, COMMERCE, "gold_rush", 2),
                grants(EXCAVATION, XP_RATE, 1.05, COMMERCE, XP_RATE, 1.10), null);
        add("trade_tools", needs(ENGINEERING, "precision_parts", 3, COMMERCE, "better_deals", 2),
                grants(ENGINEERING, XP_RATE, 1.05, COMMERCE, ABILITY_DURATION, 1.10), null);
        add("fletchers_stall", needs(ARCHERY, "fletching", 3, COMMERCE, "old_debts", 2),
                grants(ARCHERY, BONUS, 1.05, COMMERCE, BONUS, 1.10), null);
        add("salon", needs(DECORATING, "host", 2, SOCIAL, "strength_in_numbers", 2),
                grants(SOCIAL, PROC_CHANCE, 1.05, DECORATING, ABILITY_DURATION, 1.10), null);

        // The one that is about the whole character rather than a pairing.
        Synergy renaissance = new Synergy("renaissance", List.of(), 3,
                List.of(new Grant(null, XP_RATE, 1.10), new Grant(null, BONUS, 1.05)), null);
        ALL.add(renaissance);
        BY_ID.put(renaissance.id(), renaissance);
    }

    private static void add(String id, List<Need> needs, List<Grant> grants, String special) {
        Synergy synergy = new Synergy(id, needs, 0, grants, special);
        ALL.add(synergy);
        BY_ID.put(id, synergy);
    }

    private static List<Need> needs(Object... parts) {
        List<Need> needs = new ArrayList<>();
        for (int i = 0; i < parts.length; i += 3) {
            needs.add(new Need((Skill) parts[i], (String) parts[i + 1], (Integer) parts[i + 2]));
        }
        return List.copyOf(needs);
    }

    private static List<Grant> grants(Object... parts) {
        List<Grant> grants = new ArrayList<>();
        for (int i = 0; i < parts.length; i += 3) {
            grants.add(new Grant((Skill) parts[i], (PerkEffect) parts[i + 1], (Double) parts[i + 2]));
        }
        return List.copyOf(grants);
    }

    public static List<Synergy> all() {
        return List.copyOf(ALL);
    }

    public static Synergy byId(String id) {
        return BY_ID.get(id);
    }

    public static List<Synergy> involving(Skill skill) {
        return ALL.stream().filter(synergy -> synergy.involves(skill)
                || synergy.grants().stream().anyMatch(grant -> grant.skill() == null)).toList();
    }

    public static boolean isActive(PlayerSkills skills, Synergy synergy) {
        for (Need need : synergy.requires()) {
            Talent talent = need.talent();
            if (talent == null || skills.rank(talent) < need.minRank()) {
                return false;
            }
        }
        return synergy.grandmastersNeeded() <= 0
                || skills.grandmasters() >= synergy.grandmastersNeeded();
    }

    /** Whether any active synergy carries this special tag. */
    public static boolean hasSpecial(PlayerSkills skills, String special) {
        for (Synergy synergy : ALL) {
            if (special.equals(synergy.special()) && isActive(skills, synergy)) {
                return true;
            }
        }
        return false;
    }

    public static List<Synergy> active(PlayerSkills skills) {
        return ALL.stream().filter(synergy -> isActive(skills, synergy)).toList();
    }

    /** Product of every active synergy's grant for this skill and effect. 1.0 when none apply. */
    public static double multiplier(PlayerSkills skills, Skill skill, PerkEffect effect) {
        double product = 1.0;
        for (Synergy synergy : ALL) {
            boolean relevant = false;
            for (Grant grant : synergy.grants()) {
                if (grant.effect() == effect && grant.appliesTo(skill)) {
                    relevant = true;
                    break;
                }
            }
            if (!relevant || !isActive(skills, synergy)) {
                continue;
            }
            for (Grant grant : synergy.grants()) {
                if (grant.effect() == effect && grant.appliesTo(skill)) {
                    product *= grant.multiplier();
                }
            }
        }
        return product;
    }

    /** Discipline's contribution to a category's passive, as a fraction: 0.0 to 0.10. */
    public static double discipline(PlayerSkills skills, SkillCategory category) {
        int spent = skills.pointsSpentInCategory(category);
        double steps = spent / DISCIPLINE_POINTS_PER_STEP;
        return Math.min(DISCIPLINE_CAP, steps * DISCIPLINE_STEP);
    }
}
