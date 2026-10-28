package dev.amman.proficiency.mixin.client;

import dev.amman.proficiency.client.SynergySlam;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * The synergy slam over an open screen (1.45.0): a click in the talent tree is exactly when a synergy wakes,
 * and the HUD is under the screen then. Drawn after the screen and its tooltip, never taking input. Not a
 * required mixin.
 */
@Mixin(Screen.class)
public abstract class ScreenSlamMixin {

    @Inject(method = "renderWithTooltip", at = @At("TAIL"))
    private void proficiency$slamOver(GuiGraphics graphics, int mouseX, int mouseY, float partialTick, CallbackInfo info) {
        SynergySlam.overScreen(graphics);
    }
}
