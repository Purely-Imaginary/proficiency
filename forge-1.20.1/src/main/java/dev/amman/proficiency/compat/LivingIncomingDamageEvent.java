package dev.amman.proficiency.compat;

import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraftforge.event.entity.living.LivingEvent;
import net.minecraftforge.eventbus.api.Cancelable;

/**
 * NeoForge's {@code LivingIncomingDamageEvent}, rebuilt for Forge 1.20.1 by {@link DamageBridge}.
 *
 * <p>It fires where NeoForge fires it: at the start of {@code hurt}, after the invulnerable, dead
 * and fire-resistance checks and before a shield is asked. Cancelling it cancels the hit outright
 * (no knockback, no hurt flash), as on NeoForge. A changed amount is applied to the hit when it
 * reaches {@code actuallyHurt}, before armour, so a doubled amount is a doubled hit.
 */
@Cancelable
public class LivingIncomingDamageEvent extends LivingEvent {

    private final DamageSource source;
    private final float originalAmount;
    private float amount;

    public LivingIncomingDamageEvent(LivingEntity entity, DamageSource source, float amount) {
        super(entity);
        this.source = source;
        this.originalAmount = amount;
        this.amount = amount;
    }

    public DamageSource getSource() {
        return source;
    }

    public float getAmount() {
        return amount;
    }

    public void setAmount(float amount) {
        this.amount = amount;
    }

    public float getOriginalAmount() {
        return originalAmount;
    }
}
