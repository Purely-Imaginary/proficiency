package dev.amman.proficiency.mixin;

import dev.amman.proficiency.platform.bus.NeoForge;
import dev.amman.proficiency.platform.event.entity.living.LivingDeathEvent;
import dev.amman.proficiency.platform.event.entity.player.PlayerContainerEvent;
import dev.amman.proficiency.platform.event.entity.player.PlayerEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.animal.horse.AbstractHorse;
import net.minecraft.world.Container;
import net.minecraft.world.MenuProvider;
import org.jetbrains.annotations.Nullable;
import org.objectweb.asm.Opcodes;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.OptionalInt;

@Mixin(ServerPlayer.class)
public abstract class ServerPlayerMixin {

    @Unique private boolean proficiency$hasTabListName;
    @Unique @Nullable private Component proficiency$tabListName;

    @Unique
    private ServerPlayer proficiency$self() {
        return (ServerPlayer) (Object) this;
    }

    /** ServerPlayer.die does not call super, so NeoForge posts LivingDeathEvent here too. */
    @Inject(method = "die", at = @At("HEAD"), cancellable = true)
    private void proficiency$death(DamageSource source, CallbackInfo ci) {
        if (NeoForge.EVENT_BUS.post(new LivingDeathEvent(proficiency$self(), source)).isCanceled()) {
            ci.cancel();
        }
    }

    /** NeoForge: TabListNameFormat, computed on first request and cached, like its patch. */
    @Inject(method = "getTabListDisplayName", at = @At("HEAD"), cancellable = true)
    private void proficiency$tabListName(CallbackInfoReturnable<Component> cir) {
        if (!proficiency$hasTabListName) {
            var event = NeoForge.EVENT_BUS.post(new PlayerEvent.TabListNameFormat(proficiency$self()));
            proficiency$tabListName = event.getDisplayName();
            proficiency$hasTabListName = true;
        }
        cir.setReturnValue(proficiency$tabListName);
    }

    @Inject(method = "openMenu", at = @At(value = "FIELD",
            target = "Lnet/minecraft/server/level/ServerPlayer;containerMenu:Lnet/minecraft/world/inventory/AbstractContainerMenu;",
            opcode = Opcodes.PUTFIELD, shift = At.Shift.AFTER))
    private void proficiency$menuOpened(MenuProvider provider, CallbackInfoReturnable<OptionalInt> cir) {
        NeoForge.EVENT_BUS.post(new PlayerContainerEvent.Open(proficiency$self(), proficiency$self().containerMenu));
    }

    @Inject(method = "openHorseInventory", at = @At("TAIL"))
    private void proficiency$horseOpened(AbstractHorse horse, Container container, CallbackInfo ci) {
        NeoForge.EVENT_BUS.post(new PlayerContainerEvent.Open(proficiency$self(), proficiency$self().containerMenu));
    }

    /** NeoForge posts Close with the closing menu, just before containerMenu reverts to the inventory. */
    @Inject(method = "doCloseContainer", at = @At(value = "FIELD",
            target = "Lnet/minecraft/server/level/ServerPlayer;containerMenu:Lnet/minecraft/world/inventory/AbstractContainerMenu;",
            opcode = Opcodes.PUTFIELD))
    private void proficiency$menuClosed(CallbackInfo ci) {
        NeoForge.EVENT_BUS.post(new PlayerContainerEvent.Close(proficiency$self(), proficiency$self().containerMenu));
    }
}
