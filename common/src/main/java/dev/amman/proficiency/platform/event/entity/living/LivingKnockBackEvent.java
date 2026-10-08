package dev.amman.proficiency.platform.event.entity.living;

import dev.amman.proficiency.platform.bus.ICancellableEvent;
import net.minecraft.world.entity.LivingEntity;

/** Start of {@code LivingEntity.knockback}; canceling removes the knockback. */
public class LivingKnockBackEvent extends LivingEvent implements ICancellableEvent {

    private float strength;
    private double ratioX;
    private double ratioZ;
    private final float originalStrength;
    private final double originalRatioX;
    private final double originalRatioZ;

    public LivingKnockBackEvent(LivingEntity target, float strength, double ratioX, double ratioZ) {
        super(target);
        this.strength = this.originalStrength = strength;
        this.ratioX = this.originalRatioX = ratioX;
        this.ratioZ = this.originalRatioZ = ratioZ;
    }

    public float getStrength() {
        return strength;
    }

    public double getRatioX() {
        return ratioX;
    }

    public double getRatioZ() {
        return ratioZ;
    }

    public float getOriginalStrength() {
        return originalStrength;
    }

    public double getOriginalRatioX() {
        return originalRatioX;
    }

    public double getOriginalRatioZ() {
        return originalRatioZ;
    }

    public void setStrength(float strength) {
        this.strength = strength;
    }

    public void setRatioX(double ratioX) {
        this.ratioX = ratioX;
    }

    public void setRatioZ(double ratioZ) {
        this.ratioZ = ratioZ;
    }
}
