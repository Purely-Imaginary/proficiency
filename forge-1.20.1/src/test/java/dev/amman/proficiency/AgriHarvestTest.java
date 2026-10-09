package dev.amman.proficiency;

import dev.amman.proficiency.skill.AgriHarvest;
import dev.amman.proficiency.skill.AgriHarvest.Snapshot;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AgriHarvestTest {

    private static final Snapshot MATURE = new Snapshot(true, "minecraft:wheat", 7, true);
    private static final Snapshot REVERTED = new Snapshot(true, "minecraft:wheat", 3, false);
    private static final Snapshot BARE = new Snapshot(false, "", 0, false);

    @Test
    void aMatureCropSetBackAStageIsAHarvest() {
        assertTrue(AgriHarvest.isHarvest(MATURE, REVERTED));
    }

    @Test
    void anUnchangedCropIsNotAHarvest() {
        assertFalse(AgriHarvest.isHarvest(MATURE, MATURE));
    }

    @Test
    void aGrowingCropIsNotAHarvest() {
        Snapshot young = new Snapshot(true, "minecraft:wheat", 2, false);
        Snapshot older = new Snapshot(true, "minecraft:wheat", 3, false);
        assertFalse(AgriHarvest.isHarvest(young, older));
        assertFalse(AgriHarvest.isHarvest(older, young));
    }

    @Test
    void anImmatureCropSetBackIsNotAHarvest() {
        Snapshot young = new Snapshot(true, "minecraft:wheat", 4, false);
        assertFalse(AgriHarvest.isHarvest(young, REVERTED));
    }

    @Test
    void aPlantThatVanishedIsNotAHarvest() {
        // A trowel lifting a mature plant, or weeds killing it.
        assertFalse(AgriHarvest.isHarvest(MATURE, BARE));
    }

    @Test
    void aDifferentPlantIsNotAHarvest() {
        Snapshot other = new Snapshot(true, "minecraft:potato", 0, false);
        assertFalse(AgriHarvest.isHarvest(MATURE, other));
    }

    @Test
    void missingSnapshotsAreNeverAHarvest() {
        assertFalse(AgriHarvest.isHarvest(null, REVERTED));
        assertFalse(AgriHarvest.isHarvest(MATURE, null));
        assertFalse(AgriHarvest.isHarvest(new Snapshot(true, null, 7, true), REVERTED));
    }

    @Test
    void plantingNeedsAPlantThatWasNotThere() {
        assertTrue(AgriHarvest.isPlanting(BARE, REVERTED));
        assertTrue(AgriHarvest.isPlanting(null, REVERTED));
        assertFalse(AgriHarvest.isPlanting(REVERTED, REVERTED));
        assertFalse(AgriHarvest.isPlanting(BARE, BARE));
        assertFalse(AgriHarvest.isPlanting(BARE, null));
    }

    @Test
    void breakingPaysOnlyAMaturePlant() {
        assertTrue(AgriHarvest.breakPays(MATURE));
        assertFalse(AgriHarvest.breakPays(REVERTED));
        assertFalse(AgriHarvest.breakPays(BARE));
        assertFalse(AgriHarvest.breakPays(null));
    }

    @Test
    void plantKeysMatchAgriCraftsLangKeys() {
        assertEquals("plant.agricraft.minecraft.wheat", AgriHarvest.plantKey("minecraft:wheat"));
        assertEquals("plant.agricraft.agricraft.diamahlia", AgriHarvest.plantKey("agricraft:diamahlia"));
        assertEquals("plant.agricraft.agricraft.petinia", AgriHarvest.plantKey("petinia"));
        assertEquals("plant.agricraft.agricraft.unknown", AgriHarvest.plantKey(null));
        assertEquals("plant.agricraft.agricraft.unknown", AgriHarvest.plantKey(""));
    }

    @Test
    void plantKeysAreDistinctPerPlantSoTheFirstTimeBonusIsPerPlant() {
        assertNotEquals(AgriHarvest.plantKey("minecraft:wheat"), AgriHarvest.plantKey("minecraft:potato"));
    }

    @Test
    void thePayKeySeparatesTicksPositionsAndDimensions() {
        assertEquals(AgriHarvest.payKey("overworld", 5L, 100L), AgriHarvest.payKey("overworld", 5L, 100L));
        assertNotEquals(AgriHarvest.payKey("overworld", 5L, 100L), AgriHarvest.payKey("overworld", 5L, 101L));
        assertNotEquals(AgriHarvest.payKey("overworld", 5L, 100L), AgriHarvest.payKey("overworld", 6L, 100L));
        assertNotEquals(AgriHarvest.payKey("overworld", 5L, 100L), AgriHarvest.payKey("the_nether", 5L, 100L));
    }
}
