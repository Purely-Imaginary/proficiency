package dev.amman.proficiency.platform.event.entity.living;

import dev.amman.proficiency.platform.event.entity.EntityEvent;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import org.jetbrains.annotations.Nullable;

public abstract class LivingEvent extends EntityEvent {

    private final LivingEntity livingEntity;

    protected LivingEvent(LivingEntity entity) {
        super(entity);
        this.livingEntity = entity;
    }

    @Override
    public LivingEntity getEntity() {
        return livingEntity;
    }

    /** From {@code LivingEntity.jumpFromGround}. */
    public static class LivingJumpEvent extends LivingEvent {
        public LivingJumpEvent(LivingEntity e) {
            super(e);
        }
    }

    /** From {@code LivingEntity.getVisibilityPercent}: multiply how visible the entity is to a mob. */
    public static class LivingVisibilityEvent extends LivingEvent {

        private double visibilityModifier;
        @Nullable
        private final Entity lookingEntity;

        public LivingVisibilityEvent(LivingEntity livingEntity, @Nullable Entity lookingEntity,
                double originalMultiplier) {
            super(livingEntity);
            this.visibilityModifier = originalMultiplier;
            this.lookingEntity = lookingEntity;
        }

        public void modifyVisibility(double mod) {
            visibilityModifier *= mod;
        }

        public double getVisibilityModifier() {
            return visibilityModifier;
        }

        @Nullable
        public Entity getLookingEntity() {
            return lookingEntity;
        }
    }
}
