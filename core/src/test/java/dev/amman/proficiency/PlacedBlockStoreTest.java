package dev.amman.proficiency;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.amman.proficiency.skill.PlacedBlockStore;
import org.junit.jupiter.api.Test;

class PlacedBlockStoreTest {

    @Test
    void marksAreFoundAndCleared() {
        PlacedBlockStore store = new PlacedBlockStore();
        store.mark(5, 64, -3, 123);
        assertTrue(store.isMarked(5, 64, -3));
        assertEquals(123, store.hashAt(5, 64, -3));
        assertFalse(store.isMarked(5, 65, -3));
        assertTrue(store.clear(5, 64, -3));
        assertFalse(store.clear(5, 64, -3));
        assertTrue(store.isEmpty());
    }

    @Test
    void encodeDecodeKeepsEveryMarkIncludingNegativeCoordinates() {
        PlacedBlockStore store = new PlacedBlockStore();
        int[][] spots = {{0, -64, 0}, {15, 319, 15}, {-1, 0, -1}, {-17, 70, 33}, {1000, 12, -1000}, {-1, -64, 16}};
        for (int i = 0; i < spots.length; i++) {
            store.mark(spots[i][0], spots[i][1], spots[i][2], -1000 - i);
        }
        PlacedBlockStore copy = new PlacedBlockStore();
        for (long key : store.chunkKeys()) {
            copy.decode(key, store.encode(key));
        }
        assertEquals(spots.length, copy.size());
        for (int i = 0; i < spots.length; i++) {
            assertTrue(copy.isMarked(spots[i][0], spots[i][1], spots[i][2]), "lost " + i);
            assertEquals(-1000 - i, copy.hashAt(spots[i][0], spots[i][1], spots[i][2]));
        }
        assertFalse(copy.isMarked(2, 2, 2));
    }

    @Test
    void chunkKeyRoundTrips() {
        long key = PlacedBlockStore.chunkKey(-5, 7);
        assertEquals(-5, PlacedBlockStore.chunkX(key));
        assertEquals(7, PlacedBlockStore.chunkZ(key));
    }

    @Test
    void encodedChunksAreSorted() {
        PlacedBlockStore store = new PlacedBlockStore();
        for (int i = 20; i >= 0; i--) {
            store.mark(i % 16, 60 + i, 3, i);
        }
        long[] encoded = store.encode(PlacedBlockStore.chunkKey(0, 0));
        for (int i = 1; i < encoded.length; i++) {
            assertTrue(encoded[i - 1] < encoded[i]);
        }
    }

    @Test
    void forEachInChunkVisitsAbsolutePositionsInTheHeightBand() {
        PlacedBlockStore store = new PlacedBlockStore();
        int[][] spots = {{-17, 70, 33}, {-20, 12, 40}, {-18, 300, 47}, {-1, 70, 33}, {5, 70, 33}};
        for (int i = 0; i < spots.length; i++) {
            store.mark(spots[i][0], spots[i][1], spots[i][2], 100 + i);
        }
        java.util.Map<String, Integer> seen = new java.util.HashMap<>();
        // Chunk (-2, 2) holds x -32..-17 and z 32..47: the first three spots.
        store.forEachInChunk(-2, 2, 0, 100, (x, y, z, hash) -> seen.put(x + "," + y + "," + z, hash));
        assertEquals(2, seen.size(), "the band 0..100 leaves out the mark at y 300: " + seen);
        assertEquals(100, seen.get("-17,70,33"));
        assertEquals(101, seen.get("-20,12,40"));
        seen.clear();
        store.forEachInChunk(-2, 2, -64, 319, (x, y, z, hash) -> seen.put(x + "," + y + "," + z, hash));
        assertEquals(3, seen.size());
        assertEquals(102, seen.get("-18,300,47"));
        seen.clear();
        store.forEachInChunk(0, 2, -64, 319, (x, y, z, hash) -> seen.put(x + "," + y + "," + z, hash));
        assertEquals(java.util.Map.of("5,70,33", 104), seen);
        store.forEachInChunk(9, 9, -64, 319, (x, y, z, hash) -> org.junit.jupiter.api.Assertions.fail("an empty chunk"));
    }
}
