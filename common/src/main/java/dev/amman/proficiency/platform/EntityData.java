package dev.amman.proficiency.platform;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.entity.Entity;

/**
 * NeoForge's {@code Entity.getPersistentData()} on every loader: a free-form tag saved with the
 * entity. NeoForge answers with the entity's own tag, Fabric with an attachment.
 */
public final class EntityData {

    private EntityData() {
    }

    public static CompoundTag of(Entity entity) {
        return Services.platform().persistentData(entity);
    }
}
