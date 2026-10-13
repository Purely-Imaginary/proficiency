package dev.amman.proficiency.mixin;

import dev.amman.proficiency.hearth.effects.HearthStations;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BrewingStandBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Workshop (the Hearth's station speed) for brewing stands, around vanilla's {@code serverTick}:
 * the head marks the brew time, every return asks {@link HearthStations#afterBrewer} for extra
 * steps when the tick really brewed one. Shared by NeoForge and Fabric.
 */
@Mixin(BrewingStandBlockEntity.class)
public abstract class HearthBrewingStandMixin implements HearthStations.Brewer {

    @Shadow
    int brewTime;

    @Unique
    private int proficiency$marked;
    @Unique
    private float proficiency$carry;

    @Inject(method = "serverTick", at = @At("HEAD"), require = 1)
    private static void proficiency$hearthBefore(Level level, BlockPos pos, BlockState state,
            BrewingStandBlockEntity stand, CallbackInfo ci) {
        ((HearthStations.Brewer) stand).proficiency$hearthMark();
    }

    @Inject(method = "serverTick", at = @At("RETURN"), require = 1)
    private static void proficiency$hearthAfter(Level level, BlockPos pos, BlockState state,
            BrewingStandBlockEntity stand, CallbackInfo ci) {
        HearthStations.afterBrewer(level, pos, (HearthStations.Brewer) stand);
    }

    @Override
    public void proficiency$hearthMark() {
        proficiency$marked = brewTime;
    }

    @Override
    public int proficiency$hearthMarked() {
        return proficiency$marked;
    }

    @Override
    public int proficiency$brewTime() {
        return brewTime;
    }

    @Override
    public void proficiency$setBrewTime(int time) {
        brewTime = time;
    }

    @Override
    public float proficiency$hearthCarry() {
        return proficiency$carry;
    }

    @Override
    public void proficiency$setHearthCarry(float carry) {
        proficiency$carry = carry;
    }
}
