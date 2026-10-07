package dev.amman.proficiency.platform.event.entity.living;

import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.level.ServerLevelAccessor;

public abstract class MobSpawnEvent extends LivingEvent {

    private final ServerLevelAccessor level;
    private final double x;
    private final double y;
    private final double z;

    protected MobSpawnEvent(Mob mob, ServerLevelAccessor level, double x, double y, double z) {
        super(mob);
        this.level = level;
        this.x = x;
        this.y = y;
        this.z = z;
    }

    @Override
    public Mob getEntity() {
        return (Mob) super.getEntity();
    }

    public ServerLevelAccessor getLevel() {
        return level;
    }

    public double getX() {
        return x;
    }

    public double getY() {
        return y;
    }

    public double getZ() {
        return z;
    }

    /** {@code NaturalSpawner}, where a natural spawn asks whether its spot is allowed. */
    public static class PositionCheck extends MobSpawnEvent {

        public enum Result {
            SUCCEED, DEFAULT, FAIL
        }

        private final MobSpawnType spawnType;
        private Result result = Result.DEFAULT;

        public PositionCheck(Mob mob, ServerLevelAccessor level, MobSpawnType spawnType) {
            super(mob, level, mob.getX(), mob.getY(), mob.getZ());
            this.spawnType = spawnType;
        }

        public MobSpawnType getSpawnType() {
            return spawnType;
        }

        public void setResult(Result result) {
            this.result = result;
        }

        public Result getResult() {
            return result;
        }
    }
}
