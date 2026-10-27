package dev.amman.proficiency.jade;

import dev.amman.proficiency.Proficiency;
import dev.amman.proficiency.look.HearthLookData;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import snownee.jade.api.BlockAccessor;
import snownee.jade.api.IBlockComponentProvider;
import snownee.jade.api.IServerDataProvider;
import snownee.jade.api.ITooltip;
import snownee.jade.api.config.IPluginConfig;

/**
 * The Hearth in Jade: its stage and design lane, the charge tier and the pool feeding it, your Warmth, and
 * the self-build's status line (the same line the panel prints, so they never disagree).
 */
enum HearthLookProvider implements IBlockComponentProvider, IServerDataProvider<BlockAccessor> {
    INSTANCE;

    static final ResourceLocation ID = Proficiency.id("look_hearth");

    @Override
    public ResourceLocation getUid() {
        return ID;
    }

    @Override
    public void appendServerData(CompoundTag data, BlockAccessor accessor) {
        if (accessor.getPlayer() instanceof ServerPlayer player
                && accessor.getBlockEntity() instanceof dev.amman.proficiency.hearth.HearthBlockEntity hearth) {
            HearthLookData.write(player, hearth, data);
        }
    }

    @Override
    public void appendTooltip(ITooltip tooltip, BlockAccessor accessor, IPluginConfig config) {
        CompoundTag data = accessor.getServerData();
        if (!data.contains(HearthLookData.STAGE)) {
            return;
        }
        for (Component line : LookText.hearthLines(data)) {
            tooltip.add(line);
        }
    }
}
