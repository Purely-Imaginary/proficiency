package dev.amman.proficiency.mixin;

import dev.amman.proficiency.platform.bus.NeoForge;
import dev.amman.proficiency.platform.event.entity.EntityJoinLevelEvent;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.entity.EntityAccess;
import net.minecraft.world.level.entity.PersistentEntitySectionManager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** NeoForge: EntityJoinLevelEvent at the start of the server entity manager's addEntity. */
@Mixin(PersistentEntitySectionManager.class)
public abstract class PersistentEntitySectionManagerMixin<T extends EntityAccess> {

    @Inject(method = "addEntity", at = @At("HEAD"), cancellable = true)
    private void proficiency$joinLevel(T access, boolean loadedFromDisk, CallbackInfoReturnable<Boolean> cir) {
        if (access instanceof Entity entity
                && NeoForge.EVENT_BUS.post(new EntityJoinLevelEvent(entity, entity.level(), loadedFromDisk)).isCanceled()) {
            cir.setReturnValue(false);
        }
    }
}
