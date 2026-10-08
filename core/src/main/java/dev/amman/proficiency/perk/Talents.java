package dev.amman.proficiency.perk;

import dev.amman.proficiency.skill.Skill;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Every skill's talent tree, Core Keeper style, and each one its own shape. The nodes themselves
 * are generated into {@link TalentTable} from {@code tools/talents_spec.py}; this class holds the
 * material lists and the lookups.
 *
 * <p>The rules every tree keeps, which {@code TalentsTableTest} enforces: a node opens only when
 * every node it hangs from is full; points are one per level of that skill, a hundred at 100; and
 * no tree costs more than a hundred, so a maxed skill fills every branch. Choosing a branch sets the
 * order, never the outcome.
 *
 * <p>The four material lists are the old tiers', unchanged, on the root (fills at 10), two
 * row-three nodes (60) and one row-four node (90). Woodcutting's tree is still an expedition.
 */
public final class Talents {

    /** Levels of a skill per talent point it produces. A hundred points at level 100. */
    public static final int LEVELS_PER_POINT = 1;

    private static final Map<Skill, List<Talent>> BY_SKILL = new EnumMap<>(Skill.class);
    private static final Map<String, Talent> BY_KEY = new HashMap<>();
    private static final Map<Skill, List<List<Requirement>>> DEFAULT_TIERS = new EnumMap<>(Skill.class);
    private static final Map<Skill, List<List<Requirement>>> CURRENT_TIERS = new EnumMap<>(Skill.class);

    private Talents() {
    }

