package dev.amman.proficiency.gametest;

import dev.amman.proficiency.Proficiency;
import dev.amman.proficiency.hearth.HearthAttachments;
import dev.amman.proficiency.hearth.HearthBlock;
import dev.amman.proficiency.hearth.HearthBlockEntity;
import dev.amman.proficiency.hearth.HearthBlocks;
import dev.amman.proficiency.hearth.HearthDataLoader;
import dev.amman.proficiency.hearth.HearthHooks;
import dev.amman.proficiency.hearth.HearthIndexData;
import dev.amman.proficiency.hearth.HearthLedger;
import dev.amman.proficiency.hearth.HearthPool;
import dev.amman.proficiency.hearth.HearthRange;
import dev.amman.proficiency.hearth.HearthRules;
import dev.amman.proficiency.hearth.HearthState;
import dev.amman.proficiency.hearth.HearthWorldData;
import dev.amman.proficiency.hearth.Hearths;
import dev.amman.proficiency.hearth.PlayerHearth;
import dev.amman.proficiency.hearth.net.HearthNet;
import dev.amman.proficiency.hearth.net.HearthRequestPayload;
import dev.amman.proficiency.hearth.net.HearthStatePayload;
import dev.amman.proficiency.hearth.sieges.SiegeTrophyBlock;
import dev.amman.proficiency.hearth.sieges.SiegeTrophyItem;
import dev.amman.proficiency.hearth.stages.HearthBreaking;
import dev.amman.proficiency.skill.Skill;
import dev.amman.proficiency.skill.SkillService;
import dev.amman.proficiency.skill.SpawnOrigin;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.game.ServerboundPlayerActionPacket;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.TagKey;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.boss.enderdragon.EnderDragon;
import net.minecraft.world.entity.boss.wither.WitherBoss;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.monster.Zombie;
import net.minecraft.world.entity.projectile.WitherSkull;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Explosion;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.common.util.BlockSnapshot;
import net.minecraftforge.event.entity.living.LivingEntityUseItemEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.event.entity.player.PlayerWakeUpEvent;
import net.minecraftforge.event.level.BlockEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

/**
 * The Hearth foundation on Forge 1.20.1, in a real world (the port of common's {@code HearthGameTests}):
 * the block entity saves and loads, the player capability saves and loads, the registry entries match
 * the art contract, the data (tags, loot tables, recipe, Hearth data) loads on 1.20.1, the index
 * follows the block, and every hook fires from its Forge event. The rules themselves are unit tested
 * in core. Each Hearth-placing test has its own batch: the {@code empty} template is tiny, so a
 * neighbour's Hearth would be "in range". Every test starts with {@link HearthTestGuard#watch}, which puts
 * the world back when it ends.
 */
@GameTestHolder(Proficiency.MOD_ID)
@PrefixGameTestTemplate(false)
public final class HearthGameTests {

    private static final String T = "empty";
    private static final UUID AMMAN = UUID.fromString("00000000-0000-0000-0000-0000000000a1");

    private HearthGameTests() {
    }

    private static ServerPlayer survivor(GameTestHelper helper) {
        ServerPlayer player = MockPlayers.make(helper);
        player.setGameMode(GameType.SURVIVAL);
        return player;
    }

    private static void clearSpawnProtection(ServerPlayer player) {
        try {
            java.lang.reflect.Field field = ServerPlayer.class.getDeclaredField("spawnInvulnerableTime");
            field.setAccessible(true);
            field.setInt(player, 0);
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException("could not clear spawn protection", e);
        }
    }

    private static BlockState hearth(int stage, boolean smouldering) {
        return HearthBlocks.HEARTH.get().defaultBlockState().setValue(HearthBlock.STAGE, stage)
                .setValue(HearthBlock.SMOULDERING, smouldering);
    }

    private static TagKey<Block> blockTag(String path) {
        return TagKey.create(Registries.BLOCK, Proficiency.id(path));
    }

    /**
     * Reach (docs/HEARTH-REACH-SPEC.md): Homebuilder XP on a Hearth widens its territory, while the
     * house (the base radius) stays put. Half of reachFullXp on the defaults (24 -> 64) is 44 blocks.
     */
    @GameTest(template = T, batch = "hearth_reach", timeoutTicks = 200)
    public static void reachWidensTheTerritoryNotTheHouse(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos rel = new BlockPos(1, 2, 1);
        BlockPos abs = helper.absolutePos(rel);
        helper.setBlock(rel, hearth(1, false));
        helper.assertTrue(HearthRange.hearthAt(level, abs.offset(40, 1, 0)) == null, "40 blocks out is outside at first");
        if (!(level.getBlockEntity(abs) instanceof HearthBlockEntity hearth)) {
            helper.fail("no Hearth block entity");
            return;
        }
        hearth.state().addReach(dev.amman.proficiency.config.ProficiencyConfig.hearthReachFullXp() / 2);
        helper.runAfterDelay(45, () -> {
            helper.assertTrue(abs.equals(HearthRange.hearthAt(level, abs.offset(40, 1, 0))),
                    "half Reach should reach 40 blocks (radius " + HearthRange.radius(level, abs) + ")");
            helper.assertTrue(HearthRange.hearthAt(level, abs.offset(50, 1, 0)) == null, "but not 50");
            helper.assertTrue(HearthRange.houseAt(level, abs.offset(40, 1, 0)) == null, "the house stays at 24");
            helper.assertTrue(HearthRange.inRange(level, abs, abs.offset(40, -8, 0)), "the territory keeps the heights");
            hearth.state().setReach(0);
            helper.runAfterDelay(25, () -> {
                helper.assertTrue(HearthRange.hearthAt(level, abs.offset(40, 1, 0)) == null, "Reach 0 is the base again");
                helper.setBlock(rel, net.minecraft.world.level.block.Blocks.AIR.defaultBlockState());
                helper.succeed();
            });
        });
    }

    /** Reach charges from Homebuilder XP earned by a player at the Hearth, at its base amount. */
    @GameTest(template = T, batch = "hearth_reach_charge", timeoutTicks = 200)
    public static void homebuilderXpAtHomeChargesReach(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos rel = new BlockPos(1, 2, 1);
        BlockPos abs = helper.absolutePos(rel);
        helper.setBlock(rel, hearth(1, false));
        ServerPlayer player = survivor(helper);
        player.setPos(abs.getX() + 3.5, abs.getY(), abs.getZ() + 0.5);
        helper.runAfterDelay(45, () -> {
            if (!(level.getBlockEntity(abs) instanceof HearthBlockEntity hearth)) {
                helper.fail("no Hearth block entity");
                return;
            }
            helper.assertTrue(abs.equals(HearthRange.homeOf(player)), "setup: the player should be home");
            double before = hearth.state().reach();
            dev.amman.proficiency.skill.SkillService.grant(player, dev.amman.proficiency.skill.Skill.HOMEBUILDER,
                    40.0, "proficiency.xplog.source.test");
            helper.assertTrue(Math.abs(hearth.state().reach() - before - 40.0) < 1e-6,
                    "40 Homebuilder XP should add 40 Reach, added " + (hearth.state().reach() - before));
            // Another skill's XP adds nothing to Reach.
            dev.amman.proficiency.skill.SkillService.grant(player, dev.amman.proficiency.skill.Skill.MASONRY, 40.0,
                    "proficiency.xplog.source.test");
            helper.assertTrue(Math.abs(hearth.state().reach() - before - 40.0) < 1e-6, "Masonry XP charged Reach");
            hearth.state().setReach(0);
            player.server.getPlayerList().remove(player);
            helper.setBlock(rel, net.minecraft.world.level.block.Blocks.AIR.defaultBlockState());
            helper.succeed();
        });
    }

