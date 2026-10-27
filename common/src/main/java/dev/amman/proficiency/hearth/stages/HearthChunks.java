package dev.amman.proficiency.hearth.stages;

import dev.amman.proficiency.Proficiency;
import dev.amman.proficiency.hearth.HearthBlockEntity;
import dev.amman.proficiency.hearth.HearthIndexData;
import dev.amman.proficiency.config.ProficiencyConfig;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.TicketType;
import net.minecraft.world.level.ChunkPos;

import java.util.Comparator;
import java.util.HashSet;
import java.util.Set;

/**
 * The chunk ticket a running self-build holds (rules in core {@link ChunkHold}). One ticket per Hearth, on the
 * Hearth's own chunk only, of a type of our own: a vanilla region ticket, so it works the same on NeoForge, Fabric and
 * Forge 1.20.1, never touches a player's {@code /forceload} or another mod's ticket, and goes with the server. It is
 * not saved: {@link #restore} puts it back at server start for every saved running job.
 *
 * <p>Both Minecraft versions compile this file unchanged (forge-1.20.1's sharedHearthJava).
 */
public final class HearthChunks {

    /** Distance 2 is what a forced chunk uses: the chunk ticks (block entities, entities, random ticks). */
    private static final int DISTANCE = 2;

    /** No timeout (lifespan 0): the ticket stays until we remove it. */
    static final TicketType<BlockPos> TYPE = TicketType.create("proficiency_hearth_build",
            Comparator.comparingLong(BlockPos::asLong));

    private static final Set<String> HELD = new HashSet<>();

    private HearthChunks() {
    }

    private static String key(ServerLevel level, BlockPos pos) {
        return level.dimension().location() + "|" + pos.asLong();
    }

    /** Whether this Hearth's chunk is held by its build now. */
    public static boolean isHeld(ServerLevel level, BlockPos pos) {
        return HELD.contains(key(level, pos));
    }

    public static int heldCount() {
        return HELD.size();
    }

    static void hold(ServerLevel level, BlockPos pos) {
        if (HELD.add(key(level, pos))) {
            level.getChunkSource().addRegionTicket(TYPE, new ChunkPos(pos), DISTANCE, pos);
            HearthIndexData.get(level).setHeld(pos, true);
        }
    }

    /** Lets the chunk go (idempotent); true when a ticket was actually released. Nothing to do (no key built) when nothing is held. */
    public static boolean release(ServerLevel level, BlockPos pos) {
        if (HELD.isEmpty()) {
            return false;
        }
        if (HELD.remove(key(level, pos))) {
            level.getChunkSource().removeRegionTicket(TYPE, new ChunkPos(pos), DISTANCE, pos);
            HearthIndexData.get(level).setHeld(pos, false);
            return true;
        }
        return false;
    }

    /**
     * Server start: loads only the chunks of Hearths that held a ticket when the world was saved, and holds each
     * again when its saved job still runs. With {@code hearth.buildKeepsChunkLoaded} off it loads nothing and forgets
     * the saved record.
     */
    public static void restore(MinecraftServer server) {
        HELD.clear();
        boolean on = ProficiencyConfig.hearthBuildKeepsChunk();
        for (ServerLevel level : server.getAllLevels()) {
            HearthIndexData data = HearthIndexData.get(level);
            for (long packed : data.heldPositions()) {
                BlockPos pos = BlockPos.of(packed);
                try {
                    if (on && level.getChunk(pos.getX() >> 4, pos.getZ() >> 4) != null
                            && level.getBlockEntity(pos) instanceof HearthBlockEntity hearth) {
                        HearthAutoBuild.refreshHold(level, pos, hearth);
                    }
                } catch (RuntimeException e) {
                    Proficiency.LOG.warn("Hearth build: could not restore the chunk ticket at {}", pos, e);
                }
                if (!isHeld(level, pos)) {
                    data.setHeld(pos, false);
                }
            }
        }
    }

    /** Server stop: the tickets go with the server; forget our record. */
    public static void forgetAll() {
        HELD.clear();
    }
}
