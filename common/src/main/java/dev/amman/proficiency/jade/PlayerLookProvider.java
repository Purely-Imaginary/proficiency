package dev.amman.proficiency.jade;

import dev.amman.proficiency.Proficiency;
import dev.amman.proficiency.look.PlayerLookData;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import snownee.jade.api.EntityAccessor;
import snownee.jade.api.IEntityComponentProvider;
import snownee.jade.api.IServerDataProvider;
import snownee.jade.api.ITooltip;
import snownee.jade.api.config.IPluginConfig;

/**
 * Another player in Jade: their title, their three best skills and what the two of you gain together now.
 * Both loaders use Jade's own entity data channel (Jade 15 and Jade 11 alike), so no packet of the mod's own.
 * The server's {@code company.jadePlayers} switch decides in {@link PlayerLookData}; the client's "Players"
 * toggle in Jade's plugin settings is {@link ProficiencyJadePlugin#LOOK_PLAYERS}.
 */
enum PlayerLookProvider implements IEntityComponentProvider, IServerDataProvider<EntityAccessor> {
    INSTANCE;

    static final ResourceLocation ID = Proficiency.id("look_players");

    @Override
    public ResourceLocation getUid() {
        return ID;
    }

    @Override
    public void appendServerData(CompoundTag data, EntityAccessor accessor) {
        if (accessor.getPlayer() instanceof ServerPlayer player) {
            PlayerLookData.write(player, accessor.getEntity(), data);
        }
    }

    @Override
    public void appendTooltip(ITooltip tooltip, EntityAccessor accessor, IPluginConfig config) {
        if (config.get(ProficiencyJadePlugin.LOOK_PLAYERS)) {
            PlayerLookView.append(tooltip, accessor.getServerData());
        }
    }
}
