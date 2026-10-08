package dev.amman.proficiency.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import dev.amman.proficiency.platform.bus.NeoForge;
import dev.amman.proficiency.platform.event.AnvilUpdateEvent;
import dev.amman.proficiency.platform.event.entity.player.AnvilRepairEvent;
import net.minecraft.core.BlockPos;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AnvilMenu;
import net.minecraft.world.inventory.ContainerLevelAccess;
import net.minecraft.world.inventory.DataSlot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.AnvilBlock;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.function.BiConsumer;

@Mixin(AnvilMenu.class)
public abstract class AnvilMenuMixin {

    @Shadow @Final private DataSlot cost;
    @Shadow private int repairItemCountCost;
    @Shadow private String itemName;

    @Unique private float proficiency$breakChance = 0.12F;

    /** NeoForge: CommonHooks.onAnvilChange, right after cost.set(1), only with a left input. */
    @Inject(method = "createResult", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/world/inventory/DataSlot;set(I)V", ordinal = 0, shift = At.Shift.AFTER),
            cancellable = true)
    private void proficiency$anvilUpdate(CallbackInfo ci) {
        ItemCombinerMenuAccessor menu = (ItemCombinerMenuAccessor) this;
        ItemStack left = menu.proficiency$inputSlots().getItem(0);
        if (left.isEmpty()) {
            return;
        }
        AnvilUpdateEvent event = NeoForge.EVENT_BUS.post(new AnvilUpdateEvent(
                left, menu.proficiency$inputSlots().getItem(1), itemName, 0, menu.proficiency$player()));
        if (event.isCanceled()) {
            menu.proficiency$resultSlots().setItem(0, ItemStack.EMPTY);
            cost.set(0);
            repairItemCountCost = 0;
            ci.cancel();
            return;
        }
        if (event.getOutput().isEmpty()) {
            return;
        }
        menu.proficiency$resultSlots().setItem(0, event.getOutput());
        cost.set((int) event.getCost());
        repairItemCountCost = event.getMaterialCost();
        ci.cancel();
    }

    /** NeoForge: CommonHooks.onAnvilRepair, while both inputs are still in their slots. */
    @Inject(method = "onTake", at = @At("HEAD"))
    private void proficiency$anvilRepair(Player player, ItemStack output, CallbackInfo ci) {
        ItemCombinerMenuAccessor menu = (ItemCombinerMenuAccessor) this;
        proficiency$breakChance = NeoForge.EVENT_BUS.post(new AnvilRepairEvent(player, menu.proficiency$inputSlots().getItem(0),
                menu.proficiency$inputSlots().getItem(1), output)).getBreakChance();
    }

    /**
     * Vanilla's anvil-damage lambda with the event's break chance in place of its 0.12. Replaced
     * whole rather than patching the constant inside a synthetic lambda, whose name is not stable.
     */
    @WrapOperation(method = "onTake", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/world/inventory/ContainerLevelAccess;execute(Ljava/util/function/BiConsumer;)V"))
    private void proficiency$anvilDamage(ContainerLevelAccess access, BiConsumer<Level, BlockPos> vanilla,
            Operation<Void> original, Player player) {
        float chance = proficiency$breakChance;
        proficiency$breakChance = 0.12F;
        original.call(access, (BiConsumer<Level, BlockPos>) (level, pos) -> {
            BlockState state = level.getBlockState(pos);
            if (!player.getAbilities().instabuild && state.is(BlockTags.ANVIL) && player.getRandom().nextFloat() < chance) {
                BlockState damaged = AnvilBlock.damage(state);
                if (damaged == null) {
                    level.removeBlock(pos, false);
                    level.levelEvent(1029, pos, 0);
                } else {
                    level.setBlock(pos, damaged, 2);
                    level.levelEvent(1030, pos, 0);
                }
            } else {
                level.levelEvent(1030, pos, 0);
            }
        });
    }
}
