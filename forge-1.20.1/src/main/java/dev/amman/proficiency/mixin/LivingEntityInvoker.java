package dev.amman.proficiency.mixin;

import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

/** Vanilla's shield wear, for Blocking's cheaper shield (see CombatEvents.onShieldBlock). */
@Mixin(LivingEntity.class)
public interface LivingEntityInvoker {

    @Invoker("hurtCurrentlyUsedShield")
    void proficiency$hurtCurrentlyUsedShield(float damage);
}
