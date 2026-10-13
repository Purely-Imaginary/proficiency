package dev.amman.proficiency.homebuilder.net;

import dev.amman.proficiency.hearth.HearthAttachments;
import dev.amman.proficiency.hearth.net.HearthNet;
import dev.amman.proficiency.platform.net.PacketDistributor;
import net.minecraft.server.level.ServerPlayer;

import java.util.function.Consumer;

/**
 * Homebuilder's sends and the client seam. Server code sends through here (every send checks the
 * channel, as the rest of the mod does); the loaders' receivers hand a payload to {@link #client()},
 * which the client installs with {@link #setClient} and is a no-op on a dedicated server.
 */
public final class HomebuilderNet {

    private static volatile Consumer<HomebuilderPayload> client = payload -> {
    };

    private HomebuilderNet() {
    }

    public static void send(ServerPlayer player, HomebuilderPayload payload) {
        if (HearthNet.canReceive(player, HomebuilderPayload.TYPE)) {
            PacketDistributor.sendToPlayer(player, payload);
        }
    }

    /** The magnet switch as it stands (login, toggle). */
    public static void sendMagnet(ServerPlayer player) {
        send(player, new HomebuilderPayload(HomebuilderPayload.MAGNET, 0L,
                HearthAttachments.of(player).magnetOff() ? 1 : 0, ""));
    }

    /**
     * The in-game day and the game time Homeward was last used (login, use), so the wheel can say
     * "used today" by the same rule as the server.
     */
    public static void sendHomewardDay(ServerPlayer player) {
        send(player, new HomebuilderPayload(HomebuilderPayload.HOMEWARD_DAY,
                HearthAttachments.of(player).homewardDay(), 0, ""));
        send(player, new HomebuilderPayload(HomebuilderPayload.HOMEWARD_AT,
                HearthAttachments.of(player).mark(dev.amman.proficiency.homebuilder.Homeward.AT_MARK, Long.MIN_VALUE),
                0, ""));
    }

    public static Consumer<HomebuilderPayload> client() {
        return client;
    }

    public static void setClient(Consumer<HomebuilderPayload> handler) {
        client = handler == null ? payload -> {
        } : handler;
    }
}
