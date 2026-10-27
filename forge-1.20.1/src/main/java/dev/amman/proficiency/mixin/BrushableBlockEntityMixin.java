package dev.amman.proficiency.mixin;

import com.mojang.logging.LogUtils;
import dev.amman.proficiency.skill.Archaeology;
import net.minecraft.core.Direction;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BrushableBlockEntity;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.storage.loot.LootTable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Archaeology (Excavation) on vanilla's brush: remembers the loot table the block was generated with
 * (vanilla forgets it at the first stroke), lets Careful Hands count a stroke twice, and when the
 * tenth stroke lands, before the block turns into its plain block, hands the find to
 * {@link Archaeology#completed}. The Forge 1.20.1 copy of common's mixin (listed in
 * {@code proficiency.mixins.json}); 1.20.1 keeps the loot table as a ResourceLocation.
 */
@Mixin(BrushableBlockEntity.class)
public abstract class BrushableBlockEntityMixin {

    @Shadow
    private int brushCount;
    @Shadow
    private long coolDownEndsAtTick;
    @Shadow
    private ItemStack item;
    @Shadow
    private Direction hitDirection;
    @Shadow
    private ResourceLocation lootTable;

    @Shadow
    abstract int getCompletionState();

    @Unique
    private ResourceLocation proficiency$table;
    @Unique
    private int proficiency$before;
    @Unique
    private ItemStack proficiency$more = ItemStack.EMPTY;

    @Shadow
    abstract void dropContent(Player player);

    @Inject(method = "brush", at = @At("HEAD"), require = 1)
    private void proficiency$brushHead(long time, Player player, Direction face, CallbackInfoReturnable<Boolean> cir) {
        proficiency$before = brushCount;
        if (lootTable != null) {
            proficiency$table = lootTable;
        }
    }

    @Inject(method = "brush", at = @At("RETURN"), require = 1)
    private void proficiency$brushReturn(long time, Player player, Direction face, CallbackInfoReturnable<Boolean> cir) {
        BlockEntity self = (BlockEntity) (Object) this;
        // Only a stroke the game counted this very call raised the counter by one; a stroke it
        // rejected (a second player in the same tick, the cooldown) left it alone.
        if (cir.getReturnValueZ() || !(self.getLevel() instanceof ServerLevel level)
                || brushCount != proficiency$before + 1) {
            return;
        }
        try {
            if (Archaeology.extraStroke(player, brushCount) > 0) {
                int before = getCompletionState();
                brushCount++;
                int after = getCompletionState();
                if (before != after) {
                    level.setBlock(self.getBlockPos(),
                            self.getBlockState().setValue(BlockStateProperties.DUSTED, after), 3);
                }
            }
        } catch (RuntimeException e) {
            LogUtils.getLogger().warn("Proficiency: Careful Hands failed", e);
        }
    }

    @Inject(method = "brushingCompleted", at = @At("HEAD"), require = 1)
    private void proficiency$completed(Player player, CallbackInfo ci) {
        BlockEntity self = (BlockEntity) (Object) this;
        proficiency$more = ItemStack.EMPTY;
        if (!(self.getLevel() instanceof ServerLevel level)) {
            return;
        }
        try {
            LootTable table = proficiency$table == null ? null
                    : level.getServer().getLootData().getLootTable(proficiency$table);
            proficiency$more = Archaeology.completed(level, self.getBlockPos(), self.getBlockState(), player, item, table);
        } catch (RuntimeException e) {
            LogUtils.getLogger().warn("Proficiency: brush XP failed", e);
        }
    }

    /** Careful Hands' second find drops through vanilla's own drop path, after the first find. */
    @Inject(method = "brushingCompleted", at = @At("TAIL"), require = 1)
    private void proficiency$secondFind(Player player, CallbackInfo ci) {
        ItemStack more = proficiency$more;
        proficiency$more = ItemStack.EMPTY;
        if (more.isEmpty()) {
            return;
        }
        try {
            item = more;
            dropContent(player);
        } catch (RuntimeException e) {
            LogUtils.getLogger().warn("Proficiency: Careful Hands second find failed", e);
        } finally {
            item = ItemStack.EMPTY;
        }
    }

    /** Vanilla forgets the loot table at the first stroke; keep it so a chunk reload cannot lose the second roll. */
    @Inject(method = "saveAdditional", at = @At("TAIL"), require = 1)
    private void proficiency$save(CompoundTag tag, CallbackInfo ci) {
        if (proficiency$table != null && lootTable == null) {
            tag.putString("proficiency_table", proficiency$table.toString());
        }
    }

    @Inject(method = "load", at = @At("TAIL"), require = 1)
    private void proficiency$load(CompoundTag tag, CallbackInfo ci) {
        if (tag.contains("proficiency_table", 8)) {
            proficiency$table = ResourceLocation.tryParse(tag.getString("proficiency_table"));
        }
    }
}
