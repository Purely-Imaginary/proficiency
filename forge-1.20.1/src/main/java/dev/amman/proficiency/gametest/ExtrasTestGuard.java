package dev.amman.proficiency.gametest;

import dev.amman.proficiency.Proficiency;
import dev.amman.proficiency.hearth.HearthBlockEntity;
import dev.amman.proficiency.hearth.HearthBlocks;
import dev.amman.proficiency.hearth.HearthIndexData;
import dev.amman.proficiency.hearth.HearthPos;
import dev.amman.proficiency.hearth.SiegeRules;
import dev.amman.proficiency.hearth.sieges.SiegeService;
import dev.amman.proficiency.skill.SpawnOrigin;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.gametest.framework.GameTestInfo;
import net.minecraft.gametest.framework.GameTestListener;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.AABB;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.WeakHashMap;

/**
 * The forge-extras lane's part of common's {@code HearthTestGuard} on Forge 1.20.1: every siege, tales
 * and Homebuilder GameTest leaves the world as it found it, passed or failed. When the test ends it
 * takes away every Hearth within {@link #HEARTH_REACH} blocks (a running or pending siege is cancelled
 * first, and the Hearth leaves {@link SiegeService#MANUAL}) and the mock players the test made after it
 * was watched. A Hearth left behind would flag the next tests' players as home and refuse their
 * lightings within its spacing.
 *
 * <p>It also takes away every siege mob ({@link SpawnOrigin#isSiege}) within {@link #MOB_REACH} blocks:
 * {@code SiegeService.cancel} sends away the mobs of the roster it knows, but a test that failed mid-wave,
 * or tagged a mob by hand, can leave one that never despawns (siege mobs are persistent) and would wander
 * into a later cell. These tests each run in a batch of their own, so a siege mob that close is this
 * test's. {@link #atEnd} runs a test's own cleanup at the same moment (the siege rig puts its arena back
 * there), so a check that fails in a later tick, or a timeout, cleans up too (fix round, 2026-10-10).
 *
 * <p>Common's guard also clears comfort-marked blocks; that is forge-server's {@code HearthTestGuard} on
 * this line, and no siege, tales or Homebuilder test marks comfort.
 *
 * <p>Differences from 1.21.1: 1.20.1's {@link GameTestListener} has no runner argument and
 * {@code GameTestHelper} has no public bounds, so "the structure" is the cell at the test's origin
 * (the {@code empty} template is a few blocks wide; the 32-block reach covers it). Forge's mock players
 * are {@code MockPlayers}' anonymous subclass.
 */
final class ExtrasTestGuard {

    /** How far around the test's origin a Hearth counts as this test's (Hearth tests run alone in their batch). */
    static final int HEARTH_REACH = 40;
    /** How far around the test's origin a siege mob counts as this test's: the wave ring and a walk past it. */
    static final int MOB_REACH = 96;

    private static final Set<GameTestInfo> WATCHED = Collections.newSetFromMap(new WeakHashMap<>());
    private static final Field TEST_INFO = testInfoField();

    private ExtrasTestGuard() {
    }

    private static Field testInfoField() {
        try {
            Field field = GameTestHelper.class.getDeclaredField("testInfo");
            field.setAccessible(true);
            return field;
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException("GameTestHelper.testInfo is gone: the test guard needs it", e);
        }
    }

    /** Puts the world back when this test ends, passed or failed. Safe to call more than once. */
    static void watch(GameTestHelper helper) {
        GameTestInfo info;
        try {
            info = (GameTestInfo) TEST_INFO.get(helper);
        } catch (IllegalAccessException e) {
            throw new IllegalStateException("cannot read GameTestHelper.testInfo", e);
        }
        synchronized (WATCHED) {
            if (!WATCHED.add(info)) {
                return;
            }
        }
        ServerLevel level = helper.getLevel();
        BlockPos origin = helper.absolutePos(BlockPos.ZERO);
        Set<UUID> before = new HashSet<>();
        for (ServerPlayer player : level.getServer().getPlayerList().getPlayers()) {
            before.add(player.getUUID());
        }
        info.addListener(new GameTestListener() {
            @Override
            public void testStructureLoaded(GameTestInfo test) {
            }

            @Override
            public void testPassed(GameTestInfo test) {
                sweep(level, origin, before, test.getTestName());
            }

            @Override
            public void testFailed(GameTestInfo test) {
                sweep(level, origin, before, test.getTestName());
            }
        });
    }

