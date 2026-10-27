package dev.amman.proficiency.gametest;

import com.mojang.authlib.GameProfile;
import io.netty.channel.embedded.EmbeddedChannel;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.network.Connection;
import net.minecraft.network.protocol.PacketFlow;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.network.CommonListenerCookie;

import java.util.UUID;

/**
 * The one place GameTests make a mock {@link ServerPlayer}. It is vanilla's
 * {@code GameTestHelper.makeMockServerPlayerInLevel} (deprecated for removal in 1.21.1), copied so
 * the call sites do not warn and do not break when vanilla drops it. {@code makeMockPlayer(GameType)}
 * is no replacement: it returns a plain {@code Player}, not a {@code ServerPlayer} in the player list.
 * Same player as before: name "test-mock-player", creative, not a spectator.
 */
public final class MockPlayers {

    private MockPlayers() {
    }

    /** True for a player made by {@link #make} or by vanilla's own (still deprecated) mock-player method. */
    public static boolean isMock(ServerPlayer player) {
        String name = player.getClass().getName();
        return name.startsWith(MockPlayers.class.getName() + "$")
                || name.startsWith(GameTestHelper.class.getName() + "$");
    }

    public static ServerPlayer make(GameTestHelper helper) {
        var level = helper.getLevel();
        CommonListenerCookie cookie = CommonListenerCookie.createInitial(
                new GameProfile(UUID.randomUUID(), "test-mock-player"), false);
        ServerPlayer player = new ServerPlayer(level.getServer(), level, cookie.gameProfile(),
                cookie.clientInformation()) {
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
        level.getServer().getPlayerList().placeNewPlayer(connection, player, cookie);
        return player;
    }
}
