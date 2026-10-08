package dev.amman.proficiency;

import dev.amman.proficiency.event.GatheringMath;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class GatheringMathTest {

    @Test
    void saplingFollowsTheLogNamingConvention() {
        assertEquals("oak_sapling", GatheringMath.saplingPathFor("oak_log"));
        assertEquals("dark_oak_sapling", GatheringMath.saplingPathFor("dark_oak_log"));
        assertEquals("birch_sapling", GatheringMath.saplingPathFor("stripped_birch_log"));
    }

    @Test
    void logsOutsideTheConventionHaveNoSapling() {
        assertNull(GatheringMath.saplingPathFor("crimson_stem"));
        assertNull(GatheringMath.saplingPathFor("oak_wood"));
        assertNull(GatheringMath.saplingPathFor("_log"));
        assertNull(GatheringMath.saplingPathFor("logbook"));
    }

    @Test
    void timberLimitScalesWithPowerUpToTheCeiling() {
        assertEquals(128, GatheringMath.timberLimit(128, 1.0, 256));
        assertEquals(186, GatheringMath.timberLimit(128, 1.45, 256));
        assertEquals(256, GatheringMath.timberLimit(192, 1.8, 256));
        // Power below one never shrinks the lag guard.
        assertEquals(128, GatheringMath.timberLimit(128, 0.5, 256));
    }

    @Test
    void perRankIsCappedAtCertainty() {
        assertEquals(0.15, GatheringMath.perRank(3, 0.05), 1e-9);
        assertEquals(1.0, GatheringMath.perRank(3, 0.50), 1e-9);
        assertEquals(0.0, GatheringMath.perRank(0, 0.10), 1e-9);
    }
}
