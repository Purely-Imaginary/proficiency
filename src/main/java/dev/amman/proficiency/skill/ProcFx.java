package dev.amman.proficiency.skill;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/**
 * The look of each skill's signature proc, as plain data. Every recipe is a few layers of vanilla
 * particles. The client turns them into particles ({@code client.ProcFxPlayer}), so this class
 * touches no Minecraft class and a unit test can check the budget.
 *
 * <p>Budget, per proc: at most {@link #MAX_PARTICLES} particles and at most {@link #MAX_TICKS}
 * ticks (0.5 s) from the first particle to the last.
 */
public final class ProcFx {

    public static final int MAX_PARTICLES = 24;
    public static final int MAX_TICKS = 10;

    /** Vanilla particle types a recipe may use. The client maps each one. */
    public enum Kind {
        CRIT, ENCHANTED_HIT, SWEEP, DAMAGE_INDICATOR, ANGRY_VILLAGER,
        WAX_ON, WAX_OFF, ELECTRIC_SPARK, GLOW, HAPPY_VILLAGER, COMPOSTER, HEART, NOTE,
        SPLASH, BUBBLE, BUBBLE_COLUMN_UP, FISHING,
        CLOUD, POOF, SMOKE, LARGE_SMOKE, CAMPFIRE_SMOKE, ASH,
        SOUL, SOUL_FIRE_FLAME, FLAME, SMALL_FLAME, LAVA,
        END_ROD, ENCHANT, WITCH, EFFECT, DRAGON_BREATH, FIREWORK,
        /** Cracks of the block the proc was about, or of the layer's own block. */
        BLOCK,
        /** A dust puff in the colour of the block the proc was about. */
        BLOCK_DUST
    }

    /** Where a layer's particles appear. The client works the position out from the proc's context. */
    public enum Anchor {
        /** The block or creature the proc was about, else the player's body. */
        FOCUS,
        BODY,
        FEET,
        /** About a block ahead of the player, at chest height. */
        FRONT,
        /** A flat arc in front of the player, left to right. */
        ARC,
        /** From the player towards the focus, or straight ahead when there is none. */
        LINE,
        /** From the focus block upwards along the trunk. */
        COLUMN,
        /** Above the top of the trunk. */
        CROWN,
        /** A ring on the ground round the focus. */
        RING,
        /** Rising from the player's feet to above the head. */
        SELF_COLUMN,
        /** At the feet and back along the way the player came. */
        TRAIL
    }

    /**
     * @param spread box half-size for most anchors, ring radius for {@link Anchor#RING}
     * @param delay ticks before the first particle
     * @param over ticks the layer's particles are spread across (0 = all at once)
     * @param block block id for {@link Kind#BLOCK} / {@link Kind#BLOCK_DUST}, or null for the proc's own
     */
    public record Layer(Kind kind, int count, Anchor anchor, float spread, float speed, int delay,
            int over, String block) {
    }

    /** @param fallbackBlock the block to crack when the proc had none (a creature, thin air) */
    public record Recipe(String fallbackBlock, List<Layer> layers) {

        public int particles() {
            int sum = 0;
            for (Layer layer : layers) {
                sum += layer.count();
            }
            return sum;
        }

        /** Ticks from the proc to the last particle. */
        public int ticks() {
            int last = 0;
            for (Layer layer : layers) {
                last = Math.max(last, layer.delay() + layer.over());
            }
            return last;
        }
    }

    private static final Map<Skill, Recipe> TABLE = new EnumMap<>(Skill.class);

    private static Layer l(Kind kind, int count, Anchor anchor, float spread, float speed) {
        return new Layer(kind, count, anchor, spread, speed, 0, 0, null);
    }

    private static Layer l(Kind kind, int count, Anchor anchor, float spread, float speed, int delay,
            int over) {
        return new Layer(kind, count, anchor, spread, speed, delay, over, null);
    }

