package dev.amman.proficiency.mixin.client;

import dev.amman.proficiency.client.AbilityFx;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Gui;
import net.minecraft.client.gui.GuiGraphics;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * The held item's cooldown sweep and ready glint ({@link AbilityFx#renderHotbarSlot}), drawn at the end of the
 * hotbar's own draw. On 1.21 the HUD layers are stacked 200 apart in depth and the HUD event fires after that
 * stack is popped, so the same fills drawn from the event sat behind the hotbar (NeoForge and Fabric). Not a
 * required mixin: if it fails to apply the slot shows nothing extra, as before.
 */
@Mixin(Gui.class)
public abstract class HotbarSlotFxMixin {

    @Inject(method = "renderItemHotbar", at = @At("TAIL"))
    private void proficiency$slotFx(GuiGraphics graphics, DeltaTracker tracker, CallbackInfo info) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player != null) {
            AbilityFx.renderHotbarSlot(graphics, mc.player);
        }
    }
}
