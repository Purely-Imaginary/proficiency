package dev.amman.proficiency.platform.event.entity.living;

import dev.amman.proficiency.platform.bus.ICancellableEvent;
import net.minecraft.world.entity.LivingEntity;
import org.jetbrains.annotations.Nullable;

/** Start of {@code Mob.setTarget}; canceling keeps the old target. Only cancel is honoured, not a new target. */
public class LivingChangeTargetEvent extends LivingEvent implements ICancellableEvent {

    @Nullable
    private final LivingEntity newTarget;

    public LivingChangeTargetEvent(LivingEntity entity, @Nullable LivingEntity newTarget) {
        super(entity);
        this.newTarget = newTarget;
    }

    @Nullable
    public LivingEntity getNewAboutToBeSetTarget() {
        return newTarget;
    }
}
