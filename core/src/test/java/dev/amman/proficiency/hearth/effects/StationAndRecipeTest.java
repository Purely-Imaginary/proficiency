package dev.amman.proficiency.hearth.effects;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Workshop: the station carry and the reversible-recipe set (abuse check 12, the pure part). */
class StationAndRecipeTest {

    /** A furnace run tick by tick the way the mixin runs it: vanilla's step, then the extra. */
    private static int furnaceTicks(double speed, int total) {
        int progress = 0;
        float carry = 0f;
        int lit = 100_000;
        for (int tick = 1; tick < 10_000; tick++) {
            progress++;
            lit--;
            if (progress == total) {
                return tick;
            }
            carry = StationMath.carryAfter(carry, speed);
            int add = StationMath.extra(carry, StationMath.furnaceRoom(progress, total, lit));
            progress += add;
            lit -= add;
            carry = StationMath.carryLeft(carry, add);
        }
        return -1;
    }

    @Test
    void aFurnaceRunsFasterAndAlwaysFinishesOnVanillasTick() {
        assertEquals(200, furnaceTicks(0.0, 200));
        int fast = furnaceTicks(0.40, 200);
        assertTrue(fast >= 142 && fast <= 144, "40% faster: about 200 / 1.4 ticks, got " + fast);
        int slow = furnaceTicks(0.08, 200);
        assertTrue(slow >= 185 && slow <= 187, "8% faster, got " + slow);
        // Never past the total, whatever the speed.
        assertTrue(furnaceTicks(5.0, 200) > 0);
    }

    @Test
    void extraProgressStopsOneShortAndNeedsFuel() {
        assertEquals(0, StationMath.furnaceRoom(199, 200, 50));
        assertEquals(9, StationMath.furnaceRoom(190, 200, 50));
        assertEquals(0, StationMath.furnaceRoom(10, 200, 1), "the last tick of fuel has no room");
        assertEquals(1, StationMath.extra(1.4f, 9));
        assertEquals(0, StationMath.extra(1.4f, 0));
        assertEquals(1f, StationMath.carryLeft(3.5f, 0), 1e-6, "a blocked station banks at most one step");
        assertEquals(0f, StationMath.carryAfter(0.7f, 0.0), 1e-6, "no speed, no carry");
        assertEquals(399, StationMath.brewRoom(400));
        assertEquals(0, StationMath.brewRoom(1));
    }

    private static ReversibleRecipes.Shape uniform(String id, String result, String input, int count) {
        List<Set<String>> slots = new java.util.ArrayList<>();
        for (int i = 0; i < count; i++) {
            slots.add(Set.of(input));
        }
        return new ReversibleRecipes.Shape(id, result, slots);
    }

    @Test
    void storageBlocksAndNuggetsAreReversibleAndToolsAreNot() {
        List<ReversibleRecipes.Shape> recipes = List.of(
                uniform("minecraft:iron_block", "minecraft:iron_block", "minecraft:iron_ingot", 9),
                uniform("minecraft:iron_ingot_from_iron_block", "minecraft:iron_ingot", "minecraft:iron_block", 1),
                uniform("minecraft:iron_ingot_from_nuggets", "minecraft:iron_ingot", "minecraft:iron_nugget", 9),
                uniform("minecraft:iron_nugget", "minecraft:iron_nugget", "minecraft:iron_ingot", 1),
                // Planks from any log (a tag): no way back.
                new ReversibleRecipes.Shape("minecraft:oak_planks", "minecraft:oak_planks",
                        List.of(Set.of("minecraft:oak_log", "minecraft:oak_wood"))),
                // Bone -> bone meal is one way; bone meal <-> bone block goes both ways.
                uniform("minecraft:bone_meal", "minecraft:bone_meal", "minecraft:bone", 1),
                uniform("minecraft:bone_block", "minecraft:bone_block", "minecraft:bone_meal", 9),
                uniform("minecraft:bone_meal_from_bone_block", "minecraft:bone_meal", "minecraft:bone_block", 1),
                // A chest (uniform, but nothing crafts it back) and a mixed recipe.
                uniform("minecraft:chest", "minecraft:chest", "minecraft:oak_planks", 8),
                new ReversibleRecipes.Shape("minecraft:iron_pickaxe", "minecraft:iron_pickaxe",
                        List.of(Set.of("minecraft:iron_ingot"), Set.of("minecraft:iron_ingot"),
                                Set.of("minecraft:iron_ingot"), Set.of("minecraft:stick"), Set.of("minecraft:stick"))),
                new ReversibleRecipes.Shape("broken", "", List.of()));
        ReversibleRecipes.Result result = ReversibleRecipes.find(recipes);
        assertTrue(result.isReversible("minecraft:iron_block"));
        assertTrue(result.isReversible("minecraft:iron_ingot_from_iron_block"));
        assertTrue(result.isReversible("minecraft:iron_ingot_from_nuggets"));
        assertTrue(result.isReversible("minecraft:iron_nugget"));
        assertTrue(result.isReversible("minecraft:bone_block"));
        assertTrue(result.isReversible("minecraft:bone_meal_from_bone_block"));
        assertFalse(result.isReversible("minecraft:bone_meal"));
        assertFalse(result.isReversible("minecraft:oak_planks"));
        assertFalse(result.isReversible("minecraft:chest"));
        assertFalse(result.isReversible("minecraft:iron_pickaxe"));
        assertFalse(result.isReversible("broken"));
        assertFalse(result.isReversible(null));
        assertTrue(result.isReversiblePair("minecraft:iron_block", "minecraft:iron_ingot"));
        assertTrue(result.isReversiblePair("minecraft:iron_ingot", "minecraft:iron_block"));
        assertFalse(result.isReversiblePair("minecraft:chest", "minecraft:oak_planks"));
        assertEquals(6, result.ids().size());
    }
}
