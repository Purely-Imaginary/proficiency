package dev.amman.proficiency.gametest;

import dev.amman.proficiency.Proficiency;
import dev.amman.proficiency.ProficiencyAttachments;
import dev.amman.proficiency.hearth.HearthAttachments;
import dev.amman.proficiency.hearth.HearthBlock;
import dev.amman.proficiency.hearth.HearthBlockEntity;
import dev.amman.proficiency.hearth.HearthBlocks;
import dev.amman.proficiency.hearth.HearthData;
import dev.amman.proficiency.hearth.HearthEvents;
import dev.amman.proficiency.hearth.HearthHooks;
import dev.amman.proficiency.hearth.HearthLedger;
import dev.amman.proficiency.hearth.HearthPool;
import dev.amman.proficiency.hearth.HearthRange;
import dev.amman.proficiency.hearth.HearthState;
import dev.amman.proficiency.hearth.HearthWorldData;
import dev.amman.proficiency.hearth.Hearths;
import dev.amman.proficiency.hearth.SiegeRules;
import dev.amman.proficiency.hearth.sieges.HornCheck;
import dev.amman.proficiency.hearth.sieges.SiegeClock;
import dev.amman.proficiency.hearth.tales.HearthBell;
import dev.amman.proficiency.hearth.sieges.SiegeMobs;
import dev.amman.proficiency.hearth.sieges.SiegeService;
import dev.amman.proficiency.hearth.sieges.SiegeTrophyBlock;
import dev.amman.proficiency.hearth.sieges.SiegeTrophyItem;
import dev.amman.proficiency.hearth.sieges.SiegeWorldData;
import net.minecraftforge.event.ForgeEventFactory;
import dev.amman.proficiency.skill.SpawnOrigin;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.monster.Creeper;
import net.minecraft.world.entity.monster.Zombie;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Sieges in a real world on Forge 1.20.1, the port of common's {@code SiegeGameTests} (the same test
 * names; docs/HEARTH-SPEC.md, "Sieges", abuse check 13): the
 * condition (first horn, the whole active group home and warm, AFK alts, ceiling, pools, cooldown),
 * the roll and the pity rule, the horn and its cooldown, postponing and fizzling at an empty
 * nightfall (also caught up after an unloaded stretch or a night slept through), a restart
 * mid-countdown, a defended, an unearned and a doused siege, the relight, dawn and an abandoned siege,
 * no griefing, no sleep, the trophy.
 *
 * <p>Each test is its own batch (a Hearth's range is far bigger than the template) and drives the
 * siege by hand ({@link SiegeService#MANUAL}) with its own clock, players and dice, so the Hearth's
 * own ticking cannot interfere. Mock players are removed at the end.
 */
@GameTestHolder(Proficiency.MOD_ID)
@PrefixGameTestTemplate(false)
public final class SiegeGameTests {

    private static final String T = "empty";
    private static final long HOUR = HearthData.TICKS_PER_HOUR;
    private static final long DAY = HearthData.TICKS_PER_DAY;
    /** A day time in the middle of the night (dusk is 13000). */
    private static final long NIGHT = 14_000L;

    private SiegeGameTests() {
    }

    /** One test's Hearths and players. */
    private static final class Rig {
        final GameTestHelper helper;
        final ServerLevel level;
        final List<ServerPlayer> players = new ArrayList<>();
        final List<HearthBlockEntity> hearths = new ArrayList<>();
        private boolean cleaned;

        Rig(GameTestHelper helper) {
            this.helper = helper;
            this.level = helper.getLevel();
            // A check that fails in a later tick, or a timeout, never reaches the catch around the test body:
            // the test's own end runs the cleanup too, so its sieges end and its Hearths go (fix round, 2026-10-10).
            ExtrasTestGuard.atEnd(helper, this::cleanup);
        }

        long now() {
            return level.getGameTime();
        }

        long playNow() {
            return HearthWorldData.get(level.getServer()).playTicks();
        }

        /** A lit Hearth at {@code stage} with Character for it, Hearty at tier II, the last siege long ago. */
        HearthBlockEntity hearth(BlockPos rel, int stage) {
            helper.setBlock(rel, HearthBlocks.HEARTH.get().defaultBlockState().setValue(HearthBlock.STAGE, Math.max(1, stage)));
            BlockEntity be = level.getBlockEntity(helper.absolutePos(rel));
            if (!(be instanceof HearthBlockEntity hearth)) {
                throw new IllegalStateException("no Hearth block entity at " + rel);
            }
            HearthState state = hearth.state();
            state.setStage(stage);
            state.setHighestOffering(stage);
            state.setCharacter(50);
            state.setCharge(HearthPool.HEARTY, 700);
            state.onLit(now(), playNow());
            lastSiege(hearth, 100 * HOUR, 100 * DAY);
            SiegeService.MANUAL.add(hearth.key());
            hearths.add(hearth);
            return hearth;
        }

        void lastSiege(HearthBlockEntity hearth, long playAgo, long ticksAgo) {
            hearth.state().setLastSiegeEnd(new SiegeRules.SiegeEnd(playNow() - playAgo, now() - ticksAgo));
        }

        ServerPlayer player(BlockPos rel, boolean active, double warmth) {
            ServerPlayer player = MockPlayers.make(helper);
            player.setGameMode(GameType.SURVIVAL);
            player.setPos(helper.absoluteVec(Vec3.atBottomCenterOf(rel)));
            if (active) {
                ProficiencyAttachments.of(player).noteActive(now());
            }
            HearthAttachments.of(player).setWarmth(warmth);
            players.add(player);
            return player;
        }

        void away(ServerPlayer player) {
            player.setPos(player.getX() + 300, player.getY(), player.getZ());
        }

        void home(ServerPlayer player, BlockPos rel) {
            player.setPos(helper.absoluteVec(Vec3.atBottomCenterOf(rel)));
        }

        /**
         * Loads the chunks under the ring a wave spawns on (a GameTest only loads its own structure). The
         * Forge GameTest world is flat since the forge-server lane (as on 1.21.1), so the ring has open
         * ground: the stone arena forge-extras cut into the old terrain world is gone.
         */
        void loadRing(BlockPos hearth) {
            int r = (int) Math.ceil(HearthRange.range().radius()) + 2;
            for (int cx = (hearth.getX() - r) >> 4; cx <= (hearth.getX() + r) >> 4; cx++) {
                for (int cz = (hearth.getZ() - r) >> 4; cz <= (hearth.getZ() + r) >> 4; cz++) {
                    level.getChunk(cx, cz);
                }
            }
        }

        void killAll(HearthBlockEntity hearth, ServerPlayer by) {
            for (Mob mob : SiegeService.siegeMobs(level, hearth)) {
                mob.hurt(level.damageSources().playerAttack(by), 10_000f);
            }
        }

        /**
         * Leaves nothing behind, also after a failed check: a Hearth left standing would sit in the
         * index and refuse the next test's Hearth (64-block spacing) or keep its players "home".
         */
        void cleanup() {
            if (cleaned) {
                return;
            }
            cleaned = true;
            for (HearthBlockEntity hearth : hearths) {
                if (hearth.state().siegePhase() != SiegeRules.Phase.IDLE && !hearth.isRemoved()) {
                    SiegeService.cancel(level, hearth.getBlockPos(), hearth, now());
                }
                SiegeService.MANUAL.remove(hearth.key());
                if (level.getBlockState(hearth.getBlockPos()).is(HearthBlocks.HEARTH.get())) {
                    level.setBlock(hearth.getBlockPos(), Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL);
                }
            }
            for (ServerPlayer player : players) {
                // The guard may have logged this one out already (it sweeps first when the test ends).
                if (player.server.getPlayerList().getPlayer(player.getUUID()) == player) {
                    player.server.getPlayerList().remove(player);
                }
            }
        }

        void done() {
            cleanup();
            helper.succeed();
        }
    }

    private static void check(GameTestHelper helper, boolean ok, String message) {
        helper.assertTrue(ok, message);
    }

    // ---- The condition and the roll (abuse check 13) ---------------------------------------------

    @GameTest(template = T, batch = "siege_first_horn")
    public static void aHearthThatNeverHeardAHornNeverRollsAndPityIsSure(GameTestHelper helper) {
        ExtrasTestGuard.watch(helper);
        Rig rig = new Rig(helper);
        try {
            HearthBlockEntity hearth = rig.hearth(new BlockPos(2, 2, 2), 6);
            BlockPos pos = hearth.getBlockPos();
            ServerPlayer amman = rig.player(new BlockPos(1, 2, 2), true, 1.0);
            List<ServerPlayer> online = List.of(amman);
            long now = rig.now();
            for (int i = 0; i < 300; i++) {
                check(helper, !SiegeService.rollIfReady(rig.level, pos, hearth, now, 6_000, 0.0, online),
                        "a Hearth that never heard a horn rolled a siege");
            }
            check(helper, hearth.state().siegePhase() == SiegeRules.Phase.IDLE, "still idle without a horn");
            check(helper, !SiegeService.conditionHolds(rig.level, pos, hearth, now, online), "no condition before the horn");

            hearth.state().setSieged(true);
            SiegeRules.Facts facts = SiegeService.facts(rig.level, pos, hearth, now, online);
            check(helper, SiegeRules.conditionHolds(facts, Hearths.data().siege()), "the horn heard, the condition holds: " + facts);
            // 100 play hours since the last siege is past the pity point: even the worst dice hit.
            check(helper, SiegeService.rollIfReady(rig.level, pos, hearth, now, 6_000, 0.999_999, online), "pity is a sure hit");
            HearthState state = hearth.state();
            check(helper, state.siegePhase() == SiegeRules.Phase.COUNTDOWN && !state.siegeByHorn(), "a rolled countdown");
            check(helper, state.siegeDue() == now + Hearths.data().siege().warningTicks(),
                    "due a day of game time later: " + (state.siegeDue() - now));
            // Two sieges never overlap: a pending one blocks the condition and the horn.
            check(helper, !SiegeService.conditionHolds(rig.level, pos, hearth, now, online), "busy blocks a second roll");
            check(helper, SiegeService.blowHorn(amman) == HornCheck.Result.BUSY, "busy refuses the horn");
            SiegeWorldData.Watch watch = SiegeWorldData.get(rig.level.getServer()).watch(hearth.key());
            check(helper, watch != null && watch.concerned().contains(amman.getUUID()), "the group is concerned");
            rig.done();
        } catch (RuntimeException | Error e) {
            rig.cleanup();
            throw e;
        }
    }

    @GameTest(template = T, batch = "siege_roll")
    public static void theRollRampsWithPlayTimeAfterTheCooldown(GameTestHelper helper) {
        ExtrasTestGuard.watch(helper);
        Rig rig = new Rig(helper);
        try {
            HearthBlockEntity hearth = rig.hearth(new BlockPos(2, 2, 2), 3);
            hearth.state().setSieged(true);
            BlockPos pos = hearth.getBlockPos();
            List<ServerPlayer> online = List.of(rig.player(new BlockPos(1, 2, 2), true, 1.0));
            long now = rig.now();
            rig.lastSiege(hearth, 3 * HOUR, 10 * DAY);
            check(helper, !SiegeService.conditionHolds(rig.level, pos, hearth, now, online), "3 play hours: cooldown");
            check(helper, !SiegeService.rollIfReady(rig.level, pos, hearth, now, 6_000, 0.0, online), "no roll in the cooldown");
            rig.lastSiege(hearth, 10 * HOUR, DAY);
            check(helper, !SiegeService.conditionHolds(rig.level, pos, hearth, now, online), "one in-game day: cooldown");
            rig.lastSiege(hearth, 10 * HOUR, 3 * DAY);
            check(helper, SiegeService.conditionHolds(rig.level, pos, hearth, now, online), "both clocks served");
            // 10 h: 4 h cooldown + the whole 6 h ramp, so the chance is pMax (0.02).
            check(helper, !SiegeService.rollIfReady(rig.level, pos, hearth, now, 6_000, 0.5, online), "0.5 misses at 2%");
            check(helper, hearth.state().siegePhase() == SiegeRules.Phase.IDLE, "a miss stays idle");
            check(helper, SiegeService.rollIfReady(rig.level, pos, hearth, now, 6_000, 0.019, online), "0.019 hits at 2%");
            check(helper, hearth.state().siegeDue() == now + DAY, "due a day later");
            rig.done();
        } catch (RuntimeException | Error e) {
            rig.cleanup();
            throw e;
        }
    }

    @GameTest(template = T, batch = "siege_group")
    public static void theWholeActiveGroupMustBeHomeAndWarmAndAfkAltsDoNotCount(GameTestHelper helper) {
        ExtrasTestGuard.watch(helper);
        Rig rig = new Rig(helper);
        try {
            HearthBlockEntity hearth = rig.hearth(new BlockPos(2, 2, 2), 3);
            hearth.state().setSieged(true);
            BlockPos pos = hearth.getBlockPos();
            long now = rig.now();
            ServerPlayer amman = rig.player(new BlockPos(1, 2, 2), true, 1.0);
            ServerPlayer alt = rig.player(new BlockPos(3, 2, 2), false, 0.0);
            rig.away(alt);
            check(helper, SiegeService.conditionHolds(rig.level, pos, hearth, now, List.of(amman, alt)),
                    "an AFK alt away does not block it");
            rig.home(alt, new BlockPos(3, 2, 2));
            HearthAttachments.of(alt).setWarmth(1.0);
            rig.away(amman);
            check(helper, !SiegeService.conditionHolds(rig.level, pos, hearth, now, List.of(amman, alt)),
                    "an AFK alt at home does not stand in for the active player");
            check(helper, !SiegeService.conditionHolds(rig.level, pos, hearth, now, List.of(alt)),
                    "an AFK alt alone is nobody");
            rig.home(amman, new BlockPos(1, 2, 2));
            HearthAttachments.of(amman).setWarmth(0.5);
            check(helper, !SiegeService.conditionHolds(rig.level, pos, hearth, now, List.of(amman, alt)), "half Warmth is not rested");
            HearthAttachments.of(amman).setWarmth(1.0);
            ServerPlayer friend = rig.player(new BlockPos(1, 2, 3), true, 1.0);
            rig.away(friend);
            check(helper, !SiegeService.conditionHolds(rig.level, pos, hearth, now, List.of(amman, friend)),
                    "a friend out exploring blocks it");
            friend.setGameMode(GameType.CREATIVE);
            check(helper, SiegeService.conditionHolds(rig.level, pos, hearth, now, List.of(amman, friend)),
                    "a creative player is not part of the group");
            hearth.state().setStage(2);
            check(helper, !SiegeService.conditionHolds(rig.level, pos, hearth, now, List.of(amman)), "never at ceiling II");
            hearth.state().setStage(3);
            hearth.state().setCharge(HearthPool.HEARTY, 0);
            check(helper, !SiegeService.conditionHolds(rig.level, pos, hearth, now, List.of(amman)), "never with empty pools");
            rig.done();
        } catch (RuntimeException | Error e) {
            rig.cleanup();
            throw e;
        }
    }

    // ---- The horn -------------------------------------------------------------------------------

    @GameTest(template = T, batch = "siege_horn")
    public static void theHornCallsTheFirstSiegeAtThatHearthOnlyAndKeepsItsCooldown(GameTestHelper helper) {
        ExtrasTestGuard.watch(helper);
        Rig rig = new Rig(helper);
        try {
            BlockPos rel = new BlockPos(2, 2, 2);
            HearthBlockEntity first = rig.hearth(rel, 3);
            // Lit a moment ago: the lighting seeds the roll's clock, but the horn has no siege to wait after.
            first.state().onLit(rig.now(), rig.playNow());
            HearthBlockEntity other = rig.hearth(new BlockPos(42, 2, 2), 2);
            BlockPos pos = first.getBlockPos();
            ServerPlayer amman = rig.player(new BlockPos(1, 2, 2), true, 0.0);
            check(helper, rig.level.getRecipeManager().byKey(Proficiency.id("siege_horn")).isPresent(), "the horn has a recipe");

            ItemStack horn = new ItemStack(HearthBlocks.SIEGE_HORN.get());
            amman.setItemInHand(InteractionHand.MAIN_HAND, horn);
            long now = rig.now();
            InteractionResultHolder<ItemStack> blown = horn.use(rig.level, amman, InteractionHand.MAIN_HAND);
            check(helper, blown.getResult().consumesAction(), "the first horn at a freshly lit Hearth was blown: "
                    + blown.getResult());
            HearthState state = first.state();
            check(helper, state.siegePhase() == SiegeRules.Phase.COUNTDOWN && state.siegeByHorn(), "a horn countdown");
            check(helper, state.sieged(), "the first horn switches rolling on here");
            check(helper, !other.state().sieged(), "and only here");
            check(helper, state.siegeDue() == now + Math.round(Hearths.data().siege().hornMinDelaySeconds() * 20),
                    "due two real minutes later: " + (state.siegeDue() - now));
            check(helper, amman.getItemInHand(InteractionHand.MAIN_HAND).isEmpty(), "the horn is used up");

            check(helper, SiegeService.blowHorn(amman) == HornCheck.Result.BUSY, "no second siege while one is pending");
            SiegeService.end(rig.level, pos, first, SiegeRules.Outcome.DAWN, now, List.of(amman));
            check(helper, SiegeService.blowHorn(amman) == HornCheck.Result.COOLDOWN, "1 h of play since the last siege");
            rig.lastSiege(first, HOUR, 0);
            check(helper, SiegeService.blowHorn(amman) == HornCheck.Result.OK, "an hour later it may be blown again");
            SiegeService.end(rig.level, pos, first, SiegeRules.Outcome.DAWN, now, List.of(amman));

            rig.home(amman, new BlockPos(41, 2, 2));
            check(helper, SiegeService.blowHorn(amman) == HornCheck.Result.CEILING, "never at ceiling II");
            rig.away(amman);
            check(helper, SiegeService.blowHorn(amman) == HornCheck.Result.NO_HEARTH, "no Hearth in range");

            // Relighting a broken Hearth starts with the flag off and no siege mirror left behind.
            String key = first.key();
            rig.lastSiege(first, 10 * HOUR, 0);
            rig.home(amman, new BlockPos(1, 2, 2));
            check(helper, SiegeService.blowHorn(amman) == HornCheck.Result.OK, "a second horn");
            // Broken by a player mid-countdown: the siege goes with it.
            HearthHooks.onBlockBroken(rig.level, pos, rig.level.getBlockState(pos), amman);
            check(helper, first.state().siegePhase() == SiegeRules.Phase.IDLE
                    && SiegeWorldData.get(rig.level.getServer()).watch(key) == null, "breaking it cancels the siege");
            helper.setBlock(rel, Blocks.AIR);
            check(helper, HearthWorldData.get(rig.level.getServer()).siegeDue(key) == HearthState.NEVER, "the mirror goes with it");
            HearthBlockEntity relit = rig.hearth(rel, 3);
            check(helper, !relit.state().sieged() && relit.state().siegePhase() == SiegeRules.Phase.IDLE,
                    "a relit Hearth has not heard a horn");
            rig.done();
        } catch (RuntimeException | Error e) {
            rig.cleanup();
            throw e;
        }
    }

    // ---- Nightfall: postpone, fizzle, catch up ----------------------------------------------------

    @GameTest(template = T, batch = "siege_postpone")
    public static void nobodyHomeAtNightfallPostponesTwiceThenFizzles(GameTestHelper helper) {
        ExtrasTestGuard.watch(helper);
        Rig rig = new Rig(helper);
        try {
            HearthBlockEntity hearth = rig.hearth(new BlockPos(2, 2, 2), 3);
            BlockPos pos = hearth.getBlockPos();
            ServerPlayer alt = rig.player(new BlockPos(1, 2, 2), false, 1.0);
            List<ServerPlayer> online = List.of(alt);
            long n = rig.now();
            SiegeService.beginCountdown(rig.level, pos, hearth, n, 12_000, true, online, alt);
            HearthState state = hearth.state();
            SiegeService.second(rig.level, pos, hearth, n + 20, 12_020, online, () -> 0.5);
            check(helper, state.siegePhase() == SiegeRules.Phase.COUNTDOWN, "counting down by day");
            for (int night = 1; night <= 3; night++) {
                long dusk = n + 1_000 + night * DAY;
                long dayDusk = 13_000 + night * DAY;
                SiegeService.second(rig.level, pos, hearth, dusk - 20, dayDusk - 20, online, () -> 0.5);
                check(helper, state.siegePhase() == SiegeRules.Phase.COUNTDOWN, "still waiting before dusk " + night);
                SiegeService.second(rig.level, pos, hearth, dusk, dayDusk, online, () -> 0.5);
                if (night < 3) {
                    check(helper, state.siegePhase() == SiegeRules.Phase.COUNTDOWN && state.postponed() == night,
                            "an AFK alt at home is nobody: postponed " + state.postponed() + " at night " + night);
                } else {
                    check(helper, state.siegePhase() == SiegeRules.Phase.IDLE, "the third empty nightfall fizzles it");
                    long playNow = rig.playNow();
                    check(helper, state.lastSiegeEndPlay() == playNow - 2 * HOUR,
                            "a fizzle counts half of the 4 h served: " + (playNow - state.lastSiegeEndPlay()));
                    check(helper, state.lastSiegeEndTick() == dusk - DAY, "and half of the 2 days");
                    check(helper, state.siegeDue() == HearthState.NEVER, "nothing pending");
                }
            }
            check(helper, SiegeService.siegeMobs(rig.level, hearth).isEmpty(), "nothing spawned with nobody home");
            rig.done();
        } catch (RuntimeException | Error e) {
            rig.cleanup();
            throw e;
        }
    }

    @GameTest(template = T, batch = "siege_unloaded")
    public static void anUnloadedHearthCatchesUpTheNightfallsItMissed(GameTestHelper helper) {
        ExtrasTestGuard.watch(helper);
        Rig rig = new Rig(helper);
        try {
            HearthBlockEntity hearth = rig.hearth(new BlockPos(2, 2, 2), 3);
            BlockPos pos = hearth.getBlockPos();
            ServerPlayer amman = rig.player(new BlockPos(1, 2, 2), true, 1.0);
            long n = rig.now();
            SiegeService.beginCountdown(rig.level, pos, hearth, n, 12_000, true, List.of(amman), amman);
            // Three days unloaded, coming back in the middle of a night with someone home: the three dusks
            // it missed (after the due time) were two postponements and a fizzle; tonight's start was missed.
            SiegeService.second(rig.level, pos, hearth, n + 3 * DAY + 2_000, 12_000 + 3 * DAY + 2_000, List.of(amman),
                    () -> 0.5);
            HearthState state = hearth.state();
            check(helper, state.siegePhase() == SiegeRules.Phase.IDLE, "caught up to a fizzle: " + state.siegePhase()
                    + " postponed " + state.postponed());
            check(helper, SiegeService.siegeMobs(rig.level, hearth).isEmpty(), "and spawned nothing");
            rig.done();
        } catch (RuntimeException | Error e) {
            rig.cleanup();
            throw e;
        }
    }

    @GameTest(template = T, batch = "siege_restart", timeoutTicks = 100)
    public static void aRestartMidCountdownKeepsTheTime(GameTestHelper helper) {
        ExtrasTestGuard.watch(helper);
        Rig rig = new Rig(helper);
        try {
            HearthBlockEntity hearth = rig.hearth(new BlockPos(2, 2, 2), 4);
            BlockPos pos = hearth.getBlockPos();
            ServerPlayer amman = rig.player(new BlockPos(1, 2, 2), true, 1.0);
            long now = rig.now();
            hearth.state().setSieged(true);
            SiegeService.beginCountdown(rig.level, pos, hearth, now, 6_000, false, List.of(amman), null);
            long due = hearth.state().siegeDue();
            long id = hearth.laneData(SiegeService.LANE).getLong("id");

            CompoundTag saved = hearth.saveWithFullMetadata();
            BlockEntity loaded = BlockEntity.loadStatic(pos, rig.level.getBlockState(pos), saved);
            check(helper, loaded instanceof HearthBlockEntity, "the Hearth loads back");
            HearthBlockEntity copy = (HearthBlockEntity) loaded;
            check(helper, copy.state().siegePhase() == SiegeRules.Phase.COUNTDOWN && copy.state().siegeDue() == due,
                    "the countdown and its due time survive a save");
            check(helper, copy.laneData(SiegeService.LANE).getLong("id") == id, "the siege id survives");

            SiegeWorldData world = SiegeWorldData.get(rig.level.getServer());
            SiegeWorldData back = SiegeWorldData.load(world.save(new CompoundTag()));
            SiegeWorldData.Watch watch = back.watch(hearth.key());
            check(helper, watch != null && watch.concerned().contains(amman.getUUID()), "who it concerns survives too");
            helper.runAfterDelay(25, () -> {
                try {
                    check(helper, HearthWorldData.get(rig.level.getServer()).siegeDue(hearth.key()) == due,
                            "the world data mirrors the due time");
                } finally {
                    rig.cleanup();
                }
                rig.done();
            });
        } catch (RuntimeException | Error e) {
            rig.cleanup();
            throw e;
        }
    }

    // ---- Outcomes -------------------------------------------------------------------------------

    private static final AtomicReference<HearthEvents.SiegeDefended> DEFENDED = new AtomicReference<>();
    private static final AtomicBoolean DEFENDED_LISTENER = new AtomicBoolean();
    /** How often SIEGE_DEFENDED fired in this test: once per defended siege (integration). */
    private static final java.util.concurrent.atomic.AtomicInteger DEFENDED_FIRES =
            new java.util.concurrent.atomic.AtomicInteger();

    @GameTest(template = T, batch = "siege_defended")
    public static void aDefendedSiegeRewardsEveryPoolAndLeavesATrophy(GameTestHelper helper) {
        ExtrasTestGuard.watch(helper);
        if (DEFENDED_LISTENER.compareAndSet(false, true)) {
            HearthEvents.SIEGE_DEFENDED.add(event -> {
                DEFENDED.set(event);
                DEFENDED_FIRES.incrementAndGet();
            });
        }
        DEFENDED.set(null);
        DEFENDED_FIRES.set(0);
        Rig rig = new Rig(helper);
        try {
            HearthBlockEntity hearth = rig.hearth(new BlockPos(2, 2, 2), 4);
            BlockPos pos = hearth.getBlockPos();
            rig.loadRing(pos);
            ServerPlayer amman = rig.player(new BlockPos(1, 2, 2), true, 1.0);
            List<ServerPlayer> online = List.of(amman);
            HearthState state = hearth.state();
            HearthData data = Hearths.data();
            long now = rig.now();
            SiegeService.beginCountdown(rig.level, pos, hearth, now, 12_000, true, online, amman);
            SiegeService.start(rig.level, pos, hearth, now, NIGHT, online);
            check(helper, state.siegePhase() == SiegeRules.Phase.ACTIVE && state.wave() == 1,
                    "the siege began with wave 1 (wave " + state.wave() + ")");

            List<Mob> wave = SiegeService.siegeMobs(rig.level, hearth);
            int expected = SiegeRules.waveSize(4, 1, data.siege());
            check(helper, !wave.isEmpty() && wave.size() <= expected + 2,
                    "wave 1 spawned " + wave.size() + " of " + expected + " (ring ground?)");
            double radius = HearthRange.range().radius();
            for (Mob mob : wave) {
                double dx = mob.getX() - (pos.getX() + 0.5);
                double dz = mob.getZ() - (pos.getZ() + 0.5);
                double d = Math.sqrt(dx * dx + dz * dz);
                check(helper, d <= radius && d >= radius - 5, "spawned at the edge: " + d);
                check(helper, HearthRange.inHouse(pos, mob.position()), "inside the range");
                check(helper, SpawnOrigin.isSiege(mob) && SiegeMobs.hearthOf(mob) == pos.asLong(), "tagged for this Hearth");
                check(helper, SiegeMobs.marching(mob), "it has the go-to-Hearth goal");
                check(helper, mob.isPersistenceRequired(), "it does not despawn on its own");
                // The base: finalizeSpawn adds a random +-11% on top, as for any mob.
                check(helper, mob.getAttribute(Attributes.FOLLOW_RANGE).getBaseValue() >= radius + 16, "it can find the fire");
                BlockPos below = mob.blockPosition().below();
                check(helper, rig.level.getBlockState(below).isFaceSturdy(rig.level, below, Direction.UP), "on solid ground");
                if (mob instanceof Zombie zombie) {
                    check(helper, !zombie.canBreakDoors(), "a siege zombie breaks no doors");
                }
            }
            int kills = 0;
            double hearty = 0;
            double workshop = 0;
            double credited = 0;
            for (int w = 1; w <= data.siege().waves(); w++) {
                kills += SiegeService.siegeMobs(rig.level, hearth).size();
                rig.killAll(hearth, amman);
                check(helper, SiegeService.siegeMobs(rig.level, hearth).isEmpty(), "wave " + w + " is dead");
                // Before the last second: the charge as the kills left it (another lane may add their share).
                hearty = state.charge(HearthPool.HEARTY);
                workshop = state.charge(HearthPool.WORKSHOP);
                credited = state.ledger().total(amman.getUUID(), HearthPool.HEARTY, HearthLedger.Window.LIFETIME);
                SiegeService.second(rig.level, pos, hearth, now + 20L * w, NIGHT + 20L * w, online, () -> 0.5);
                if (w < data.siege().waves()) {
                    check(helper, state.wave() == w + 1, "the next wave came: " + state.wave());
                }
            }
            check(helper, state.siegePhase() == SiegeRules.Phase.IDLE, "every wave dead: the siege ended");
            check(helper, state.defenceStreak() == 1, "defence streak 1");
            double heartyBurst = SiegeRules.burst(hearty, 1, data);
            double workshopBurst = SiegeRules.burst(workshop, 1, data);
            check(helper, Math.abs(state.charge(HearthPool.HEARTY) - hearty - heartyBurst) < 1e-6,
                    "Hearty burst " + (state.charge(HearthPool.HEARTY) - hearty) + ", wanted " + heartyBurst);
            check(helper, Math.abs(state.charge(HearthPool.WORKSHOP) - workshop - workshopBurst) < 1e-6,
                    "Workshop burst " + (state.charge(HearthPool.WORKSHOP) - workshop));
            // Hearty 700 is tier II: the burst is 15% of III's 1400 (10% + 5% for a streak of 1) = 210.
            check(helper, hearty >= 1400 || Math.abs(heartyBurst - 210) < 1e-6, "15% of the next tier: " + heartyBurst);
            HearthLedger.Entry entry = state.ledger().get(amman.getUUID());
            check(helper, entry != null && entry.siegeKills() == kills && entry.siegesDefended() == 1,
                    "ledger kills " + (entry == null ? -1 : entry.siegeKills()) + " of " + kills);
            check(helper, Math.abs(state.ledger().total(amman.getUUID(), HearthPool.HEARTY, HearthLedger.Window.LIFETIME)
                    - credited - heartyBurst) < 1e-6, "the burst is credited to the killer");
            check(helper, state.lastSiegeEndPlay() == rig.playNow(), "the cooldown starts at the end");
            HearthEvents.SiegeDefended event = DEFENDED.get();
            check(helper, event != null && event.kills().getOrDefault(amman.getUUID(), 0) == kills
                    && event.present().contains(amman), "SIEGE_DEFENDED with the kills");
            check(helper, DEFENDED_FIRES.get() == 1, "SIEGE_DEFENDED fires once per defended siege: " + DEFENDED_FIRES.get());
            ItemEntity trophy = null;
            for (ItemEntity item : rig.level.getEntitiesOfClass(ItemEntity.class, new AABB(pos).inflate(3))) {
                if (item.getItem().getItem() instanceof SiegeTrophyItem) {
                    trophy = item;
                }
            }
            check(helper, trophy != null && SiegeTrophyItem.tier(trophy.getItem()) == 4, "a tier IV Siege Trophy at the Hearth");
            trophy.discard();
            rig.done();
        } catch (RuntimeException | Error e) {
            rig.cleanup();
            throw e;
        }
    }

    @GameTest(template = T, batch = "siege_doused")
    public static void aDousedHearthSmouldersUntilRelitWithFlintAndSteel(GameTestHelper helper) {
        ExtrasTestGuard.watch(helper);
        Rig rig = new Rig(helper);
        try {
            HearthBlockEntity hearth = rig.hearth(new BlockPos(2, 2, 2), 3);
            BlockPos pos = hearth.getBlockPos();
            rig.loadRing(pos);
            ServerPlayer amman = rig.player(new BlockPos(1, 2, 2), true, 1.0);
            List<ServerPlayer> online = List.of(amman);
            HearthState state = hearth.state();
            HearthData.Siege s = Hearths.data().siege();
            state.setCharge(HearthPool.HEARTY, 1000);
            state.setDefenceStreak(2);
            long now = rig.now();
            SiegeService.beginCountdown(rig.level, pos, hearth, now, 12_000, true, online, amman);
            SiegeService.start(rig.level, pos, hearth, now, NIGHT, online);
            List<Mob> wave = SiegeService.siegeMobs(rig.level, hearth);
            check(helper, !wave.isEmpty(), "wave 1 spawned (ring ground?)");
            HearthBell.SiegeLink link = HearthBell.siegeLink;
            check(helper, link != null && link.mobs(rig.level, hearth).size() == wave.size()
                    && link.concerned(rig.level, hearth).contains(amman), "the bell's alarm sees this siege");
            int near = 0;
            for (Mob mob : wave) {
                mob.teleportTo(pos.getX() + 1.5, pos.getY(), pos.getZ() + 0.5);
                near++;
            }
            SiegeService.second(rig.level, pos, hearth, now + 20, NIGHT + 20, online, () -> 0.5);
            check(helper, Math.abs(state.douse() - near * s.dousePerMobSecond()) < 1e-6, "each mob by the fire adds to the meter: "
                    + state.douse());
            state.setDouse(55);
            SiegeService.second(rig.level, pos, hearth, now + 30, NIGHT + 30, online, () -> 0.5);
            SiegeWorldData.Watch watch = SiegeWorldData.get(rig.level.getServer()).watch(hearth.key());
            check(helper, watch != null && watch.douse() >= 55, "a concerned player logging in gets the meter back: "
                    + (watch == null ? -1 : watch.douse()));
            state.setDouse(s.douseMax() - 0.5);
            SiegeService.second(rig.level, pos, hearth, now + 40, NIGHT + 40, online, () -> 0.5);
            check(helper, state.siegePhase() == SiegeRules.Phase.IDLE, "a full meter ends the siege");
            check(helper, state.smouldering(), "the Hearth smoulders");
            BlockState block = rig.level.getBlockState(pos);
            check(helper, block.getValue(HearthBlock.SMOULDERING), "the block shows it");
            check(helper, Math.abs(state.charge(HearthPool.HEARTY) - 900) < 1e-6, "every pool loses 10%: " + state.charge(HearthPool.HEARTY));
            check(helper, state.defenceStreak() == 0, "the defence streak is gone");
            check(helper, state.effectiveTier(HearthPool.HEARTY, Hearths.data()) == 0, "boons are off");
            check(helper, HearthAttachments.of(amman).warmth() == 0f, "nobody in range keeps Warmth");
            for (Mob mob : wave) {
                check(helper, mob.isRemoved(), "the attackers left");
            }
            check(helper, state.ceiling(Hearths.data()) == 3 && state.stage() == 3, "the build is untouched");
            // It stays cold while it smoulders.
            HearthAttachments.of(amman).setWarmth(0.7);
            SiegeService.second(rig.level, pos, hearth, now + 60, NIGHT + 60, online, () -> 0.5);
            check(helper, HearthAttachments.of(amman).warmth() == 0f, "Warmth stays 0 by a smouldering Hearth");

            ItemStack bread = new ItemStack(Items.BREAD);
            HearthHooks.onBlockUse(amman, rig.level, pos, block, InteractionHand.MAIN_HAND, bread);
            check(helper, state.smouldering(), "only flint and steel relights it");
            ItemStack flint = new ItemStack(Items.FLINT_AND_STEEL);
            amman.setItemInHand(InteractionHand.MAIN_HAND, flint);
            check(helper, HearthHooks.onBlockUse(amman, rig.level, pos, block, InteractionHand.MAIN_HAND, flint)
                    == InteractionResult.SUCCESS, "flint and steel relights it");
            check(helper, !state.smouldering() && !rig.level.getBlockState(pos).getValue(HearthBlock.SMOULDERING), "it burns again");
            check(helper, flint.getDamageValue() == 1, "it costs the flint and steel one use");
            HearthHooks.onBlockUse(amman, rig.level, pos, rig.level.getBlockState(pos), InteractionHand.MAIN_HAND, flint);
            check(helper, !state.smouldering() && flint.getDamageValue() == 1, "a burning Hearth is not relit again");
            rig.done();
        } catch (RuntimeException | Error e) {
            rig.cleanup();
            throw e;
        }
    }

    @GameTest(template = T, batch = "siege_neither")
    public static void dawnOrEveryoneLeavingEndsItNeitherWonNorLost(GameTestHelper helper) {
        ExtrasTestGuard.watch(helper);
        Rig rig = new Rig(helper);
        try {
            HearthBlockEntity hearth = rig.hearth(new BlockPos(2, 2, 2), 3);
            BlockPos pos = hearth.getBlockPos();
            rig.loadRing(pos);
            ServerPlayer amman = rig.player(new BlockPos(1, 2, 2), true, 1.0);
            HearthState state = hearth.state();
            HearthData.Siege s = Hearths.data().siege();
            state.setDefenceStreak(2);
            long now = rig.now();
            SiegeService.beginCountdown(rig.level, pos, hearth, now, 12_000, true, List.of(amman), amman);
            SiegeService.start(rig.level, pos, hearth, now, NIGHT, List.of(amman));
            List<Mob> wave = SiegeService.siegeMobs(rig.level, hearth);
            long t = now;
            for (int i = 0; i < Math.round(s.abandonGraceSeconds()); i++) {
                t += 20;
                check(helper, state.siegePhase() == SiegeRules.Phase.ACTIVE, "running while the grace lasts (" + i + ")");
                SiegeService.second(rig.level, pos, hearth, t, NIGHT + (t - now), List.of(), () -> 0.5);
            }
            check(helper, state.siegePhase() == SiegeRules.Phase.IDLE, "everyone gone: it ends");
            check(helper, state.defenceStreak() == 2 && !state.smouldering(), "neither won nor lost");
            for (Mob mob : wave) {
                check(helper, mob.isRemoved(), "leftovers despawn");
            }

            rig.lastSiege(hearth, 10 * HOUR, 10 * DAY);
            SiegeService.beginCountdown(rig.level, pos, hearth, t, 12_000, true, List.of(amman), amman);
            SiegeService.start(rig.level, pos, hearth, t, NIGHT, List.of(amman));
            wave = SiegeService.siegeMobs(rig.level, hearth);
            SiegeService.second(rig.level, pos, hearth, t + 20, 23_500, List.of(amman), () -> 0.5);
            check(helper, state.siegePhase() == SiegeRules.Phase.IDLE, "dawn ends it");
            check(helper, state.defenceStreak() == 2, "the streak is kept");
            for (Mob mob : wave) {
                check(helper, mob.isRemoved(), "leftovers despawn at dawn");
            }
            // A siege whose Hearth was unloaded mid-fight was abandoned.
            rig.lastSiege(hearth, 10 * HOUR, 10 * DAY);
            SiegeService.beginCountdown(rig.level, pos, hearth, t, 12_000, true, List.of(amman), amman);
            SiegeService.start(rig.level, pos, hearth, t, NIGHT, List.of(amman));
            SiegeService.second(rig.level, pos, hearth, t + 2_000, NIGHT + 2_000, List.of(amman), () -> 0.5);
            check(helper, state.siegePhase() == SiegeRules.Phase.IDLE, "a long gap mid-siege is an abandoned siege");
            rig.done();
        } catch (RuntimeException | Error e) {
            rig.cleanup();
            throw e;
        }
    }

    @GameTest(template = T, batch = "siege_bed")
    public static void aNightSleptThroughIsAPostponementNeverAFreeDelay(GameTestHelper helper) {
        ExtrasTestGuard.watch(helper);
        Rig rig = new Rig(helper);
        try {
            HearthBlockEntity hearth = rig.hearth(new BlockPos(2, 2, 2), 3);
            BlockPos pos = hearth.getBlockPos();
            ServerPlayer amman = rig.player(new BlockPos(1, 2, 2), true, 1.0);
            List<ServerPlayer> online = List.of(amman);
            HearthState state = hearth.state();
            state.setSieged(true);

            // A jump that lands in a night begun since the last check (/time set night) is that night's fall,
            // taken once by the live check: nobody home, so one postponement, and never a second for it.
            long n = rig.now();
            SiegeService.beginCountdown(rig.level, pos, hearth, n, 6_000, false, online, null);
            state.setSiegeDue(n);
            rig.away(amman);
            SiegeService.second(rig.level, pos, hearth, n + 20, 6_020, online, () -> 0.5);
            SiegeService.second(rig.level, pos, hearth, n + 40, 14_000, online, () -> 0.5);
            check(helper, state.siegePhase() == SiegeRules.Phase.COUNTDOWN && state.postponed() == 1,
                    "the fresh night's fall was seen once: postponed " + state.postponed());
            SiegeService.second(rig.level, pos, hearth, n + 60, 14_020, online, () -> 0.5);
            check(helper, state.postponed() == 1, "the same night does not count twice");
            // Called off while counting down (sieges switched off, Peaceful): the cooldown starts now too.
            SiegeService.cancel(rig.level, pos, hearth, n + 60);
            check(helper, state.siegePhase() == SiegeRules.Phase.IDLE && state.lastSiegeEndTick() == n + 60
                    && state.lastSiegeEndPlay() == rig.playNow(), "a cancelled countdown starts the cooldown");
            rig.home(amman, new BlockPos(1, 2, 2));

            // Rolled at noon: due a day of game time later, so it starts at tomorrow's dusk.
            rig.lastSiege(hearth, 100 * HOUR, 100 * DAY);
            SiegeService.beginCountdown(rig.level, pos, hearth, n, 6_000, false, online, null);
            long due = state.siegeDue();
            // Tomorrow evening at 12 600 (the night between was before the due time and costs nothing).
            long g = n + DAY + 6_600;
            SiegeService.second(rig.level, pos, hearth, g, DAY + 12_600, online, () -> 0.5);
            check(helper, state.siegePhase() == SiegeRules.Phase.COUNTDOWN && state.postponed() == 0, "still counting down");
            check(helper, SiegeClock.bedSkipsStart(due, g, DAY + 12_600, state.nightBefore()),
                    "a bed in range now would skip the dusk that starts it (refused, see siege_sleep)");
            // A bed out of range skips that dusk anyway: three evenings of it, and the siege fizzles.
            for (int evening = 1; evening <= 3; evening++) {
                long morning = (evening + 1) * DAY;
                SiegeService.second(rig.level, pos, hearth, g + 20, morning, online, () -> 0.5);
                if (evening < 3) {
                    check(helper, state.siegePhase() == SiegeRules.Phase.COUNTDOWN && state.postponed() == evening,
                            "a dusk slept through is a postponement: " + state.postponed() + " on evening " + evening);
                    check(helper, state.siegeDue() == due, "the due time stays");
                    g += DAY;
                    SiegeService.second(rig.level, pos, hearth, g, morning + 12_600, online, () -> 0.5);
                } else {
                    check(helper, state.siegePhase() == SiegeRules.Phase.IDLE, "the third dusk slept through fizzles it");
                    check(helper, state.lastSiegeEndPlay() == rig.playNow() - 2 * HOUR, "the cooldown counts half served");
                }
            }
            rig.done();
        } catch (RuntimeException | Error e) {
            rig.cleanup();
            throw e;
        }
    }

    @GameTest(template = T, batch = "siege_unearned")
    public static void onlyPlayersAndPetsDefendTheHearthAndAnUnloadedMobStillStands(GameTestHelper helper) {
        ExtrasTestGuard.watch(helper);
        Rig rig = new Rig(helper);
        try {
            HearthBlockEntity hearth = rig.hearth(new BlockPos(2, 2, 2), 3);
            BlockPos pos = hearth.getBlockPos();
            rig.loadRing(pos);
            ServerPlayer amman = rig.player(new BlockPos(1, 2, 2), true, 1.0);
            List<ServerPlayer> online = List.of(amman);
            HearthState state = hearth.state();
            HearthData data = Hearths.data();
            state.setDefenceStreak(2);
            long now = rig.now();
            SiegeService.beginCountdown(rig.level, pos, hearth, now, 12_000, true, online, amman);
            SiegeService.start(rig.level, pos, hearth, now, NIGHT, online);
            CompoundTag lane = hearth.laneData(SiegeService.LANE);
            long id = lane.getLong("id");
            List<Mob> wave = SiegeService.siegeMobs(rig.level, hearth);
            check(helper, wave.size() >= 2, "wave 1 spawned at least two (ring ground?): " + wave.size());

            // Friendly fire: a siege mob felled by another is nobody's kill and spoils nothing.
            Mob felled = wave.get(0);
            felled.hurt(rig.level.damageSources().mobAttack(wave.get(1)), 10_000f);
            check(helper, !felled.isAlive() && !SiegeService.siegeMobs(rig.level, hearth).contains(felled), "off the roster");
            check(helper, lane.getInt("unearned") == 0, "a fellow siege mob's blow is not unearned");

            // A zombie that drowns into a drowned stays the siege's.
            Zombie zombie = helper.spawn(EntityType.ZOMBIE, new BlockPos(3, 2, 3));
            SiegeMobs.mark(zombie, pos, id);
            ListTag roster = lane.getList("mobs", Tag.TAG_COMPOUND);
            CompoundTag entry = new CompoundTag();
            entry.putUUID("u", zombie.getUUID());
            entry.putInt("w", state.wave());
            roster.add(entry);
            lane.put("mobs", roster);
            Mob drowned = zombie.convertTo(EntityType.DROWNED, true);
            check(helper, drowned != null && SiegeService.siegeMobs(rig.level, hearth).contains(drowned),
                    "the drowned took the zombie's place on the roster");
            check(helper, SpawnOrigin.isSiege(drowned) && SiegeMobs.hearthOf(drowned) == pos.asLong()
                    && SiegeMobs.siegeIdOf(drowned) == id && SiegeMobs.marching(drowned), "marked and marching");
            check(helper, lane.getInt("unearned") == 0, "a conversion is no fall");

            // A mob whose chunk is not loaded (after a restart, say) still stands.
            CompoundTag ghost = new CompoundTag();
            UUID ghostId = UUID.randomUUID();
            ghost.putUUID("u", ghostId);
            ghost.putInt("w", 1);
            roster = lane.getList("mobs", Tag.TAG_COMPOUND);
            roster.add(ghost);
            lane.put("mobs", roster);

            // Lava takes one with no player's hand on it: this siege can no longer be won.
            Mob burnt = null;
            for (Mob mob : SiegeService.siegeMobs(rig.level, hearth)) {
                if (mob != drowned) {
                    burnt = mob;
                }
            }
            check(helper, burnt != null, "a mob for the lava");
            burnt.hurt(rig.level.damageSources().lava(), 10_000f);
            check(helper, lane.getInt("unearned") == 1, "lava's kill is unearned: " + lane.getInt("unearned"));

            // Every wave brought down by hand from here.
            long t = now;
            for (int w = 1; w <= data.siege().waves(); w++) {
                rig.killAll(hearth, amman);
                t += 20;
                SiegeService.second(rig.level, pos, hearth, t, NIGHT + (t - now), online, () -> 0.5);
            }
            check(helper, state.siegePhase() == SiegeRules.Phase.ACTIVE && state.wave() == data.siege().waves(),
                    "the unloaded mob still stands, so the siege runs on: " + state.siegePhase() + " wave " + state.wave());
            check(helper, SiegeService.siegeMobs(rig.level, hearth).isEmpty(), "nothing loaded is left");

            // It loads and falls (here: it leaves the roster); the next second ends the siege.
            roster = lane.getList("mobs", Tag.TAG_COMPOUND);
            for (int i = roster.size() - 1; i >= 0; i--) {
                if (roster.getCompound(i).getUUID("u").equals(ghostId)) {
                    roster.remove(i);
                }
            }
            lane.put("mobs", roster);
            double hearty = state.charge(HearthPool.HEARTY);
            t += 20;
            SiegeService.second(rig.level, pos, hearth, t, NIGHT + (t - now), online, () -> 0.5);
            check(helper, state.siegePhase() == SiegeRules.Phase.IDLE, "every wave down: it is over");
            check(helper, state.defenceStreak() == 2 && !state.smouldering(), "unearned: neither won nor lost");
            check(helper, Math.abs(state.charge(HearthPool.HEARTY) - hearty) < 1e-6, "no burst");
            HearthLedger.Entry ledger = state.ledger().get(amman.getUUID());
            check(helper, ledger == null || ledger.siegesDefended() == 0, "no defence in the ledger");
            for (ItemEntity item : rig.level.getEntitiesOfClass(ItemEntity.class, new AABB(pos).inflate(3))) {
                check(helper, !(item.getItem().getItem() instanceof SiegeTrophyItem), "no trophy");
            }
            check(helper, state.lastSiegeEndPlay() == rig.playNow(), "the cooldown starts at the end");
            rig.done();
        } catch (RuntimeException | Error e) {
            rig.cleanup();
            throw e;
        }
    }

    // ---- What siege mobs may not do -------------------------------------------------------------

    @GameTest(template = T, batch = "siege_griefing")
    public static void siegeMobsBreakNoBlocks(GameTestHelper helper) {
        ExtrasTestGuard.watch(helper);
        Rig rig = new Rig(helper);
        try {
            ServerLevel level = rig.level;
            BlockPos hearthPos = helper.absolutePos(new BlockPos(0, 2, 0));
            // Forge's event answers per entity (what every per-entity griefing check asks).
            Zombie plain = helper.spawnWithNoFreeWill(EntityType.ZOMBIE, new BlockPos(1, 2, 1));
            Zombie siege = helper.spawnWithNoFreeWill(EntityType.ZOMBIE, new BlockPos(3, 2, 3));
            SiegeMobs.mark(siege, hearthPos, 1L);
            check(helper, ForgeEventFactory.getMobGriefingEvent(level, plain), "a plain zombie may grief");
            check(helper, !ForgeEventFactory.getMobGriefingEvent(level, siege), "a siege zombie may not");

            // Farmland: the loader's own trampling check asks it.
            BlockPos farmRel = new BlockPos(1, 2, 3);
            helper.setBlock(farmRel, Blocks.FARMLAND);
            BlockPos farm = helper.absolutePos(farmRel);
            Blocks.FARMLAND.fallOn(level, level.getBlockState(farm), farm, siege, 3.0f);
            check(helper, level.getBlockState(farm).is(Blocks.FARMLAND), "a siege zombie leaves the farmland alone");
            Blocks.FARMLAND.fallOn(level, level.getBlockState(farm), farm, plain, 3.0f);
            check(helper, level.getBlockState(farm).is(Blocks.DIRT), "a plain zombie tramples it");

            // A siege creeper's explosion breaks nothing; a plain one does (mobGriefing is on here).
            BlockPos dirtRel = new BlockPos(2, 2, 1);
            helper.setBlock(dirtRel, Blocks.DIRT);
            BlockPos dirt = helper.absolutePos(dirtRel);
            Creeper creeper = helper.spawnWithNoFreeWill(EntityType.CREEPER, new BlockPos(2, 2, 2));
            SiegeMobs.mark(creeper, hearthPos, 1L);
            level.explode(creeper, dirt.getX() + 0.5, dirt.getY() + 0.5, dirt.getZ() + 1.5, 3.0f, Level.ExplosionInteraction.MOB);
            check(helper, level.getBlockState(dirt).is(Blocks.DIRT), "a siege creeper breaks no blocks");
            creeper.discard();
            Creeper wild = helper.spawnWithNoFreeWill(EntityType.CREEPER, new BlockPos(2, 2, 2));
            level.explode(wild, dirt.getX() + 0.5, dirt.getY() + 0.5, dirt.getZ() + 1.5, 3.0f, Level.ExplosionInteraction.MOB);
            check(helper, level.getBlockState(dirt).isAir(), "a wild creeper does");
            wild.discard();

            // Spawned siege mobs lose their door breaking for good.
            Zombie fresh = EntityType.ZOMBIE.create(level);
            fresh.setCanBreakDoors(true);
            SiegeMobs.disarm(fresh);
            check(helper, !fresh.canBreakDoors(), "disarmed: no door breaking");
            fresh.discard();
            plain.discard();
            siege.discard();
            rig.done();
        } catch (RuntimeException | Error e) {
            rig.cleanup();
            throw e;
        }
    }

    @GameTest(template = T, batch = "siege_sleep")
    public static void noSleepInRangeWhileASiegeRuns(GameTestHelper helper) {
        ExtrasTestGuard.watch(helper);
        Rig rig = new Rig(helper);
        try {
            HearthBlockEntity hearth = rig.hearth(new BlockPos(2, 2, 2), 3);
            ServerPlayer amman = rig.player(new BlockPos(1, 2, 2), true, 1.0);
            BlockPos bed = helper.absolutePos(new BlockPos(3, 2, 3));
            HearthState state = hearth.state();
            long dayBefore = rig.level.getDayTime();
            try {
                // A clear evening: vanilla lets you into bed from about 12 540.
                rig.level.setDayTime(dayBefore - Math.floorMod(dayBefore, DAY) + 12_600);
                check(helper, sleep(amman, bed) == null, "idle: sleep is allowed");
                state.setSiegePhase(SiegeRules.Phase.COUNTDOWN);
                state.setSiegeDue(rig.now() + 5_000);
                check(helper, sleep(amman, bed) == null, "counting down to tomorrow's dusk: sleep is allowed");
                state.setSiegeDue(rig.now());
                check(helper, sleep(amman, bed) == Player.BedSleepingProblem.OTHER_PROBLEM,
                        "a bed that would sleep through the dusk that starts it is refused");
                rig.level.setDayTime(dayBefore - Math.floorMod(dayBefore, DAY) + 15_000);
                state.setNightBefore(true);
                check(helper, sleep(amman, bed) == null, "later that night (started or put off already) a bed is fine");
                state.setSiegePhase(SiegeRules.Phase.ACTIVE);
                check(helper, sleep(amman, bed) == Player.BedSleepingProblem.OTHER_PROBLEM, "a running siege refuses it");
                BlockPos far = bed.offset(200, 0, 0);
                check(helper, sleep(amman, far) == null, "out of range it is allowed");
                state.setSiegePhase(SiegeRules.Phase.IDLE);
                state.setSiegeDue(HearthState.NEVER);
            } finally {
                rig.level.setDayTime(dayBefore);
            }
            rig.done();
        } catch (RuntimeException | Error e) {
            rig.cleanup();
            throw e;
        }
    }

    /**
     * Forge asks its sleep event before vanilla sets the respawn point; NeoForge asks after. A bed the siege
     * refuses still becomes the spawn when vanilla would have made it one, so a player who dies in the siege
     * comes back at it. A bed vanilla itself turns down first (out of reach) does not. The real
     * {@code startSleepInBed}, on a real bed.
     */
    @GameTest(template = T, batch = "siege_sleep_spawn")
    public static void aBedTheSiegeRefusesStillBecomesTheSpawn(GameTestHelper helper) {
        ExtrasTestGuard.watch(helper);
        Rig rig = new Rig(helper);
        try {
            HearthBlockEntity hearth = rig.hearth(new BlockPos(2, 2, 2), 3);
            ServerPlayer amman = rig.player(new BlockPos(1, 2, 2), true, 1.0);
            ServerPlayer far = rig.player(new BlockPos(1, 2, 2), true, 1.0);
            far.setPos(far.getX() - 10, far.getY(), far.getZ());
            BlockPos footRel = new BlockPos(3, 2, 3);
            helper.setBlock(footRel, Blocks.RED_BED.defaultBlockState()
                    .setValue(net.minecraft.world.level.block.BedBlock.FACING, Direction.EAST)
                    .setValue(net.minecraft.world.level.block.BedBlock.PART,
                            net.minecraft.world.level.block.state.properties.BedPart.FOOT));
            helper.setBlock(footRel.east(), Blocks.RED_BED.defaultBlockState()
                    .setValue(net.minecraft.world.level.block.BedBlock.FACING, Direction.EAST)
                    .setValue(net.minecraft.world.level.block.BedBlock.PART,
                            net.minecraft.world.level.block.state.properties.BedPart.HEAD));
            BlockPos bed = helper.absolutePos(footRel);
            check(helper, amman.getRespawnPosition() == null, "setup: no spawn yet");
            hearth.state().setSiegePhase(SiegeRules.Phase.ACTIVE);

            var refused = amman.startSleepInBed(bed);
            check(helper, refused.left().orElse(null) == Player.BedSleepingProblem.OTHER_PROBLEM && !amman.isSleeping(),
                    "a running siege refuses the bed: " + refused);
            check(helper, bed.equals(amman.getRespawnPosition()) && amman.getRespawnDimension() == Level.OVERWORLD,
                    "the refused bed did not become the spawn: " + amman.getRespawnPosition());

            var tooFar = far.startSleepInBed(bed);
            check(helper, tooFar.left().isPresent() && far.getRespawnPosition() == null,
                    "a bed out of reach became the spawn: " + far.getRespawnPosition());

            hearth.state().setSiegePhase(SiegeRules.Phase.IDLE);
            helper.setBlock(footRel.east(), Blocks.AIR);
            helper.setBlock(footRel, Blocks.AIR);
            rig.done();
        } catch (RuntimeException | Error e) {
            rig.cleanup();
            throw e;
        }
    }

    /** Forge's sleep check, asked at the head of {@code ServerPlayer.startSleepInBed}: null allows it. */
    private static Player.BedSleepingProblem sleep(ServerPlayer player, BlockPos bed) {
        return ForgeEventFactory.onPlayerSleepInBed(player, java.util.Optional.of(bed));
    }

    // ---- The trophy -----------------------------------------------------------------------------

    @GameTest(template = T, batch = "siege_trophy")
    public static void theTrophyHangsOnAWallWithItsTier(GameTestHelper helper) {
        ExtrasTestGuard.watch(helper);
        Rig rig = new Rig(helper);
        try {
            ServerLevel level = rig.level;
            ItemStack trophy = SiegeTrophyItem.of(5, 12);
            check(helper, SiegeTrophyItem.tier(trophy) == 5, "the item carries tier V");
            CompoundTag stateTag = trophy.getTagElement(BlockItem.BLOCK_STATE_TAG);
            check(helper, stateTag != null && "5".equals(stateTag.getString(SiegeTrophyBlock.TIER.getName())),
                    "its BlockStateTag sets the tier");

            BlockPos wallRel = new BlockPos(2, 2, 3);
            helper.setBlock(wallRel, Blocks.STONE);
            BlockPos wall = helper.absolutePos(wallRel);
            ServerPlayer amman = rig.player(new BlockPos(2, 2, 1), true, 0.0);
            amman.setItemInHand(InteractionHand.MAIN_HAND, trophy);
            BlockHitResult hit = new BlockHitResult(Vec3.atCenterOf(wall).add(0, 0, -0.5), Direction.NORTH, wall, false);
            InteractionResult placed = trophy.useOn(new UseOnContext(amman, InteractionHand.MAIN_HAND, hit));
            BlockPos at = wall.north();
            BlockState state = level.getBlockState(at);
            check(helper, placed.consumesAction() && state.is(HearthBlocks.SIEGE_TROPHY.get()), "placed on the wall: " + placed);
            check(helper, state.getValue(SiegeTrophyBlock.FACING) == Direction.NORTH, "facing away from its wall");
            check(helper, state.getValue(SiegeTrophyBlock.TIER) == 5, "with the item's tier");
            List<ItemStack> drops = Block.getDrops(state, level, at, null);
            check(helper, drops.size() == 1 && SiegeTrophyItem.tier(drops.get(0)) == 5, "breaking it keeps the tier");
            check(helper, !HearthBlocks.SIEGE_TROPHY.get().defaultBlockState().setValue(SiegeTrophyBlock.FACING, Direction.SOUTH)
                    .canSurvive(level, at), "no wall behind, no trophy");

            level.setBlock(wall, Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL);
            check(helper, level.getBlockState(at).isAir(), "it falls when its wall goes");
            for (ItemEntity item : level.getEntitiesOfClass(ItemEntity.class, new AABB(at).inflate(2))) {
                item.discard();
            }
            rig.done();
        } catch (RuntimeException | Error e) {
            rig.cleanup();
            throw e;
        }
    }
}
