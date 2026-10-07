package dev.amman.proficiency.mixin;

import dev.amman.proficiency.platform.EntityData;
import dev.amman.proficiency.skill.PlacedBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.item.FallingBlockEntity;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Falling sand and gravel carry the placed-block mark from where they fell to where they land. */
@Mixin(FallingBlockEntity.class)
public abstract class FallingBlockEntityMixin {

    private static final String PROFICIENCY$TAG = "proficiency_placed_fall";

    @Inject(method = "fall", at = @At("RETURN"))
    private static void proficiency$pickUp(Level level, BlockPos pos, BlockState state,
            CallbackInfoReturnable<FallingBlockEntity> cir) {
        if (level instanceof ServerLevel server && cir.getReturnValue() != null
                && PlacedBlocks.pickUp(server, pos, state)) {
            EntityData.of(cir.getReturnValue()).putBoolean(PROFICIENCY$TAG, true);
        }
    }

    @Inject(method = "tick", at = @At("TAIL"))
    private void proficiency$land(CallbackInfo ci) {
        FallingBlockEntity self = (FallingBlockEntity) (Object) this;
        if (self.level() instanceof ServerLevel server && self.getRemovalReason() == Entity.RemovalReason.DISCARDED
                && EntityData.of(self).getBoolean(PROFICIENCY$TAG)) {
            EntityData.of(self).remove(PROFICIENCY$TAG);
            BlockState there = server.getBlockState(self.blockPosition());
            if (there.is(self.getBlockState().getBlock())) {
                PlacedBlocks.land(server, self.blockPosition(), there);
            }
        }
    }
}
