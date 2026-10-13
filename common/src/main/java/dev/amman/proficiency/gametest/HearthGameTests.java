package dev.amman.proficiency.gametest;

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
import dev.amman.proficiency.hearth.net.HearthStatePayload;
import dev.amman.proficiency.hearth.sieges.SiegeTrophyBlock;
import dev.amman.proficiency.platform.bus.NeoForge;
import dev.amman.proficiency.platform.event.entity.living.LivingEntityUseItemEvent;
import dev.amman.proficiency.platform.event.entity.player.PlayerEvent;
import dev.amman.proficiency.platform.event.entity.player.PlayerInteractEvent;
import dev.amman.proficiency.platform.event.entity.player.PlayerWakeUpEvent;
import dev.amman.proficiency.platform.event.level.BlockEvent;
import dev.amman.proficiency.skill.Skill;
import dev.amman.proficiency.skill.SkillService;
import dev.amman.proficiency.skill.SpawnOrigin;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.monster.Zombie;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

/**
 * The Hearth foundation in a real world, on both loaders: the block entity saves and loads, the
 * registry entries exist with the art contract's properties, the index follows the block, and every
 * hook fires from its game event. The rules themselves are unit tested in core.
 */
public final class HearthGameTests {

    private static final String T = "proficiency:empty";
    private static final UUID AMMAN = UUID.fromString("00000000-0000-0000-0000-0000000000a1");

    /** Fabric instantiates each test class, as a {@code fabric-gametest} entrypoint. */
    public HearthGameTests() {
    }

    private static ServerPlayer survivor(GameTestHelper helper) {
        ServerPlayer player = helper.makeMockServerPlayerInLevel();
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

        CompoundTag saved = hearth.saveWithFullMetadata(level.registryAccess());
        BlockEntity loaded = BlockEntity.loadStatic(abs, level.getBlockState(abs), saved, level.registryAccess());
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

        // The one state builder every lane sends with.
        HearthStatePayload panel = HearthNet.stateOf(hearth, HearthStatePayload.PANEL);
        helper.assertTrue(panel.pos() == abs.asLong() && panel.stage() == 4 && panel.ceiling() == 4,
                "state payload: stage " + panel.stage() + " ceiling " + panel.ceiling());
        helper.assertTrue(panel.pools().size() == HearthPool.COUNT && panel.siegesOn(), "state payload rows");
        helper.assertTrue("minecraft:overworld".equals(panel.dimension()) || !panel.dimension().isEmpty(), "dimension");

        helper.setBlock(rel, Blocks.AIR);
        helper.succeed();
    }

