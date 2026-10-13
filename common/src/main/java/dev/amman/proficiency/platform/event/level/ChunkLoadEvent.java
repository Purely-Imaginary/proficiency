package dev.amman.proficiency.platform.event.level;

import dev.amman.proficiency.platform.bus.Event;
import net.minecraft.world.level.LevelAccessor;
import net.minecraft.world.level.chunk.ChunkAccess;

/**
 * A chunk has finished loading (from disk or freshly generated): its blocks can be read. NeoForge
 * relays its own {@code ChunkEvent.Load}; Fabric posts it from API's {@code ServerChunkEvents.CHUNK_LOAD},
 * which only ever fires on the server. Handlers check that the level is a {@code ServerLevel}.
 */
public class ChunkLoadEvent extends Event {

    private final LevelAccessor level;
    private final ChunkAccess chunk;

    public ChunkLoadEvent(LevelAccessor level, ChunkAccess chunk) {
        this.level = level;
        this.chunk = chunk;
    }

    public LevelAccessor getLevel() {
        return level;
    }

    public ChunkAccess getChunk() {
        return chunk;
    }
}
