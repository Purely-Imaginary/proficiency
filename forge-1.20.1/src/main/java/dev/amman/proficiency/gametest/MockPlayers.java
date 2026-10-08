package dev.amman.proficiency.gametest;

import com.mojang.authlib.GameProfile;
import io.netty.channel.embedded.EmbeddedChannel;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.Connection;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.server.level.ServerPlayer;

import java.util.UUID;

/**
 * Vanilla's {@code GameTestHelper.makeMockServerPlayerInLevel}, made to work on Forge 1.20.1.
 *
 * <p>Forge's {@code PlayerList.placeNewPlayer} calls {@code NetworkHooks.sendMCRegistryPackets},
 * which reads the connection's netty pipeline, and vanilla's mock connection has no channel, so
 * every mock player threw a NullPointerException there. This is the same player (same name, same
 * "creative" answer from {@code isCreative()}, the trap the README records), on a connection that
 * has an in-memory netty channel. Nothing reads from that channel; it only exists to be asked.
 */
final class MockPlayers {

    private MockPlayers() {
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
        return player;
    }
}
