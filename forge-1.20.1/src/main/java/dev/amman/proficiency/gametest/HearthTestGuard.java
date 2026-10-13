package dev.amman.proficiency.gametest;

import dev.amman.proficiency.Proficiency;
import dev.amman.proficiency.hearth.HearthBlockEntity;
import dev.amman.proficiency.hearth.HearthBlocks;
import dev.amman.proficiency.hearth.HearthIndexData;
import dev.amman.proficiency.hearth.HearthPos;
import dev.amman.proficiency.hearth.SiegeRules;
import dev.amman.proficiency.hearth.sieges.SiegeService;
import dev.amman.proficiency.skill.PlacedBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.gametest.framework.GameTestInfo;
import net.minecraft.gametest.framework.GameTestListener;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.WeakHashMap;

/**
 * Every Hearth GameTest leaves the world as it found it, passed or failed (integration, 2026-10-09).
 * A GameTest's structure stays standing when it ends, and the cells sit a few blocks apart, so a
 * Hearth left behind kept ticking: it flagged the players of later tests as "home" (mirror, streak
 * hold, Homebuilder share), refused their lightings within its spacing and counted their furniture.
 *
 * <p>{@link #watch} hooks the test's own end (vanilla's {@link GameTestListener}, reached through the
 * helper's private {@code GameTestInfo} field, found by type). When the test passes or fails it puts
 * back, in this order:
 * <ul>
 * <li>every Hearth within {@link #HEARTH_REACH} blocks (a lit one ends its siege first) and its
 * index entry: Hearth tests run alone in their batch, so a Hearth that close is this test's or a
 * leftover of one that ran before;</li>
 * <li>every comfort-marked block inside the structure (and one block around it): the mark, and the
 * block itself while it is still the one placed;</li>
 * <li>the mock players the test made after it was watched.</li>
 * </ul>
 * The fixtures call {@link #watch} first thing, before they make a player.
 *
 * <p>Forge 1.20.1 copy of common's guard: 1.20.1's {@link GameTestListener} has three methods (no runner,
 * no rerun), the structure box comes from the test info ({@code GameTestHelper.getBounds} is private), and
 * the mock players are {@link MockPlayers}' own class.
 */
final class HearthTestGuard {

    /** How far around the structure a Hearth counts as this test's (the spacing test lights one 23 out). */
    static final int HEARTH_REACH = 32;
    /** A comfort-marked block this close outside the structure is this test's too. */
    private static final int MARK_MARGIN = 1;

    private static final Set<GameTestInfo> WATCHED = Collections.newSetFromMap(new WeakHashMap<>());
    private static final Field TEST_INFO = testInfoField();

    private HearthTestGuard() {
    }

    /**
     * The helper's {@code GameTestInfo} field, found by its type: Mojang names it {@code testInfo}, but
     * under SRG names (a reobfuscated run) it is {@code f_NNNN_}, and this runs in a static initialiser,
     * where a miss would fail every Hearth test before it starts. Common's guard does the same.
     */
    private static Field testInfoField() {
        Field found = null;
        for (Field field : GameTestHelper.class.getDeclaredFields()) {
            if (field.getType() == GameTestInfo.class && !Modifier.isStatic(field.getModifiers())) {
                if (found != null) {
                    throw new IllegalStateException("GameTestHelper has two GameTestInfo fields ("
                            + found.getName() + ", " + field.getName() + "): the Hearth test guard cannot tell which");
                }
                found = field;
            }
        }
        if (found == null) {
            throw new IllegalStateException("GameTestHelper holds no GameTestInfo: the Hearth test guard needs it");
        }
        found.setAccessible(true);
        return found;
    }

    /** The test's structure box (1.20.1's {@code GameTestHelper.getBounds} is private). */
    static AABB bounds(GameTestHelper helper) {
        try {
            return ((GameTestInfo) TEST_INFO.get(helper)).getStructureBounds();
        } catch (IllegalAccessException e) {
            throw new IllegalStateException("cannot read the GameTestInfo of the GameTestHelper", e);
        }
    }

    /** Puts the world back when this test ends, passed or failed. Safe to call more than once. */
    static void watch(GameTestHelper helper) {
        GameTestInfo info;
        try {
            info = (GameTestInfo) TEST_INFO.get(helper);
        } catch (IllegalAccessException e) {
            throw new IllegalStateException("cannot read the GameTestInfo of the GameTestHelper", e);
        }
        synchronized (WATCHED) {
            if (!WATCHED.add(info)) {
                return;
            }
        }
        ServerLevel level = helper.getLevel();
        AABB bounds = info.getStructureBounds();
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
                sweep(level, bounds, before, test.getTestName());
            }

            @Override
            public void testFailed(GameTestInfo test) {
                sweep(level, bounds, before, test.getTestName());
            }
        });
    }

    /** What {@link #watch} does at the end; also callable from a test that wants it now. */
    static void sweep(ServerLevel level, AABB bounds, Set<UUID> keepPlayers, String test) {
        int hearths = 0;
        int marks = 0;
        try {
            hearths = removeHearths(level, bounds);
            marks = removeMarks(level, bounds);
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
        if (hearths + marks > 0) {
            // A test that tidies up after itself leaves nothing for the guard; this names one that did not.
            Proficiency.LOG.info("Hearth test guard after {}: took away {} Hearths, {} marked blocks, {} mock players",
                    test, hearths, marks, players);
        }
    }

    private static int removeHearths(ServerLevel level, AABB bounds) {
        AABB reach = bounds.inflate(HEARTH_REACH, 16, HEARTH_REACH);
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

    private static int removeMarks(ServerLevel level, AABB bounds) {
        AABB box = bounds.inflate(MARK_MARGIN);
        int minY = (int) Math.floor(box.minY);
        int maxY = (int) Math.ceil(box.maxY);
        List<long[]> marked = new ArrayList<>();
        for (int cx = (int) Math.floor(box.minX) >> 4; cx <= (int) Math.floor(box.maxX) >> 4; cx++) {
            for (int cz = (int) Math.floor(box.minZ) >> 4; cz <= (int) Math.floor(box.maxZ) >> 4; cz++) {
                PlacedBlocks.forEachPlaced(level, cx, cz, minY, maxY, (x, y, z, hash, comfortMark) -> {
                    if (comfortMark && box.contains(x + 0.5, y + 0.5, z + 0.5)) {
                        marked.add(new long[] {x, y, z, hash});
                    }
                });
            }
        }
        for (long[] m : marked) {
            BlockPos pos = new BlockPos((int) m[0], (int) m[1], (int) m[2]);
            PlacedBlocks.clearComfort(level, pos);
            BlockState there = level.getBlockState(pos);
            if (!there.isAir() && PlacedBlocks.hash(there) == (int) m[3]) {
                PlacedBlocks.consume(level, pos, there);
                level.setBlock(pos, Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL);
            }
        }
        return marked.size();
    }
}
