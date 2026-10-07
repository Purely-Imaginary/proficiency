package dev.amman.proficiency.compat;

import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraftforge.event.entity.living.LivingEvent;

/**
 * NeoForge's {@code LivingDamageEvent.Pre} and {@code .Post}, rebuilt for Forge 1.20.1 by
 * {@link DamageBridge}.
 *
 * <p>{@link Pre} is Forge's own {@code LivingDamageEvent}: after armour, enchantments and
 * absorption, before health is taken. {@link Post} fires after health is taken and before the
 * death check, exactly as on NeoForge; Forge has no such hook, so a mixin at the return of
 * {@code actuallyHurt} posts it (see {@code mixin.LivingEntityDamageMixin}).
 */
public abstract class LivingDamageEvent extends LivingEvent {

    private final DamageSource source;

    protected LivingDamageEvent(LivingEntity entity, DamageSource source) {
        super(entity);
        this.source = source;
    }

    public DamageSource getSource() {
        return source;
    }

    public static class Pre extends LivingDamageEvent {

        private final float originalDamage;
        private float newDamage;

        public Pre(LivingEntity entity, DamageSource source, float originalDamage, float damage) {
            super(entity, source);
            this.originalDamage = originalDamage;
            this.newDamage = damage;
        }

        /** The amount the hit entered hurt() with, before shield, armour and absorption. */
        public float getOriginalDamage() {
            return originalDamage;
        }

        public float getNewDamage() {
            return newDamage;
        }

        public void setNewDamage(float damage) {
            this.newDamage = damage;
        }
    }

    public static class Post extends LivingDamageEvent {

        private final float originalDamage;
        private final float newDamage;

        public Post(LivingEntity entity, DamageSource source, float originalDamage, float newDamage) {
            super(entity, source);
            this.originalDamage = originalDamage;
            this.newDamage = newDamage;
        }

        public float getOriginalDamage() {
            return originalDamage;
        }

        public float getNewDamage() {
            return newDamage;
        }
    }
}