    static {
        tree(Skill.SWORDS,
                List.of(Requirement.of("minecraft:rotten_flesh", 64), Requirement.of("minecraft:bone", 32)),
                List.of(Requirement.of("minecraft:string", 32), Requirement.of("minecraft:gunpowder", 32), Requirement.of("minecraft:spider_eye", 16)),
                List.of(Requirement.of("minecraft:ender_pearl", 16), Requirement.of("minecraft:blaze_rod", 8)),
                List.of(Requirement.of("minecraft:ghast_tear", 4), Requirement.of("proficiency:hunters_charm", 2)));

        tree(Skill.AXES,
                List.of(Requirement.of("minecraft:bone", 64), Requirement.of("minecraft:rotten_flesh", 32)),
                List.of(Requirement.of("minecraft:leather", 32), Requirement.of("minecraft:gunpowder", 16)),
                List.of(Requirement.of("minecraft:blaze_rod", 8), Requirement.of("minecraft:ender_pearl", 8)),
                List.of(Requirement.of("minecraft:ghast_tear", 4), Requirement.of("proficiency:hunters_charm", 2)));

        tree(Skill.MACES,
                List.of(Requirement.of("minecraft:iron_ingot", 32), Requirement.of("minecraft:bone", 32)),
                List.of(Requirement.of("minecraft:wind_charge", 32), Requirement.of("minecraft:breeze_rod", 8)),
                List.of(Requirement.of("minecraft:copper_ingot", 64), Requirement.of("minecraft:mace", 1)),
                List.of(Requirement.of("minecraft:heavy_core", 1), Requirement.of("proficiency:hunters_charm", 2)));

        tree(Skill.TRIDENTS,
                List.of(Requirement.of("minecraft:prismarine_shard", 32), Requirement.of("minecraft:cod", 32)),
                List.of(Requirement.of("minecraft:prismarine_crystals", 16), Requirement.of("minecraft:ink_sac", 16)),
                List.of(Requirement.of("minecraft:nautilus_shell", 8), Requirement.of("minecraft:sponge", 4)),
                List.of(Requirement.of("minecraft:heart_of_the_sea", 1), Requirement.of("proficiency:hunters_charm", 2)));

        tree(Skill.UNARMED,
                List.of(Requirement.of("minecraft:leather", 32), Requirement.of("minecraft:feather", 32)),
                List.of(Requirement.of("minecraft:rabbit_hide", 16), Requirement.of("minecraft:string", 32)),
                List.of(Requirement.of("minecraft:phantom_membrane", 8), Requirement.of("minecraft:blaze_powder", 16)),
                List.of(Requirement.of("minecraft:ghast_tear", 4), Requirement.of("proficiency:hunters_charm", 2)));

        tree(Skill.BLOCKING,
                List.of(Requirement.of("minecraft:shield", 8), Requirement.of("minecraft:iron_ingot", 32)),
                List.of(Requirement.of("minecraft:obsidian", 16), Requirement.of("minecraft:copper_ingot", 32)),
                List.of(Requirement.of("minecraft:netherite_scrap", 2), Requirement.of("minecraft:diamond", 8)),
                List.of(Requirement.of("proficiency:masterwork_ingot", 2), Requirement.of("proficiency:hunters_charm", 1)));

        // Endurance is paid in what a body needs to heal, and ends at the one item that cheats death.
        tree(Skill.ENDURANCE,
                List.of(Requirement.of("minecraft:cooked_beef", 32), Requirement.of("minecraft:leather", 32)),
                List.of(Requirement.of("minecraft:golden_carrot", 16), Requirement.of("minecraft:glistering_melon_slice", 16)),
                List.of(Requirement.of("minecraft:golden_apple", 4), Requirement.of("minecraft:turtle_scute", 8)),
                List.of(Requirement.of("minecraft:totem_of_undying", 1), Requirement.of("proficiency:hunters_charm", 2)));

        tree(Skill.ARCHERY,
                List.of(Requirement.of("minecraft:arrow", 128), Requirement.of("minecraft:feather", 64)),
                List.of(Requirement.of("minecraft:flint", 32), Requirement.of("minecraft:string", 64)),
                List.of(Requirement.of("minecraft:spectral_arrow", 16), Requirement.of("minecraft:blaze_rod", 8)),
                List.of(Requirement.of("minecraft:ghast_tear", 4), Requirement.of("proficiency:hunters_charm", 2)));

        tree(Skill.CROSSBOWS,
                List.of(Requirement.of("minecraft:arrow", 128), Requirement.of("minecraft:tripwire_hook", 8)),
                List.of(Requirement.of("minecraft:iron_ingot", 32), Requirement.of("minecraft:string", 64)),
                List.of(Requirement.of("minecraft:firework_rocket", 32), Requirement.of("minecraft:redstone", 32)),
                List.of(Requirement.of("proficiency:masterwork_ingot", 2), Requirement.of("proficiency:hunters_charm", 1)));

        tree(Skill.MINING,
                List.of(Requirement.of("minecraft:cobblestone", 128), Requirement.of("minecraft:coal", 64)),
                List.of(Requirement.of("minecraft:iron_ingot", 32), Requirement.of("minecraft:copper_ingot", 32), Requirement.of("minecraft:redstone", 32)),
                List.of(Requirement.of("minecraft:gold_ingot", 16), Requirement.of("minecraft:diamond", 8), Requirement.of("minecraft:lapis_lazuli", 16)),
                List.of(Requirement.of("minecraft:ancient_debris", 4), Requirement.of("proficiency:prospectors_draught", 2)));

        tree(Skill.WOODCUTTING,
                List.of(Requirement.of("minecraft:oak_log", 64), Requirement.of("minecraft:birch_log", 64)),
                List.of(Requirement.of("minecraft:spruce_log", 32), Requirement.of("minecraft:jungle_log", 32), Requirement.of("minecraft:acacia_log", 32), Requirement.of("biomeswevegone:redwood_log", 32)),
                List.of(Requirement.of("minecraft:dark_oak_log", 16), Requirement.of("minecraft:cherry_log", 16), Requirement.of("minecraft:mangrove_log", 16), Requirement.of("biomeswevegone:rainbow_eucalyptus_log", 16), Requirement.of("aether:skyroot_log", 16)),
                List.of(Requirement.of("minecraft:crimson_stem", 8), Requirement.of("minecraft:warped_stem", 8), Requirement.of("twilightforest:canopy_log", 8), Requirement.of("gardens_of_the_dead:soulblight_stem", 8), Requirement.of("proficiency:masterwork_ingot", 2)));

        tree(Skill.EXCAVATION,
                List.of(Requirement.of("minecraft:dirt", 128), Requirement.of("minecraft:sand", 128)),
                List.of(Requirement.of("minecraft:gravel", 64), Requirement.of("minecraft:clay_ball", 32), Requirement.of("minecraft:soul_sand", 32)),
                List.of(Requirement.of("minecraft:red_sand", 32), Requirement.of("minecraft:flint", 32), Requirement.of("minecraft:mud", 32)),
                List.of(Requirement.of("minecraft:snow_block", 32), Requirement.of("proficiency:prospectors_draught", 2)));

        tree(Skill.FARMING,
                List.of(Requirement.of("minecraft:wheat", 128), Requirement.of("minecraft:carrot", 64)),
                List.of(Requirement.of("minecraft:potato", 64), Requirement.of("minecraft:beetroot", 64), Requirement.of("minecraft:pumpkin", 32)),
                List.of(Requirement.of("minecraft:melon", 16), Requirement.of("minecraft:sweet_berries", 32), Requirement.of("minecraft:cocoa_beans", 32)),
                List.of(Requirement.of("minecraft:nether_wart", 32), Requirement.of("proficiency:prospectors_draught", 2)));

        tree(Skill.FISHING,
                List.of(Requirement.of("minecraft:cod", 64), Requirement.of("minecraft:salmon", 32)),
                List.of(Requirement.of("minecraft:tropical_fish", 8), Requirement.of("minecraft:pufferfish", 8), Requirement.of("minecraft:ink_sac", 32)),
                List.of(Requirement.of("minecraft:nautilus_shell", 4), Requirement.of("minecraft:prismarine_shard", 32)),
                List.of(Requirement.of("minecraft:heart_of_the_sea", 1), Requirement.of("proficiency:prospectors_draught", 2)));

        tree(Skill.RUNNING,
                List.of(Requirement.of("minecraft:leather_boots", 4), Requirement.of("minecraft:sugar", 64)),
                List.of(Requirement.of("minecraft:rabbit_foot", 4), Requirement.of("minecraft:feather", 32)),
                List.of(Requirement.of("minecraft:phantom_membrane", 8), Requirement.of("minecraft:blaze_powder", 16)),
                List.of(Requirement.of("minecraft:ghast_tear", 2), Requirement.of("proficiency:wanderers_token", 2)));

        tree(Skill.SNEAKING,
                List.of(Requirement.of("minecraft:white_wool", 32), Requirement.of("minecraft:black_wool", 32)),
                List.of(Requirement.of("minecraft:soul_sand", 32), Requirement.of("minecraft:phantom_membrane", 8)),
                List.of(Requirement.of("minecraft:ender_pearl", 16), Requirement.of("minecraft:sculk", 32)),
                List.of(Requirement.of("minecraft:echo_shard", 4), Requirement.of("proficiency:wanderers_token", 2)));

        tree(Skill.JUMPING,
                List.of(Requirement.of("minecraft:slime_ball", 32), Requirement.of("minecraft:feather", 32)),
                List.of(Requirement.of("minecraft:rabbit_foot", 4), Requirement.of("minecraft:honey_bottle", 8)),
                List.of(Requirement.of("minecraft:phantom_membrane", 8), Requirement.of("minecraft:popped_chorus_fruit", 32)),
                List.of(Requirement.of("minecraft:shulker_shell", 4), Requirement.of("proficiency:wanderers_token", 2)));

        tree(Skill.SWIMMING,
                List.of(Requirement.of("minecraft:kelp", 128), Requirement.of("minecraft:cod", 32)),
                List.of(Requirement.of("minecraft:prismarine_shard", 32), Requirement.of("minecraft:sponge", 4)),
                List.of(Requirement.of("minecraft:nautilus_shell", 4), Requirement.of("minecraft:turtle_scute", 8)),
                List.of(Requirement.of("minecraft:heart_of_the_sea", 1), Requirement.of("proficiency:wanderers_token", 2)));

        tree(Skill.SMITHING,
                List.of(Requirement.of("minecraft:iron_ingot", 64), Requirement.of("minecraft:coal", 64)),
                List.of(Requirement.of("minecraft:copper_ingot", 64), Requirement.of("minecraft:gold_ingot", 32), Requirement.of("minecraft:anvil", 1)),
                List.of(Requirement.of("minecraft:diamond", 16), Requirement.of("minecraft:netherite_scrap", 2)),
                List.of(Requirement.of("minecraft:netherite_ingot", 2), Requirement.of("proficiency:prospectors_draught", 2)));

        tree(Skill.COOKING,
                List.of(Requirement.of("minecraft:bread", 32), Requirement.of("minecraft:cooked_beef", 32)),
                List.of(Requirement.of("minecraft:cooked_porkchop", 32), Requirement.of("minecraft:cake", 4), Requirement.of("minecraft:golden_carrot", 16)),
                List.of(Requirement.of("minecraft:honey_bottle", 16), Requirement.of("farmersdelight:cooking_pot", 1), Requirement.of("farmersdelight:rice", 64)),
                List.of(Requirement.of("minecraft:golden_apple", 4), Requirement.of("proficiency:masterwork_ingot", 1)));

        tree(Skill.ALCHEMY,
                List.of(Requirement.of("minecraft:nether_wart", 64), Requirement.of("minecraft:glass_bottle", 32)),
                List.of(Requirement.of("minecraft:blaze_powder", 32), Requirement.of("minecraft:fermented_spider_eye", 16), Requirement.of("minecraft:glistering_melon_slice", 16)),
                List.of(Requirement.of("minecraft:ghast_tear", 8), Requirement.of("minecraft:magma_cream", 16)),
                List.of(Requirement.of("minecraft:dragon_breath", 8), Requirement.of("proficiency:masterwork_ingot", 1)));

        tree(Skill.SPELLCASTING,
                List.of(Requirement.of("minecraft:lapis_lazuli", 64), Requirement.of("ars_nouveau:source_gem", 16)),
                List.of(Requirement.of("minecraft:amethyst_shard", 64), Requirement.of("ars_nouveau:source_gem_block", 4)),
                List.of(Requirement.of("minecraft:echo_shard", 8), Requirement.of("ars_nouveau:wilden_wing", 4)),
                List.of(Requirement.of("minecraft:dragon_breath", 4), Requirement.of("proficiency:masterwork_ingot", 1)));

        tree(Skill.ENGINEERING,
                List.of(Requirement.of("minecraft:iron_ingot", 64), Requirement.of("create:andesite_alloy", 32)),
                List.of(Requirement.of("minecraft:redstone", 64), Requirement.of("create:cogwheel", 16)),
                List.of(Requirement.of("minecraft:gold_ingot", 32), Requirement.of("create:precision_mechanism", 4)),
                List.of(Requirement.of("minecraft:diamond", 16), Requirement.of("ae2:logic_processor", 8), Requirement.of("proficiency:masterwork_ingot", 1)));

        tree(Skill.BEASTSLAYING,
                List.of(Requirement.of("minecraft:bone", 64), Requirement.of("minecraft:rotten_flesh", 64)),
                List.of(Requirement.of("minecraft:blaze_rod", 16), Requirement.of("minecraft:ender_pearl", 16)),
                List.of(Requirement.of("minecraft:ghast_tear", 8), Requirement.of("twilightforest:naga_scale", 8)),
                List.of(Requirement.of("minecraft:nether_star", 1), Requirement.of("proficiency:hunters_charm", 2)));

        tree(Skill.WAYFARING,
                List.of(Requirement.of("minecraft:map", 8), Requirement.of("minecraft:compass", 4)),
                List.of(Requirement.of("minecraft:ender_pearl", 16), Requirement.of("waystones:waystone", 1)),
                List.of(Requirement.of("minecraft:ender_eye", 16), Requirement.of("minecraft:echo_shard", 8)),
                List.of(Requirement.of("minecraft:elytra", 1), Requirement.of("proficiency:wanderers_token", 2)));

        tree(Skill.SPELUNKING,
                List.of(Requirement.of("minecraft:cobblestone", 128), Requirement.of("minecraft:torch", 64)),
                List.of(Requirement.of("minecraft:deepslate", 64), Requirement.of("minecraft:redstone", 32), Requirement.of("minecraft:amethyst_shard", 16)),
                List.of(Requirement.of("minecraft:sculk", 32), Requirement.of("minecraft:echo_shard", 4), Requirement.of("minecraft:glow_berries", 32)),
                List.of(Requirement.of("minecraft:ancient_debris", 2), Requirement.of("proficiency:prospectors_draught", 2)));

        tree(Skill.MASONRY,
                List.of(Requirement.of("minecraft:cobblestone", 256), Requirement.of("minecraft:oak_planks", 128)),
                List.of(Requirement.of("minecraft:stone_bricks", 128), Requirement.of("minecraft:terracotta", 64), Requirement.of("minecraft:glass", 64)),
                List.of(Requirement.of("minecraft:deepslate_tiles", 64), Requirement.of("minecraft:quartz_block", 64), Requirement.of("minecraft:copper_block", 32)),
                List.of(Requirement.of("minecraft:amethyst_block", 16), Requirement.of("proficiency:masterwork_ingot", 1)));

        tree(Skill.DECORATING,
                List.of(Requirement.of("minecraft:white_wool", 64), Requirement.of("minecraft:flower_pot", 16), Requirement.of("minecraft:torch", 64)),
                List.of(Requirement.of("minecraft:painting", 8), Requirement.of("minecraft:item_frame", 16), Requirement.of("minecraft:lantern", 16), Requirement.of("handcrafted:oak_chair", 8)),
                List.of(Requirement.of("minecraft:candle", 32), Requirement.of("minecraft:glow_item_frame", 8), Requirement.of("supplementaries:sconce", 8)),
                List.of(Requirement.of("minecraft:beacon", 1), Requirement.of("handcrafted:oak_table", 4), Requirement.of("proficiency:masterwork_ingot", 1)));

        // Social is paid in what people share: a meal, a bell to gather at, books to teach from,
        // and at the end one of each specialist item, which is easiest to get from friends.
        tree(Skill.SOCIAL,
                List.of(Requirement.of("minecraft:cake", 2), Requirement.of("minecraft:bread", 32)),
                List.of(Requirement.of("minecraft:bell", 1), Requirement.of("minecraft:emerald", 16)),
                List.of(Requirement.of("minecraft:writable_book", 4), Requirement.of("minecraft:name_tag", 2)),
                List.of(Requirement.of("proficiency:masterwork_ingot", 1), Requirement.of("proficiency:prospectors_draught", 1),
                        Requirement.of("proficiency:hunters_charm", 1), Requirement.of("proficiency:wanderers_token", 1)));

        // Nightwalker is paid in what the night leaves behind: things found only in the dark, the
        // phantom's wing, the Deep Dark's shards, and a compass that finds your way back.
        tree(Skill.NIGHTWALKER,
                List.of(Requirement.of("minecraft:clock", 1), Requirement.of("minecraft:glow_berries", 16)),
                List.of(Requirement.of("minecraft:phantom_membrane", 8), Requirement.of("minecraft:spider_eye", 16)),
                List.of(Requirement.of("minecraft:echo_shard", 2), Requirement.of("minecraft:sculk", 16)),
                List.of(Requirement.of("minecraft:recovery_compass", 1), Requirement.of("proficiency:hunters_charm", 2)));

        // Courage is paid in trophies of fights you should not have won: a goat horn, a raid's
        // ominous bottle, an Evoker's totem, and a Wither skeleton's skull.
        tree(Skill.COURAGE,
                List.of(Requirement.of("minecraft:goat_horn", 1), Requirement.of("minecraft:bone", 32)),
                List.of(Requirement.of("minecraft:ominous_bottle", 1), Requirement.of("minecraft:emerald", 16)),
                List.of(Requirement.of("minecraft:totem_of_undying", 1), Requirement.of("minecraft:blaze_rod", 8)),
                List.of(Requirement.of("minecraft:wither_skeleton_skull", 1), Requirement.of("proficiency:hunters_charm", 2)));

        // Guardian is paid in what you carry for other people: a shield, golden apples and
        // glistering melon for healing, the Friend Compass's amethyst, and a heart of the sea.
        tree(Skill.GUARDIAN,
                List.of(Requirement.of("minecraft:shield", 1), Requirement.of("minecraft:iron_ingot", 8)),
                List.of(Requirement.of("minecraft:golden_apple", 2), Requirement.of("minecraft:glistering_melon_slice", 8)),
                List.of(Requirement.of("minecraft:amethyst_shard", 16), Requirement.of("minecraft:ghast_tear", 2)),
                List.of(Requirement.of("minecraft:heart_of_the_sea", 1), Requirement.of("proficiency:hunters_charm", 2)));

        // Charger is paid in what you carry into a fight: a banner to charge behind, a saddle for
        // the ride in, ender pearls and gunpowder for the breach, and a trident from the drowned.
        tree(Skill.CHARGER,
                List.of(Requirement.of("minecraft:white_banner", 1), Requirement.of("minecraft:leather", 8)),
                List.of(Requirement.of("minecraft:saddle", 1), Requirement.of("minecraft:iron_ingot", 16)),
                List.of(Requirement.of("minecraft:ender_pearl", 8), Requirement.of("minecraft:gunpowder", 16)),
                List.of(Requirement.of("minecraft:trident", 1), Requirement.of("proficiency:hunters_charm", 2)));

        // Tactician is paid in what the back line carries: a spyglass to read the field, spectral
        // arrows that light a target up, target blocks and glowstone for calling a shot, and an
        // eye of ender for the long view.
        tree(Skill.TACTICIAN,
                List.of(Requirement.of("minecraft:spyglass", 1), Requirement.of("minecraft:arrow", 32)),
                List.of(Requirement.of("minecraft:spectral_arrow", 16), Requirement.of("minecraft:leather", 8)),
                List.of(Requirement.of("minecraft:target", 4), Requirement.of("minecraft:glowstone_dust", 16)),
                List.of(Requirement.of("minecraft:ender_eye", 4), Requirement.of("proficiency:hunters_charm", 2)));

    }

