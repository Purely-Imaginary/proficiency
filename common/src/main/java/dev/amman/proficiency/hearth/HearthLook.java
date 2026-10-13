package dev.amman.proficiency.hearth;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;

/**
 * What the Hearth block shows of its charge (docs/HEARTH-SPEC.md, "What the player sees": "the
 * block glows with charge (light 6 at no charge to 15 at any tier VI), particles in the colour of
 * the category feeding it most this hour"). Integration, 2026-10-09: the lanes had the light follow
 * the stage only and no feeding colour, because the art contract's blockstate had no room for them.
 *
 * <p>Once a second, after every lane's {@code HEARTH_TICK} (registered last from {@link Hearth#init}),
 * the block's {@link HearthBlock#GLOW} (highest effective tier, 0 while smouldering) and
 * {@link HearthBlock#FEED} (the pool fed most in the rolling hour) are set when they changed, with
 * {@link Block#UPDATE_CLIENTS} only: the light engine and the clients follow, no neighbour updates.
 * Both change a few times an hour at most. The model's variant keys leave them out, so the look of
 * the block stays the stage's.
 */
public final class HearthLook {

    private HearthLook() {
    }

    static void init() {
        HearthHooks.HEARTH_TICK.add(HearthLook::second);
    }

    /** The once-a-second step; public for the GameTests. */
    public static void second(ServerLevel level, BlockPos pos, HearthBlockEntity hearth) {
        BlockState current = level.getBlockState(pos);
        if (!(current.getBlock() instanceof HearthBlock)) {
            return;
        }
        BlockState next = target(current, hearth.state(), level.getGameTime());
        if (next != current) {
            level.setBlock(pos, next, Block.UPDATE_CLIENTS);
        }
    }

    /** The blockstate with {@code glow} and {@code feed} as the state says they should be. */
    static BlockState target(BlockState current, HearthState state, long gameTime) {
        int glow = state.smouldering() ? 0 : Math.min(HearthData.TIERS, state.maxEffectiveTier(Hearths.data()));
        HearthPool top = state.smouldering() ? null : HearthRules.feedingMost(state, gameTime);
        int feed = top == null ? 0 : top.ordinal() + 1;
        return current.setValue(HearthBlock.GLOW, glow).setValue(HearthBlock.FEED, feed);
    }
}
