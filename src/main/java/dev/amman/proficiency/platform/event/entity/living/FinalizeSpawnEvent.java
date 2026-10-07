package dev.amman.proficiency.platform.event.entity.living;

import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.level.ServerLevelAccessor;

/** Start of {@code Mob.finalizeSpawn}, for every spawn path. Read-only here. */
public class FinalizeSpawnEvent extends MobSpawnEvent {

    private final MobSpawnType spawnType;

    public FinalizeSpawnEvent(Mob mob, ServerLevelAccessor level, MobSpawnType spawnType) {
        super(mob, level, mob.getX(), mob.getY(), mob.getZ());
        this.spawnType = spawnType;
    }

    public MobSpawnType getSpawnType() {
        return spawnType;
    }
}
