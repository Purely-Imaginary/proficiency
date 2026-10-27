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
    /** A Hearth siege's mob (docs/HEARTH-SPEC.md, "The siege itself"): full combat XP, and it may feed the Hearth. */
    public static final String SIEGE = "siege";

    /** On a mob from a trial spawner that no player placed: 1 for a normal trial, 2 for an ominous one. */
    static final String TRIAL_TAG = "proficiency_trial";
    /** On that mob: the packed position of its spawner, so its kills count toward that spawner's wave. */
    static final String TRIAL_POS_TAG = "proficiency_trial_pos";

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

    /**
     * Marks a mob a trial spawner made. A spawner a player placed makes artificial mobs (no XP, no
     * trial factor); any other pays the spawner share and counts as a trial mob. Trial spawners
     * often skip the finalize-spawn event (a full entity tag), so the spawner share is set here too.
     */
    public static void tagTrial(Mob mob, boolean ominous, boolean playerPlaced, long spawnerPos) {
        CompoundTag data = data(mob);
        if (playerPlaced) {
            data.putString(TAG, ARTIFICIAL);
            return;
        }
        if (!data.contains(TAG)) {
            data.putString(TAG, SPAWNER);
        }
        data.putInt(TRIAL_TAG, ominous ? 2 : 1);
        data.putLong(TRIAL_POS_TAG, spawnerPos);
    }

    /** Whether a trial spawner made this mob and no player placed that spawner. */
    public static boolean isTrial(Entity entity) {
        return entity instanceof Mob && data(entity).getInt(TRIAL_TAG) > 0;
    }

    /** Whether this trial mob came from an ominous trial. */
    public static boolean isOminousTrial(Entity entity) {
        return entity instanceof Mob && data(entity).getInt(TRIAL_TAG) == 2;
    }

    /** The packed position of the spawner of a trial mob. Check {@link #isTrial} first. */
    public static long trialSpawnerPos(Entity entity) {
        return data(entity).getLong(TRIAL_POS_TAG);
    }

    /** The factor a trial mob's Courage and Charger XP is multiplied by: 1.0 for any other mob. */
    public static double trialFactor(Entity entity) {
        if (!isTrial(entity)) {
            return 1.0;
        }
        return TrialRules.mobFactor(true, isOminousTrial(entity), ProficiencyConfig.trialMobFactor(),
                ProficiencyConfig.trialOminousMobFactor());
    }

    /** Marks a mob the Sieges lane spawned for a siege. */
    public static void tagSiege(Mob mob) {
        data(mob).putString(TAG, SIEGE);
    }

    /** Whether this entity was spawned for a Hearth siege. */
    public static boolean isSiege(Entity entity) {
        return entity instanceof Mob && SIEGE.equals(data(entity).getString(TAG));
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
        return dev.amman.proficiency.platform.EntityData.of(entity);
    }
}
