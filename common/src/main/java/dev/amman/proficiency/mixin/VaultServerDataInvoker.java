package dev.amman.proficiency.mixin;

import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.entity.vault.VaultServerData;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

/** Vanilla's "has this player already been rewarded by this vault" is package-private. */
@Mixin(VaultServerData.class)
public interface VaultServerDataInvoker {

    @Invoker("hasRewardedPlayer")
    boolean proficiency$hasRewardedPlayer(Player player);
}
