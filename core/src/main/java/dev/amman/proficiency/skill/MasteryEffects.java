package dev.amman.proficiency.skill;

import java.util.EnumMap;
import java.util.Map;

/**
 * What each Mastery star does, per skill. A skill at level 100 with stars earns one bespoke rider on
 * its own signature proc: it fires every time the proc lands (never on its own, never on a death),
 * and its size is the number in this table for the stars earned. Star 0 is nothing. The code that
 * performs the rider is {@code MasteryRiders} in the game layer; the screens read this table, so the
 * tooltip can never drift from the game. See docs/MASTERY-SPEC.md for the design of every row.
 *
 * <p>A rider never changes a level, a talent point or a proc chance, never touches the death
 * penalty and never calls a proc itself, so it cannot chain. The unit of a value (seconds, blocks,
 * health, items) is in the spec and the lang text, not here.
 */
public final class MasteryEffects {

    /** One skill's rider: the lang key stem and its value at stars 1 to 5. */
    public record Effect(Skill skill, double[] perStar) {
        public double at(int stars) {
            return stars < 1 ? 0.0 : perStar[Math.min(stars, Mastery.MAX_STARS) - 1];
        }

        /** Lang key of the rider's name. */
        public String nameKey() {
            return "proficiency.mastery." + skill.id();
        }

        /** Lang key of the short form (the tree header line); its argument is the value. */
        public String shortKey() {
            return "proficiency.mastery." + skill.id() + ".short";
        }

        /** Lang key of the description; its argument is the value. */
        public String descKey() {
            return "proficiency.mastery." + skill.id() + ".desc";
        }
    }

    private static final Map<Skill, Effect> TABLE = new EnumMap<>(Skill.class);

    private static void put(Skill skill, double... perStar) {
        if (perStar.length != Mastery.MAX_STARS) {
            throw new IllegalArgumentException(skill + " needs " + Mastery.MAX_STARS + " values");
        }
        TABLE.put(skill, new Effect(skill, perStar));
    }

