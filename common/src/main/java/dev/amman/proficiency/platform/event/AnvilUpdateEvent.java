package dev.amman.proficiency.platform.event;

import dev.amman.proficiency.platform.bus.Event;
import dev.amman.proficiency.platform.bus.ICancellableEvent;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

/**
 * {@code AnvilMenu.createResult}, before vanilla computes anything. Setting an output replaces the
 * vanilla result entirely; canceling empties the output.
 */
public class AnvilUpdateEvent extends Event implements ICancellableEvent {

    private final ItemStack left;
    private final ItemStack right;
    private final String name;
    private final Player player;
    private ItemStack output;
    private long cost;
    private int materialCost;

    public AnvilUpdateEvent(ItemStack left, ItemStack right, String name, long cost, Player player) {
        this.left = left;
        this.right = right;
        this.output = ItemStack.EMPTY;
        this.name = name;
        this.player = player;
        this.setCost(cost);
        this.setMaterialCost(0);
    }

    public ItemStack getLeft() {
        return left;
    }

    public ItemStack getRight() {
        return right;
    }

    public String getName() {
        return name;
    }

    public ItemStack getOutput() {
        return output;
    }

    public void setOutput(ItemStack output) {
        this.output = output;
    }

    public long getCost() {
        return cost;
    }

    public void setCost(long cost) {
        this.cost = cost;
    }

    public int getMaterialCost() {
        return materialCost;
    }

    public void setMaterialCost(int materialCost) {
        this.materialCost = materialCost;
    }

    public Player getPlayer() {
        return player;
    }
}
