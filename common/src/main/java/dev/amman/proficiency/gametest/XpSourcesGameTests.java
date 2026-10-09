package dev.amman.proficiency.gametest;

import dev.amman.proficiency.Proficiency;
import dev.amman.proficiency.skill.BuildClassifier;
import dev.amman.proficiency.skill.FirstTimeKinds;
import dev.amman.proficiency.skill.KillXp;
import dev.amman.proficiency.skill.Skill;
import dev.amman.proficiency.skill.SkillService;
import dev.amman.proficiency.skill.SkillTools;
import dev.amman.proficiency.xp.XpDomain;
import dev.amman.proficiency.xp.XpMatch;
import dev.amman.proficiency.xp.XpReload;
import dev.amman.proficiency.xp.XpSources;
import dev.amman.proficiency.xp.XpSourcesLoader;
import dev.amman.proficiency.xp.XpSubject;
import dev.amman.proficiency.xp.XpSubjects;
import dev.amman.proficiency.xp.XpTable;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.TagKey;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.EmptyBlockGetter;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.structure.Structure;


import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * XP sources as data. The first test is the golden one: it walks EVERY block, item, mob and
 * structure registered in this game and compares the data-driven answer with the 1.2.0 hardcoded
 * rules, which are restated below. The others drive a real break and a real /reload-style table swap.
 */

public final class XpSourcesGameTests {

    /** Fabric instantiates each test class, as a {@code fabric-gametest} entrypoint. */
    public XpSourcesGameTests() {
    }


    // ---- the 1.2.0 rules, as they were ------------------------------------------------------

    private static final Set<String> FURNITURE = Set.of("handcrafted", "another_furniture",
            "supplementaries", "chipped", "adorabuild_structures", "amendments", "immersivelanterns",
            "hearths", "aurelj_paintings", "fastpaintings");
    private static final Set<String> MACHINES = Set.of("create", "createaddition", "create_dragons_plus",
            "create_enchantment_industry", "ae2", "arseng", "titanium", "functionalstorage",
            "merequester", "toms_storage");
    private static final Set<String> ARCANE = Set.of("ars_nouveau", "ars_additions", "arseng");

    private static TagKey<Block> blockTag(String ns, String path) {
        return TagKey.create(Registries.BLOCK, ResourceLocation.tryParse(ns + ":" + path));
    }

    private static Skill legacyHarvest(ItemStack tool, BlockState state) {
        if (state.is(BlockTags.LOGS) || state.is(BlockTags.LEAVES)) {
            return Skill.WOODCUTTING;
        }
        if (state.getBlock() instanceof net.minecraft.world.level.block.CropBlock || state.is(BlockTags.CROPS)) {
            return SkillTools.isRipe(state) ? Skill.FARMING : null;
        }
        if (state.is(BlockTags.MINEABLE_WITH_PICKAXE)) {
            return SkillTools.isPickaxe(tool) ? Skill.MINING : null;
        }
        if (state.is(BlockTags.MINEABLE_WITH_SHOVEL)) {
            return SkillTools.isShovel(tool) ? Skill.EXCAVATION : null;
        }
        if (state.is(BlockTags.MINEABLE_WITH_AXE)) {
            return Skill.WOODCUTTING;
        }
        return null;
    }

    private static double legacyBlockXp(float hardness) {
        return hardness < 0 ? 0 : Math.min(5.0, 0.5 + hardness * 0.30);
    }

    private static boolean legacyPlanting(BlockState state) {
        if (state.is(BlockTags.FLOWERS)) {
            return false;
        }
        Block b = state.getBlock();
        return b instanceof net.minecraft.world.level.block.CropBlock
                || b instanceof net.minecraft.world.level.block.StemBlock
                || b instanceof net.minecraft.world.level.block.NetherWartBlock
                || b instanceof net.minecraft.world.level.block.SweetBerryBushBlock
                || b instanceof net.minecraft.world.level.block.CocoaBlock
                || state.is(BlockTags.CROPS) || state.is(BlockTags.SAPLINGS);
    }

