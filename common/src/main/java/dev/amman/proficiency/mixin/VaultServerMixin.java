package dev.amman.proficiency.mixin;

import com.mojang.logging.LogUtils;
import dev.amman.proficiency.event.TrialEvents;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.VaultBlock;
import net.minecraft.world.level.block.entity.vault.VaultBlockEntity;
import net.minecraft.world.level.block.entity.vault.VaultConfig;
import net.minecraft.world.level.block.entity.vault.VaultServerData;
import net.minecraft.world.level.block.entity.vault.VaultSharedData;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Trial Chambers: a key that opened a vault added the player to the vault's rewarded set during the
 * call. Shared by NeoForge and Fabric; Forge 1.20.1 has no vaults.
 */
@Mixin(VaultBlockEntity.Server.class)
public abstract class VaultServerMixin {

    @Unique
    private static boolean proficiency$had;

    @Inject(method = "tryInsertKey", at = @At("HEAD"), require = 1)
    private static void proficiency$head(ServerLevel level, BlockPos pos, BlockState state, VaultConfig config,
            VaultServerData serverData, VaultSharedData sharedData, Player player, ItemStack stack,
            CallbackInfo ci) {
        proficiency$had = ((VaultServerDataInvoker) serverData).proficiency$hasRewardedPlayer(player);
    }

    @Inject(method = "tryInsertKey", at = @At("RETURN"), require = 1)
    private static void proficiency$opened(ServerLevel level, BlockPos pos, BlockState state, VaultConfig config,
            VaultServerData serverData, VaultSharedData sharedData, Player player, ItemStack stack,
            CallbackInfo ci) {
        if (proficiency$had || !((VaultServerDataInvoker) serverData).proficiency$hasRewardedPlayer(player)) {
            return;
        }
        try {
            TrialEvents.onVaultOpened(level, pos, state.getValue(VaultBlock.OMINOUS), player);
        } catch (RuntimeException e) {
            LogUtils.getLogger().warn("Proficiency: vault bonus failed", e);
        }
    }
}