    @GameTest(template = T, batch = "hearth_save")
    public static void aHearthBlockEntitySavesAndLoads(GameTestHelper helper) {
        HearthTestGuard.watch(helper);
        ServerLevel level = helper.getLevel();
        BlockPos rel = new BlockPos(1, 2, 1);
        BlockPos abs = helper.absolutePos(rel);
        helper.setBlock(rel, hearth(4, false));
        if (!(level.getBlockEntity(abs) instanceof HearthBlockEntity hearth)) {
            helper.fail("a Hearth block should carry a Hearth block entity");
            return;
        }
        HearthState state = hearth.state();
        state.setCharge(HearthPool.HEARTY, 1500);
        state.setCharge(HearthPool.HOMECOMING, 12.5);
        state.setStage(4);
        state.setHighestOffering(4);
        state.setCharacter(17);
        state.setSieged(true);
        state.setDefenceStreak(2);
        state.onLit(100, 4000);
        state.ledger().add(AMMAN, "Amman", HearthPool.HEARTY, "mining", 15, 3);
        hearth.laneData("sieges").putInt("douse", 7);
        hearth.setChanged();

        // 1.20.1: no registry lookup on either side.
        CompoundTag saved = hearth.saveWithFullMetadata();
        BlockEntity loaded = BlockEntity.loadStatic(abs, level.getBlockState(abs), saved);
        if (!(loaded instanceof HearthBlockEntity copy)) {
            helper.fail("the saved tag should load back as a Hearth block entity, got " + loaded);
            return;
        }
        HearthState back = copy.state();
        helper.assertTrue(back.charge(HearthPool.HEARTY) == 1500.0, "Hearty charge: " + back.charge(HearthPool.HEARTY));
        helper.assertTrue(back.charge(HearthPool.HOMECOMING) == 12.5, "Homecoming charge");
        helper.assertTrue(back.stage() == 4 && back.highestOffering() == 4, "stage and offering");
        helper.assertTrue(back.character() == 17, "Character cache");
        helper.assertTrue(back.sieged() && back.defenceStreak() == 2, "siege fields");
        helper.assertTrue(back.litAt() == 100 && back.lastSiegeEndPlay() == 4000, "clocks");
        helper.assertTrue(back.ledger().total(AMMAN, null, HearthLedger.Window.LIFETIME) == 15.0, "ledger");
        helper.assertTrue(copy.laneData("sieges").getInt("douse") == 7, "a lane's own tag");
        helper.assertTrue(saved.contains("hearth") && saved.contains("lanes"), "the NBT keys of 1.21.1: " + saved.getAllKeys());

        // The one state builder every lane sends with.
        HearthStatePayload panel = HearthNet.stateOf(hearth, HearthStatePayload.PANEL);
        helper.assertTrue(panel.pos() == abs.asLong() && panel.stage() == 4 && panel.ceiling() == 4,
                "state payload: stage " + panel.stage() + " ceiling " + panel.ceiling());
        helper.assertTrue(panel.pools().size() == HearthPool.COUNT && panel.siegesOn(), "state payload rows");
        helper.assertTrue(!panel.dimension().isEmpty(), "dimension");

        helper.setBlock(rel, Blocks.AIR);
        helper.succeed();
    }

    @GameTest(template = T, batch = "hearth_player_save")
    public static void thePlayersHearthDataSavesAndLoads(GameTestHelper helper) {
        HearthTestGuard.watch(helper);
        ServerPlayer player = survivor(helper);
        PlayerHearth data = HearthAttachments.of(player);
        data.addTales(30);
        data.setWarmth(0.5);
        data.setHomewardDay(12);
        data.markMilestone("x|stage:1");
        data.setFlag("stages.firstLit", true);
        data.setMark("homebuilder.homeward_at", 4321L);
        data.setAbsorbShare(0.2);
        CompoundTag saved = player.saveWithoutId(new CompoundTag());
        helper.assertTrue(saved.getCompound("ForgeCaps").contains(HearthAttachments.KEY.toString()),
                "the capability saves under ForgeCaps/" + HearthAttachments.KEY);

        ServerPlayer other = survivor(helper);
        // Another body, the same save: keep its own UUID (two players with one UUID confuse the level).
        saved.remove("UUID");
        other.load(saved);
        PlayerHearth back = HearthAttachments.of(other);
        helper.assertTrue(back.tales() == 30.0 && back.warmth() == 0.5f, "Tales " + back.tales() + " Warmth " + back.warmth());
        helper.assertTrue(back.homewardDay() == 12 && back.hasMilestone("x|stage:1"), "Homebuilder marks");
        helper.assertTrue(back.flag("stages.firstLit") && back.mark("homebuilder.homeward_at", 0L) == 4321L,
                "a lane's flag and mark");
        helper.assertTrue(back.absorbShare() == 0.2f, "the temp-HP share (1.20.1's absorption book-keeping)");
        helper.succeed();
    }

    @GameTest(template = T, batch = "hearth_registry")
    public static void theRegistryEntriesMatchTheArtContract(GameTestHelper helper) {
        HearthTestGuard.watch(helper);
        ServerLevel level = helper.getLevel();
        helper.assertTrue(BuiltInRegistries.BLOCK.containsKey(new ResourceLocation("proficiency:hearth")), "block hearth");
        helper.assertTrue(BuiltInRegistries.BLOCK.containsKey(new ResourceLocation("proficiency:siege_trophy")),
                "block siege_trophy");
        helper.assertTrue(BuiltInRegistries.ITEM.containsKey(new ResourceLocation("proficiency:siege_horn")), "item siege_horn");
        helper.assertTrue(BuiltInRegistries.ITEM.containsKey(new ResourceLocation("proficiency:siege_trophy")),
                "item siege_trophy");
        helper.assertTrue(BuiltInRegistries.BLOCK_ENTITY_TYPE.containsKey(new ResourceLocation("proficiency:hearth")),
                "block entity type hearth");
        // The Hearth is built in the world, never held: no item.
        helper.assertTrue(HearthBlocks.HEARTH.get().asItem() == Items.AIR, "the Hearth has no item");
        helper.assertTrue(HearthBlock.STAGE.getPossibleValues().size() == 6, "stage 1..6");
        helper.assertTrue(HearthBlock.GLOW.getPossibleValues().size() == 7, "glow 0..6");
        helper.assertTrue(HearthBlock.FEED.getPossibleValues().size() == HearthPool.COUNT + 1, "feed 0..pools");
        helper.assertTrue(SiegeTrophyBlock.TIER.getPossibleValues().size() == 4, "tier 3..6");
        // The light follows the charge (glow), not the stage, as on 1.21.1 since its integration task.
        for (int glow = 0; glow <= 6; glow++) {
            helper.assertTrue(hearth(3, false).setValue(HearthBlock.GLOW, glow).getLightEmission()
                    == HearthRules.chargeLight(glow, false), "light at glow " + glow);
        }
        helper.assertTrue(hearth(6, false).getLightEmission() == HearthRules.chargeLight(0, false),
                "a new Hearth glows as one with no charge, whatever its stage");
        helper.assertTrue(hearth(6, true).getLightEmission() == HearthRules.chargeLight(0, true), "smouldering light");
        // The shipped data (common's default.json, laid in by the build) loaded at server start.
        var loaded = dev.amman.proficiency.hearth.HearthReload.lastResult();
        helper.assertTrue(loaded != null && loaded.files() >= 1 && loaded.skippedFiles() == 0,
                "the Hearth data should have loaded: " + (loaded == null ? "never" : loaded.summary()));
        helper.assertTrue(loaded.messages().stream().noneMatch(m -> m.level() == HearthDataLoader.Level.ERROR),
                "no errors in the Hearth data: " + loaded.messages());
        helper.assertTrue(Hearths.data() == loaded.data(), "the loaded data should be live");
        helper.assertTrue(level.getBlockState(helper.absolutePos(new BlockPos(1, 2, 1))).isAir(), "setup");
        helper.succeed();
    }

