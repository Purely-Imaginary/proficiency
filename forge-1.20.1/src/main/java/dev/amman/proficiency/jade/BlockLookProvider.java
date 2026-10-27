package dev.amman.proficiency.jade;

import dev.amman.proficiency.Proficiency;
import net.minecraft.resources.ResourceLocation;
import snownee.jade.api.BlockAccessor;
import snownee.jade.api.IBlockComponentProvider;
import snownee.jade.api.ITooltip;
import snownee.jade.api.config.IPluginConfig;

/**
 * What the block under the crosshair would pay the held tool, in Jade 11. Jade 11 carries server data only for
 * blocks with a block entity, so this provider asks through the mod's own small packet ({@link LookClient});
 * the lines are the same code the 1.21.1 plugin prints ({@link BlockLookView}).
 */
enum BlockLookProvider implements IBlockComponentProvider {
    INSTANCE;

    static final ResourceLocation ID = Proficiency.id("look_block");

    @Override
    public ResourceLocation getUid() {
        return ID;
    }

    @Override
    public void appendTooltip(ITooltip tooltip, BlockAccessor accessor, IPluginConfig config) {
        BlockLookView.append(tooltip, LookClient.get(accessor.getPosition(), accessor.getBlockState()), config);
    }
}