    private static boolean legacyDecorative(BlockState state) {
        ResourceLocation id = BuiltInRegistries.BLOCK.getKey(state.getBlock());
        if (id != null && FURNITURE.contains(id.getNamespace())) {
            return true;
        }
        return state.is(BlockTags.CANDLES) || state.is(BlockTags.WOOL_CARPETS) || state.is(BlockTags.BEDS)
                || state.is(BlockTags.BANNERS) || state.is(BlockTags.FLOWER_POTS) || state.is(BlockTags.SIGNS)
                || state.is(BlockTags.FLOWERS) || state.is(BlockTags.CAMPFIRES);
    }

    private static boolean legacyShape(BlockState state) {
        return BuildClassifier.isDecorativeClass(state.getBlock())
                || state.is(blockTag("c", "glass_blocks"))
                || state.is(BlockTags.FENCES) || state.is(BlockTags.FENCE_GATES)
                || state.is(BlockTags.DOORS) || state.is(BlockTags.TRAPDOORS)
                || state.is(BlockTags.BUTTONS) || state.is(BlockTags.PRESSURE_PLATES);
    }

    private static boolean legacySplit(BlockState state) {
        if (legacyPlanting(state) || legacyDecorative(state) || legacyShape(state)) {
            return false;
        }
        Block b = state.getBlock();
        return b instanceof net.minecraft.world.level.block.StairBlock
                || b instanceof net.minecraft.world.level.block.SlabBlock
                || b instanceof net.minecraft.world.level.block.WallBlock
                || state.is(BlockTags.STAIRS) || state.is(BlockTags.SLABS) || state.is(BlockTags.WALLS);
    }

    private static Skill legacyBuildSkill(BlockState state) {
        if (legacyPlanting(state)) {
            return Skill.FARMING;
        }
        if (legacyDecorative(state) || legacyShape(state)) {
            return Skill.DECORATING;
        }
        if (legacySplit(state)) {
            return Skill.MASONRY;
        }
        return BuildClassifier.isFullCube(state) ? Skill.MASONRY : Skill.DECORATING;
    }

    // ---- the golden test --------------------------------------------------------------------

