package dev.amman.proficiency.platform.event.entity.player;

import dev.amman.proficiency.platform.bus.Event;
import net.fabricmc.fabric.api.util.TriState;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;

/** NeoForge's item pickup events. Only {@link Pre} is posted, from {@code ItemEntityMixin}. */
public abstract class ItemEntityPickupEvent extends Event {

    private final Player player;
    private final ItemEntity item;

    protected ItemEntityPickupEvent(Player player, ItemEntity item) {
        this.player = player;
        this.item = item;
    }

    public Player getPlayer() {
        return player;
    }

    public ItemEntity getItemEntity() {
        return item;
    }

    /**
     * Server side, each tick a player touches an item entity. {@link TriState#FALSE} stops the
     * pickup; the other two leave it to vanilla (NeoForge's TRUE, skipping the pickup delay, is
     * not implemented: no listener uses it).
     */
    public static class Pre extends ItemEntityPickupEvent {

        private TriState canPickup = TriState.DEFAULT;

        public Pre(Player player, ItemEntity item) {
            super(player, item);
        }

        public TriState canPickup() {
            return canPickup;
        }

        public void setCanPickup(TriState state) {
            this.canPickup = state;
        }
    }
}
