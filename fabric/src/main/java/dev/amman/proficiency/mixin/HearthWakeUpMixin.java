package dev.amman.proficiency.mixin;

import dev.amman.proficiency.platform.bus.NeoForge;
import dev.amman.proficiency.platform.event.entity.player.PlayerWakeUpEvent;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * NeoForge: EventHooks.onPlayerWakeup at the head of {@code Player.stopSleepInBed}, before vanilla
 * resets the sleep timer, so {@code isSleepingLongEnough()} still tells a real sleep. Fabric API has
 * no such event; the Hearth's {@code SLEEP} hook (Warmth to full) listens to the shared one.
 */
@Mixin(Player.class)
public abstract class HearthWakeUpMixin {

    @Inject(method = "stopSleepInBed", at = @At("HEAD"))
    private void proficiency$wakeUp(boolean wakeImmediately, boolean updateLevel, CallbackInfo ci) {
        NeoForge.EVENT_BUS.post(new PlayerWakeUpEvent((Player) (Object) this, wakeImmediately, updateLevel));
    }
}
