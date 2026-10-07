package dev.amman.proficiency.platform.event.entity.living;

import dev.amman.proficiency.platform.bus.ICancellableEvent;
import net.minecraft.world.entity.LivingEntity;

/** Start of {@code LivingEntity.heal}; canceling or a zero amount heals nothing. */
public class LivingHealEvent extends LivingEvent implements ICancellableEvent {

    private float amount;

    public LivingHealEvent(LivingEntity entity, float amount) {
        super(entity);
        this.amount = amount;
    }

    public float getAmount() {
        return amount;
    }

    public void setAmount(float amount) {
        this.amount = amount;
    }
}
