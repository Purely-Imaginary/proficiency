package dev.amman.proficiency.mixin.client;

import dev.amman.proficiency.client.SynergySlam;
import net.minecraft.client.gui.components.ChatComponent;
import net.minecraft.network.chat.Component;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * The synergy slam replaces the server's "Synergy awakened" chat lines (1.45.0): with the slam on, the
 * announcement and the effect line under it are dropped on this client. Not a required mixin: without it the
 * lines simply show as before.
 */
@Mixin(ChatComponent.class)
public abstract class ChatSynergyMixin {

    @Inject(method = "addMessage(Lnet/minecraft/network/chat/Component;)V", at = @At("HEAD"), cancellable = true)
    private void proficiency$swallow(Component message, CallbackInfo info) {
        if (SynergySlam.swallow(message)) {
            info.cancel();
        }
    }
}
