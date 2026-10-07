package dev.amman.proficiency.mixin;

import net.minecraft.world.inventory.AnvilMenu;
import net.minecraft.world.inventory.DataSlot;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/** NeoForge adds {@code AnvilMenu.setMaximumCost}; vanilla keeps the slot private. */
@Mixin(AnvilMenu.class)
public interface AnvilMenuAccessor {

    @Accessor("cost")
    DataSlot proficiency$cost();
}
