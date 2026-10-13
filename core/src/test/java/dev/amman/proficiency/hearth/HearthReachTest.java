package dev.amman.proficiency.hearth;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class HearthReachTest {

    @Test
    void theRadiusClimbsFromBaseToMaxAsReachFills() {
        assertEquals(24, HearthReach.radius(24, 192, 0, 5000));
        assertEquals(108, HearthReach.radius(24, 192, 2500, 5000));
        assertEquals(192, HearthReach.radius(24, 192, 5000, 5000));
        assertEquals(192, HearthReach.radius(24, 192, 1e9, 5000));
        assertEquals(24, HearthReach.radius(24, 192, Double.NaN, 5000));
    }

    @Test
    void aMaxBelowTheBaseNeverShrinksTheRange() {
        assertEquals(24, HearthReach.radius(24, 10, 5000, 5000));
    }

    @Test
    void aFullOfZeroMeansAlwaysFull() {
        assertEquals(192, HearthReach.radius(24, 192, 0, 0));
    }

    @Test
    void reachIsSavedAndNeverNegative() {
        HearthState state = new HearthState();
        state.addReach(120.5);
        state.addReach(-50);
        state.addReach(Double.POSITIVE_INFINITY);
        assertEquals(120.5, state.reach(), 1e-9);
        var tag = HearthState.CODEC.encodeStart(com.mojang.serialization.JavaOps.INSTANCE, state).getOrThrow(m -> new AssertionError(m));
        HearthState back = HearthState.CODEC.parse(com.mojang.serialization.JavaOps.INSTANCE, tag).getOrThrow(m -> new AssertionError(m));
        assertEquals(120.5, back.reach(), 1e-9);
    }

    @Test
    void theIndexUsesEachHearthsOwnRadius() {
        HearthIndexStore store = new HearthIndexStore();
        long home = HearthPos.pack(0, 64, 0);
        long other = HearthPos.pack(300, 64, 0);
        store.add(home);
        store.add(other);
        HearthGeometry.Range base = new HearthGeometry.Range(24, 8, 16);
        assertEquals(HearthIndexStore.NONE, store.hearthAt(100, 64, 0, base));
        assertTrue(store.setRadius(home, 150));
        assertFalse(store.setRadius(home, 150));
        assertEquals(home, store.hearthAt(100, 64, 0, base));
        // Heights stay the base ones.
        assertEquals(HearthIndexStore.NONE, store.hearthAt(100, 64 + 17, 0, base));
        // Overlap: the nearer Hearth wins.
        store.setRadius(other, 250);
        assertEquals(other, store.hearthAt(160, 64, 0, base));
        assertEquals(250.0, store.maxRadius(24), 1e-9);
        long[] saved = store.encode();
        long[] radii = store.encodeRadii();
        HearthIndexStore loaded = new HearthIndexStore();
        loaded.decode(saved);
        loaded.decodeRadii(radii);
        assertEquals(home, loaded.hearthAt(100, 64, 0, base));
        loaded.remove(home);
        assertEquals(24.0, loaded.radius(home, 24), 1e-9);
    }
}
