package dev.amman.proficiency.gametest;

import dev.amman.proficiency.event.GatheringEvents;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerPlayer;

/**
 * Breaks a block as its own player action. A GameTest runs many steps in one server tick, and the
 * area-tool rule counts every further block a player breaks in a tick as an extra, so a test that
 * means "a second, separate break" must start a fresh tick for that player first.
 */
final class Breaks {

    private Breaks() {
    }

    static boolean separately(ServerPlayer player, BlockPos pos) {
        GatheringEvents.forgetAoe(player.getUUID());
        return player.gameMode.destroyBlock(pos);
    }
}
