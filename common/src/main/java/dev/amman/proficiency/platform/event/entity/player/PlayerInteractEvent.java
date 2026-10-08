package dev.amman.proficiency.platform.event.entity.player;

import dev.amman.proficiency.platform.bus.ICancellableEvent;
import net.minecraft.core.BlockPos;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

public abstract class PlayerInteractEvent extends PlayerEvent {

    private final InteractionHand hand;
    private final BlockPos pos;

    protected PlayerInteractEvent(Player player, InteractionHand hand, BlockPos pos) {
        super(player);
        this.hand = hand;
        this.pos = pos;
    }

    public InteractionHand getHand() {
        return hand;
    }

    public ItemStack getItemStack() {
        return getEntity().getItemInHand(hand);
    }

    public BlockPos getPos() {
        return pos;
    }

    public Level getLevel() {
        return getEntity().level();
    }

    /** A right click with an item in the air (not on a block or entity), both sides. */
    public static class RightClickItem extends PlayerInteractEvent implements ICancellableEvent {
        public RightClickItem(Player player, InteractionHand hand) {
            super(player, hand, player.blockPosition());
        }
    }

    /**
     * A right click on a block, both sides, before the block or the item gets it. Canceling hands
     * back {@link #getCancellationResult()} and neither the block nor the item is used.
     * Fabric source: {@code UseBlockCallback}, which fires at the same point NeoForge posts this.
     */
    public static class RightClickBlock extends PlayerInteractEvent implements ICancellableEvent {

        private InteractionResult cancellationResult = InteractionResult.PASS;

        public RightClickBlock(Player player, InteractionHand hand, BlockPos pos) {
            super(player, hand, pos);
        }

        public InteractionResult getCancellationResult() {
            return cancellationResult;
        }

        public void setCancellationResult(InteractionResult result) {
            this.cancellationResult = result;
        }
    }
}