    @GameTest(template = "proficiency:empty")
    public static void theShippedRulesMatchTheOldHardcodedOnes(GameTestHelper helper) {
        List<String> wrong = new ArrayList<>();
        ItemStack pick = new ItemStack(Items.DIAMOND_PICKAXE);
        ItemStack shovel = new ItemStack(Items.DIAMOND_SHOVEL);
        ItemStack hand = ItemStack.EMPTY;
        int blocks = 0;
        for (Block block : BuiltInRegistries.BLOCK) {
            BlockState state = block.defaultBlockState();
            String id = String.valueOf(BuiltInRegistries.BLOCK.getKey(block));
            float hardness = state.getDestroySpeed(EmptyBlockGetter.INSTANCE, BlockPos.ZERO);
            blocks++;
            for (ItemStack tool : new ItemStack[] {pick, shovel, hand}) {
                Skill old = legacyHarvest(tool, state);
                Skill now = SkillTools.harvestSkill(tool, state);
                if (old != now) {
                    wrong.add("harvest " + id + " with " + tool.getItem() + ": was " + old + ", now " + now);
                }
            }
            if (legacyHarvest(pick, state) != null || legacyHarvest(shovel, state) != null
                    || legacyHarvest(hand, state) != null) {
                Skill any = legacyHarvest(pick, state) != null ? legacyHarvest(pick, state)
                        : legacyHarvest(shovel, state) != null ? legacyHarvest(shovel, state) : legacyHarvest(hand, state);
                double old = any == Skill.FARMING ? 1.0 : legacyBlockXp(hardness);
                double now = SkillTools.breakXp(state, hardness);
                if (old != now) {
                    wrong.add("break xp " + id + ": was " + old + ", now " + now);
                }
            }
            if (BuildClassifier.isPlanting(state) != legacyPlanting(state)) {
                wrong.add("planting " + id);
            }
            if (BuildClassifier.isDecorative(state) != legacyDecorative(state)) {
                wrong.add("decorative " + id);
            }
            if (BuildClassifier.isSplit(state) != legacySplit(state)) {
                wrong.add("split " + id);
            }
            if (BuildClassifier.skillFor(state) != legacyBuildSkill(state)) {
                wrong.add("build skill " + id + ": was " + legacyBuildSkill(state) + ", now " + BuildClassifier.skillFor(state));
            }
            XpMatch place = BuildClassifier.placement(state);
            ResourceLocation key = BuiltInRegistries.BLOCK.getKey(block);
            boolean machine = key != null && MACHINES.contains(key.getNamespace());
            if (!legacyPlanting(state) && (place.has("machine") != machine)) {
                wrong.add("machine " + id);
            }
            // The first-time tier of a block.
            boolean premium = state.is(blockTag("c", "ores/diamond")) || state.is(blockTag("c", "ores/emerald"))
                    || state.is(blockTag("c", "ores/netherite_scrap")) || block == Blocks.ANCIENT_DEBRIS;
            double oldTier = FirstTimeKinds.blockMultiplier(premium || state.is(blockTag("c", "ores")), premium);
            XpMatch tier = XpSources.table().match(XpDomain.FIRST_TIME, XpSubjects.block(state));
            double newTier = tier == null ? 1.0 : tier.rule().multiplier;
            if (oldTier != newTier) {
                wrong.add("first-time block " + id + ": was " + oldTier + ", now " + newTier);
            }
        }
        int entities = 0;
        TagKey<EntityType<?>> bosses = TagKey.create(Registries.ENTITY_TYPE, Proficiency.id("notable_bosses"));
        // The common boss tags count too: a boss mod needs no edit of ours.
        List<TagKey<EntityType<?>>> bossTags = List.of(bosses,
                TagKey.create(Registries.ENTITY_TYPE, ResourceLocation.tryParse("c:bosses")),
                TagKey.create(Registries.ENTITY_TYPE, ResourceLocation.tryParse("neoforge:bosses")),
                TagKey.create(Registries.ENTITY_TYPE, ResourceLocation.tryParse("forge:bosses")));
        for (EntityType<?> type : BuiltInRegistries.ENTITY_TYPE) {
            ResourceLocation key = BuiltInRegistries.ENTITY_TYPE.getKey(type);
            String id = String.valueOf(key);
            entities++;
            double health = XpSubjects.defaultMaxHealth(type);
            XpSubject subject = XpSubjects.entity(type, health);
            XpMatch kill = XpSources.table().match(XpDomain.KILL, subject);
            boolean modded = key != null && !"minecraft".equals(key.getNamespace());
            if ((kill != null) != modded) {
                wrong.add("modded creature " + id);
            } else if (kill != null && (kill.rule().skill != Skill.BEASTSLAYING
                    || kill.xp() != Math.min(60.0, 1.0 + health / 4.0))) {
                wrong.add("kill xp " + id + ": " + kill.xp());
            }
            boolean boss = bossTags.stream().anyMatch(tag -> type.is(tag));
            XpMatch bossRule = XpSources.table().match(XpDomain.BOSS, subject);
            if ((bossRule != null && Boolean.TRUE.equals(bossRule.rule().boss)) != boss) {
                wrong.add("boss " + id);
            }
            double oldTier = FirstTimeKinds.entityMultiplier(boss, boss ? 0.0 : health);
            XpMatch tier = XpSources.table().match(XpDomain.FIRST_TIME, subject);
            double newTier = tier == null ? 1.0 : tier.rule().multiplier;
            if (oldTier != newTier) {
                wrong.add("first-time entity " + id + ": was " + oldTier + ", now " + newTier);
            }
            boolean arcane = key != null && ARCANE.contains(key.getNamespace());
            XpMatch cast = XpSources.table().match(XpDomain.CAST, subject);
            if ((cast != null) != arcane) {
                wrong.add("arcane entity " + id);
            }
        }
        int items = 0;
        TagKey<Item> casters = TagKey.create(Registries.ITEM, Proficiency.id("caster_items"));
        for (Item item : BuiltInRegistries.ITEM) {
            ResourceLocation key = BuiltInRegistries.ITEM.getKey(item);
            String id = String.valueOf(key);
            items++;
            XpSubject subject = XpSubjects.item(item);
            XpMatch craft = XpSources.table().match(XpDomain.CRAFT, subject);
            if ((craft != null) != (key != null && MACHINES.contains(key.getNamespace()))) {
                wrong.add("machine item " + id);
            }
            XpMatch cast = XpSources.table().match(XpDomain.CAST, subject);
            if ((cast != null) != BuiltInRegistries.ITEM.wrapAsHolder(item).is(casters)) {
                wrong.add("caster item " + id);
            }
            double oldTier = FirstTimeKinds.itemMultiplier(item.getDefaultInstance().getRarity().name());
            XpMatch tier = XpSources.table().match(XpDomain.FIRST_TIME, subject);
            double newTier = tier == null ? 1.0 : tier.rule().multiplier;
            if (oldTier != newTier) {
                wrong.add("first-time item " + id + ": was " + oldTier + ", now " + newTier);
            }
        }
        int structures = 0;
        var registry = helper.getLevel().registryAccess().registryOrThrow(Registries.STRUCTURE);
        TagKey<Structure> grand = TagKey.create(Registries.STRUCTURE, Proficiency.id("grand_structures"));
        for (Structure structure : registry) {
            ResourceLocation key = registry.getKey(structure);
            structures++;
            XpMatch match = XpSources.table().match(XpDomain.STRUCTURE,
                    XpSubjects.structure(key, registry.wrapAsHolder(structure)));
            boolean isGrand = registry.wrapAsHolder(structure).is(grand);
            if ((match != null && match.has("grand")) != isGrand) {
                wrong.add("grand structure " + key);
            }
        }
        helper.assertTrue(blocks > 500 && entities > 50 && items > 800 && structures > 20,
                "the registries looked too small to mean anything: " + blocks + " blocks, " + entities
                        + " mobs, " + items + " items, " + structures + " structures");
        helper.assertTrue(wrong.isEmpty(), wrong.size() + " differences, first: "
                + wrong.subList(0, Math.min(8, wrong.size())));
        helper.succeed();
    }

