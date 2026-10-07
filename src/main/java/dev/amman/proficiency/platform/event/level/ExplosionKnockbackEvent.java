package dev.amman.proficiency.platform.event.level;

import dev.amman.proficiency.platform.bus.Event;
import net.minecraft.core.BlockPos;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Explosion;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

import java.util.List;

/** An explosion is about to push an entity by {@code knockbackVelocity}. */
public class ExplosionKnockbackEvent extends Event {

    private final Level level;
    private final Explosion explosion;
    private final Entity entity;
    private Vec3 knockbackVelocity;

    public ExplosionKnockbackEvent(Level level, Explosion explosion, Entity entity, Vec3 knockbackVelocity) {
        this.level = level;
        this.explosion = explosion;
        this.entity = entity;
        this.knockbackVelocity = knockbackVelocity;
    }

    public Level getLevel() {
        return level;
    }

    public Explosion getExplosion() {
        return explosion;
    }

    public List<BlockPos> getAffectedBlocks() {
        return explosion.getToBlow();
    }

    public Entity getAffectedEntity() {
        return entity;
    }

    public Vec3 getKnockbackVelocity() {
        return knockbackVelocity;
    }

    public void setKnockbackVelocity(Vec3 newKnockbackVelocity) {
        this.knockbackVelocity = newKnockbackVelocity;
    }
}
