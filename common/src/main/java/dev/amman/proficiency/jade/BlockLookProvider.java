package dev.amman.proficiency.jade;

import dev.amman.proficiency.Proficiency;
import dev.amman.proficiency.look.XpLookData;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;
import snownee.jade.api.BlockAccessor;
import snownee.jade.api.IBlockComponentProvider;
import snownee.jade.api.IServerDataProvider;
import snownee.jade.api.ITooltip;
import snownee.jade.api.config.IPluginConfig;

/**
 * What the block under the crosshair would pay the held tool, in Jade. The server half
 * ({@link XpLookData#block}) asks the XP code's own resolvers; the client half only prints the answer.
 */
enum BlockLookProvider implements IBlockComponentProvider, IServerDataProvider<BlockAccessor> {
    INSTANCE;

    static final ResourceLocation ID = Proficiency.id("look_block");

    @Override
    public ResourceLocation getUid() {
        return ID;
    }

    @Override
    public void appendServerData(CompoundTag data, BlockAccessor accessor) {
        if (accessor.getPlayer() instanceof ServerPlayer player && accessor.getLevel() instanceof ServerLevel level) {
            XpLookData.block(player, level, accessor.getPosition(), data);
        }
    }

    /**
     * Client side, before Jade asks the server: a block that answered "nothing" for this held item (dirt for
     * a stick, glass) is not asked about again for a second, so looking around costs no round trip per quarter
     * second. A block that pays keeps being asked, because its number moves with Tempo.
     */
    @Override
    public boolean shouldRequestData(BlockAccessor accessor) {
        Quiet quiet = QUIET;
        return quiet == null || !quiet.matches(accessor) || accessor.getLevel().getGameTime() - quiet.at >= QUIET_TICKS;
    }

    @Override
    public void appendTooltip(ITooltip tooltip, BlockAccessor accessor, IPluginConfig config) {
        CompoundTag data = accessor.getServerData();
        if (BlockLookView.skillOf(data) == null) {
            if (QUIET == null || !QUIET.matches(accessor)) {
                QUIET = new Quiet(accessor);
            }
        } else {
            QUIET = null;
        }
        BlockLookView.append(tooltip, data, config);
    }

    private static final long QUIET_TICKS = 20;
    private static Quiet QUIET;

    private static final class Quiet {
        final BlockPos pos;
        final BlockState state;
        final ItemStack held;
        final long at;

        Quiet(BlockAccessor accessor) {
            this.pos = accessor.getPosition();
            this.state = accessor.getBlockState();
            this.held = accessor.getPlayer().getMainHandItem().copy();
            this.at = accessor.getLevel().getGameTime();
        }

        boolean matches(BlockAccessor accessor) {
            return pos.equals(accessor.getPosition()) && state == accessor.getBlockState()
                    && ItemStack.isSameItemSameComponents(held, accessor.getPlayer().getMainHandItem());
        }
    }
}
