package dev.amman.proficiency.mixin;

import net.minecraft.world.entity.projectile.AbstractArrow;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

/** 1.21's {@code getPickupItemStackOrigin}: the stack a picked-up arrow gives back. */
@Mixin(AbstractArrow.class)
public interface AbstractArrowInvoker {

    @Invoker("getPickupItem")
    ItemStack proficiency$getPickupItem();
}