    private static void tree(Skill skill,
            List<Requirement> tier1, List<Requirement> tier2,
            List<Requirement> tier3, List<Requirement> tier4) {
        DEFAULT_TIERS.put(skill, List.of(List.copyOf(tier1), List.copyOf(tier2),
                List.copyOf(tier3), List.copyOf(tier4)));
        build(skill, DEFAULT_TIERS.get(skill));
    }

    private static void build(Skill skill, List<List<Requirement>> tiers) {
        List<Talent> nodes = TalentTable.nodes(skill, tiers.get(0), tiers.get(1), tiers.get(2), tiers.get(3));
        for (Talent talent : nodes) {
            BY_KEY.put(talent.key(), talent);
        }
        BY_SKILL.put(skill, List.copyOf(nodes));
        CURRENT_TIERS.put(skill, tiers);
    }

    /** Number of material lists a tree has: the root (level 10), two level-60 nodes, one level-90. */
    public static final int TIERS = 4;

    /** The material list for tier 1..4 of this skill as it stands now, overrides included. */
    public static List<Requirement> tierMaterials(Skill skill, int tier) {
        return CURRENT_TIERS.get(skill).get(tier - 1);
    }

    /** The list the code ships with, before any pack override. */
    public static List<Requirement> defaultTierMaterials(Skill skill, int tier) {
        return DEFAULT_TIERS.get(skill).get(tier - 1);
    }

