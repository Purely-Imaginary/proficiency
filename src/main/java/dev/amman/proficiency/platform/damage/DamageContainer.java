package dev.amman.proficiency.platform.damage;

import net.minecraft.world.damagesource.DamageSource;

import java.util.EnumMap;
import java.util.Map;

/**
 * One hit's damage as it moves through {@code LivingEntity.hurt}, the way NeoForge 21.1 tracks it:
 * the incoming event may rewrite the amount, a shield takes its share, armour and absorption take
 * theirs, and whatever is left is what the post event reports. One of these per hurt call, stacked
 * per entity because a hurt can cause another hurt (thorns, ripostes).
 */
public final class DamageContainer {

    public enum Reduction {
        INVULNERABILITY, ARMOR, ENCHANTMENTS, MOB_EFFECTS, ABSORPTION
    }

    private final DamageSource source;
    private final float originalDamage;
    private float newDamage;
    private float blockedDamage;
    private float shieldDamage;
    private int postAttackInvulnerabilityTicks = 20;
    private final Map<Reduction, Float> reductions = new EnumMap<>(Reduction.class);

    public DamageContainer(DamageSource source, float originalDamage) {
        this.source = source;
        this.originalDamage = originalDamage;
        this.newDamage = originalDamage;
    }

    public DamageSource getSource() {
        return source;
    }

    public float getOriginalDamage() {
        return originalDamage;
    }

    public float getNewDamage() {
        return newDamage;
    }

    public void setNewDamage(float damage) {
        this.newDamage = damage;
    }

    public float getBlockedDamage() {
        return blockedDamage;
    }

    public float getShieldDamage() {
        return shieldDamage;
    }

    public void setBlocked(float blocked, float shield) {
        this.blockedDamage = blocked;
        this.shieldDamage = shield;
    }

    public int getPostAttackInvulnerabilityTicks() {
        return postAttackInvulnerabilityTicks;
    }

    public void setPostAttackInvulnerabilityTicks(int ticks) {
        this.postAttackInvulnerabilityTicks = ticks;
    }

    public float getReduction(Reduction reduction) {
        return reductions.getOrDefault(reduction, 0f);
    }

    public void setReduction(Reduction reduction, float amount) {
        reductions.put(reduction, amount);
    }
}