    /** The 1.20.1 data: tags (plural folders, 1.21-only ids optional), loot tables, the recipe. */
    @GameTest(template = T, batch = "hearth_data")
    public static void theHearthDataLoadsOnThisVersion(GameTestHelper helper) {
        HearthTestGuard.watch(helper);
        ServerLevel level = helper.getLevel();
        // A tag naming an id 1.20.1 lacks fails as a whole: these prove each file loaded.
        helper.assertTrue(Blocks.POLISHED_ANDESITE.defaultBlockState().is(blockTag("hearth/stone")), "hearth/stone");
        helper.assertTrue(Blocks.STONE_BRICK_STAIRS.defaultBlockState().is(blockTag("hearth/stone_shaped")), "hearth/stone_shaped");
        helper.assertTrue(Blocks.OAK_SLAB.defaultBlockState().is(blockTag("hearth/wood_shaped")), "hearth/wood_shaped");
        helper.assertTrue(Blocks.WAXED_OXIDIZED_CUT_COPPER.defaultBlockState().is(blockTag("hearth/copper")), "hearth/copper");
        helper.assertTrue(Blocks.IRON_BLOCK.defaultBlockState().is(blockTag("hearth/iron")), "hearth/iron");
        helper.assertTrue(Blocks.GOLD_BLOCK.defaultBlockState().is(blockTag("hearth/gold")), "hearth/gold");
        helper.assertTrue(Blocks.IRON_BARS.defaultBlockState().is(blockTag("hearth/iron_bars")), "hearth/iron_bars");
        helper.assertTrue(Blocks.CHAIN.defaultBlockState().is(blockTag("hearth/chain")), "hearth/chain");
        helper.assertTrue(Blocks.LIGHTNING_ROD.defaultBlockState().is(blockTag("hearth/lightning_rod")), "hearth/lightning_rod");
        helper.assertTrue(Blocks.LODESTONE.defaultBlockState().is(blockTag("hearth/lodestone")), "hearth/lodestone");
        helper.assertTrue(Blocks.LANTERN.defaultBlockState().is(blockTag("hearth/lantern")), "hearth/lantern");
        helper.assertTrue(Blocks.BELL.defaultBlockState().is(blockTag("hearth/bell")), "hearth/bell");
        helper.assertTrue(Blocks.DIRT.defaultBlockState().is(blockTag("hearth/character_ignore")), "character_ignore");
        String[][] comfort = {{"bed", "red_bed"}, {"seat", "oak_stairs"}, {"carpet", "moss_carpet"},
                {"banner", "white_banner"}, {"fire", "campfire"}, {"light", "lantern"}, {"plant", "potted_poppy"},
                {"books", "chiseled_bookshelf"}, {"music", "jukebox"}, {"storage", "decorated_pot"},
                {"trophy", "piglin_head"}};
        for (String[] kind : comfort) {
            Block block = BuiltInRegistries.BLOCK.get(new ResourceLocation(kind[1]));
            helper.assertTrue(block.defaultBlockState().is(blockTag("comfort/" + kind[0])),
                    "comfort/" + kind[0] + " should hold minecraft:" + kind[1]);
        }
        helper.assertTrue(HearthBlocks.SIEGE_TROPHY.get().defaultBlockState().is(blockTag("comfort/trophy")),
                "the Siege Trophy counts as a trophy");
        helper.assertTrue(EntityType.PAINTING.is(TagKey.create(Registries.ENTITY_TYPE, Proficiency.id("comfort/art"))),
                "entity comfort/art");
        helper.assertTrue(EntityType.ARMOR_STAND.is(TagKey.create(Registries.ENTITY_TYPE, Proficiency.id("comfort/trophy"))),
                "entity comfort/trophy");

        // Loot tables: the Hearth drops a plain campfire; the trophy keeps its tier (copy_state -> BlockStateTag).
        BlockPos at = helper.absolutePos(new BlockPos(1, 2, 1));
        List<ItemStack> hearthDrops = Block.getDrops(hearth(3, false), level, at, null);
        helper.assertTrue(hearthDrops.size() == 1 && hearthDrops.get(0).is(Items.CAMPFIRE),
                "the Hearth drops one campfire: " + hearthDrops);
        BlockState trophy = HearthBlocks.SIEGE_TROPHY.get().defaultBlockState().setValue(SiegeTrophyBlock.TIER, 5);
        List<ItemStack> trophyDrops = Block.getDrops(trophy, level, at, null);
        helper.assertTrue(trophyDrops.size() == 1 && SiegeTrophyItem.tier(trophyDrops.get(0)) == 5,
                "a tier V trophy drops a tier V trophy: " + trophyDrops
                        + (trophyDrops.isEmpty() ? "" : " " + trophyDrops.get(0).getTag()));

        helper.assertTrue(level.getRecipeManager().byKey(Proficiency.id("siege_horn")).isPresent(), "the horn recipe");
        helper.succeed();
    }

    /** The trophy's tier rides in BlockStateTag, which vanilla's BlockItem applies when it is hung. */
    @GameTest(template = T, batch = "hearth_trophy")
    public static void aTrophyHangsWithItsTier(GameTestHelper helper) {
        HearthTestGuard.watch(helper);
        ServerLevel level = helper.getLevel();
        ServerPlayer player = survivor(helper);
        ItemStack stack = SiegeTrophyItem.of(6, 42);
        helper.assertTrue(SiegeTrophyItem.tier(stack) == 6 && SiegeTrophyItem.day(stack) == 42, "of/tier/day");
        helper.assertTrue(SiegeTrophyItem.tier(new ItemStack(HearthBlocks.SIEGE_TROPHY_ITEM.get())) == 3,
                "a bare trophy reads as III");
        helper.assertTrue(SiegeTrophyItem.tier(SiegeTrophyItem.of(9, 0)) == 6, "clamped to VI");
        BlockPos wallRel = new BlockPos(2, 2, 2);
        helper.setBlock(wallRel, Blocks.STONE);
        BlockPos wall = helper.absolutePos(wallRel);
        BlockPos front = wall.north();
        player.setItemInHand(InteractionHand.MAIN_HAND, stack);
        InteractionResult result = stack.useOn(new UseOnContext(player, InteractionHand.MAIN_HAND,
                new BlockHitResult(Vec3.atCenterOf(wall).relative(Direction.NORTH, 0.5), Direction.NORTH, wall, false)));
        BlockState hung = level.getBlockState(front);
        helper.assertTrue(result.consumesAction() && hung.is(HearthBlocks.SIEGE_TROPHY.get()),
                "the trophy should hang on the wall: " + result + " " + hung);
        helper.assertTrue(hung.getValue(SiegeTrophyBlock.TIER) == 6, "it hangs as tier " + hung.getValue(SiegeTrophyBlock.TIER));
        helper.assertTrue(hung.getValue(SiegeTrophyBlock.FACING) == Direction.NORTH, "facing away from its wall");
        // The wall goes, the trophy drops.
        helper.setBlock(wallRel, Blocks.AIR);
        helper.assertTrue(level.getBlockState(front).isAir(), "a trophy without its wall drops");
        helper.succeed();
    }

