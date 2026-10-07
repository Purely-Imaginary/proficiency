package dev.amman.proficiency;

import dev.amman.proficiency.event.CraftingEvents;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class HaggleCostTest {

    @Test
    void noSkillPaysFullPrice() {
        assertEquals(12, CraftingEvents.haggled(12, 0.0));
    }

    @Test
    void theDiscountScalesWithTheBonus() {
        assertEquals(6, CraftingEvents.haggled(12, 0.5));
    }

    @Test
    void theDiscountIsCappedAtThreeQuarters() {
        assertEquals(3, CraftingEvents.haggled(12, 2.0));
    }

    @Test
    void aRepairAlwaysCostsAtLeastOneLevel() {
        assertEquals(1, CraftingEvents.haggled(1, 0.75));
        assertEquals(1, CraftingEvents.haggled(2, 0.75));
    }

    @Test
    void nothingToTakeStaysNothing() {
        assertEquals(0, CraftingEvents.haggled(0, 0.75));
    }
}
