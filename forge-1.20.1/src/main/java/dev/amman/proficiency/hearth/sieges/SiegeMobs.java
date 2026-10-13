package dev.amman.proficiency.hearth.sieges;

import dev.amman.proficiency.hearth.HearthIndexStore;
import dev.amman.proficiency.skill.SpawnOrigin;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.goal.BreakDoorGoal;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.ai.goal.RemoveBlockGoal;
import net.minecraft.world.entity.ai.goal.WrappedGoal;
import net.minecraft.world.entity.monster.Zombie;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.entity.raid.Raider;
import org.jetbrains.annotations.Nullable;

/**
 * What marks a mob as a siege's and what a siege mob may not do (docs/HEARTH-SPEC.md, "They come
 * for the fire"). A siege mob carries, in its saved entity data, the {@link SpawnOrigin#SIEGE}
 * origin, the Hearth it came for and the siege it belongs to (Forge: {@code getPersistentData()}, where
 * {@code SpawnOrigin} keeps the origin too), so it finds its way back after a reload and is sent home
 * when that siege is over.
 *
 * <p>Griefing: a siege mob, or a projectile one of them fired, never changes blocks, whatever the
 * {@code mobGriefing} rule says. Forge's {@code EntityMobGriefingEvent} asks {@link #mayGrief};
 * on top, the door-breaking and block-taking goals are taken out at spawn and on every reload.
 */
public final class SiegeMobs {

    static final String HEARTH = "proficiency_siege_hearth";
    static final String SIEGE_ID = "proficiency_siege_id";
    /** Set on a siege mob its own siege sends away, so its removal is not taken for a fall. */
    static final String SENT_AWAY = "proficiency_siege_sent";

    private SiegeMobs() {
    }

    /** Marks a mob as this siege's: the origin, the Hearth and the siege id. */
    public static void mark(Mob mob, BlockPos hearth, long siegeId) {
        SpawnOrigin.tagSiege(mob);
        CompoundTag data = mob.getPersistentData();
        data.putLong(HEARTH, hearth.asLong());
        data.putLong(SIEGE_ID, siegeId);
    }

    public static boolean isSiegeMob(Entity entity) {
        return SpawnOrigin.isSiege(entity);
    }

    static void markSentAway(Entity entity) {
        if (isSiegeMob(entity)) {
            entity.getPersistentData().putBoolean(SENT_AWAY, true);
        }
    }

    static boolean sentAway(Entity entity) {
        return entity.getPersistentData().getBoolean(SENT_AWAY);
    }

    /** Whether the mob carries the siege's "go to the Hearth" goal (added at spawn and on every load). */
    public static boolean marching(Mob mob) {
        return SiegeGoal.has(mob);
    }

    /** The packed Hearth position a siege mob came for, or {@link HearthIndexStore#NONE}. */
    public static long hearthOf(Entity entity) {
        if (!isSiegeMob(entity)) {
            return HearthIndexStore.NONE;
        }
        CompoundTag data = entity.getPersistentData();
        return data.contains(HEARTH) ? data.getLong(HEARTH) : HearthIndexStore.NONE;
    }

    /** The siege a siege mob belongs to (the game time its countdown began), or 0. */
    public static long siegeIdOf(Entity entity) {
        return isSiegeMob(entity) ? entity.getPersistentData().getLong(SIEGE_ID) : 0L;
    }

    /**
     * False for a siege mob and for anything a siege mob owns (a fireball, an arrow): they change no
     * blocks. Everything else keeps whatever the rule and other mods decided.
     */
    public static boolean mayGrief(@Nullable Entity entity) {
        if (entity == null) {
            return true;
        }
        if (isSiegeMob(entity)) {
            return false;
        }
        return !(entity instanceof Projectile projectile && projectile.getOwner() != null
                && isSiegeMob(projectile.getOwner()));
    }

    /**
     * Takes away what lets a mob break or take blocks on its own: door breaking (zombies, a raid's
     * vindicators), block-removing goals (turtle eggs), an enderman's take/place goals, and a
     * raider's wish to join a vanilla raid nearby.
     */
    public static void disarm(Mob mob) {
        if (mob instanceof Zombie zombie) {
            zombie.setCanBreakDoors(false);
        }
        if (mob instanceof Raider raider) {
            raider.setCanJoinRaid(false);
        }
        mob.goalSelector.removeAllGoals(SiegeMobs::griefGoal);
    }

    static boolean griefGoal(Goal goal) {
        Goal inner = goal instanceof WrappedGoal wrapped ? wrapped.getGoal() : goal;
        if (inner instanceof BreakDoorGoal || inner instanceof RemoveBlockGoal) {
            return true;
        }
        String name = inner.getClass().getName();
        return name.contains("TakeBlock") || name.contains("LeaveBlock");
    }
}
