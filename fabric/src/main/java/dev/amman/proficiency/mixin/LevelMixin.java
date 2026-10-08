package dev.amman.proficiency.mixin;

import dev.amman.proficiency.platform.hooks.PlaceCapture;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(Level.class)
public abstract class LevelMixin {

    @Inject(method = "setBlock(Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/level/block/state/BlockState;II)Z",
            at = @At("HEAD"))
    private void proficiency$recordSnapshot(BlockPos pos, BlockState state, int flags, int recursion,
            CallbackInfoReturnable<Boolean> cir) {
        PlaceCapture.record((Level) (Object) this, pos);
    }
}
