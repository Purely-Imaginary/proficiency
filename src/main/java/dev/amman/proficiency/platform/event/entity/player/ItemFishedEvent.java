package dev.amman.proficiency.platform.event.entity.player;

import dev.amman.proficiency.platform.bus.ICancellableEvent;
import net.minecraft.core.NonNullList;
import net.minecraft.world.entity.projectile.FishingHook;
import net.minecraft.world.item.ItemStack;

import java.util.List;

/** A fishing rod reeled in loot. The drops list is live; canceling keeps the loot from spawning. */
public class ItemFishedEvent extends PlayerEvent implements ICancellableEvent {

    private final NonNullList<ItemStack> stacks = NonNullList.create();
    private final FishingHook hook;
    private int rodDamage;

    public ItemFishedEvent(List<ItemStack> stacks, int rodDamage, FishingHook hook) {
        super(hook.getPlayerOwner());
        this.stacks.addAll(stacks);
        this.rodDamage = rodDamage;
        this.hook = hook;
    }

    public int getRodDamage() {
        return rodDamage;
    }

    public void damageRodBy(int rodDamage) {
        this.rodDamage = rodDamage;
    }

    public NonNullList<ItemStack> getDrops() {
        return stacks;
    }

    public FishingHook getHookEntity() {
        return hook;
    }
}
