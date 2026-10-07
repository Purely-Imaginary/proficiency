package dev.amman.proficiency.platform;

import dev.amman.proficiency.Proficiency;
import net.fabricmc.fabric.api.attachment.v1.AttachmentRegistry;
import net.fabricmc.fabric.api.attachment.v1.AttachmentType;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.entity.Entity;

/**
 * NeoForge's {@code Entity.getPersistentData()}: a free-form tag saved with the entity. Only used
 * to mark arrows, so a lazily created attachment is all it needs to be.
 */
@SuppressWarnings("UnstableApiUsage")
public final class EntityData {

    private static final AttachmentType<CompoundTag> TAG = AttachmentRegistry.<CompoundTag>builder()
            .initializer(CompoundTag::new)
            .persistent(CompoundTag.CODEC)
            .copyOnDeath()
            .buildAndRegister(Proficiency.id("persistent_data"));

    private EntityData() {
    }

    public static void init() {
    }

    public static CompoundTag of(Entity entity) {
        return entity.getAttachedOrCreate(TAG);
    }
}
