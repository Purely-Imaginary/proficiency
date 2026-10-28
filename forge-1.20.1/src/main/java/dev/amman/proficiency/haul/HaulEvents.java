package dev.amman.proficiency.haul;

import dev.amman.proficiency.Proficiency;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/** Today's Haul: load the saved log at login (and say hello), forget it at logout, carry it across a respawn. */
@Mod.EventBusSubscriber(modid = Proficiency.MOD_ID)
public final class HaulEvents {

    private HaulEvents() {
    }

    @SubscribeEvent
    public static void onLogin(PlayerEvent.PlayerLoggedInEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            HaulService.onLogin(player);
        }
    }

    @SubscribeEvent
    public static void onLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        HaulService.onLogout(event.getEntity().getUUID());
    }

    @SubscribeEvent
    public static void onClone(PlayerEvent.Clone event) {
        HaulService.onClone(event.getOriginal(), event.getEntity());
    }
}
