package dev.amman.proficiency.event;

import dev.amman.proficiency.Proficiency;
import dev.amman.proficiency.skill.PlacedBlocks;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.item.FallingBlockEntity;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.EntityJoinLevelEvent;
import net.neoforged.neoforge.event.entity.EntityLeaveLevelEvent;
import net.neoforged.neoforge.event.level.PistonEvent;

/** Keeps the placed-block marks on blocks that move: pistons and falling sand. */
@EventBusSubscriber(modid = Proficiency.MOD_ID)
public final class PlacedBlockEvents {

    private static final String FALL_TAG = "proficiency_placed_fall";

    private PlacedBlockEvents() {
    }

    @SubscribeEvent
    public static void onPiston(PistonEvent.Pre event) {
        if (event.getLevel() instanceof Level level && !level.isClientSide()) {
            PlacedBlocks.pistonMove(level, event.getPos(), event.getDirection(),
                    event.getPistonMoveType() == PistonEvent.PistonMoveType.EXTEND);
        }
    }

    @SubscribeEvent
    public static void onJoin(EntityJoinLevelEvent event) {
        if (event.getLevel() instanceof ServerLevel level && event.getEntity() instanceof FallingBlockEntity fall
                && !fall.getPersistentData().getBoolean(FALL_TAG)
                && PlacedBlocks.pickUp(level, fall.blockPosition(), fall.getBlockState())) {
            fall.getPersistentData().putBoolean(FALL_TAG, true);
        }
    }

    @SubscribeEvent
    public static void onLeave(EntityLeaveLevelEvent event) {
        Entity entity = event.getEntity();
        if (event.getLevel() instanceof ServerLevel level && entity instanceof FallingBlockEntity fall
                && fall.getPersistentData().getBoolean(FALL_TAG)
                && fall.getRemovalReason() == Entity.RemovalReason.DISCARDED) {
            BlockState there = level.getBlockState(fall.blockPosition());
            if (there.is(fall.getBlockState().getBlock())) {
                PlacedBlocks.land(level, fall.blockPosition(), there);
            }
        }
    }
}
