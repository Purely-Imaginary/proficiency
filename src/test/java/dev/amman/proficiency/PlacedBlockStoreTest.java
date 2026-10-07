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
}
