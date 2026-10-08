package dev.amman.proficiency.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import dev.amman.proficiency.platform.hooks.LivingHooks;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/** NeoForge's Entity.captureDrops: while a living entity's death loot is captured, drops go to the list. */
@Mixin(Entity.class)
public abstract class EntityMixin {

    @WrapOperation(method = "spawnAtLocation(Lnet/minecraft/world/item/ItemStack;F)Lnet/minecraft/world/entity/item/ItemEntity;",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/world/level/Level;addFreshEntity(Lnet/minecraft/world/entity/Entity;)Z"))
    private boolean proficiency$captureDrop(Level level, Entity drop, Operation<Boolean> original) {
        if (this instanceof LivingHooks living && living.proficiency$capturedDrops() != null
                && drop instanceof ItemEntity item) {
            living.proficiency$capturedDrops().add(item);
            return true;
        }
        return original.call(level, drop);
    }
}
