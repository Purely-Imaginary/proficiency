package dev.amman.proficiency.platform.event.entity.living;

import dev.amman.proficiency.platform.bus.ICancellableEvent;
import dev.amman.proficiency.platform.damage.DamageContainer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;

/** Server side, start of {@code LivingEntity.hurt}, after the early outs; canceling negates the hit. */
public class LivingIncomingDamageEvent extends LivingEvent implements ICancellableEvent {

    private final DamageContainer container;

    public LivingIncomingDamageEvent(LivingEntity entity, DamageContainer container) {
        super(entity);
        this.container = container;
    }

    public DamageContainer getContainer() {
        return container;
    }

    public DamageSource getSource() {
        return container.getSource();
    }

    public float getAmount() {
        return container.getNewDamage();
    }

    public float getOriginalAmount() {
        return container.getOriginalDamage();
    }

    public void setAmount(float newDamage) {
        container.setNewDamage(newDamage);
    }

    public void setInvulnerabilityTicks(int ticks) {
        container.setPostAttackInvulnerabilityTicks(ticks);
    }
}
