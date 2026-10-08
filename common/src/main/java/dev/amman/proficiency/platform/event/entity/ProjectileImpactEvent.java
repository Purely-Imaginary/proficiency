package dev.amman.proficiency.platform.event.entity;

import dev.amman.proficiency.platform.bus.ICancellableEvent;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.phys.HitResult;

/**
 * A projectile is about to hit something. Posted from a mixin at the start of
 * {@code Projectile.hitTargetOrDeflectSelf}; canceling makes the projectile fly on.
 */
public class ProjectileImpactEvent extends EntityEvent implements ICancellableEvent {

    private final HitResult hit;

    public ProjectileImpactEvent(Projectile projectile, HitResult hit) {
        super(projectile);
        this.hit = hit;
    }

    public Projectile getProjectile() {
        return (Projectile) getEntity();
    }

    public HitResult getRayTraceResult() {
        return hit;
    }
}
