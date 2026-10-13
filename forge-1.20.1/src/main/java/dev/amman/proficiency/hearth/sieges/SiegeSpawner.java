package dev.amman.proficiency.hearth.sieges;

import dev.amman.proficiency.Proficiency;
import dev.amman.proficiency.hearth.HearthGeometry;
import dev.amman.proficiency.hearth.HearthRange;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.SpawnGroupData;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.Zombie;
import net.minecraft.world.level.NaturalSpawner;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.random.RandomGenerator;

/**
 * Puts a siege's mobs on the ground at the edge of the Hearth's range (docs/HEARTH-SPEC.md, "The
 * siege itself"): a ring just inside the cylinder's radius, near the wave's gate, on a block a mob
 * may spawn on (not leaves, not glass), with room for it and no liquid, in a loaded chunk, as close
 * to the Hearth's own height as the ground allows. A spot keeps {@code spawnClearance} blocks from
 * every player when one can be found. Vanilla mobs, prepared as an event spawn (their usual gear),
 * marked and disarmed ({@link SiegeMobs}), kept from despawning and given the reach to find the fire.
 */
final class SiegeSpawner {

    private static final int ATTEMPTS = 12;
    private static final Set<String> WARNED = ConcurrentHashMap.newKeySet();

    private SiegeSpawner() {
    }

    /** Spawns one mob of {@code id} near {@code gate} (radians); null when the id is no mob or no spot was found. */
    @Nullable
    static Mob spawn(ServerLevel level, BlockPos hearth, String id, double gate, long siegeId,
            List<ServerPlayer> players, double clearance, RandomGenerator random) {
        EntityType<?> type = EntityType.byString(id).orElse(null);
        if (type == null) {
            if (WARNED.add(id)) {
                Proficiency.LOG.warn("Hearth siege: unknown entity id {} in the siege mix, skipped", id);
            }
            return null;
        }
        HearthGeometry.Range range = HearthRange.range();
        double radius = Math.max(4.0, range.radius() - 1.5);
        for (int pass = 0; pass < 2; pass++) {
            double keep = pass == 0 ? clearance : 0.0;
            for (int attempt = 0; attempt < ATTEMPTS; attempt++) {
                double angle = SiegeWaves.near(gate, random);
                int x = Mth.floor(hearth.getX() + 0.5 + SiegeWaves.dx(angle, radius));
                int z = Mth.floor(hearth.getZ() + 0.5 + SiegeWaves.dz(angle, radius));
                BlockPos spot = ground(level, type, hearth, x, z, range);
                if (spot == null || tooClose(spot, players, keep)) {
                    continue;
                }
                Mob mob = create(level, type, id, spot, hearth, siegeId);
                if (mob != null) {
                    return mob;
                }
            }
        }
        return null;
    }

    @Nullable
    private static Mob create(ServerLevel level, EntityType<?> type, String id, BlockPos spot, BlockPos hearth,
            long siegeId) {
        Entity entity = type.create(level);
        if (!(entity instanceof Mob mob)) {
            if (entity != null) {
                entity.discard();
            }
            if (WARNED.add(id)) {
                Proficiency.LOG.warn("Hearth siege: {} in the siege mix is not a mob, skipped", id);
            }
            return null;
        }
        float yaw = (float) (Mth.atan2(hearth.getZ() - spot.getZ(), hearth.getX() - spot.getX()) * Mth.RAD_TO_DEG) - 90f;
        mob.moveTo(spot.getX() + 0.5, spot.getY(), spot.getZ() + 0.5, yaw, 0f);
        mob.setYHeadRot(yaw);
        if (!level.noCollision(mob)) {
            mob.discard();
            return null;
        }
        // A siege fields no chicken jockeys: a baby zombie told it may not mount one neither makes a
        // chicken nor takes a player's from a pen nearby (vanilla does both in finalizeSpawn). Checked on
        // 1.20.1: ZombieGroupData(isBaby, canSpawnJockey) is public, Mob.finalizeSpawn hands the group
        // data on unchanged, and Zombie.finalizeSpawn reads canSpawnJockey before either chicken path.
        // Called directly, as on 1.21.1 (ForgeEventFactory.onFinalizeSpawn would post the event to
        // other mods as a spawn they may cancel or dress).
        SpawnGroupData group = mob instanceof Zombie
                ? new Zombie.ZombieGroupData(Zombie.getSpawnAsBabyOdds(level.getRandom()), false) : null;
        mob.finalizeSpawn(level, level.getCurrentDifficultyAt(spot), MobSpawnType.EVENT, group, null);
        prepare(mob, hearth, siegeId);
        for (Entity passenger : mob.getPassengers()) {
            if (passenger instanceof Mob rider) {
                prepare(rider, hearth, siegeId);
            }
        }
        if (!level.tryAddFreshEntityWithPassengers(mob)) {
            return null;
        }
        level.sendParticles(ParticleTypes.LARGE_SMOKE, mob.getX(), mob.getY() + 0.6, mob.getZ(), 8, 0.3, 0.4, 0.3, 0.02);
        return mob;
    }