    /**
     * Runs {@code cleanup} when this test ends, passed or failed (a timeout too), after {@link #watch}'s
     * sweep when the test called {@link #watch} first. The cleanup must be safe to run twice: a test that
     * cleans up on its own way out has already run it.
     */
    static void atEnd(GameTestHelper helper, Runnable cleanup) {
        GameTestInfo info;
        try {
            info = (GameTestInfo) TEST_INFO.get(helper);
        } catch (IllegalAccessException e) {
            throw new IllegalStateException("cannot read GameTestHelper.testInfo", e);
        }
        info.addListener(new GameTestListener() {
            @Override
            public void testStructureLoaded(GameTestInfo test) {
            }

            @Override
            public void testPassed(GameTestInfo test) {
                run(test);
            }

            @Override
            public void testFailed(GameTestInfo test) {
                run(test);
            }

            private void run(GameTestInfo test) {
                try {
                    cleanup.run();
                } catch (RuntimeException e) {
                    Proficiency.LOG.error("Hearth test guard: the cleanup of {} failed", test.getTestName(), e);
                }
            }
        });
    }

    /** What {@link #watch} does at the end. */
    static void sweep(ServerLevel level, BlockPos origin, Set<UUID> keepPlayers, String test) {
        int hearths = 0;
        int mobs = 0;
        try {
            hearths = removeHearths(level, origin);
            mobs = removeSiegeMobs(level, origin);
        } catch (RuntimeException e) {
            Proficiency.LOG.error("Hearth test guard: cleaning up after {} failed", test, e);
        }
        int players = 0;
        for (ServerPlayer player : List.copyOf(level.getServer().getPlayerList().getPlayers())) {
            if (!keepPlayers.contains(player.getUUID())
                    && player.getClass().getName().startsWith(MockPlayers.class.getName() + "$")) {
                player.server.getPlayerList().remove(player);
                players++;
            }
        }
        if (hearths + mobs > 0) {
            // A test that tidies up after itself leaves nothing for the guard; this names one that did not.
            Proficiency.LOG.info("Hearth test guard after {}: took away {} Hearths, {} siege mobs, {} mock players",
                    test, hearths, mobs, players);
        }
    }

    private static int removeSiegeMobs(ServerLevel level, BlockPos origin) {
        AABB reach = new AABB(origin).inflate(MOB_REACH, 48, MOB_REACH);
        List<Entity> found = level.getEntitiesOfClass(Entity.class, reach, SpawnOrigin::isSiege);
        for (Entity mob : found) {
            mob.discard();
        }
        return found.size();
    }

    private static int removeHearths(ServerLevel level, BlockPos origin) {
        AABB reach = new AABB(origin).inflate(HEARTH_REACH, 24, HEARTH_REACH);
        List<BlockPos> found = new ArrayList<>();
        for (long packed : HearthIndexData.get(level).store().all().toLongArray()) {
            int x = HearthPos.unpackX(packed);
            int y = HearthPos.unpackY(packed);
            int z = HearthPos.unpackZ(packed);
            if (reach.contains(x + 0.5, y + 0.5, z + 0.5)) {
                found.add(new BlockPos(x, y, z));
            }
        }
        for (BlockPos pos : found) {
            if (level.getBlockEntity(pos) instanceof HearthBlockEntity hearth
                    && hearth.state().siegePhase() != SiegeRules.Phase.IDLE) {
                SiegeService.cancel(level, pos, hearth, level.getGameTime());
            }
            SiegeService.MANUAL.remove(HearthBlockEntity.key(level, pos));
            if (level.getBlockState(pos).is(HearthBlocks.HEARTH.get())) {
                level.setBlock(pos, Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL);
            }
            HearthIndexData.forget(level, pos);
        }
        return found.size();
    }
}
