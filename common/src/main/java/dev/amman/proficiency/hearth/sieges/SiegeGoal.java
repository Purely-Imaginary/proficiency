package dev.amman.proficiency.hearth.sieges;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.ai.goal.WrappedGoal;

import java.util.EnumSet;

/**
 * The one goal a siege adds to a vanilla mob: "go to the Hearth" (docs/HEARTH-SPEC.md, "The siege
 * itself"). It runs only while the mob has no living target, so a mob that sees a defender fights
 * them first and comes back for the fire after; it stops next to the Hearth, where it fills the
 * douse meter. Walls that keep it away keep the fire safe.
 *
 * <p>It is also how a mob learns its siege is over: every two seconds it asks the Hearth
 * ({@link SiegeService#status}). A siege that ended (or a Hearth that went) sends it away with a
 * puff of smoke; a Hearth that stays unloaded for half a minute does too, since nobody can be home.
 * Added at spawn and again whenever the mob is loaded, because goals are not saved.
 */
final class SiegeGoal extends Goal {

    /** Close enough to stand and douse (blocks from the Hearth's centre). */
    static final double NEAR = 1.8;
    private static final int CHECK_TICKS = 40;
    private static final int UNKNOWN_LIMIT_TICKS = 600;

    private final Mob mob;
    private final BlockPos hearth;
    private final long siegeId;
    private int checkIn;
    private int unknownFor;
    private int repath;

    SiegeGoal(Mob mob, BlockPos hearth, long siegeId) {
        this.mob = mob;
        this.hearth = hearth;
        this.siegeId = siegeId;
        setFlags(EnumSet.of(Flag.MOVE));
    }

    /** Whether this mob already has a siege goal (a reload must not add a second). */
    static boolean has(Mob mob) {
        for (WrappedGoal wrapped : mob.goalSelector.getAvailableGoals()) {
            if (wrapped.getGoal() instanceof SiegeGoal) {
                return true;
            }
        }
        return false;
    }

    @Override
    public boolean canUse() {
        return stillWanted() && wantsToMarch();
    }

    @Override
    public boolean canContinueToUse() {
        return stillWanted() && wantsToMarch();
    }

    @Override
    public void start() {
        repath = 0;
    }

    @Override
    public void stop() {
        mob.getNavigation().stop();
    }

    @Override
    public void tick() {
        if (--repath > 0) {
            return;
        }
        repath = 20;
        mob.getNavigation().moveTo(hearth.getX() + 0.5, hearth.getY(), hearth.getZ() + 0.5, 1.0);
    }

    private boolean wantsToMarch() {
        LivingEntity target = mob.getTarget();
        if (target != null && target.isAlive()) {
            return false;
        }
        return mob.distanceToSqr(hearth.getX() + 0.5, hearth.getY(), hearth.getZ() + 0.5) > NEAR * NEAR;
    }

    /** False once the mob has been sent away. Checks the siege every two seconds. */
    private boolean stillWanted() {
        if (mob.isRemoved() || !(mob.level() instanceof ServerLevel level)) {
            return false;
        }
        if (--checkIn > 0) {
            return true;
        }
        checkIn = CHECK_TICKS;
        switch (SiegeService.status(level, hearth, siegeId)) {
            case RUNNING -> unknownFor = 0;
            case OVER -> {
                SiegeService.sendAway(level, mob);
                return false;
            }
            case UNKNOWN -> {
                unknownFor += CHECK_TICKS;
                if (unknownFor >= UNKNOWN_LIMIT_TICKS) {
                    SiegeService.sendAway(level, mob);
                    return false;
                }
            }
        }
        return true;
    }
}