    @GameTest(template = T, batch = "hearth_index", timeoutTicks = 80)
    public static void theHearthIndexesItselfAndTicksOnceASecond(GameTestHelper helper) {
        HearthTestGuard.watch(helper);
        ServerLevel level = helper.getLevel();
        BlockPos rel = new BlockPos(1, 2, 1);
        BlockPos abs = helper.absolutePos(rel);
        AtomicInteger seconds = new AtomicInteger();
        HearthHooks.HearthTickListener listener = (lvl, pos, hearth) -> {
            if (pos.equals(abs)) {
                seconds.incrementAndGet();
            }
        };
        HearthHooks.HEARTH_TICK.add(listener);
        helper.setBlock(rel, hearth(1, false));
        helper.assertTrue(HearthIndexData.get(level).contains(abs), "placing the block should index it");
        helper.assertTrue(abs.equals(HearthRange.hearthAt(level, abs.offset(1, 1, 0))), "hearthAt should find it");
        helper.assertTrue(HearthRange.hearthAt(level, abs.offset(0, 30, 0)) == null, "the cylinder stops 16 above");
        helper.assertTrue(HearthRange.inHouse(abs, abs.offset(24, -8, 0)), "the cylinder's edge");
        helper.assertTrue(HearthRange.hearthAtPacked(level, abs.getX() + 1, abs.getY() + 1, abs.getZ()) == abs.asLong(),
                "the packed lookup agrees");
        helper.assertTrue(HearthRange.hearthAtPacked(level, abs.getX(), abs.getY() + 30, abs.getZ())
                == dev.amman.proficiency.hearth.HearthIndexStore.NONE, "the packed lookup's none");
        helper.runAfterDelay(50, () -> {
            HearthHooks.HEARTH_TICK.remove(listener);
            int n = seconds.get();
            helper.setBlock(rel, Blocks.AIR);
            helper.assertFalse(HearthIndexData.get(level).contains(abs), "removing the block should unindex it");
            helper.assertTrue(n >= 2 && n <= 3, "the Hearth should tick once a second: " + n + " in 50 ticks");
            helper.succeed();
        });
    }

    @GameTest(template = T, batch = "hearth_index_ghosts")
    public static void aGhostEntryIsForgottenOnceItsChunkShowsNoHearth(GameTestHelper helper) {
        HearthTestGuard.watch(helper);
        ServerLevel level = helper.getLevel();
        HearthIndexData index = HearthIndexData.get(level);
        BlockPos realRel = new BlockPos(3, 2, 3);
        BlockPos real = helper.absolutePos(realRel);
        BlockPos ghostA = helper.absolutePos(new BlockPos(1, 2, 1));
        BlockPos ghostB = helper.absolutePos(new BlockPos(1, 2, 4));
        BlockPos ghostC = helper.absolutePos(new BlockPos(4, 2, 3));
        helper.setBlock(realRel, hearth(2, false));
        // Entries whose block vanished without onRemove (a trimmed chunk, an editor): air there.
        index.add(ghostA);
        index.add(ghostB);
        index.add(ghostC);
        HearthWorldData world = HearthWorldData.get(level.getServer());
        try {
            // The spacing check confirms and forgets: the nearest entry (C) is a ghost, the real
            // Hearth is the answer, A and B are left for the other two ways.
            BlockPos lighting = helper.absolutePos(new BlockPos(4, 2, 4));
            BlockPos close = index.tooClose(level, lighting, 6);
            helper.assertTrue(real.equals(close), "the real Hearth is the one too close, got " + close);
            helper.assertFalse(index.contains(ghostC), "the spacing check forgot the nearest ghost");
            helper.assertTrue(index.contains(ghostA) && index.contains(ghostB), "only the ghost it met");
            helper.assertTrue(HearthRange.entity(level, ghostA) == null, "no block entity at a ghost");
            helper.assertFalse(index.contains(ghostA), "entity() forgot the ghost");
            int dropped = index.prune(level);
            helper.assertTrue(dropped >= 1 && !index.contains(ghostB), "the sweep dropped " + dropped);
            helper.assertTrue(index.contains(real), "the real Hearth stays indexed");
            helper.assertTrue(HearthRange.entity(level, real) != null, "the real Hearth's block entity");
        } finally {
            index.remove(ghostA);
            index.remove(ghostB);
            index.remove(ghostC);
            helper.setBlock(realRel, Blocks.AIR);
        }
        helper.assertFalse(index.contains(real), "breaking forgets the real one");
        helper.assertTrue(world.siegeDue(HearthBlockEntity.key(level, real)) == HearthState.NEVER, "no siege mirror left");
        helper.succeed();
    }

    @GameTest(template = T, batch = "hearth_siege_mirror", timeoutTicks = 100)
    public static void theWorldSiegeClockMirrorsTheHearthAndGoesWithIt(GameTestHelper helper) {
        HearthTestGuard.watch(helper);
        ServerLevel level = helper.getLevel();
        BlockPos rel = new BlockPos(1, 2, 1);
        BlockPos abs = helper.absolutePos(rel);
        helper.setBlock(rel, hearth(3, false));
        if (!(level.getBlockEntity(abs) instanceof HearthBlockEntity hearth)) {
            helper.fail("a Hearth block should carry a Hearth block entity");
            return;
        }
        HearthWorldData world = HearthWorldData.get(level.getServer());
        String key = hearth.key();
        helper.assertTrue(key.equals(HearthBlockEntity.key(level, abs)), "one key for loaded and unloaded: " + key);
        hearth.state().setSiegeDue(level.getGameTime() + 24_000);
        long due = hearth.state().siegeDue();
        helper.runAfterDelay(25, () -> {
            helper.assertTrue(world.siegeDue(key) == due, "the mirror should follow the state: " + world.siegeDue(key));
            hearth.state().setSiegeDue(HearthState.NEVER);
            helper.runAfterDelay(25, () -> {
                helper.assertTrue(world.siegeDue(key) == HearthState.NEVER, "a cleared due time leaves the mirror");
                hearth.state().setSiegeDue(due);
                helper.runAfterDelay(25, () -> {
                    helper.assertTrue(world.siegeDue(key) == due, "set again");
                    helper.setBlock(rel, Blocks.AIR);
                    helper.assertTrue(world.siegeDue(key) == HearthState.NEVER, "a broken Hearth leaves no siege behind");
                    helper.assertFalse(world.siegeDue().containsKey(key), "no entry at all");
                    helper.succeed();
                });
            });
        });
    }

    /** Armed only inside {@link #aCancelledPlacementNeverReachesTheHooks}; the bus keeps listeners for good. */
    private static final AtomicReference<BlockPos> CANCEL_PLACE_AT = new AtomicReference<>();
    private static final AtomicBoolean CANCEL_LISTENER = new AtomicBoolean();

    private static BlockEvent.EntityPlaceEvent placeEvent(ServerLevel level, BlockPos pos, ServerPlayer player) {
        return new BlockEvent.EntityPlaceEvent(BlockSnapshot.create(level.dimension(), level, pos),
                Blocks.STONE.defaultBlockState(), player);
    }

    @GameTest(template = T, batch = "hearth_hook_cancel")
    public static void aCancelledPlacementNeverReachesTheHooks(GameTestHelper helper) {
        HearthTestGuard.watch(helper);
        if (CANCEL_LISTENER.compareAndSet(false, true)) {
            // A claim mod's handler at LOW: it cancels before the Hearth's LOWEST listener.
            MinecraftForge.EVENT_BUS.addListener(EventPriority.LOW, false, BlockEvent.EntityPlaceEvent.class,
                    event -> {
                        if (event.getPos().equals(CANCEL_PLACE_AT.get())) {
                            event.setCanceled(true);
                        }
                    });
        }
        ServerPlayer player = survivor(helper);
        ServerLevel level = helper.getLevel();
        BlockPos rel = new BlockPos(1, 2, 1);
        BlockPos abs = helper.absolutePos(rel);
        helper.setBlock(rel, Blocks.OAK_PLANKS);
        AtomicInteger placed = new AtomicInteger();
        HearthHooks.BlockPlacedListener onPlaced = (lvl, pos, state, placer) -> {
            if (pos.equals(abs)) {
                placed.incrementAndGet();
            }
        };
        HearthHooks.BLOCK_PLACED.add(onPlaced);
        try {
            CANCEL_PLACE_AT.set(abs);
            boolean cancelled = MinecraftForge.EVENT_BUS.post(placeEvent(level, abs, player));
            helper.assertTrue(cancelled, "setup: the LOW listener cancels");
            helper.assertTrue(placed.get() == 0, "a cancelled placement reached the hook");
            CANCEL_PLACE_AT.set(null);
            MinecraftForge.EVENT_BUS.post(placeEvent(level, abs, player));
            helper.assertTrue(placed.get() == 1, "an allowed placement reaches the hook: " + placed.get());
        } finally {
            CANCEL_PLACE_AT.set(null);
            HearthHooks.BLOCK_PLACED.remove(onPlaced);
            helper.setBlock(rel, Blocks.AIR);
        }
        helper.succeed();
    }

