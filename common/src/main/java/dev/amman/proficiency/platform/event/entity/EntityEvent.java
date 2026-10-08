package dev.amman.proficiency.platform.event.entity;

import dev.amman.proficiency.platform.bus.Event;
import net.minecraft.world.entity.Entity;

public abstract class EntityEvent extends Event {

    private final Entity entity;

    protected EntityEvent(Entity entity) {
        this.entity = entity;
    }

    public Entity getEntity() {
        return entity;
    }
}
