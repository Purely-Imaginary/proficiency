package dev.amman.proficiency.mixin;

import net.minecraft.world.Container;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.ItemCombinerMenu;
import net.minecraft.world.inventory.ResultContainer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(ItemCombinerMenu.class)
public interface ItemCombinerMenuAccessor {

    @Accessor("resultSlots")
    ResultContainer proficiency$resultSlots();

    @Accessor("inputSlots")
    Container proficiency$inputSlots();

    @Accessor("player")
    Player proficiency$player();
}