    @GameTest(template = T, batch = "hearth_hook_grant")
    public static void theGrantHookSeesAPaidGrant(GameTestHelper helper) {
        HearthTestGuard.watch(helper);
        ServerPlayer player = survivor(helper);
        List<HearthHooks.Grant> grants = new ArrayList<>();
        HearthHooks.GrantListener listener = grant -> {
            if (grant.player() == player) {
                grants.add(grant);
            }
        };
        HearthHooks.GRANT.add(listener);
        try {
            float paid = SkillService.grant(player, Skill.MINING, 10.0, "block.minecraft.stone");
            helper.assertTrue(paid > 0f, "setup: the grant paid nothing");
            helper.assertFalse(grants.isEmpty(), "the grant hook should fire");
            HearthHooks.Grant grant = grants.get(0);
            helper.assertTrue(grant.skill() == Skill.MINING && grant.play() && !grant.aoe(), "grant fields");
            helper.assertTrue(grant.beforeRested() > 0f && grant.beforeRested() <= grant.amount(),
                    "beforeRested " + grant.beforeRested() + " amount " + grant.amount());
            helper.assertTrue(grant.baseAmount() == 10.0, "base amount");
            helper.assertFalse(grant.firstTime(), "a plain line is no first-time line");
        } finally {
            HearthHooks.GRANT.remove(listener);
        }
        helper.succeed();
    }

    @GameTest(template = T, batch = "hearth_hook_second", timeoutTicks = 80)
    public static void thePlayerSecondHookRunsForEveryOnlinePlayer(GameTestHelper helper) {
        HearthTestGuard.watch(helper);
        ServerPlayer player = survivor(helper);
        AtomicInteger seen = new AtomicInteger();
        HearthHooks.PlayerListener listener = p -> {
            if (p == player) {
                seen.incrementAndGet();
            }
        };
        HearthHooks.PLAYER_SECOND.add(listener);
        helper.runAfterDelay(45, () -> {
            HearthHooks.PLAYER_SECOND.remove(listener);
            helper.assertTrue(seen.get() >= 2, "the per-player second should have run twice, ran " + seen.get());
            helper.succeed();
        });
    }

    @GameTest(template = T, batch = "hearth_hook_events")
    public static void theEventHooksFire(GameTestHelper helper) {
        HearthTestGuard.watch(helper);
        ServerPlayer player = survivor(helper);
        ServerLevel level = helper.getLevel();
        BlockPos abs = helper.absolutePos(new BlockPos(1, 2, 1));
        AtomicReference<String> placed = new AtomicReference<>();
        AtomicReference<String> broken = new AtomicReference<>();
        AtomicReference<String> eaten = new AtomicReference<>();
        AtomicInteger slept = new AtomicInteger();
        AtomicInteger damaged = new AtomicInteger();
        HearthHooks.BlockPlacedListener onPlaced = (lvl, pos, state, placer) -> {
            if (pos.equals(abs) && placer == player) {
                placed.set(BuiltInRegistries.BLOCK.getKey(state.getBlock()).toString());
            }
        };
        HearthHooks.BlockBrokenListener onBroken = (lvl, pos, state, who) -> {
            if (pos.equals(abs) && who == player) {
                broken.set(BuiltInRegistries.BLOCK.getKey(state.getBlock()).toString());
            }
        };
        HearthHooks.ItemEatenListener onEaten = (who, food) -> {
            if (who == player) {
                eaten.set(BuiltInRegistries.ITEM.getKey(food.getItem()).toString());
            }
        };
        HearthHooks.PlayerListener onSleep = who -> {
            if (who == player) {
                slept.incrementAndGet();
            }
        };
        HearthHooks.DamageListener onDamage = event -> {
            if (event.getEntity() == player) {
                damaged.incrementAndGet();
            }
        };
        HearthHooks.BlockUseListener onUse = (who, lvl, pos, state, hand, held) ->
                who == player && pos.equals(abs) ? InteractionResult.SUCCESS : InteractionResult.PASS;
        HearthHooks.BLOCK_PLACED.add(onPlaced);
        HearthHooks.BLOCK_BROKEN.add(onBroken);
        HearthHooks.ITEM_EATEN.add(onEaten);
        HearthHooks.SLEEP.add(onSleep);
        HearthHooks.INCOMING_DAMAGE.add(onDamage);
        HearthHooks.BLOCK_USE.add(onUse);
        try {
            // Placed: Forge's EntityPlaceEvent (the snapshot's current block is what was placed).
            helper.setBlock(new BlockPos(1, 2, 1), Blocks.OAK_PLANKS);
            MinecraftForge.EVENT_BUS.post(placeEvent(level, abs, player));
            helper.assertTrue("minecraft:oak_planks".equals(placed.get()), "placed hook: " + placed.get());

            // Broken: a real break through the player's game mode (Forge's BreakEvent).
            player.gameMode.destroyBlock(abs);
            helper.assertTrue("minecraft:oak_planks".equals(broken.get()), "broken hook: " + broken.get());

            // Eaten: food only.
            MinecraftForge.EVENT_BUS.post(new LivingEntityUseItemEvent.Finish(player, new ItemStack(Items.STICK), 0,
                    ItemStack.EMPTY));
            helper.assertTrue(eaten.get() == null, "a stick is not food");
            MinecraftForge.EVENT_BUS.post(new LivingEntityUseItemEvent.Finish(player, new ItemStack(Items.BREAD), 0,
                    ItemStack.EMPTY));
            helper.assertTrue("minecraft:bread".equals(eaten.get()), "eaten hook: " + eaten.get());

            // Use: a non-PASS answer consumes the click.
            PlayerInteractEvent.RightClickBlock click = new PlayerInteractEvent.RightClickBlock(player,
                    InteractionHand.MAIN_HAND, abs, new BlockHitResult(Vec3.atCenterOf(abs), Direction.UP, abs, false));
            MinecraftForge.EVENT_BUS.post(click);
            helper.assertTrue(click.isCanceled() && click.getCancellationResult() == InteractionResult.SUCCESS,
                    "use hook should consume the click");

            // Damage: a real hit, through the port's incoming-damage event (LivingEntityDamageMixin).
            clearSpawnProtection(player);
            player.invulnerableTime = 0;
            player.hurt(level.damageSources().generic(), 1.0f);
            helper.assertTrue(damaged.get() >= 1, "damage hook: " + damaged.get());

            // Sleep: only a real one (the timer at 100 while still in bed).
            MinecraftForge.EVENT_BUS.post(new PlayerWakeUpEvent(player, true, true));
            helper.assertTrue(slept.get() == 0, "getting out of a bed at once is no sleep");
            CompoundTag tag = player.saveWithoutId(new CompoundTag());
            tag.putShort("SleepTimer", (short) 100);
            player.load(tag);
            player.setSleepingPos(abs);
            helper.assertTrue(player.isSleepingLongEnough(), "setup: the player should count as asleep");
            MinecraftForge.EVENT_BUS.post(new PlayerWakeUpEvent(player, false, true));
            player.clearSleepingPos();
            helper.assertTrue(slept.get() == 1, "sleep hook: " + slept.get());
        } finally {
            HearthHooks.BLOCK_PLACED.remove(onPlaced);
            HearthHooks.BLOCK_BROKEN.remove(onBroken);
            HearthHooks.ITEM_EATEN.remove(onEaten);
            HearthHooks.SLEEP.remove(onSleep);
            HearthHooks.INCOMING_DAMAGE.remove(onDamage);
            HearthHooks.BLOCK_USE.remove(onUse);
        }
        helper.succeed();
    }

