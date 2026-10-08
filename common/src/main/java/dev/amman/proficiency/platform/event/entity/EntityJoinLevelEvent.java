package dev.amman.proficiency.platform.event.entity;

import dev.amman.proficiency.platform.bus.ICancellableEvent;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;

/** Posted when an entity is added to a level; {@link #loadedFromDisk()} is true for chunk loads. */
public class EntityJoinLevelEvent extends EntityEvent implements ICancellableEvent {

    private final Level level;
    private final boolean loadedFromDisk;

    public EntityJoinLevelEvent(Entity entity, Level level, boolean loadedFromDisk) {
        super(entity);
        this.level = level;
        this.loadedFromDisk = loadedFromDisk;
    }

    public Level getLevel() {
        return level;
    }

    public boolean loadedFromDisk() {
        return loadedFromDisk;
    }
}
