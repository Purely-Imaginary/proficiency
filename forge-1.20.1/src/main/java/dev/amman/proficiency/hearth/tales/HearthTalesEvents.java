package dev.amman.proficiency.hearth.tales;

import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.server.ServerStartedEvent;
import net.minecraftforge.event.server.ServerStoppingEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraft.server.level.ServerPlayer;

/**
 * The game events the tales lane listens to on Forge's bus (registered from {@code HearthTales.init}):
 * its command, the end of the tick (a bell the player rang), the Chronicle ratings that arrived as a
 * file, and the tale a rated author has not heard yet.
 */
public final class HearthTalesEvents {

    /** Ticks between looks at the ratings file: once a minute. */
    private static final int QUEUE_TICKS = 1200;

    private static int ticks;

    private HearthTalesEvents() {
    }

    @SubscribeEvent
    public static void onRegisterCommands(RegisterCommandsEvent event) {
        event.getDispatcher().register(ChronicleCommand.node());
    }

    @SubscribeEvent
    public static void onServerStarted(ServerStartedEvent event) {
        HearthChronicle.drainQueue(event.getServer());
    }

    @SubscribeEvent
    public static void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END) {
            return;
        }
        HearthBell.tick(event.getServer());
        if (++ticks >= QUEUE_TICKS) {
            ticks = 0;
            HearthChronicle.drainQueue(event.getServer());
        }
    }

    @SubscribeEvent
    public static void onLogin(PlayerEvent.PlayerLoggedInEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            HearthChronicle.announcePending(player);
        }
    }

    @SubscribeEvent
    public static void onServerStopping(ServerStoppingEvent event) {
        HearthBell.forget();
        HearthChronicle.forget();
        TalesBank.forget();
    }
}
