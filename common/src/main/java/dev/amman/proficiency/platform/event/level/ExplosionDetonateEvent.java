package dev.amman.proficiency.platform.event.level;

import dev.amman.proficiency.platform.bus.Event;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Explosion;
import net.minecraft.world.level.Level;

import java.util.List;

/**
 * An explosion has worked out what it will destroy ({@link Explosion#getToBlow}) and is about to
 * blow it up: the blocks are still standing. Server and client both see it; handlers check the
 * level. NeoForge relays its own {@code ExplosionEvent.Detonate}; Fabric posts it from a mixin at the
 * end of {@code Explosion.explode}.
 */
public class ExplosionDetonateEvent extends Event {

    private final Level level;
    private final Explosion explosion;

    public ExplosionDetonateEvent(Level level, Explosion explosion) {
        this.level = level;
        this.explosion = explosion;
    }

    public Level getLevel() {
        return level;
    }

    public Explosion getExplosion() {
        return explosion;
    }

    /** The blocks the explosion will destroy. */
    public List<BlockPos> getAffectedBlocks() {
        return explosion.getToBlow();
    }
}