    static {
        // Combat
        put(Skill.SWORDS, 3, 5, 7, 9, 12);          // Echo Slash: damage of the phantom second slash (hits the foe and the ones beside it)
        put(Skill.AXES, 2, 3, 4, 5, 6);             // Whirl: radius in blocks of the spin that hits every hostile around you
        put(Skill.MACES, 3, 4, 5, 6, 7);            // Tremor: radius in blocks in which every hostile is thrown into the air
        put(Skill.TRIDENTS, 1, 2, 3, 4, 5);         // Arcing Tide: hostiles the charge jumps to after the target
        put(Skill.UNARMED, 2, 3, 4, 5, 6);          // Flurry: extra jabs (2 damage each) in the same blink
        put(Skill.BLOCKING, 3, 4, 5, 6, 7);         // Shield Bash: blocks the attacker is hurled back
        put(Skill.ENDURANCE, 2, 3, 5, 7, 9);        // Shrug It Off: kinds of harmful effect washed off (see MasteryMath.SHRUG_ORDER)
        put(Skill.ARCHERY, 1, 2, 3, 4, 5);          // Ricochet: second, third... targets the arrow glances on to
        put(Skill.CROSSBOWS, 2, 3, 4, 5, 6);        // Splinter Bolt: radius in blocks of the shrapnel burst
        put(Skill.COURAGE, 4, 6, 8, 10, 12);        // War Cry: radius in blocks in which hostiles are shoved back and weakened
        put(Skill.GUARDIAN, 3, 4, 5, 6, 7);         // Warding Pulse: radius in blocks in which hostiles are pushed off you
        put(Skill.CHARGER, 4, 6, 8, 10, 12);        // Stampede: length in blocks of the lane in front of you that is cleared
        put(Skill.TACTICIAN, 4, 6, 8, 10, 12);      // Pinning Fire: radius in blocks of the slowed, lit-up hostiles
        // Gathering
        put(Skill.MINING, 6, 8, 10, 12, 14);        // Motherlode Ping: radius in blocks in which ores glow through the rock
        put(Skill.WOODCUTTING, 2, 3, 4, 5, 6);      // Replant: saplings planted around the stump
        put(Skill.EXCAVATION, 2, 4, 6, 9, 12);      // Landslide: loose ground blocks that give way around you
        put(Skill.FARMING, 2, 3, 5, 7, 10);         // Green Thumb: crops bone-mealed around you
        put(Skill.FISHING, 1, 2, 3, 4, 5);          // Second Catch: extra fish hooked and flung to you
        put(Skill.HUSBANDRY, 6, 8, 10, 12, 14);     // Open Barn: radius in blocks (wool regrows, hens lay, calves grow)
        // Movement
        put(Skill.RUNNING, 3, 4, 5, 6, 7);          // Gale Step: blocks of burst forward
        put(Skill.SNEAKING, 4, 6, 8, 10, 12);       // Smoke Step: blocks: mobs this close forget the hunt
        put(Skill.JUMPING, 2, 3, 4, 5, 6);          // Rebound: blocks you are thrown up
        put(Skill.SWIMMING, 3, 4, 6, 8, 10);        // Riptide Glide: blocks of burst through the water
        put(Skill.RIDING, 2, 4, 6, 8, 10);          // Rested Mount: health the mount recovers
        // Crafting
        put(Skill.SMITHING, 5, 10, 15, 20, 25);     // Quench: percent of durability restored on worn armour and the held tool
        put(Skill.COOKING, 2, 3, 4, 5, 6);          // Seconds Served: food points for you and the table within 8 blocks
        put(Skill.ALCHEMY, 10, 20, 30, 40, 50);     // Reagent Echo: percent every helpful effect is stretched by
        // Mastery
        put(Skill.SPELLCASTING, 1, 2, 3, 4, 5);     // Magic Missiles: bolts that fly at separate hostiles
        put(Skill.ENGINEERING, 4, 6, 8, 11, 14);    // Tractor Beam: radius in blocks in which dropped items fly to you
        // Expedition
        put(Skill.BEASTSLAYING, 2, 3, 4, 5, 6);     // Fresh Kill: food points (and saturation) from the kill
        put(Skill.WAYFARING, 6, 10, 14, 18, 24);    // New Horizon: chunks searched for the nearest unseen structure
        put(Skill.SPELUNKING, 12, 16, 20, 26, 32);  // Cave Sense: radius in blocks in which loot chests and spawners glow
        // Construction
        put(Skill.MASONRY, 2, 4, 6, 9, 12);         // Repoint: weathered blocks restored to clean stone
        put(Skill.DECORATING, 3, 5, 7, 9, 12);      // Bloom: flowers that open on the grass around you
        put(Skill.HOMEBUILDER, 4, 6, 8, 10, 12);    // Hearthglow: radius in blocks in which campfires and candles relight
        // Social and Survival
        put(Skill.SOCIAL, 2, 3, 4, 5, 6);           // Toast: health you and your company regain
        put(Skill.COMMERCE, 1, 2, 3, 4, 5);         // Thrown In: loaves of bread passed over by a villager
        put(Skill.NIGHTWALKER, 6, 8, 10, 12, 14);   // Moon Gaze: radius in blocks in which hostiles are lit up
    }

    private MasteryEffects() {
    }

    /** The rider of a skill; every skill has one. */
    public static Effect of(Skill skill) {
        return TABLE.get(skill);
    }

    /**
     * The stars that count for the rider: none below level 100 or with Mastery switched off
     * ({@code masteryMaxStars} 0), and never above the configured cap.
     */
    public static int stars(PlayerSkills skills, Skill skill) {
        if (skills.level(skill) < SkillMath.MAX_LEVEL || Mastery.maxStars() <= 0) {
            return 0;
        }
        return Mastery.shownStars(skills.stars(skill));
    }

    /** The rider's value for these stars, 0 for none. */
    public static double value(Skill skill, int stars) {
        Effect effect = TABLE.get(skill);
        return effect == null ? 0.0 : effect.at(stars);
    }

    /** The rider's value for this player, 0 when no star counts. */
    public static double value(PlayerSkills skills, Skill skill) {
        return value(skill, stars(skills, skill));
    }

    /** A value as written: whole numbers without a decimal, halves with one. */
    public static String format(double value) {
        return value == Math.rint(value) ? Long.toString((long) value) : Double.toString(value);
    }

    /** Ticks for a value in seconds. */
    public static int ticks(double seconds) {
        return (int) Math.round(seconds * 20.0);
    }
}
