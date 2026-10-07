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

import static dev.amman.proficiency.perk.PerkEffect.BONUS;
import static dev.amman.proficiency.perk.PerkEffect.PROC_CHANCE;
import static dev.amman.proficiency.perk.PerkEffect.PROC_POWER;
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
