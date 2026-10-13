package dev.amman.proficiency.homebuilder;

import dev.amman.proficiency.config.ProficiencyConfig;
import dev.amman.proficiency.hearth.HearthBlockEntity;
import dev.amman.proficiency.hearth.HearthHooks;
import dev.amman.proficiency.hearth.HearthIndexData;
import dev.amman.proficiency.hearth.HearthRange;
import dev.amman.proficiency.hearth.HearthReach;
import dev.amman.proficiency.hearth.net.HearthNet;
import dev.amman.proficiency.hearth.net.HearthStatePayload;
import dev.amman.proficiency.skill.Skill;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;

/**
 * Reach (docs/HEARTH-REACH-SPEC.md): the Hearth charges itself with the Homebuilder XP earned at
 * it, and its range grows with that. A Homebuilder grant made while in range of a Hearth adds its
 * base amount to that Hearth's Reach; the radius goes into the dimension's index, which every
 * territory check reads. Once a second each Hearth re-derives its radius, so a config edit
 * (rangeMax, reachFullXp) takes effect without anything being earned. Shared by both Minecraft lines.
 */
public final class HearthReachCharge {

    private HearthReachCharge() {
    }

    /** {@code HearthHooks.GRANT}: Homebuilder XP earned in range charges that Hearth's Reach. */
    static void onGrant(HearthHooks.Grant grant) {
        if (grant.skill() != Skill.HOMEBUILDER || !grant.play() || !(grant.baseAmount() > 0)
                || !ProficiencyConfig.hearthEnabled()) {
            return;
        }
        ServerPlayer player = grant.player();
        HearthBlockEntity hearth = HearthRange.homeEntity(player);
        if (hearth == null) {
            return;
        }
        hearth.state().addReach(grant.baseAmount());
        hearth.setChanged();
        apply(player.serverLevel(), hearth.getBlockPos(), hearth);
    }

    /** {@code HearthHooks.HEARTH_TICK}: keeps the index's radius in step with Reach and the config. */
    static void onHearthSecond(ServerLevel level, BlockPos pos, HearthBlockEntity hearth) {
        apply(level, pos, hearth);
    }

    /** The radius this Hearth's Reach gives under the current config. */
    public static int radius(HearthBlockEntity hearth) {
        return HearthReach.radius(ProficiencyConfig.hearthRange(), ProficiencyConfig.hearthRangeMax(),
                hearth.state().reach(), ProficiencyConfig.hearthReachFullXp());
    }

    /**
     * Writes the radius into the index; when the edge moved, everyone home there gets a fresh state
     * (the ring, the guide and the map read the radius from it).
     */
    private static void apply(ServerLevel level, BlockPos pos, HearthBlockEntity hearth) {
        if (!HearthIndexData.get(level).setRadius(pos, radius(hearth))) {
            return;
        }
        for (ServerPlayer player : level.players()) {
            if (HearthRange.homeIs(level, pos, player.position())) {
                HearthNet.sendState(player, HearthNet.stateOf(hearth, HearthStatePayload.UPDATE));
            }
        }
    }
}
