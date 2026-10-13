package dev.amman.proficiency.gametest;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.common.util.BlockSnapshot;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.level.BlockEvent;
import net.minecraftforge.event.level.ChunkEvent;
import org.jetbrains.annotations.Nullable;

/**
 * Forge's own events as the ported Hearth GameTests post them (common's tests post the shared bus's
 * events, whose constructors differ). Each one is what the game posts at that moment.
 */
final class ForgeTestEvents {

    private ForgeTestEvents() {
    }

    /**
     * A block placement, posted after the block is in the world (Forge reads a player's placed block
     * from the world, an entity's from the snapshot, which is taken here). Returns whether it was
     * canceled.
     */
    static boolean place(ServerLevel level, BlockPos pos, BlockState placedAgainst, @Nullable Entity entity) {
        return MinecraftForge.EVENT_BUS.post(new BlockEvent.EntityPlaceEvent(
                BlockSnapshot.create(level.dimension(), level, pos), placedAgainst, entity));
    }

    /** The end of a player's tick ({@code PlayerTickEvent.Post} on 1.21.1). */
    static void playerTickEnd(Player player) {
        MinecraftForge.EVENT_BUS.post(new TickEvent.PlayerTickEvent(TickEvent.Phase.END, player));
    }

    /** A chunk finished loading ({@code ChunkLoadEvent} on 1.21.1): an old chunk, not a new one. */
    static void chunkLoaded(LevelChunk chunk) {
        MinecraftForge.EVENT_BUS.post(new ChunkEvent.Load(chunk, false));
    }
}
