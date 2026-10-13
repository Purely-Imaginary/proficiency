package dev.amman.proficiency.hearth.tales;

import dev.amman.proficiency.Proficiency;
import dev.amman.proficiency.platform.bus.EventBusSubscriber;
import dev.amman.proficiency.platform.bus.SubscribeEvent;
import dev.amman.proficiency.platform.event.RegisterCommandsEvent;
import dev.amman.proficiency.platform.event.entity.player.PlayerEvent;
import dev.amman.proficiency.platform.event.server.ServerStartedEvent;
import dev.amman.proficiency.platform.event.server.ServerStoppingEvent;
import dev.amman.proficiency.platform.event.tick.ServerTickEvent;
import net.minecraft.server.level.ServerPlayer;

/**
 * The game events the tales lane listens to on the shared bus (registered from {@code HearthTales.init}):
 * its command, the end of the tick (a bell the player rang), the Chronicle ratings that arrived as a
 * file, and the tale a rated author has not heard yet.
 */
@EventBusSubscriber(modid = Proficiency.MOD_ID)
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
    public static void onServerTick(ServerTickEvent.Post event) {
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
