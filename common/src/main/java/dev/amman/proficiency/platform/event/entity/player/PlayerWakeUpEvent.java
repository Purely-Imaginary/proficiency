package dev.amman.proficiency.platform.event.entity.player;

import net.minecraft.world.entity.player.Player;

/**
 * A player is waking up, before vanilla resets the sleep timer: {@code isSleepingLongEnough()} is
 * still true after a real sleep (5 s in bed, the point vanilla counts the player as asleep). Both
 * sides. NeoForge source: its own {@code PlayerWakeUpEvent}. Fabric: the effects lane's
 * {@code HearthWakeUpMixin} posts it at the head of {@code Player.stopSleepInBed}.
 */
public class PlayerWakeUpEvent extends PlayerEvent {

    private final boolean wakeImmediately;
    private final boolean updateLevel;

    public PlayerWakeUpEvent(Player player, boolean wakeImmediately, boolean updateLevel) {
        super(player);
        this.wakeImmediately = wakeImmediately;
        this.updateLevel = updateLevel;
    }

    public boolean wakeImmediately() {
        return wakeImmediately;
    }

    public boolean updateLevel() {
        return updateLevel;
    }
}
