package dev.amman.proficiency.mixin;

import dev.amman.proficiency.hearth.charge.ChargeEvents;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.raid.Raid;
import net.minecraft.world.entity.raid.Raider;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * The raid a raider belonged to as it began to die (the Hearth's combat filter, docs/HEARTH-SPEC.md
 * "Combat: defending the home"). Vanilla's {@code Raider.die} leaves the raid before
 * {@code LivingEntity.die} posts the death event, so by the time {@link ChargeEvents} looks, the
 * raider has no raid; this keeps it from the head of {@code Raider.die}. Shared by NeoForge and
 * Fabric (both list it in {@code proficiency.mixins.json}).
 */
@Mixin(Raider.class)
public abstract class HearthRaiderMixin implements ChargeEvents.RaidMember {

    @Unique
    @Nullable
    private Raid proficiency$raidAtDeath;

    @Inject(method = "die", at = @At("HEAD"), require = 1)
    private void proficiency$noteRaid(DamageSource source, CallbackInfo ci) {
        proficiency$raidAtDeath = ((Raider) (Object) this).getCurrentRaid();
    }

    @Override
    @Nullable
    public Raid proficiency$raidAtDeath() {
        return proficiency$raidAtDeath;
    }
}
