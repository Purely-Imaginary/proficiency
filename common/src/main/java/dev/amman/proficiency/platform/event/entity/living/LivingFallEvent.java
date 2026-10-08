package dev.amman.proficiency.platform.event.entity.living;

import dev.amman.proficiency.platform.bus.ICancellableEvent;
import net.minecraft.world.entity.LivingEntity;

/** Start of {@code LivingEntity.causeFallDamage}; canceling means no fall damage at all. */
public class LivingFallEvent extends LivingEvent implements ICancellableEvent {

    private float distance;
    private float damageMultiplier;

    public LivingFallEvent(LivingEntity entity, float distance, float damageMultiplier) {
        super(entity);
        this.distance = distance;
        this.damageMultiplier = damageMultiplier;
    }

    public float getDistance() {
        return distance;
    }

    public void setDistance(float distance) {
        this.distance = distance;
    }

    public float getDamageMultiplier() {
        return damageMultiplier;
    }

    public void setDamageMultiplier(float damageMultiplier) {
        this.damageMultiplier = damageMultiplier;
    }
}
