package dev.amman.proficiency.skill;

import dev.amman.proficiency.net.ProcFxPayload;
import dev.amman.proficiency.net.ProficiencyNetwork;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

/** Tells the clients near a player to play that skill's proc particles. */
public final class ProcFxSender {

    private ProcFxSender() {
    }

    /**
     * Moves a block's centre out to the face that looks at the viewer, so the particles come off
     * the surface instead of being hidden inside the block. {@code sideOnly} keeps the height, for a
     * trunk.
     */
    static Vec3 onFaceToward(Vec3 center, Vec3 eye, boolean sideOnly) {
        double dx = eye.x - center.x;
        double dy = eye.y - center.y;
        double dz = eye.z - center.z;
        double out = 0.55;
        // Looking down at a block, the face you see is the top one, even when it is further away
        // sideways than it is below you.
        if (!sideOnly && dy > 0.5) {
            return center.add(0, out, 0);
        }
        if (!sideOnly && dy < -0.5 && Math.abs(dy) >= Math.abs(dx) && Math.abs(dy) >= Math.abs(dz)) {
            return center.add(0, -out, 0);
        }
        return Math.abs(dx) >= Math.abs(dz) ? center.add(Math.signum(dx) * out, 0, 0)
                : center.add(0, 0, Math.signum(dz) * out);
    }

    public static void send(ServerPlayer player, Skill skill, @Nullable LivingEntity target,
            @Nullable BlockPos pos) {
        var level = player.serverLevel();
        boolean has = true;
        Vec3 focus;
        int stateId = 0;
        int height = 0;
        if (target != null) {
            focus = target.position().add(0, target.getBbHeight() * 0.5, 0);
        } else if (pos != null) {
            focus = Vec3.atCenterOf(pos);
            focus = onFaceToward(focus, player.getEyePosition(), skill == Skill.WOODCUTTING);
            BlockState state = level.getBlockState(pos);
            if (!state.isAir()) {
                stateId = Block.getId(state);
            }
            if (skill == Skill.WOODCUTTING && state.is(BlockTags.LOGS)) {
                while (height < ProcFxPayload.MAX_HEIGHT
                        && level.getBlockState(pos.above(height + 1)).is(BlockTags.LOGS)) {
                    height++;
                }
            }
        } else if (skill == Skill.FISHING && player.fishing != null) {
            focus = player.fishing.position();
        } else {
            has = false;
            focus = Vec3.ZERO;
        }
        ProficiencyNetwork.sendProcFx(level, player, new ProcFxPayload(skill.ordinal(), player.getId(),
                has, focus.x, focus.y, focus.z, stateId, height));
    }
}
