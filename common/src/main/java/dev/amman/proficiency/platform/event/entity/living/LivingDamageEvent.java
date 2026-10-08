package dev.amman.proficiency.platform.event.entity.living;

import dev.amman.proficiency.platform.damage.DamageContainer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;

public abstract class LivingDamageEvent extends LivingEvent {

    protected LivingDamageEvent(LivingEntity entity) {
        super(entity);
    }

    /** In {@code actuallyHurt}, after armour and enchantments, before absorption and health. */
    public static class Pre extends LivingDamageEvent {

        private final DamageContainer container;

        public Pre(LivingEntity entity, DamageContainer container) {
            super(entity);
            this.container = container;
        }

        public DamageContainer getContainer() {
            return container;
        }

        public DamageSource getSource() {
            return container.getSource();
        }

        public float getNewDamage() {
            return container.getNewDamage();
        }

        public float getOriginalDamage() {
            return container.getOriginalDamage();
        }

        public void setNewDamage(float newDamage) {
            container.setNewDamage(newDamage);
        }
    }

    /** End of {@code actuallyHurt}: what actually came off health. Read-only. */
    public static class Post extends LivingDamageEvent {

        private final float originalDamage;
        private final DamageSource source;
        private final float newDamage;
        private final float blockedDamage;
        private final float shieldDamage;
        private final int postAttackInvulnerabilityTicks;
        private final DamageContainer container;

        public Post(LivingEntity entity, DamageContainer container) {
            super(entity);
            this.container = container;
            this.originalDamage = container.getOriginalDamage();
            this.source = container.getSource();
            this.newDamage = container.getNewDamage();
            this.blockedDamage = container.getBlockedDamage();
            this.shieldDamage = container.getShieldDamage();
            this.postAttackInvulnerabilityTicks = container.getPostAttackInvulnerabilityTicks();
        }

        public float getOriginalDamage() {
            return originalDamage;
        }

        public DamageSource getSource() {
            return source;
        }

        public float getNewDamage() {
            return newDamage;
        }

        public float getBlockedDamage() {
            return blockedDamage;
        }

        public float getShieldDamage() {
            return shieldDamage;
        }

        public int getPostAttackInvulnerabilityTicks() {
            return postAttackInvulnerabilityTicks;
        }

        public float getReduction(DamageContainer.Reduction reduction) {
            return container.getReduction(reduction);
        }
    }
}