    // ---- a real load, a real break ----------------------------------------------------------

    @GameTest(template = "proficiency:empty")
    public static void theDefaultsLoadCleanAgainstTheRealRegistries(GameTestHelper helper) {
        XpSourcesLoader.Result result = XpReload.reload(helper.getLevel().getServer());
        helper.assertTrue(result.messages.isEmpty(), "the load reported: " + result.messages);
        helper.assertTrue(result.files >= 1 && result.sources >= 30,
                "too little loaded: " + result.summary());
        helper.assertTrue(result.table.ruleCount() == result.sources, "the table and the summary disagree");
        helper.succeed();
    }

    private static String id(Block block) {
        return block.getDescriptionId();
    }

    private static float paid(ServerPlayer player, Skill skill, String source) {
        float sum = 0f;
        var log = SkillService.xpLog(player);
        if (log != null) {
            for (var entry : log.entries()) {
                if (entry.skill() == skill.ordinal() && entry.source().equals(source)) {
                    sum += entry.amount();
                }
            }
        }
        return sum;
    }

    /** Swaps in defaults plus one pack file, breaks a real block, and puts the old table back. */
    private static XpTable withPack(String json) {
        XpSourcesLoader.Result result = XpSourcesLoader.load(List.of(
                new XpSourcesLoader.SourceFile("proficiency:xp_sources/defaults", XpSources.readDefaults()),
                new XpSourcesLoader.SourceFile("pack:xp_sources/test", json)), XpSourcesLoader.Registries.ANY);
        return result.table;
    }

