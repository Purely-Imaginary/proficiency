package dev.amman.proficiency.gametest;

import dev.amman.proficiency.hearth.stages.HearthDesigns;
import dev.amman.proficiency.hearth.stages.StageBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;

/**
 * Builds the Hearth's stage designs (docs/HEARTH-DESIGNS-SPEC.md) for the GameTests: a lane and a stage placed
 * around a campfire with one concrete block per role (the lane's palette, else {@link StageBlocks#defaultState}: stone bricks,
 * stone brick stairs, oak logs, oak planks, oak slabs, a white banner, the named metals, a lodestone).
 * Blocks go in without neighbour updates, so a banner or a lantern stays where the design puts it.
 */
final class HearthDesignBuilds {

    /** The lane the Hearth tests build unless one is named. */
    static final String LANE = "longhouse";

    private HearthDesignBuilds() {
    }

    /** Stage {@code stage} of {@link #LANE} around {@code fire} (relative). */
    static void place(GameTestHelper helper, BlockPos fire, int stage) {
        place(helper, fire, LANE, stage);
    }

    /** Stage {@code stage} of lane {@code lane}: air cells are cleared, the fire cell is left alone. */
    static void place(GameTestHelper helper, BlockPos fire, String lane, int stage) {
        HearthDesigns.Lane design = HearthDesigns.shipped().lane(lane);
        for (HearthDesigns.Cell cell : design.stage(stage).cells()) {
            if (cell.role() == 'F') {
                continue;
            }
            BlockState state = StageBlocks.laneState(design, cell.role());
            set(helper, fire.offset(cell.dx(), cell.dy(), cell.dz()), state);
        }
    }

    static void set(GameTestHelper helper, BlockPos rel, BlockState state) {
        helper.getLevel().setBlock(helper.absolutePos(rel), state, 2 | 16);
    }

    /** Air over the whole 9x9x7 box around {@code fire}, and no drops left lying there. */
    static void clear(GameTestHelper helper, BlockPos fire) {
        BlockState air = Blocks.AIR.defaultBlockState();
        for (int x = -HearthDesigns.RADIUS; x <= HearthDesigns.RADIUS; x++) {
            for (int z = -HearthDesigns.RADIUS; z <= HearthDesigns.RADIUS; z++) {
                for (int y = HearthDesigns.MAX_Y; y >= HearthDesigns.MIN_Y; y--) {
                    helper.getLevel().setBlock(helper.absolutePos(fire.offset(x, y, z)), air, 3);
                }
            }
        }
        BlockPos centre = helper.absolutePos(fire);
        for (ItemEntity drop : helper.getLevel().getEntitiesOfClass(ItemEntity.class, new AABB(centre).inflate(6.0))) {
            drop.discard();
        }
    }
}
