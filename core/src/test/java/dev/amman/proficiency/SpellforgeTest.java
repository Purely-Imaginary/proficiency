package dev.amman.proficiency;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.amman.proficiency.perk.Spellforge;
import dev.amman.proficiency.perk.Synergies;
import dev.amman.proficiency.perk.Synergy;
import dev.amman.proficiency.skill.Skill;
import java.util.Iterator;
import java.util.List;
import java.util.function.DoubleSupplier;
import org.junit.jupiter.api.Test;

class SpellforgeTest {

    private static DoubleSupplier rolls(Double... values) {
        Iterator<Double> it = List.of(values).iterator();
        return () -> it.hasNext() ? it.next() : 1.0;
    }

    @Test
    void theGiftComesMoreOftenButNeverPastCertain() {
        assertEquals(0.45, Spellforge.giftChance(0.45, false), 1e-9);
        assertEquals(0.675, Spellforge.giftChance(0.45, true), 1e-9);
        assertEquals(0.45, Spellforge.giftChance(0.30, true), 1e-9);
        assertEquals(1.0, Spellforge.giftChance(0.9, true), 1e-9);
    }

    @Test
    void theStepUpGrowsWithSpellcasting() {
        assertEquals(0.25, Spellforge.stepChance(0), 1e-9);
        assertEquals(0.375, Spellforge.stepChance(50), 1e-9);
        assertEquals(0.50, Spellforge.stepChance(100), 1e-9);
        assertEquals(0.50, Spellforge.stepChance(400), 1e-9);
    }

    @Test
    void withoutTheSynergyAGiftIsAlwaysLevelOne() {
        assertEquals(1, Spellforge.giftLevel(false, 100, 5, rolls(0.0, 0.0, 0.0)));
    }

    @Test
    void eachStepIsItsOwnRollAndStopsAtThree() {
        assertEquals(1, Spellforge.giftLevel(true, 0, 5, rolls(0.30)));
        assertEquals(2, Spellforge.giftLevel(true, 0, 5, rolls(0.10, 0.30)));
        assertEquals(3, Spellforge.giftLevel(true, 0, 5, rolls(0.10, 0.10, 0.0)));
        assertEquals(2, Spellforge.giftLevel(true, 100, 5, rolls(0.45, 0.55)));
    }

    @Test
    void aGiftNeverPassesTheEnchantmentsOwnTop() {
        assertEquals(1, Spellforge.giftLevel(true, 100, 1, rolls(0.0, 0.0)));
        assertEquals(2, Spellforge.giftLevel(true, 100, 2, rolls(0.0, 0.0)));
        assertEquals(1, Spellforge.giftLevel(true, 100, 0, rolls(0.0)));
    }

    @Test
    void theSynergyPairsSmithingWithSpellcasting() {
        Synergy synergy = Synergies.byId("spellforged");
        assertNotNull(synergy);
        assertEquals("spellforged", synergy.special());
        assertTrue(synergy.involves(Skill.SMITHING));
        assertTrue(synergy.involves(Skill.SPELLCASTING));
    }
}
