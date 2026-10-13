package dev.amman.proficiency.platform.event.entity;

import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;

/**
 * Posted when an entity is removed from a level: killed, discarded, or saved away with its chunk
 * (check {@link Entity#getRemovalReason()}: {@code shouldSave()} is true for an unload, false for a kill).
 * NeoForge relays its own {@code EntityLeaveLevelEvent}; Fabric posts it from API's
 * {@code ServerEntityEvents.ENTITY_UNLOAD}. Server side only on Fabric: handlers check the level.
 */
public class EntityLeaveLevelEvent extends EntityEvent {

    private final Level level;

    public EntityLeaveLevelEvent(Entity entity, Level level) {
        super(entity);
        this.level = level;
    }

    public Level getLevel() {
        return level;
    }
}
