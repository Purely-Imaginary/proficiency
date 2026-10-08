package dev.amman.proficiency.mixin;

import net.minecraft.resources.ResourceKey;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageSources;
import net.minecraft.world.damagesource.DamageType;
import net.minecraft.world.entity.Entity;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

/** 1.21 made {@code DamageSources.source(type, direct, causing)} public; 1.20.1 keeps it private. */
@Mixin(DamageSources.class)
public interface DamageSourcesInvoker {

    @Invoker("source")
    DamageSource proficiency$source(ResourceKey<DamageType> type, @Nullable Entity direct, @Nullable Entity causing);
}
