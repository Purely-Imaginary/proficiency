package dev.amman.proficiency.mixin.client;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import dev.amman.proficiency.client.AdvancementFx;
import net.minecraft.advancements.AdvancementType;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.toasts.AdvancementToast;
import net.minecraft.client.gui.components.toasts.Toast;
import net.minecraft.client.gui.components.toasts.ToastComponent;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;

/**
 * The advancement burst's hook (1.37.0). Wraps the one call each toast slot makes into its toast's
 * {@code render}, so the burst is drawn right after the toast, inside its pose, whichever mod made it.
 * WrapOperation, not Redirect, so another mod wrapping the same call still works; the toast's own
 * render (and its visibility answer) is called first and returned untouched. The mixin config is not
 * required: if this ever fails to apply the toasts are plain.
 */
@Mixin(targets = "net.minecraft.client.gui.components.toasts.ToastComponent$ToastInstance")
public abstract class ToastInstanceMixin {

    @Unique
    private boolean proficiency$chimed;
    @Unique
    private int proficiency$tier = -1;

    @WrapOperation(method = "render", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/gui/components/toasts/Toast;render(Lnet/minecraft/client/gui/GuiGraphics;"
                    + "Lnet/minecraft/client/gui/components/toasts/ToastComponent;J)"
                    + "Lnet/minecraft/client/gui/components/toasts/Toast$Visibility;"))
    private Toast.Visibility proficiency$burst(Toast toast, GuiGraphics graphics, ToastComponent parent,
            long shownFor, Operation<Toast.Visibility> original) {
        Toast.Visibility visibility = original.call(toast, graphics, parent, shownFor);
        try {
            if (proficiency$tier < 0) {
                boolean challenge = toast instanceof AdvancementToast advancement
                        && ((AdvancementToastAccessor) (Object) advancement).proficiency$advancement().value().display()
                                .map(d -> d.getType() == AdvancementType.CHALLENGE).orElse(false);
                proficiency$tier = AdvancementFx.tier(toast, challenge);
            }
            if (proficiency$tier > 0) {
                proficiency$chimed = AdvancementFx.draw(graphics, toast, proficiency$tier, shownFor, proficiency$chimed);
            }
        } catch (RuntimeException e) {
            // Never let a flourish break a toast.
            proficiency$tier = 0;
        }
        return visibility;
    }
}
