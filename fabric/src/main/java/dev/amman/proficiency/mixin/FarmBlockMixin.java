package dev.amman.proficiency.mixin;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.llamalad7.mixinextras.sugar.Local;
import dev.amman.proficiency.platform.bus.NeoForge;
import dev.amman.proficiency.platform.event.entity.EntityMobGriefingEvent;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.block.FarmBlock;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/**
 * NeoForge: {@code Entity.canTrample} asks {@code EventHooks.canEntityGrief} before a mob turns
 * farmland to dirt. Vanilla reads the plain {@code mobGriefing} rule in {@code FarmBlock.fallOn};
 * this asks the shared {@link EntityMobGriefingEvent} there (a siege zombie leaves the field alone).
 */
@Mixin(FarmBlock.class)
public abstract class FarmBlockMixin {

    @ModifyExpressionValue(method = "fallOn", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/world/level/GameRules;getBoolean(Lnet/minecraft/world/level/GameRules$Key;)Z"))
    private boolean proficiency$mobGriefing(boolean rule, @Local(argsOnly = true) Entity entity) {
        return NeoForge.EVENT_BUS.post(new EntityMobGriefingEvent(entity, rule)).canGrief();
    }
}
