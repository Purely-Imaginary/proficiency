package dev.amman.proficiency.skill;

import java.util.Arrays;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * The thirty-eight skills. Order here is the order they appear in the panel, inside each category.
 *
 * <p>New skills go at the END. The sync packet and the XP log send skills by ordinal, so a skill
 * added in the middle would shift every later one. Saves are keyed by id and do not care.
 *
 * <p>{@code defaultMaxBonus} is the size of the passive effect at level 100, expressed as a
 * fraction. What the fraction means is up to whoever reads it: for {@link #SWORDS} it is extra
 * damage, for {@link #MINING} extra break speed, for {@link #FARMING} the chance of a second drop.
 * Each one is overridable in the server config.
 *
 * <p>{@code defaultProcChance} is the other half, and the half that makes the system worth playing.
 * Every skill has one signature moment that fires at random once the skill passes the unlock level,
 * and this is how often it fires at 100. The passive is what you have; the proc is what happens.
 */
public enum Skill {
    // Combat
    SWORDS("swords", SkillCategory.COMBAT, 0.80, 0.25),
    AXES("axes", SkillCategory.COMBAT, 0.80, 0.25),
    MACES("maces", SkillCategory.COMBAT, 0.80, 0.25),
    TRIDENTS("tridents", SkillCategory.COMBAT, 0.80, 0.25),
    UNARMED("unarmed", SkillCategory.COMBAT, 2.50, 0.25),
    BLOCKING("blocking", SkillCategory.COMBAT, 0.75, 0.3),
    // Max health: 1.00 is +100% of the base 20, so ten more hearts at level 100. The proc only
    // rolls on a hit that leaves you under 30% health, which is why its chance sits higher.
    ENDURANCE("endurance", SkillCategory.COMBAT, 1.00, 0.35),
    ARCHERY("archery", SkillCategory.COMBAT, 0.80, 0.25),
    CROSSBOWS("crossbows", SkillCategory.COMBAT, 0.80, 0.25),

    // Gathering
    MINING("mining", SkillCategory.GATHERING, 1.00, 0.2),
    WOODCUTTING("woodcutting", SkillCategory.GATHERING, 1.00, 0.15),
    EXCAVATION("excavation", SkillCategory.GATHERING, 1.00, 0.15),
    FARMING("farming", SkillCategory.GATHERING, 0.60, 0.25),
    FISHING("fishing", SkillCategory.GATHERING, 0.50, 0.2),

    // Movement
    RUNNING("running", SkillCategory.MOVEMENT, 0.30, 0.2),
    SNEAKING("sneaking", SkillCategory.MOVEMENT, 0.60, 0.2),
    JUMPING("jumping", SkillCategory.MOVEMENT, 0.35, 0.2),
    SWIMMING("swimming", SkillCategory.MOVEMENT, 0.50, 0.2),

    // Crafting
    SMITHING("smithing", SkillCategory.CRAFTING, 0.50, 0.25),
    COOKING("cooking", SkillCategory.CRAFTING, 0.50, 0.30),
    ALCHEMY("alchemy", SkillCategory.CRAFTING, 0.75, 0.20),

    // Mastery. These exist because of what is in the pack; with none of those mods loaded they
    // simply never earn anything, which is why nothing here is a hard dependency.
    SPELLCASTING("spellcasting", SkillCategory.MASTERY, 0.80, 0.25),
    ENGINEERING("engineering", SkillCategory.MASTERY, 0.40, 0.25),

    // Expedition
    BEASTSLAYING("beastslaying", SkillCategory.EXPEDITION, 0.60, 0.25),
    WAYFARING("wayfaring", SkillCategory.EXPEDITION, 0.40, 0.25),
    SPELUNKING("spelunking", SkillCategory.EXPEDITION, 0.50, 0.25),

    // Construction. The passive is a refund chance on what you place, which is the only currency a
    // builder actually spends.
    MASONRY("masonry", SkillCategory.CONSTRUCTION, 0.45, 0.25),
    DECORATING("decorating", SkillCategory.CONSTRUCTION, 0.45, 0.25),

    // Social. Paid only from XP the company bonus added, and the passive makes that bonus bigger:
    // 0.30 is +30% on top of camaraderie or mentor at level 100. The proc rolls at most once a
    // minute of shared work (SocialService), which is why its chance sits higher.
    SOCIAL("social", SkillCategory.SOCIAL, 0.30, 0.35),

    // Survival. Paid only as a share of other skills' XP earned in the dark, plus a trickle for
    // active time outdoors at night. The passive is Dark Sight: 0.10 is a tenth of Night
    // Vision's strength while you stand in the dark, at level 100. The light map's Night Vision
    // curve is steep, so a tenth already shows a dark room dimly (measured in a real client).
    NIGHTWALKER("nightwalker", SkillCategory.SURVIVAL, 0.10, 0.25),

    // Combat, added last so no ordinal moves (idea 37). Paid for damage dealt and kills times the
    // odds (foes after you, a stronger foe, low health, less armour); nothing at even odds. The
    // passive is +damage for each foe after you beyond the first: 0.40 is +40% with five foes at
    // level 100. Rally only rolls on a kill while outnumbered, which is why its chance sits higher.
    COURAGE("courage", SkillCategory.COMBAT, 0.40, 0.35),

    // Combat, added last so no ordinal moves (idea 38). Paid only for protecting another player
    // (cover, block, avenger, heal, revive), scaled by how much danger that player is in. The
    // passive is less damage for the players around you: 0.15 is 15% at level 100. Intercept
    // rolls on every hit an ally near you takes, which is why its chance sits lower.
    GUARDIAN("guardian", SkillCategory.COMBAT, 0.15, 0.20),

    // Combat, added last so no ordinal moves (idea 39). Initiative and momentum: first blood,
    // charge hits (5 blocks closed in 2 s, by any means) and kills at the head of a group. The
    // passive is more damage on a first blood: 0.30 is +30% at level 100. Breach only rolls on a
    // charge hit, which is why its chance sits a little higher.
    CHARGER("charger", SkillCategory.COMBAT, 0.30, 0.30),

    // Combat, added last so no ordinal moves (idea 40). The back line, the mirror of Charger:
    // ranged hits on mobs that are after a friend, ranged kills of mobs that just hurt one, and
    // shots over a friend's shoulder (Overwatch). Any ranged weapon. The passive is more ranged
    // damage on a mob that is after someone else: 0.25 is +25% at level 100.
    TACTICIAN("tactician", SkillCategory.COMBAT, 0.25, 0.25),

    // Construction, added last so no ordinal moves (docs/HOMEBUILDER-SPEC.md). The home skill: paid
    // only as a share of other skills' XP earned at a Hearth, plus the home's milestones, so it
    // never pays twice for a block Masonry or Decorating already paid. The passive works only at
    // home: 0.20 is +20% speed, +2 blocks reach and, with Tidy Home, a 6-block item magnet at level
    // 100. Hearthglow rolls at most once per 10 s on a grant that fed the Hearth.
    HOMEBUILDER("homebuilder", SkillCategory.CONSTRUCTION, 0.20, 0.20),

    // Gathering, added last so no ordinal moves (docs/batch-log/husbandry.md). Animals: breeding,
    // taming, shearing, milking, brushing an armadillo, feeding a baby, honey. The passive is
    // faster growing for the babies you breed and hardier pets: 0.50 is +50% growth speed and
    // +50% pet health at level 100. Twins only rolls on a breeding you caused.
    HUSBANDRY("husbandry", SkillCategory.GATHERING, 0.50, 0.25),

    // Movement, added last so no ordinal moves (docs/batch-log/riding.md). Metres ridden: horses,
    // donkeys, camels, pigs, striders, boats, minecarts, any modded mount, and elytra flight. The
    // movement skills pay nothing while riding, so this fills that gap. The passive is a faster
    // mount: 0.30 is +30% mount speed, +15% jump and a 45% chance an elytra wear point is spared at
    // level 100. Tailwind rolls once per whole point of ride XP.
    RIDING("riding", SkillCategory.MOVEMENT, 0.30, 0.20),

    // Social, added last so no ordinal moves (docs/batch-log/commerce.md). Villager and wandering
    // trader trades (and any modded trader that uses the vanilla merchant screen) and piglin
    // bartering. The passive is a price discount: 0.40 is a 10% discount at level 100 (a quarter of
    // the passive, before perks). Good Deal rolls once per paid trade.
    COMMERCE("commerce", SkillCategory.SOCIAL, 0.40, 0.20);

    public static final Skill[] VALUES = values();

    private static final Map<String, Skill> BY_ID =
            Arrays.stream(VALUES).collect(Collectors.toMap(Skill::id, Function.identity()));

    private final String id;
    private final SkillCategory category;
    private final double defaultMaxBonus;
    private final double defaultProcChance;

    Skill(String id, SkillCategory category, double defaultMaxBonus, double defaultProcChance) {
        this.id = id;
        this.category = category;
        this.defaultMaxBonus = defaultMaxBonus;
        this.defaultProcChance = defaultProcChance;
    }

    public String id() {
        return id;
    }

    public SkillCategory category() {
        return category;
    }

    public double defaultMaxBonus() {
        return defaultMaxBonus;
    }

    public double defaultProcChance() {
        return defaultProcChance;
    }

    /** Lang key for the proc's name, the word that gets shouted on the action bar. */
    public String procKey() {
        return "proficiency.proc." + id;
    }

    /** Lang key for the one-line description of what the proc does. */
    public String procDescKey() {
        return "proficiency.proc." + id + ".desc";
    }

    /** Lang key for the one-line version of the proc's description (the short tooltip). */
    public String procShortKey() {
        return "proficiency.proc." + id + ".short";
    }

    /** Lang key for the active ability's name. */
    public String activeKey() {
        return "proficiency.active." + id;
    }

    public String translationKey() {
        return "proficiency.skill." + id;
    }

    public String descriptionKey() {
        return "proficiency.skill." + id + ".desc";
    }

    public static Skill byId(String id) {
        return BY_ID.get(id);
    }
}
