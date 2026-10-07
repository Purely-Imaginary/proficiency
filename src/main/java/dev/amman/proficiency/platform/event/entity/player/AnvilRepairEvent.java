package dev.amman.proficiency.platform.event.entity.player;

import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

/** The anvil output was taken. {@code breakChance} is the chance the anvil degrades (vanilla 0.12). */
public class AnvilRepairEvent extends PlayerEvent {

    private final ItemStack left;
    private final ItemStack right;
    private final ItemStack output;
    private float breakChance = 0.12f;

    public AnvilRepairEvent(Player player, ItemStack left, ItemStack right, ItemStack output) {
        super(player);
        this.output = output;
        this.left = left;
        this.right = right;
    }

    public ItemStack getOutput() {
        return output;
    }

    public ItemStack getLeft() {
        return left;
    }

    public ItemStack getRight() {
        return right;
    }

    public float getBreakChance() {
        return breakChance;
    }

    public void setBreakChance(float breakChance) {
        this.breakChance = breakChance;
    }
}