    /** Marked, disarmed, kept, and able to find the Hearth from anywhere in range (radius + 16). */
    static void prepare(Mob mob, BlockPos hearth, long siegeId) {
        SiegeMobs.mark(mob, hearth, siegeId);
        SiegeMobs.disarm(mob);
        mob.setPersistenceRequired();
        double reach = HearthRange.range().radius() + 16;
        AttributeInstance follow = mob.getAttribute(Attributes.FOLLOW_RANGE);
        if (follow != null && follow.getBaseValue() < reach) {
            follow.setBaseValue(reach);
        }
    }

    private static boolean tooClose(BlockPos spot, List<ServerPlayer> players, double keep) {
        if (keep <= 0) {
            return false;
        }
        double sq = keep * keep;
        for (ServerPlayer player : players) {
            if (player.distanceToSqr(spot.getX() + 0.5, spot.getY(), spot.getZ() + 0.5) < sq) {
                return true;
            }
        }
        return false;
    }

    /**
     * The spot at column (x, z) closest to the Hearth's height, inside the range's vertical band, where
     * {@code type} can stand: a block below it may spawn on, two clear cells, no liquid, room for its box.
     */
    @Nullable
    static BlockPos ground(ServerLevel level, EntityType<?> type, BlockPos hearth, int x, int z,
            HearthGeometry.Range range) {
        int up = Math.max(0, range.above() - 2);
        int down = Math.max(0, range.below() - 1);
        int most = Math.max(up, down);
        BlockPos.MutableBlockPos at = new BlockPos.MutableBlockPos();
        for (int step = 0; step <= most * 2; step++) {
            int dy = (step + 1) / 2 * (step % 2 == 1 ? 1 : -1);
            if (dy > up || -dy > down) {
                continue;
            }
            at.set(x, hearth.getY() + dy, z);
            if (standable(level, type, at)) {
                return at.immutable();
            }
        }
        return null;
    }

    static boolean standable(ServerLevel level, EntityType<?> type, BlockPos pos) {
        if (!level.isLoaded(pos) || !level.getWorldBorder().isWithinBounds(pos)) {
            return false;
        }
        BlockPos below = pos.below();
        BlockState floor = level.getBlockState(below);
        if (!floor.isFaceSturdy(level, below, Direction.UP) || !floor.isValidSpawn(level, below, type)) {
            return false;
        }
        BlockState feet = level.getBlockState(pos);
        BlockState head = level.getBlockState(pos.above());
        if (!NaturalSpawner.isValidEmptySpawnBlock(level, pos, feet, feet.getFluidState(), type)
                || !NaturalSpawner.isValidEmptySpawnBlock(level, pos.above(), head, head.getFluidState(), type)) {
            return false;
        }
        AABB box = type.getAABB(pos.getX() + 0.5, pos.getY(), pos.getZ() + 0.5);
        return level.noCollision(box) && !level.containsAnyLiquid(box);
    }
}