    @GameTest(template = T, batch = "hearth_hook_death")
    public static void aDeathCarriesTheDataAndClearsWhatDeathCosts(GameTestHelper helper) {
        HearthTestGuard.watch(helper);
        ServerPlayer dead = survivor(helper);
        ServerPlayer fresh = survivor(helper);
        PlayerHearth before = HearthAttachments.of(dead);
        before.addTales(30);
        before.setWarmth(1.0);
        before.setHomewardDay(12);
        before.markMilestone("x|stage:1");
        AtomicReference<Double> lost = new AtomicReference<>();
        HearthHooks.DeathListener listener = (player, data, talesLost) -> {
            if (player == fresh) {
                lost.set(talesLost);
            }
        };
        HearthHooks.DEATH.add(listener);
        try {
            // Forge invalidates the dead body's capabilities before the clone event; the handler revives them.
            dead.invalidateCaps();
            MinecraftForge.EVENT_BUS.post(new PlayerEvent.Clone(fresh, dead, true));
        } finally {
            HearthHooks.DEATH.remove(listener);
        }
        PlayerHearth after = HearthAttachments.of(fresh);
        helper.assertTrue(lost.get() != null && lost.get() == 30.0, "the death hook should report 30 Tales lost: " + lost.get());
        helper.assertTrue(after.tales() == 0.0 && after.warmth() == 0f, "Tales and Warmth are lost");
        helper.assertTrue(after.homewardDay() == 12 && after.hasMilestone("x|stage:1"), "Homebuilder marks stay");

        // Leaving the End is a clone too, and keeps everything.
        ServerPlayer traveller = survivor(helper);
        HearthAttachments.of(fresh).addTales(5);
        MinecraftForge.EVENT_BUS.post(new PlayerEvent.Clone(traveller, fresh, false));
        helper.assertTrue(HearthAttachments.of(traveller).tales() == 5.0, "an End exit keeps the Tales");
        helper.succeed();
    }

    /**
     * The charge API the forge-extras lane builds on (Tales, the Feast, the Chronicle, sieges, Hearthglow):
     * the foundation ported it whole, so it works before the forge-server lane's charge port lands.
     */
    @GameTest(template = T, batch = "hearth_charge_api")
    public static void theChargeApiCreditsThePoolAndTheLedger(GameTestHelper helper) {
        HearthTestGuard.watch(helper);
        ServerLevel level = helper.getLevel();
        ServerPlayer player = survivor(helper);
        BlockPos rel = new BlockPos(1, 2, 1);
        helper.setBlock(rel, hearth(3, false));
        if (!(level.getBlockEntity(helper.absolutePos(rel)) instanceof HearthBlockEntity hearth)) {
            helper.fail("a Hearth block should carry a Hearth block entity");
            return;
        }
        try {
            double cap = Hearths.data().hourlyCap();
            double in = dev.amman.proficiency.hearth.charge.HearthCharge.credit(level, hearth, player, HearthPool.HEARTY,
                    HearthLedger.TALES, 40.0, true, null);
            helper.assertTrue(in == 40.0 && hearth.state().charge(HearthPool.HEARTY) == 40.0, "credited " + in);
            helper.assertTrue(hearth.state().ledger().total(player.getUUID(), HearthPool.HEARTY,
                    HearthLedger.Window.LIFETIME) == 40.0, "the ledger cell");
            double capped = dev.amman.proficiency.hearth.charge.HearthCharge.credit(level, hearth, player,
                    HearthPool.HEARTY, HearthLedger.TALES, cap * 2, true, null);
            helper.assertTrue(Math.abs(capped - (cap - 40.0)) < 1e-6, "the hourly cap: " + capped + " of " + cap);
            double burst = dev.amman.proficiency.hearth.charge.HearthCharge.credit(level, hearth, player,
                    HearthPool.HEARTY, HearthLedger.CHRONICLE, 10.0, false, null);
            helper.assertTrue(burst == 10.0, "uncapped credit goes past the cap: " + burst);
            dev.amman.proficiency.hearth.charge.HearthCharge.siegeKill(hearth, player.getUUID(), "Amman");
            dev.amman.proficiency.hearth.charge.HearthCharge.siegeDefended(hearth, List.of(player));
            var entry = hearth.state().ledger().get(player.getUUID());
            helper.assertTrue(entry != null && entry.siegeKills() == 1 && entry.siegesDefended() == 1, "siege counts");
            double before = hearth.state().charge(HearthPool.HEARTY);
            dev.amman.proficiency.hearth.charge.HearthCharge.cut(level, hearth, 0.10);
            helper.assertTrue(Math.abs(hearth.state().charge(HearthPool.HEARTY) - before * 0.9) < 1e-6, "doused: -10%");
        } finally {
            helper.setBlock(rel, Blocks.AIR);
        }
        helper.succeed();
    }

    @GameTest(template = T, batch = "hearth_siege_origin")
    public static void siegeMobsCarryTheirOrigin(GameTestHelper helper) {
        HearthTestGuard.watch(helper);
        Zombie zombie = helper.spawn(EntityType.ZOMBIE, new BlockPos(1, 2, 1));
        helper.assertFalse(SpawnOrigin.isSiege(zombie), "an ordinary zombie is no siege mob");
        SpawnOrigin.tagSiege(zombie);
        helper.assertTrue(SpawnOrigin.isSiege(zombie), "tagged as a siege mob");
        helper.assertTrue(SpawnOrigin.xpFactor(zombie) == 1.0, "a siege mob pays full combat XP");
        zombie.discard();
        helper.succeed();
    }

    // ---- Review of the Forge foundation (2026-10-09) ------------------------------------------------

    /**
     * The client swallowed every main-hand click on a Hearth, so bread was not eaten, a bow not drawn and
     * an off-hand torch not placed against it. Only a click the server answers is swallowed now, and the
     * server's own block passes every click (BLOCK_USE answers before the block is asked).
     */
    @GameTest(template = T, batch = "hearth_click")
    public static void onlyAClickTheServerAnswersIsSwallowed(GameTestHelper helper) {
        HearthTestGuard.watch(helper);
        for (ItemStack used : List.of(new ItemStack(Items.BREAD), new ItemStack(Items.BOW), new ItemStack(Items.SHIELD),
                new ItemStack(Items.POTION), new ItemStack(Items.TORCH), new ItemStack(Items.DIRT))) {
            helper.assertFalse(HearthBlock.predicts(InteractionHand.MAIN_HAND, used),
                    "the client lets " + used + " be used on a Hearth");
        }
        helper.assertTrue(HearthBlock.predicts(InteractionHand.MAIN_HAND, ItemStack.EMPTY), "an empty hand opens the panel");
        helper.assertTrue(HearthBlock.predicts(InteractionHand.MAIN_HAND, new ItemStack(Items.FLINT_AND_STEEL)),
                "flint and steel lights or relights");
        helper.assertFalse(HearthBlock.predicts(InteractionHand.OFF_HAND, ItemStack.EMPTY), "the off hand is never swallowed");
        helper.assertFalse(HearthBlock.predicts(InteractionHand.OFF_HAND, new ItemStack(Items.FLINT_AND_STEEL)),
                "not even with flint and steel");

        ServerPlayer player = survivor(helper);
        BlockPos rel = new BlockPos(1, 2, 1);
        helper.setBlock(rel, hearth(1, false));
        BlockPos abs = helper.absolutePos(rel);
        try {
            BlockState state = helper.getLevel().getBlockState(abs);
            BlockHitResult hit = new BlockHitResult(Vec3.atCenterOf(abs), Direction.UP, abs, false);
            for (ItemStack held : List.of(ItemStack.EMPTY, new ItemStack(Items.BREAD))) {
                player.setItemInHand(InteractionHand.MAIN_HAND, held);
                helper.assertTrue(state.use(helper.getLevel(), player, InteractionHand.MAIN_HAND, hit) == InteractionResult.PASS,
                        "the server's block passes a click with " + held);
            }
        } finally {
            helper.setBlock(rel, Blocks.AIR);
        }
        helper.succeed();
    }

