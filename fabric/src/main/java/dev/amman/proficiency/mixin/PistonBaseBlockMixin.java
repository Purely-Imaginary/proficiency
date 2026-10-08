package dev.amman.proficiency.mixin;

import dev.amman.proficiency.skill.PlacedBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.piston.PistonBaseBlock;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** NeoForge's PistonEvent.Pre, for the placed-block marks: they travel with the pushed blocks. */
@Mixin(PistonBaseBlock.class)
public abstract class PistonBaseBlockMixin {

    @Inject(method = "moveBlocks", at = @At("HEAD"))
    private void proficiency$moveMarks(Level level, BlockPos pos, Direction direction, boolean extending,
            CallbackInfoReturnable<Boolean> cir) {
        if (!level.isClientSide()) {
            PlacedBlocks.pistonMove(level, pos, direction, extending);
        }
    }
}
