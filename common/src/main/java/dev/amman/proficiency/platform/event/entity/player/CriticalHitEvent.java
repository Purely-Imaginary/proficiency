package dev.amman.proficiency.platform.event.entity.player;

import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;

/**
 * NeoForge's {@code CriticalHitEvent}: fired in {@code Player.attack} once vanilla has decided
 * whether the swing is a critical hit, before the target is hurt. Server and client both run it.
 * A listener can turn the crit on or off and change its damage multiplier; the multiplier only
 * applies when the hit ends up critical.
 */
public class CriticalHitEvent extends PlayerEvent {

    private final Entity target;
    private final boolean vanillaCritical;
    private boolean criticalHit;
    private float damageMultiplier;

    public CriticalHitEvent(Player player, Entity target, boolean vanillaCritical, float damageMultiplier) {
        super(player);
        this.target = target;
        this.vanillaCritical = vanillaCritical;
        this.criticalHit = vanillaCritical;
        this.damageMultiplier = damageMultiplier;
    }

    public Entity getTarget() {
        return target;
    }

    public boolean isVanillaCritical() {
        return vanillaCritical;
    }

    public boolean isCriticalHit() {
        return criticalHit;
    }

    public void setCriticalHit(boolean criticalHit) {
        this.criticalHit = criticalHit;
    }

    public float getDamageMultiplier() {
        return damageMultiplier;
    }

    public void setDamageMultiplier(float damageMultiplier) {
        this.damageMultiplier = damageMultiplier;
    }
}
