package dev.amman.proficiency.mixin.client;

import dev.amman.proficiency.client.SynergySlam;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.GuiGraphics;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * The synergy slam's 3 px shake (1.45.0): the whole HUD shifts for a moment as the icons hit. A pose pushed
 * at the start of {@code Gui.render} and popped at its end, only while the shake runs. Not a required mixin: if
 * it fails to apply the slam simply does not shake.
 */
@Mixin(Gui.class)
public abstract class GuiSlamShakeMixin {

    @Unique
    private boolean proficiency$shaking;

    @Inject(method = "render", at = @At("HEAD"))
    private void proficiency$shakeIn(GuiGraphics graphics, DeltaTracker tracker, CallbackInfo info) {
        proficiency$shaking = false;
        int x = SynergySlam.shakeX();
        int y = SynergySlam.shakeY();
        if (x != 0 || y != 0) {
            graphics.pose().pushPose();
            graphics.pose().translate(x, y, 0);
            proficiency$shaking = true;
        }
    }

    @Inject(method = "render", at = @At("RETURN"))
    private void proficiency$shakeOut(GuiGraphics graphics, DeltaTracker tracker, CallbackInfo info) {
        if (proficiency$shaking) {
            graphics.pose().popPose();
            proficiency$shaking = false;
        }
    }
}
