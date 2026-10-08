package dev.amman.proficiency.platform.event.entity.player;

import dev.amman.proficiency.platform.bus.ICancellableEvent;
import dev.amman.proficiency.platform.event.entity.living.LivingEvent;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.Container;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;

import java.util.Optional;

public abstract class PlayerEvent extends LivingEvent {

    private final Player player;

    protected PlayerEvent(Player player) {
        super(player);
        this.player = player;
    }

    @Override
    public Player getEntity() {
        return player;
    }

    /** {@code Player.getDestroySpeed}, both sides. Canceling means the block cannot be broken. */
    public static class BreakSpeed extends PlayerEvent implements ICancellableEvent {

        private final BlockState state;
        private final float originalSpeed;
        private float newSpeed;
        private final Optional<BlockPos> pos;

        public BreakSpeed(Player player, BlockState state, float original, @Nullable BlockPos pos) {
            super(player);
            this.state = state;
            this.originalSpeed = original;
            this.newSpeed = original;
            this.pos = Optional.ofNullable(pos);
        }

        public BlockState getState() {
            return state;
        }

        public float getOriginalSpeed() {
            return originalSpeed;
        }

        public float getNewSpeed() {
            return newSpeed;
        }

        public void setNewSpeed(float newSpeed) {
            this.newSpeed = newSpeed;
        }

        public Optional<BlockPos> getPosition() {
            return pos;
        }
    }

    /** The name shown for this player in the tab list; null means the default. */
    public static class TabListNameFormat extends PlayerEvent {

        @Nullable
        private Component displayName;

        public TabListNameFormat(Player player) {
            super(player);
        }

        @Nullable
        public Component getDisplayName() {
            return displayName;
        }

        public void setDisplayName(@Nullable Component displayName) {
            this.displayName = displayName;
        }
    }

    /** A new player object replaced an old one: death respawn, or leaving the End. */
    public static class Clone extends PlayerEvent {

        private final Player original;
        private final boolean wasDeath;

        public Clone(Player _new, Player oldPlayer, boolean wasDeath) {
            super(_new);
            this.original = oldPlayer;
            this.wasDeath = wasDeath;
        }

        public Player getOriginal() {
            return original;
        }

        public boolean isWasDeath() {
            return wasDeath;
        }
    }

    public static class ItemCraftedEvent extends PlayerEvent {

        private final ItemStack crafting;
        private final Container craftMatrix;

        public ItemCraftedEvent(Player player, ItemStack crafting, Container craftMatrix) {
            super(player);
            this.crafting = crafting;
            this.craftMatrix = craftMatrix;
        }

        public ItemStack getCrafting() {
            return crafting;
        }

        public Container getInventory() {
            return craftMatrix;
        }
    }

    public static class ItemSmeltedEvent extends PlayerEvent {

        private final ItemStack smelting;

        public ItemSmeltedEvent(Player player, ItemStack crafting) {
            super(player);
            this.smelting = crafting;
        }

        public ItemStack getSmelting() {
            return smelting;
        }
    }

    public static class PlayerLoggedInEvent extends PlayerEvent {
        public PlayerLoggedInEvent(Player player) {
            super(player);
        }
    }

    public static class PlayerLoggedOutEvent extends PlayerEvent {
        public PlayerLoggedOutEvent(Player player) {
            super(player);
        }
    }

    public static class PlayerRespawnEvent extends PlayerEvent {

        private final boolean endConquered;

        public PlayerRespawnEvent(Player player, boolean endConquered) {
            super(player);
            this.endConquered = endConquered;
        }

        public boolean isEndConquered() {
            return endConquered;
        }
    }

    public static class PlayerChangedDimensionEvent extends PlayerEvent {

        private final ResourceKey<Level> fromDim;
        private final ResourceKey<Level> toDim;

        public PlayerChangedDimensionEvent(Player player, ResourceKey<Level> fromDim, ResourceKey<Level> toDim) {
            super(player);
            this.fromDim = fromDim;
            this.toDim = toDim;
        }

        public ResourceKey<Level> getFrom() {
            return fromDim;
        }

        public ResourceKey<Level> getTo() {
            return toDim;
        }
    }
}