    /** Breaks a stone block with a fresh player and returns what the Mining log line paid. */
    private static float stonePaid(GameTestHelper helper) {
        ServerPlayer player = helper.makeMockServerPlayerInLevel();
        player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.DIAMOND_PICKAXE));
        helper.setBlock(new BlockPos(1, 1, 1), Blocks.STONE);
        Breaks.separately(player, helper.absolutePos(new BlockPos(1, 1, 1)));
        return paid(player, Skill.MINING, id(Blocks.STONE));
    }

    @GameTest(template = "proficiency:empty")
    public static void aPackRuleChangesWhatABlockPays(GameTestHelper helper) {
        XpTable before = XpSources.table();
        try {
            // Stone pays 0.5 + 0.3 x 1.5 = 0.95 by default. Every other factor (rate, perks, tempo)
            // is the same for two fresh players, so the ratio is the rule's.
            float base = stonePaid(helper);
            XpSources.install(withPack("{\"break\":[{\"match\":\"minecraft:stone\",\"skill\":\"mining\",\"xp\":8}]}"));
            float changed = stonePaid(helper);
            helper.assertTrue(base > 0f, "stone paid nothing by default");
            helper.assertTrue(Math.abs(changed / base - 8f / 0.95f) < 0.1f,
                    "stone paid " + changed + " against " + base + " before, wanted a ratio of " + 8f / 0.95f);
        } finally {
            XpSources.install(before);
        }
        helper.succeed();
    }

    @GameTest(template = "proficiency:empty")
    public static void aPackRuleCanMakeAModdedLogPayWoodcutting(GameTestHelper helper) {
        XpTable before = XpSources.table();
        try {
            // Not a mineable-with-axe block: with no rule it pays nothing at all.
            XpSources.install(withPack("{\"break\":[{\"match\":\"minecraft:bookshelf\",\"skill\":\"woodcutting\","
                    + "\"xp\":{\"base\":1,\"per_hardness\":1,\"max\":4}}]}"));
            ServerPlayer player = helper.makeMockServerPlayerInLevel();
            player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.DIAMOND_AXE));
            helper.setBlock(new BlockPos(1, 1, 1), Blocks.BOOKSHELF);
            Breaks.separately(player, helper.absolutePos(new BlockPos(1, 1, 1)));
            float got = paid(player, Skill.WOODCUTTING, id(Blocks.BOOKSHELF));
            helper.assertTrue(got > 0f, "the bookshelf paid no Woodcutting XP under the pack rule");
        } finally {
            XpSources.install(before);
        }
        helper.succeed();
    }

    @GameTest(template = "proficiency:empty")
    public static void skillNonePaysNothing(GameTestHelper helper) {
        XpTable before = XpSources.table();
        try {
            XpSources.install(withPack("{\"break\":[{\"match\":\"minecraft:stone\",\"skill\":\"none\"}]}"));
            ServerPlayer player = helper.makeMockServerPlayerInLevel();
            player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.DIAMOND_PICKAXE));
            helper.setBlock(new BlockPos(1, 1, 1), Blocks.STONE);
            Breaks.separately(player, helper.absolutePos(new BlockPos(1, 1, 1)));
            helper.assertTrue(paid(player, Skill.MINING, id(Blocks.STONE)) == 0f, "a none rule still paid");
        } finally {
            XpSources.install(before);
        }
        helper.succeed();
    }

    @GameTest(template = "proficiency:empty")
    public static void theExplainCommandRuns(GameTestHelper helper) throws Exception {
        var server = helper.getLevel().getServer();
        int lines = server.getCommands().getDispatcher().execute("proficiency xpsources minecraft:stone",
                server.createCommandSourceStack().withPermission(4));
        helper.assertTrue(lines >= 6, "the command printed only " + lines + " lines");
        int mob = server.getCommands().getDispatcher().execute("proficiency xpsources minecraft:plains",
                server.createCommandSourceStack().withPermission(4));
        helper.assertTrue(mob >= 2, "a biome printed " + mob + " lines");
        helper.succeed();
    }
}
