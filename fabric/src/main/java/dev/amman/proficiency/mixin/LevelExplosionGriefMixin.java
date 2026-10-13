package dev.amman.proficiency.mixin;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;
import com.llamalad7.mixinextras.sugar.Local;
import dev.amman.proficiency.platform.bus.NeoForge;
import dev.amman.proficiency.platform.event.entity.EntityMobGriefingEvent;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

/**
 * NeoForge: {@code EventHooks.canEntityGrief(level, source)} decides whether a mob-caused explosion
 * ({@code ExplosionInteraction.MOB}: creepers, fireballs) breaks blocks. Vanilla reads the plain
 * {@code mobGriefing} rule there; this asks the shared {@link EntityMobGriefingEvent} instead, so
 * a siege creeper breaks nothing on Fabric either. No source entity: the plain rule, as NeoForge.
 */
@Mixin(Level.class)
public abstract class LevelExplosionGriefMixin {

    @ModifyExpressionValue(
            method = "explode(Lnet/minecraft/world/entity/Entity;Lnet/minecraft/world/damagesource/DamageSource;"
                    + "Lnet/minecraft/world/level/ExplosionDamageCalculator;DDDFZ"
                    + "Lnet/minecraft/world/level/Level$ExplosionInteraction;Z"
                    + "Lnet/minecraft/core/particles/ParticleOptions;Lnet/minecraft/core/particles/ParticleOptions;"
                    + "Lnet/minecraft/core/Holder;)Lnet/minecraft/world/level/Explosion;",
            at = @At(value = "INVOKE",
                    target = "Lnet/minecraft/world/level/GameRules;getBoolean(Lnet/minecraft/world/level/GameRules$Key;)Z"))
    private boolean proficiency$mobGriefing(boolean rule, @Local(argsOnly = true) @Nullable Entity source) {
        if (source == null || ((Level) (Object) this).isClientSide()) {
            return rule;
        }
        return NeoForge.EVENT_BUS.post(new EntityMobGriefingEvent(source, rule)).canGrief();
    }
}