    @GameTest(template = T, batch = "hearth_registry")
    public static void theRegistryEntriesMatchTheArtContract(GameTestHelper helper) {
        HearthTestGuard.watch(helper);
        ServerLevel level = helper.getLevel();
        helper.assertTrue(BuiltInRegistries.BLOCK.containsKey(ResourceLocation.parse("proficiency:hearth")), "block hearth");
        helper.assertTrue(BuiltInRegistries.BLOCK.containsKey(ResourceLocation.parse("proficiency:siege_trophy")),
                "block siege_trophy");
        helper.assertTrue(BuiltInRegistries.ITEM.containsKey(ResourceLocation.parse("proficiency:siege_horn")), "item siege_horn");
        helper.assertTrue(BuiltInRegistries.ITEM.containsKey(ResourceLocation.parse("proficiency:siege_trophy")),
                "item siege_trophy");
        // The Hearth is built in the world, never held: no item.
        helper.assertTrue(HearthBlocks.HEARTH.get().asItem() == Items.AIR, "the Hearth has no item");
        helper.assertTrue(HearthBlock.STAGE.getPossibleValues().size() == 6, "stage 1..6");
        helper.assertTrue(SiegeTrophyBlock.TIER.getPossibleValues().size() == 4, "tier 3..6");
        // The light follows the charge (glow = the highest effective tier), not the stage; feed is the
        // pool fed most this hour (integration: HearthLook keeps both in step).
        helper.assertTrue(HearthBlock.GLOW.getPossibleValues().size() == 7, "glow 0..6");
        helper.assertTrue(HearthBlock.FEED.getPossibleValues().size() == HearthPool.COUNT + 1, "feed 0..5");
        for (int glow = 0; glow <= 6; glow++) {
            helper.assertTrue(hearth(1, false).setValue(HearthBlock.GLOW, glow).getLightEmission()
                    == HearthRules.chargeLight(glow, false), "light at glow " + glow);
        }
        helper.assertTrue(hearth(6, false).getLightEmission() == 6, "no charge: light 6 whatever the stage");
        helper.assertTrue(hearth(6, true).setValue(HearthBlock.GLOW, 6).getLightEmission() == HearthRules.chargeLight(6, true),
                "smouldering light");
        // The shipped data loaded at server start, without a problem.
        var loaded = dev.amman.proficiency.hearth.HearthReload.lastResult();
        helper.assertTrue(loaded != null && loaded.files() >= 1 && loaded.skippedFiles() == 0,
                "the Hearth data should have loaded: " + (loaded == null ? "never" : loaded.summary()));
        helper.assertTrue(loaded.messages().stream().noneMatch(m -> m.level() == HearthDataLoader.Level.ERROR),
                "no errors in the Hearth data: " + loaded.messages());
        helper.assertTrue(Hearths.data() == loaded.data(), "the loaded data should be live");
        helper.assertTrue(level.getBlockState(helper.absolutePos(new BlockPos(1, 2, 1))).isAir(), "setup");
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
            // The spacing check never refuses a lighting over a ghost: it confirms and forgets. The
            // nearest entry (C, 1 block) is a ghost; the real Hearth (1.4 blocks) is the answer; A
            // and B lie further out and are left for the other two ways. A small spacing keeps
            // other tests' Hearths out of it.
            BlockPos lighting = helper.absolutePos(new BlockPos(4, 2, 4));
            BlockPos close = index.tooClose(level, lighting, 6);
            helper.assertTrue(real.equals(close), "the real Hearth is the one too close, got " + close);
            helper.assertFalse(index.contains(ghostC), "the spacing check forgot the nearest ghost");
            helper.assertTrue(index.contains(ghostA) && index.contains(ghostB), "only the ghost it met");
            // The block entity lookup forgets a ghost it meets.
            helper.assertTrue(HearthRange.entity(level, ghostA) == null, "no block entity at a ghost");
            helper.assertFalse(index.contains(ghostA), "entity() forgot the ghost");
            // The sweep forgets the rest and keeps the real one.
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
    private static final java.util.concurrent.atomic.AtomicBoolean CANCEL_LISTENER = new java.util.concurrent.atomic.AtomicBoolean();

    @GameTest(template = T, batch = "hearth_hook_cancel")
    public static void aCancelledPlacementNeverReachesTheHooks(GameTestHelper helper) {
        HearthTestGuard.watch(helper);
        if (CANCEL_LISTENER.compareAndSet(false, true)) {
            // A claim mod's handler at LOW: it cancels before the Hearth's LOWEST listener.
            NeoForge.EVENT_BUS.addListener(dev.amman.proficiency.platform.bus.EventPriority.LOW, false,
                    BlockEvent.EntityPlaceEvent.class, event -> {
                        if (event.getPos().equals(CANCEL_PLACE_AT.get())) {
                            event.setCanceled(true);
                        }
                    });
        }
        ServerPlayer player = survivor(helper);
        ServerLevel level = helper.getLevel();
        BlockPos abs = helper.absolutePos(new BlockPos(1, 2, 1));
        AtomicInteger placed = new AtomicInteger();
        HearthHooks.BlockPlacedListener onPlaced = (lvl, pos, state, placer) -> {
            if (pos.equals(abs)) {
                placed.incrementAndGet();
            }
        };
        HearthHooks.BLOCK_PLACED.add(onPlaced);
        try {
            CANCEL_PLACE_AT.set(abs);
            BlockEvent.EntityPlaceEvent cancelled = NeoForge.EVENT_BUS.post(new BlockEvent.EntityPlaceEvent(level, abs,
                    Blocks.AIR.defaultBlockState(), Blocks.OAK_PLANKS.defaultBlockState(), Blocks.STONE.defaultBlockState(),
                    player));
            helper.assertTrue(cancelled.isCanceled(), "setup: the LOW listener cancels");
            helper.assertTrue(placed.get() == 0, "a cancelled placement reached the hook");
            CANCEL_PLACE_AT.set(null);
            NeoForge.EVENT_BUS.post(new BlockEvent.EntityPlaceEvent(level, abs, Blocks.AIR.defaultBlockState(),
                    Blocks.OAK_PLANKS.defaultBlockState(), Blocks.STONE.defaultBlockState(), player));
            helper.assertTrue(placed.get() == 1, "an allowed placement reaches the hook: " + placed.get());
        } finally {
            CANCEL_PLACE_AT.set(null);
            HearthHooks.BLOCK_PLACED.remove(onPlaced);
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
            // Placed: the shared event every loader posts after a block is placed.
            helper.setBlock(new BlockPos(1, 2, 1), Blocks.OAK_PLANKS);
            NeoForge.EVENT_BUS.post(new BlockEvent.EntityPlaceEvent(level, abs, Blocks.AIR.defaultBlockState(),
                    Blocks.OAK_PLANKS.defaultBlockState(), Blocks.STONE.defaultBlockState(), player));
            helper.assertTrue("minecraft:oak_planks".equals(placed.get()), "placed hook: " + placed.get());

            // Broken: a real break through the player's game mode (NeoForge event / Fabric callback).
            player.gameMode.destroyBlock(abs);
            helper.assertTrue("minecraft:oak_planks".equals(broken.get()), "broken hook: " + broken.get());

            // Eaten: food only.
            NeoForge.EVENT_BUS.post(new LivingEntityUseItemEvent.Finish(player, new ItemStack(Items.STICK), 0,
                    ItemStack.EMPTY));
            helper.assertTrue(eaten.get() == null, "a stick is not food");
            NeoForge.EVENT_BUS.post(new LivingEntityUseItemEvent.Finish(player, new ItemStack(Items.BREAD), 0,
                    ItemStack.EMPTY));
            helper.assertTrue("minecraft:bread".equals(eaten.get()), "eaten hook: " + eaten.get());

            // Use: a non-PASS answer consumes the click.
            PlayerInteractEvent.RightClickBlock click = NeoForge.EVENT_BUS.post(
                    new PlayerInteractEvent.RightClickBlock(player, InteractionHand.MAIN_HAND, abs));
            helper.assertTrue(click.isCanceled() && click.getCancellationResult() == InteractionResult.SUCCESS,
                    "use hook should consume the click");

            // Damage: a real hit, through the loader's own damage event.
            clearSpawnProtection(player);
            player.invulnerableTime = 0;
            player.hurt(level.damageSources().generic(), 1.0f);
            helper.assertTrue(damaged.get() >= 1, "damage hook: " + damaged.get());

            // Sleep: only a real one (the timer at 100 while still in bed).
            NeoForge.EVENT_BUS.post(new PlayerWakeUpEvent(player, true, true));
            helper.assertTrue(slept.get() == 0, "getting out of a bed at once is no sleep");
            CompoundTag tag = player.saveWithoutId(new CompoundTag());
            tag.putShort("SleepTimer", (short) 100);
            player.load(tag);
            player.setSleepingPos(abs);
            helper.assertTrue(player.isSleepingLongEnough(), "setup: the player should count as asleep");
            NeoForge.EVENT_BUS.post(new PlayerWakeUpEvent(player, false, true));
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
            NeoForge.EVENT_BUS.post(new PlayerEvent.Clone(fresh, dead, true));
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
        NeoForge.EVENT_BUS.post(new PlayerEvent.Clone(traveller, fresh, false));
        helper.assertTrue(HearthAttachments.of(traveller).tales() == 5.0, "an End exit keeps the Tales");
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
}