    /**
     * Replaces whole material lists, keyed by skill and tier 1..4, and rebuilds those trees. Node
     * keys do not change, so a node a player already paid for stays paid. Tiers not named keep the
     * list they had. Applying an empty map, or {@link #resetMaterials}, goes back to the defaults.
     */
    public static synchronized void applyMaterialOverrides(Map<Skill, Map<Integer, List<Requirement>>> overrides) {
        for (Skill skill : Skill.VALUES) {
            Map<Integer, List<Requirement>> forSkill = overrides.getOrDefault(skill, Map.of());
            List<List<Requirement>> tiers = new ArrayList<>(DEFAULT_TIERS.get(skill));
            for (Map.Entry<Integer, List<Requirement>> entry : forSkill.entrySet()) {
                int tier = entry.getKey();
                if (tier < 1 || tier > TIERS) {
                    throw new IllegalArgumentException("tier " + tier + " for " + skill.id());
                }
                tiers.set(tier - 1, List.copyOf(entry.getValue()));
            }
            build(skill, List.copyOf(tiers));
        }
    }

    /** Back to the shipped lists. Tests use it; nothing in game does. */
    public static void resetMaterials() {
        applyMaterialOverrides(Map.of());
    }

    /** A skill's nodes, trunk first, in reading order. */
    public static List<Talent> of(Skill skill) {
        return BY_SKILL.getOrDefault(skill, List.of());
    }

    public static Talent byKey(String key) {
        return BY_KEY.get(key);
    }

    public static Talent get(Skill skill, String id) {
        return BY_KEY.get(skill.id() + "/" + id);
    }

    /** Total point cost of one skill's whole tree, never more than the 50 a maxed skill yields. */
    public static int fullTreeCost(Skill skill) {
        return of(skill).stream().mapToInt(Talent::totalCost).sum();
    }

    /** Nodes in this tree that still owe materials, shallowest first. What the compass reads. */
    public static List<Talent> unpaidMaterialNodes(Skill skill, java.util.function.Predicate<Talent> paid) {
        return of(skill).stream()
                .filter(talent -> !talent.materials().isEmpty() && !paid.test(talent))
                .sorted(Comparator.comparingInt(Talent::row).thenComparingInt(Talent::col))
                .toList();
    }
}