    /**
     * Only the sneak-hold breaks a Hearth: the Wither's charge, its blue skulls and the dragon's flight
     * must not. Forge asks the block ({@code canEntityDestroy}), which reads the two vanilla tags.
     */
    @GameTest(template = T, batch = "hearth_immune")
    public static void theWitherAndTheDragonCannotBreakTheHearth(GameTestHelper helper) {
        HearthTestGuard.watch(helper);
        ServerLevel level = helper.getLevel();
        BlockState planks = Blocks.OAK_PLANKS.defaultBlockState();
        helper.assertTrue(hearth(6, false).is(BlockTags.WITHER_IMMUNE) && hearth(6, false).is(BlockTags.DRAGON_IMMUNE),
                "both immunity tags list the Hearth");
        helper.assertFalse(WitherBoss.canDestroy(hearth(6, false)), "the Wither's charge passes it by");
        helper.assertTrue(WitherBoss.canDestroy(planks), "setup: the Wither breaks planks");
        BlockPos rel = new BlockPos(1, 2, 1);
        helper.setBlock(rel, hearth(6, false));
        BlockPos abs = helper.absolutePos(rel);
        WitherBoss wither = EntityType.WITHER.create(level);
        EnderDragon dragon = EntityType.ENDER_DRAGON.create(level);
        WitherSkull skull = EntityType.WITHER_SKULL.create(level);
        try {
            BlockState placed = level.getBlockState(abs);
            helper.assertFalse(placed.canEntityDestroy(level, abs, wither), "the Wither may not destroy it");
            helper.assertFalse(placed.canEntityDestroy(level, abs, dragon), "the dragon may not destroy it");
            helper.assertTrue(planks.canEntityDestroy(level, abs, dragon), "setup: the dragon breaks planks");
            // A blue skull would cut the resistance of what the Wither may destroy to 0.8.
            skull.setDangerous(true);
            Explosion blast = new Explosion(level, skull, abs.getX() + 0.5, abs.getY() + 0.5, abs.getZ() + 0.5, 1.0f,
                    false, Explosion.BlockInteraction.DESTROY);
            float resistance = skull.getBlockExplosionResistance(blast, level, abs, placed, placed.getFluidState(),
                    placed.getExplosionResistance(level, abs, blast));
            helper.assertTrue(resistance >= 1200f, "a blue skull meets the full resistance: " + resistance);
            helper.assertTrue(skull.getBlockExplosionResistance(blast, level, abs, planks, planks.getFluidState(), 3f) < 1f,
                    "setup: a blue skull softens planks");
        } finally {
            helper.setBlock(rel, Blocks.AIR);
        }
        helper.succeed();
    }

