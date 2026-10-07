package dev.amman.proficiency.platform.hooks;

import dev.amman.proficiency.platform.damage.DamageContainer;
import net.minecraft.world.entity.item.ItemEntity;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayDeque;
import java.util.List;

/** Per-entity state the living-entity mixins keep, reached through a duck interface. */
public interface LivingHooks {

    /** One container per hurt call in flight on this entity, innermost last. */
    ArrayDeque<DamageContainer> proficiency$containers();

    /** Death loot being captured for LivingDropsEvent, or null when not capturing. */
    @Nullable
    List<ItemEntity> proficiency$capturedDrops();

    void proficiency$setCapturedDrops(@Nullable List<ItemEntity> drops);
}
