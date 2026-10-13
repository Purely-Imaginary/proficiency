package dev.amman.proficiency.mixin;

import dev.amman.proficiency.hearth.effects.HearthStations;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.AbstractFurnaceBlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Workshop (the Hearth's station speed) for furnaces, smokers and blast furnaces: around vanilla's
 * {@code serverTick}, which stays untouched. The head marks the progress; the return asks
 * {@link HearthStations#afterFurnace} for extra steps when the tick really cooked one. The Forge
 * 1.20.1 copy of common's mixin (listed in {@code proficiency.mixins.json}).
 */
@Mixin(AbstractFurnaceBlockEntity.class)
public abstract class HearthFurnaceMixin implements HearthStations.Furnace {

    @Shadow
    int litTime;
    @Shadow
    int cookingProgress;
    @Shadow
    int cookingTotalTime;

    @Unique
    private int proficiency$marked;
    @Unique
    private float proficiency$carry;

    @Inject(method = "serverTick", at = @At("HEAD"), require = 1)
    private static void proficiency$hearthBefore(Level level, BlockPos pos, BlockState state,
            AbstractFurnaceBlockEntity furnace, CallbackInfo ci) {
        ((HearthStations.Furnace) furnace).proficiency$hearthMark();
    }

    @Inject(method = "serverTick", at = @At("RETURN"), require = 1)
    private static void proficiency$hearthAfter(Level level, BlockPos pos, BlockState state,
            AbstractFurnaceBlockEntity furnace, CallbackInfo ci) {
        HearthStations.afterFurnace(level, pos, (HearthStations.Furnace) furnace);
    }

    @Override
    public void proficiency$hearthMark() {
        proficiency$marked = cookingProgress;
    }

    @Override
    public int proficiency$hearthMarked() {
        return proficiency$marked;
    }

    @Override
    public int proficiency$cookingProgress() {
        return cookingProgress;
    }

    @Override
    public void proficiency$setCookingProgress(int progress) {
        cookingProgress = progress;
    }

    @Override
    public int proficiency$cookingTotalTime() {
        return cookingTotalTime;
    }

    @Override
    public int proficiency$litTime() {
        return litTime;
    }

    @Override
    public void proficiency$setLitTime(int time) {
        litTime = time;
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
