package dev.amman.proficiency.mixin.client;

import net.minecraft.advancements.Advancement;
import net.minecraft.client.gui.components.toasts.AdvancementToast;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/** Reads which advancement a toast shows, so a challenge can get the capstone flare ({@code AdvancementFx}). */
@Mixin(AdvancementToast.class)
public interface AdvancementToastAccessor {

    @Accessor("advancement")
    Advancement proficiency$advancement();
}
