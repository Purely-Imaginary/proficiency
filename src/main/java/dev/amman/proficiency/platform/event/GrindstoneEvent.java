package dev.amman.proficiency.platform.event;

import dev.amman.proficiency.platform.bus.Event;
import dev.amman.proficiency.platform.bus.ICancellableEvent;
import net.minecraft.world.item.ItemStack;

public abstract class GrindstoneEvent extends Event {

    private final ItemStack top;
    private final ItemStack bottom;
    private int xp;

    protected GrindstoneEvent(ItemStack top, ItemStack bottom, int xp) {
        this.top = top;
        this.bottom = bottom;
        this.xp = xp;
    }

    public ItemStack getTopItem() {
        return top;
    }

    public ItemStack getBottomItem() {
        return bottom;
    }

    public int getXp() {
        return xp;
    }

    public void setXp(int xp) {
        this.xp = xp;
    }

    /** The grindstone output was taken; {@code xp} is what will be dropped (-1 = vanilla amount). */
    public static class OnTakeItem extends GrindstoneEvent implements ICancellableEvent {

        private ItemStack newTop = ItemStack.EMPTY;
        private ItemStack newBottom = ItemStack.EMPTY;

        public OnTakeItem(ItemStack top, ItemStack bottom, int xp) {
            super(top, bottom, xp);
        }

        public ItemStack getNewTopItem() {
            return newTop;
        }

        public ItemStack getNewBottomItem() {
            return newBottom;
        }

        public void setNewTopItem(ItemStack newTop) {
            this.newTop = newTop;
        }

        public void setNewBottomItem(ItemStack newBottom) {
            this.newBottom = newBottom;
        }
    }
}
