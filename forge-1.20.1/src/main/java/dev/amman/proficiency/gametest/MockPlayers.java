package dev.amman.proficiency.gametest;

import com.mojang.authlib.GameProfile;
import dev.amman.proficiency.Proficiency;
import io.netty.channel.embedded.EmbeddedChannel;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.gametest.framework.GameTestInfo;
import net.minecraft.gametest.framework.GameTestListener;
import net.minecraft.network.Connection;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.TickTask;
import net.minecraft.server.level.ServerPlayer;

import java.lang.reflect.Field;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Vanilla's {@code GameTestHelper.makeMockServerPlayerInLevel}, made to work on Forge 1.20.1.
 *
 * <p>Forge's {@code PlayerList.placeNewPlayer} calls {@code NetworkHooks.sendMCRegistryPackets},
 * which reads the connection's netty pipeline, and vanilla's mock connection has no channel, so
 * every mock player threw a NullPointerException there. This is the same player (same name, same
 * "creative" answer from {@code isCreative()}, the trap the README records), on a connection that
 * has an in-memory netty channel. Nothing reads from that channel; it only exists to be asked.
 *
 * <p>A mock player leaves when the test that made it ends, passed or failed (fix round, 2026-10-10).
 * Vanilla's never leaves: it stays where the server spawned it (the world spawn, give or take the
 * spawn radius) for the rest of the run, so every test that read XP met more and more strangers
 * there, and a Social company or mentor bonus reached some players and skills and not others. Two
 * players of one test can still meet the players of the tests running beside it in the same batch:
 * a test that compares what two players or two skills earned moves them out of the crowd first
 * ({@link #isolate}).
 */
final class MockPlayers {

    /** {@link #isolate}'s spots, each far from the batch and from every other spot. */
    private static final AtomicInteger SPOTS = new AtomicInteger();
    private static final Field TEST_INFO = testInfoField();

    private MockPlayers() {
    }

    private static Field testInfoField() {
        try {
            Field field = GameTestHelper.class.getDeclaredField("testInfo");
            field.setAccessible(true);
            return field;
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException("GameTestHelper.testInfo is gone: mock players need it to leave", e);
        }
    }

    static ServerPlayer make(GameTestHelper helper) {
        var level = helper.getLevel();
        ServerPlayer player = new ServerPlayer(level.getServer(), level,
                new GameProfile(UUID.randomUUID(), "test-mock-player")) {
            @Override
            public boolean isSpectator() {
                return false;
            }

            @Override
            public boolean isCreative() {
                return true;
            }
        };
        Connection connection = new Connection(PacketFlow.SERVERBOUND);
        new EmbeddedChannel(connection);
        level.getServer().getPlayerList().placeNewPlayer(connection, player);
        leaveWithTheTest(helper, level.getServer(), player.getUUID());
        return player;
    }

    /**
     * Moves these players, together, to a spot of their own: far from the world spawn where the batch's
     * mock players stand and from every other isolated group, so no one else's company or mentor bonus
     * reaches them. Give each player its own call when the players of one test must not keep each other
     * company either. Nothing but the position changes: a mock player's chunks do not follow it.
     */
    static void isolate(ServerPlayer... players) {
        double x = 3_000_000.0 + SPOTS.getAndIncrement() * 4_096.0;
        for (ServerPlayer player : players) {
            player.setPos(x, 80.0, 3_000_000.0);
        }
    }

    /**
     * Logs the player out once its test has passed or failed, on the server's next pass through its task
     * queue rather than inside the test's last tick, which may still be using it. A player the test (or a
     * test guard) already logged out is left alone; a respawned copy under the same id goes too.
     */
    private static void leaveWithTheTest(GameTestHelper helper, MinecraftServer server, UUID id) {
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
                leave(test);
            }

            @Override
            public void testFailed(GameTestInfo test) {
                leave(test);
            }

            private void leave(GameTestInfo test) {
                server.tell(new TickTask(server.getTickCount(), () -> {
                    ServerPlayer still = server.getPlayerList().getPlayer(id);
                    if (still == null) {
                        return;
                    }
                    try {
                        server.getPlayerList().remove(still);
                    } catch (RuntimeException e) {
                        Proficiency.LOG.error("GameTest: the mock player of {} did not leave", test.getTestName(), e);
                    }
                }));
            }
        });
    }
}
