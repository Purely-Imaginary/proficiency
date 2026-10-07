package dev.amman.proficiency.skill;

import dev.amman.proficiency.config.ProficiencyConfig;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MobSpawnType;

/**
 * How a mob came to be. Spawn eggs, dispensers, commands and mob buckets pay no combat XP; spawner
 * mobs pay a share. Natural spawns, breeding and the rest are unchanged. Tagged once at spawn in the
 * entity's saved data, so it survives a reload.
 */
public final class SpawnOrigin {

    static final String TAG = "proficiency_spawn";
    static final String ARTIFICIAL = "artificial";
    static final String SPAWNER = "spawner";

    private SpawnOrigin() {
    }

    /** The kind of origin a spawn type means: ARTIFICIAL, SPAWNER, or null for an ordinary spawn. */
    public static String kindOf(MobSpawnType type) {
        return switch (type.name()) {
            case "SPAWN_EGG", "COMMAND", "DISPENSER", "BUCKET" -> ARTIFICIAL;
            case "SPAWNER", "TRIAL_SPAWNER" -> SPAWNER;
            default -> null;
        };
    }

    /** Called from the finalize-spawn event for every mob. */
    public static void tag(Mob mob, MobSpawnType type) {
        String kind = kindOf(type);
        if (kind != null) {
            data(mob).putString(TAG, kind);
        }
    }

    /** What share of the usual combat XP killing or hitting this entity pays: 1.0 unless it was spawned artificially. */
    public static double xpFactor(Entity entity) {
        if (!(entity instanceof Mob)) {
            return 1.0;
        }
        String kind = data(entity).getString(TAG);
        if (ARTIFICIAL.equals(kind)) {
            return ProficiencyConfig.artificialMobXp();
        }
        if (SPAWNER.equals(kind)) {
            return ProficiencyConfig.spawnerMobXp();
        }
        return 1.0;
    }

    private static CompoundTag data(Entity entity) {
        return entity.getPersistentData();
    }
}