    /**
     * The sneak-hold on the server (stages.HearthBreaking, ported by the foundation): the server counts
     * the ticks really sneaked, so a modified client that swings 3 s and sneaks for the last tick does
     * not put the Hearth out; vanilla's late finish after that refused STOP does not shorten the next,
     * honest hold either (it would multiply by the ticks since the first swing); the full hold does it.
     */
    @GameTest(template = T, batch = "hearth_break_hold", timeoutTicks = 300)
    public static void onlyAServerCountedSneakHoldPutsItOut(GameTestHelper helper) {
        HearthTestGuard.watch(helper);
        ServerLevel level = helper.getLevel();
        BlockPos rel = new BlockPos(1, 2, 1);
        BlockPos abs = helper.absolutePos(rel);
        helper.setBlock(rel, hearth(1, false));
        ServerPlayer player = survivor(helper);
        player.setPos(helper.absoluteVec(new Vec3(2.5, 2, 3.5)));
        player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.DIAMOND_PICKAXE));
        player.setShiftKeyDown(false);
        int top = level.getMaxBuildHeight();
        Runnable cleanup = () -> {
            player.setShiftKeyDown(false);
            helper.setBlock(rel, Blocks.AIR);
            for (ItemEntity drop : level.getEntitiesOfClass(ItemEntity.class, new AABB(abs).inflate(2.0),
                    item -> item.getItem().is(Items.CAMPFIRE))) {
                drop.discard();
            }
            player.server.getPlayerList().remove(player);
        };
        int ticked = player.tickCount;
        try {
            helper.assertTrue(level.getBlockState(abs).getDestroyProgress(player, level, abs) == 0f, "no hold: no progress");
            player.gameMode.handleBlockBreakAction(abs, ServerboundPlayerActionPacket.Action.START_DESTROY_BLOCK,
                    Direction.UP, top, 1);
            helper.assertTrue(HearthBreaking.sneakTicks(player, abs) == 0, "the swing starts the server's count");
        } catch (RuntimeException e) {
            cleanup.run();
            throw e;
        }
        helper.runAfterDelay(HearthBreaking.HOLD_TICKS + 5, () -> {
            try {
                helper.assertTrue(player.tickCount >= ticked + HearthBreaking.HOLD_TICKS,
                        "setup: the level ticks the player's break: " + (player.tickCount - ticked) + " ticks");
                helper.assertTrue(HearthBreaking.sneakTicks(player, abs) == 0, "a swing without sneaking counts nothing");
                // The modified client sneaks for the last tick only, then says it is done.
                player.setShiftKeyDown(true);
            } catch (RuntimeException e) {
                cleanup.run();
                throw e;
            }
            helper.runAfterDelay(1, () -> {
                try {
                    player.gameMode.handleBlockBreakAction(abs, ServerboundPlayerActionPacket.Action.STOP_DESTROY_BLOCK,
                            Direction.UP, top, 2);
                    helper.assertTrue(level.getBlockState(abs).is(HearthBlocks.HEARTH.get()),
                            "3 s of swinging with one tick of sneak does not put it out");
                    int counted = HearthBreaking.sneakTicks(player, abs);
                    helper.assertTrue(counted >= 0 && counted <= 2, "one or two ticks of sneak counted: " + counted);
                    // A new swing, sneaking from the start, while vanilla still finishes the refused one late.
                    player.gameMode.handleBlockBreakAction(abs, ServerboundPlayerActionPacket.Action.START_DESTROY_BLOCK,
                            Direction.UP, top, 3);
                } catch (RuntimeException e) {
                    cleanup.run();
                    throw e;
                }
                helper.runAfterDelay(HearthBreaking.HOLD_TICKS / 2, () -> {
                    try {
                        helper.assertTrue(level.getBlockState(abs).is(HearthBlocks.HEARTH.get()),
                                "half a hold after a refused finish does not put it out");
                    } catch (RuntimeException e) {
                        cleanup.run();
                        throw e;
                    }
                    helper.runAfterDelay(HearthBreaking.HOLD_TICKS / 2 + 2, () -> {
                        try {
                            player.gameMode.handleBlockBreakAction(abs,
                                    ServerboundPlayerActionPacket.Action.STOP_DESTROY_BLOCK, Direction.UP, top, 4);
                            helper.assertTrue(level.getBlockState(abs).isAir(),
                                    "the full hold puts it out: " + level.getBlockState(abs));
                            helper.assertFalse(HearthIndexData.get(level).contains(abs), "the index forgets it");
                            helper.succeed();
                        } finally {
                            cleanup.run();
                        }
                    });
                });
            });
        });
    }

    /**
     * The trophy hangs on the wall that was clicked, then on the nearest wall the player looks toward:
     * vanilla's {@code BlockPlaceContext.getNearestLookingDirections} puts the clicked face's opposite
     * first (WallTorchBlock and WallSignBlock rely on the same). In a corner, a player looking north
     * who clicks the west wall's east face gets it on the west wall, not the north one.
     */
    @GameTest(template = T, batch = "hearth_trophy_corner")
    public static void aTrophyHangsOnTheWallThatWasClicked(GameTestHelper helper) {
        HearthTestGuard.watch(helper);
        ServerPlayer player = survivor(helper);
        BlockPos spotRel = new BlockPos(2, 2, 2);
        List<BlockPos> walls = List.of(spotRel.west(), spotRel.north(), spotRel.below());
        for (BlockPos wall : walls) {
            helper.setBlock(wall, Blocks.STONE);
        }
        BlockPos spot = helper.absolutePos(spotRel);
        ItemStack stack = SiegeTrophyItem.of(4, 1);
        player.setItemInHand(InteractionHand.MAIN_HAND, stack);
        // Looking north and a little east: north, east, down, up, west, south is the looking order. The
        // head's yaw is what counts (LivingEntity.getViewYRot), so both are set.
        player.setYRot(185f);
        player.setYHeadRot(185f);
        player.setXRot(0f);
        try {
            Direction[] looking = Direction.orderedByNearest(player);
            helper.assertTrue(looking[0] == Direction.NORTH && looking[4] == Direction.WEST,
                    "setup: the player looks north, west last but one: " + java.util.Arrays.toString(looking));
            BlockState west = placeTrophy(player, stack, spot.west(), Direction.EAST);
            helper.assertTrue(west != null && west.getValue(SiegeTrophyBlock.FACING) == Direction.EAST,
                    "the clicked west wall, not the north one the player looks at: " + west);
            BlockState north = placeTrophy(player, stack, spot.north(), Direction.SOUTH);
            helper.assertTrue(north != null && north.getValue(SiegeTrophyBlock.FACING) == Direction.SOUTH,
                    "the clicked north wall: " + north);
            // A click on the floor has no wall: the nearest one the player looks toward (north).
            BlockState floor = placeTrophy(player, stack, spot.below(), Direction.UP);
            helper.assertTrue(floor != null && floor.getValue(SiegeTrophyBlock.FACING) == Direction.SOUTH,
                    "the floor: the wall the player looks at: " + floor);
        } finally {
            for (BlockPos wall : walls) {
                helper.setBlock(wall, Blocks.AIR);
            }
        }
        helper.succeed();
    }

    private static BlockState placeTrophy(ServerPlayer player, ItemStack stack, BlockPos clicked, Direction face) {
        BlockHitResult hit = new BlockHitResult(Vec3.atCenterOf(clicked).relative(face, 0.5), face, clicked, false);
        return HearthBlocks.SIEGE_TROPHY.get().getStateForPlacement(
                new BlockPlaceContext(player, InteractionHand.MAIN_HAND, stack, hit));
    }

    /**
     * The block's glow and feed follow the state through HearthLook (1.21.1's integration task, common's
     * own source here): light 6 with no charge, the highest effective tier's light, the colour of the
     * pool fed most this hour, embers while smouldering, and the Hearth's own second keeps them current.
     */
    @GameTest(template = T, batch = "hearth_glow", timeoutTicks = 100)
    public static void theBlockGlowsWithChargeAndTakesTheColourOfWhatFedItMost(GameTestHelper helper) {
        HearthTestGuard.watch(helper);
        ServerLevel level = helper.getLevel();
        BlockPos rel = new BlockPos(2, 2, 2);
        BlockPos abs = helper.absolutePos(rel);
        helper.setBlock(rel, hearth(6, false));
        if (!(level.getBlockEntity(abs) instanceof HearthBlockEntity hearth)) {
            helper.setBlock(rel, Blocks.AIR);
            helper.fail("no Hearth block entity");
            return;
        }
        HearthState state = hearth.state();
        long now = level.getGameTime();
        try {
            BlockState fresh = level.getBlockState(abs);
            helper.assertTrue(fresh.getValue(HearthBlock.GLOW) == 0 && fresh.getLightEmission() == 6,
                    "no charge: glow 0, light 6, got " + fresh.getLightEmission());
            state.setStage(6);
            state.setHighestOffering(6);
            state.setCharge(HearthPool.HEARTY, 650);
            state.addCapped(HearthPool.WORKSHOP, 40, now, Hearths.data());
            state.addCapped(HearthPool.HEARTY, 15, now, Hearths.data());
            dev.amman.proficiency.hearth.HearthLook.second(level, abs, hearth);
            BlockState lit = level.getBlockState(abs);
            helper.assertTrue(lit.getValue(HearthBlock.GLOW) == state.maxEffectiveTier(Hearths.data())
                    && lit.getValue(HearthBlock.GLOW) == 2, "glow is the highest effective tier (II): " + lit.getValue(HearthBlock.GLOW));
            helper.assertTrue(lit.getLightEmission() == HearthRules.chargeLight(2, false), "light follows: " + lit.getLightEmission());
            helper.assertTrue(lit.getValue(HearthBlock.FEED) == HearthPool.WORKSHOP.ordinal() + 1,
                    "Workshop was fed most this hour: feed " + lit.getValue(HearthBlock.FEED));
            helper.assertTrue(lit.getValue(HearthBlock.STAGE) == 6 && level.getBlockEntity(abs) == hearth,
                    "the stage and the block entity are untouched");
            state.setSmouldering(true);
            dev.amman.proficiency.hearth.HearthLook.second(level, abs, hearth);
            BlockState doused = level.getBlockState(abs);
            // The light's smouldering half is the blockstate's SMOULDERING (the sieges lane sets it), not this flag.
            helper.assertTrue(doused.getValue(HearthBlock.GLOW) == 0 && doused.getValue(HearthBlock.FEED) == 0,
                    "smouldering: glow 0, no colour");
            state.setSmouldering(false);
            state.addCapped(HearthPool.HEARTY, 60, now, Hearths.data());
        } catch (RuntimeException e) {
            helper.setBlock(rel, Blocks.AIR);
            throw e;
        }
        // The Hearth's own second does it too (HearthLook is on HEARTH_TICK, registered by Hearth.init).
        helper.runAfterDelay(45, () -> {
            try {
                BlockState ticked = level.getBlockState(abs);
                helper.assertTrue(ticked.getValue(HearthBlock.GLOW) == state.maxEffectiveTier(Hearths.data()),
                        "the Hearth's second set the glow: " + ticked.getValue(HearthBlock.GLOW));
                helper.assertTrue(ticked.getValue(HearthBlock.FEED) == HearthPool.HEARTY.ordinal() + 1,
                        "Hearty took the lead this hour: feed " + ticked.getValue(HearthBlock.FEED));
                helper.succeed();
            } finally {
                helper.setBlock(rel, Blocks.AIR);
            }
        });
    }

    /**
     * The ledger request's rate limit (HearthCharge, ported by the foundation) is per session: a logout
     * forgets it, so the map does not grow for the life of the JVM.
     */
    @GameTest(template = T, batch = "hearth_charge_logout")
    public static void aLogoutForgetsTheLedgerRequestLimit(GameTestHelper helper) {
        HearthTestGuard.watch(helper);
        ServerPlayer player = survivor(helper);
        BlockPos rel = new BlockPos(1, 2, 1);
        helper.setBlock(rel, hearth(1, false));
        BlockPos abs = helper.absolutePos(rel);
        try {
            dev.amman.proficiency.hearth.charge.HearthCharge.onRequest(player,
                    new HearthRequestPayload(HearthRequestPayload.LEDGER, abs.asLong()));
            helper.assertTrue(dev.amman.proficiency.hearth.charge.HearthCharge.remembersRequest(player.getUUID()),
                    "setup: the request is rate-limited");
            player.server.getPlayerList().remove(player);
            helper.assertFalse(dev.amman.proficiency.hearth.charge.HearthCharge.remembersRequest(player.getUUID()),
                    "the logout forgets it");
        } finally {
            helper.setBlock(rel, Blocks.AIR);
        }
        helper.succeed();
    }
}
