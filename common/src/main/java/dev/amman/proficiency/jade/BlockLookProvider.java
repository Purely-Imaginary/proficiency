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
import snownee.jade.impl.ObjectDataCenter;

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
        if (heldChanged(accessor)) {
            // The tool in hand changed (item or its components): the answer on screen is for the old one. Jade
            // asks again only when its own timer (about half a second) runs out, so wind the timer back and ask now.
            QUIET = null;
            try {
                ObjectDataCenter.requestServerData();
            } catch (Throwable ignored) {
                // A Jade without this hook just keeps its own timer.
            }
            return true;
        }
        Quiet quiet = QUIET;
        return quiet == null || !quiet.matches(accessor) || accessor.getLevel().getGameTime() - quiet.at >= QUIET_TICKS;
    }

    @Override
    public void appendTooltip(ITooltip tooltip, BlockAccessor accessor, IPluginConfig config) {
        CompoundTag data = accessor.getServerData();
        if (DEBUG) {
            debugLatency(accessor, data);
        }
        if (BlockLookView.skillOf(data) == null) {
            if (QUIET == null || !QUIET.matches(accessor)) {
                QUIET = new Quiet(accessor);
            }
        } else {
            QUIET = null;
        }
        BlockLookView.append(tooltip, data, config);
    }

    /** Debug (-Dproficiency.lookDebug=true): ticks from a new target or tool to the first XP line. */
    private static final boolean DEBUG = Boolean.getBoolean("proficiency.lookDebug");
    private static BlockPos dbgPos;
    private static ItemStack dbgHeld = ItemStack.EMPTY;
    private static long dbgSince = Long.MIN_VALUE;

    private static void debugLatency(BlockAccessor accessor, CompoundTag data) {
        long now = accessor.getLevel().getGameTime();
        ItemStack held = accessor.getPlayer().getMainHandItem();
        if (!accessor.getPosition().equals(dbgPos) || !ItemStack.isSameItemSameComponents(dbgHeld, held)) {
            dbgPos = accessor.getPosition();
            dbgHeld = held.copy();
            dbgSince = now;
        }
        if (dbgSince != Long.MIN_VALUE && !held.isEmpty() && BlockLookView.skillOf(data) != null) {
            Proficiency.LOG.info("LOOKLAG target-to-line {} ticks", now - dbgSince);
            dbgSince = Long.MIN_VALUE;
        }
    }

    private static ItemStack lastHeld = ItemStack.EMPTY;

    private static boolean heldChanged(BlockAccessor accessor) {
        ItemStack now = accessor.getPlayer().getMainHandItem();
        if (ItemStack.isSameItemSameComponents(lastHeld, now)) {
            return false;
        }
        lastHeld = now.copy();
        return true;
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