    private static Layer block(Kind kind, int count, Anchor anchor, float spread, float speed, int delay,
            int over, String block) {
        return new Layer(kind, count, anchor, spread, speed, delay, over, block);
    }

    private static void put(Skill skill, String fallback, Layer... layers) {
        TABLE.put(skill, new Recipe(fallback, List.of(layers)));
    }

    static {
        // Combat
        put(Skill.SWORDS, "minecraft:stone",
                l(Kind.SWEEP, 5, Anchor.ARC, 0f, 0f, 0, 4),
                l(Kind.CRIT, 8, Anchor.FOCUS, 0.35f, 0.3f));
        put(Skill.AXES, "minecraft:oak_planks",
                l(Kind.DAMAGE_INDICATOR, 4, Anchor.FOCUS, 0.3f, 0.2f),
                l(Kind.CRIT, 8, Anchor.FOCUS, 0.4f, 0.5f),
                block(Kind.BLOCK, 4, Anchor.FOCUS, 0.3f, 0.1f, 0, 0, "minecraft:oak_planks"));
        put(Skill.MACES, "minecraft:stone",
                l(Kind.POOF, 8, Anchor.RING, 0.9f, 0.08f, 0, 2),
                l(Kind.BLOCK, 8, Anchor.RING, 0.7f, 0.12f, 0, 2),
                l(Kind.CRIT, 4, Anchor.FOCUS, 0.3f, 0.4f));
        put(Skill.TRIDENTS, "minecraft:water",
                l(Kind.SPLASH, 8, Anchor.FOCUS, 0.5f, 0.2f),
                l(Kind.ELECTRIC_SPARK, 8, Anchor.FOCUS, 0.5f, 0.5f, 1, 4));
        put(Skill.UNARMED, "minecraft:stone",
                l(Kind.CRIT, 8, Anchor.FOCUS, 0.3f, 0.35f),
                l(Kind.CLOUD, 4, Anchor.FOCUS, 0.2f, 0.06f),
                l(Kind.DAMAGE_INDICATOR, 2, Anchor.FOCUS, 0.2f, 0.1f));
        put(Skill.BLOCKING, "minecraft:iron_block",
                l(Kind.ELECTRIC_SPARK, 8, Anchor.FRONT, 0.45f, 0.4f),
                l(Kind.CRIT, 6, Anchor.FRONT, 0.3f, 0.25f, 1, 3));
        put(Skill.ENDURANCE, "minecraft:stone",
                l(Kind.END_ROD, 8, Anchor.SELF_COLUMN, 0.25f, 0.03f, 0, 6),
                l(Kind.HEART, 3, Anchor.BODY, 0.35f, 0.0f, 2, 4));
        put(Skill.ARCHERY, "minecraft:stone",
                l(Kind.CRIT, 12, Anchor.LINE, 0.05f, 0.02f, 0, 6),
                l(Kind.ENCHANTED_HIT, 4, Anchor.FOCUS, 0.25f, 0.3f, 4, 0));
        put(Skill.CROSSBOWS, "minecraft:stone",
                l(Kind.CRIT, 8, Anchor.LINE, 0.04f, 0.02f, 0, 4),
                l(Kind.SMOKE, 4, Anchor.FRONT, 0.15f, 0.02f),
                l(Kind.FIREWORK, 4, Anchor.FOCUS, 0.2f, 0.12f, 3, 0));
        put(Skill.COURAGE, "minecraft:stone",
                l(Kind.FLAME, 6, Anchor.RING, 0.8f, 0.03f, 0, 3),
                l(Kind.CRIT, 8, Anchor.BODY, 0.45f, 0.3f),
                l(Kind.ANGRY_VILLAGER, 2, Anchor.BODY, 0.4f, 0f, 1, 0));
        put(Skill.GUARDIAN, "minecraft:iron_block",
                l(Kind.ELECTRIC_SPARK, 10, Anchor.RING, 0.9f, 0.1f, 0, 4),
                l(Kind.ENCHANTED_HIT, 6, Anchor.BODY, 0.5f, 0.15f, 1, 0));
        put(Skill.CHARGER, "minecraft:stone",
                l(Kind.CLOUD, 10, Anchor.TRAIL, 0.2f, 0.04f, 0, 6),
                l(Kind.CRIT, 6, Anchor.FRONT, 0.35f, 0.35f),
                l(Kind.POOF, 4, Anchor.FEET, 0.3f, 0.08f));
        put(Skill.TACTICIAN, "minecraft:stone",
                l(Kind.ENCHANTED_HIT, 8, Anchor.LINE, 0.05f, 0.02f, 0, 4),
                l(Kind.GLOW, 6, Anchor.FOCUS, 0.4f, 0.04f, 3, 0));

        // Gathering
        put(Skill.MINING, "minecraft:stone",
                l(Kind.WAX_ON, 12, Anchor.FOCUS, 0.45f, 0.45f),
                l(Kind.BLOCK, 8, Anchor.FOCUS, 0.4f, 0.12f));
        put(Skill.WOODCUTTING, "minecraft:oak_log",
                l(Kind.BLOCK, 8, Anchor.COLUMN, 0.6f, 0.1f, 0, 4),
                block(Kind.BLOCK, 12, Anchor.CROWN, 1.1f, 0.15f, 3, 3, "minecraft:oak_leaves"));
        put(Skill.EXCAVATION, "minecraft:dirt",
                l(Kind.BLOCK, 10, Anchor.FOCUS, 0.45f, 0.14f),
                l(Kind.CLOUD, 4, Anchor.FOCUS, 0.3f, 0.03f));
        put(Skill.FARMING, "minecraft:wheat",
                l(Kind.HAPPY_VILLAGER, 10, Anchor.FOCUS, 0.55f, 0.02f, 0, 5),
                l(Kind.COMPOSTER, 6, Anchor.FOCUS, 0.4f, 0.02f, 1, 3));
        put(Skill.FISHING, "minecraft:water",
                l(Kind.SPLASH, 10, Anchor.FOCUS, 0.35f, 0.1f),
                l(Kind.BUBBLE_COLUMN_UP, 8, Anchor.FOCUS, 0.2f, 0.25f, 0, 4),
                l(Kind.FISHING, 4, Anchor.FOCUS, 0.3f, 0.05f, 1, 2));

        // Movement
        put(Skill.RUNNING, "minecraft:stone",
                l(Kind.CLOUD, 10, Anchor.TRAIL, 0.15f, 0.03f, 0, 5),
                l(Kind.CRIT, 4, Anchor.TRAIL, 0.2f, 0.1f, 1, 3));
        put(Skill.SNEAKING, "minecraft:stone",
                l(Kind.SMOKE, 8, Anchor.TRAIL, 0.15f, 0.01f, 0, 5),
                l(Kind.ASH, 6, Anchor.TRAIL, 0.25f, 0.01f, 0, 5));
        put(Skill.JUMPING, "minecraft:stone",
                l(Kind.CLOUD, 8, Anchor.RING, 0.5f, 0.1f),
                l(Kind.POOF, 6, Anchor.FEET, 0.25f, 0.1f));
        put(Skill.SWIMMING, "minecraft:water",
                l(Kind.BUBBLE, 10, Anchor.TRAIL, 0.2f, 0.04f, 0, 6),
                l(Kind.SPLASH, 6, Anchor.TRAIL, 0.25f, 0.08f, 0, 4));

        // Crafting
        put(Skill.SMITHING, "minecraft:iron_block",
                l(Kind.LAVA, 4, Anchor.FRONT, 0.2f, 0.0f),
                l(Kind.CRIT, 10, Anchor.FRONT, 0.35f, 0.4f),
                l(Kind.SMALL_FLAME, 4, Anchor.FRONT, 0.2f, 0.03f, 1, 3));
        put(Skill.COOKING, "minecraft:stone",
                l(Kind.CAMPFIRE_SMOKE, 3, Anchor.SELF_COLUMN, 0.2f, 0.01f, 0, 4),
                l(Kind.SMALL_FLAME, 6, Anchor.FRONT, 0.2f, 0.02f),
                l(Kind.COMPOSTER, 6, Anchor.FRONT, 0.3f, 0.02f, 1, 4));
        put(Skill.ALCHEMY, "minecraft:stone",
                l(Kind.WITCH, 10, Anchor.BODY, 0.5f, 0.05f),
                l(Kind.EFFECT, 8, Anchor.BODY, 0.45f, 0.05f, 1, 4));

        // Mastery
        put(Skill.SPELLCASTING, "minecraft:stone",
                l(Kind.ENCHANT, 12, Anchor.BODY, 0.8f, 0f, 0, 5),
                l(Kind.END_ROD, 6, Anchor.SELF_COLUMN, 0.2f, 0.03f, 1, 5),
                l(Kind.DRAGON_BREATH, 4, Anchor.BODY, 0.4f, 0.02f, 2, 0));
        put(Skill.ENGINEERING, "minecraft:iron_block",
                l(Kind.ELECTRIC_SPARK, 12, Anchor.FOCUS, 0.45f, 0.4f),
                l(Kind.SMOKE, 4, Anchor.FOCUS, 0.3f, 0.02f, 1, 0),
                l(Kind.CRIT, 4, Anchor.FOCUS, 0.3f, 0.3f, 2, 0));

        // Expedition
        put(Skill.BEASTSLAYING, "minecraft:stone",
                l(Kind.DAMAGE_INDICATOR, 6, Anchor.FOCUS, 0.35f, 0.2f),
                l(Kind.CRIT, 8, Anchor.FOCUS, 0.4f, 0.4f),
                l(Kind.ANGRY_VILLAGER, 2, Anchor.FOCUS, 0.4f, 0f, 1, 0));
        put(Skill.WAYFARING, "minecraft:stone",
                l(Kind.GLOW, 8, Anchor.TRAIL, 0.25f, 0.02f, 0, 6),
                l(Kind.ENCHANT, 8, Anchor.RING, 0.8f, 0f, 0, 4));
        put(Skill.SPELUNKING, "minecraft:deepslate",
                l(Kind.GLOW, 8, Anchor.FOCUS, 0.45f, 0.04f),
                l(Kind.BLOCK, 8, Anchor.FOCUS, 0.4f, 0.1f),
                l(Kind.WAX_OFF, 4, Anchor.FOCUS, 0.4f, 0.2f, 1, 2));

        // Construction
        put(Skill.MASONRY, "minecraft:stone",
                l(Kind.BLOCK_DUST, 12, Anchor.FOCUS, 0.4f, 0.04f),
                l(Kind.BLOCK, 4, Anchor.FOCUS, 0.35f, 0.08f));
        put(Skill.DECORATING, "minecraft:white_wool",
                l(Kind.BLOCK_DUST, 8, Anchor.FOCUS, 0.4f, 0.03f),
                l(Kind.WAX_OFF, 6, Anchor.FOCUS, 0.4f, 0.25f, 1, 3));

        // Social and survival
        put(Skill.SOCIAL, "minecraft:stone",
                l(Kind.NOTE, 8, Anchor.BODY, 0.6f, 0f, 0, 5),
                l(Kind.HEART, 3, Anchor.BODY, 0.4f, 0f, 2, 3));
        put(Skill.NIGHTWALKER, "minecraft:stone",
                l(Kind.SOUL, 10, Anchor.SELF_COLUMN, 0.3f, 0.03f, 0, 6),
                l(Kind.SMOKE, 6, Anchor.BODY, 0.4f, 0.02f),
                l(Kind.SOUL_FIRE_FLAME, 4, Anchor.FEET, 0.3f, 0.03f, 2, 3));
    }

    private ProcFx() {
    }

    /** The recipe for a skill. Every skill has one, and a test keeps it so. */
    public static Recipe recipe(Skill skill) {
        return TABLE.get(skill);
    }
}
