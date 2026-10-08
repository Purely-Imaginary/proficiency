package dev.amman.proficiency.platform.event.tick;

import dev.amman.proficiency.platform.event.entity.player.PlayerEvent;
import net.minecraft.world.entity.player.Player;

/** {@code Player.tick}, on both logical sides, as on NeoForge. */
public abstract class PlayerTickEvent extends PlayerEvent {

    protected PlayerTickEvent(Player player) {
        super(player);
    }

    public static class Pre extends PlayerTickEvent {
        public Pre(Player player) {
            super(player);
        }
    }

    public static class Post extends PlayerTickEvent {
        public Post(Player player) {
            super(player);
        }
    }
}
