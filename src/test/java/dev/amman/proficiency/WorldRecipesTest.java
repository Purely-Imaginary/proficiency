package dev.amman.proficiency;

import dev.amman.proficiency.perk.Talent;
import dev.amman.proficiency.perk.Talents;
import dev.amman.proficiency.recipe.Station;
import dev.amman.proficiency.recipe.WorldRecipe;
import dev.amman.proficiency.recipe.WorldRecipes;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The station recipe table: every recipe is taught, named, and reachable. */
class WorldRecipesTest {

    @Test
    void everyRecipeIsTaughtByANodeInItsOwnTree() {
        for (WorldRecipe recipe : WorldRecipes.all()) {
            boolean taught = Talents.of(recipe.skill()).stream()
                    .map(Talent::special).anyMatch(recipe.unlock()::equals);
            assertTrue(taught, recipe.id() + " is taught by nothing in " + recipe.skill().id());
        }
    }

    @Test
    void everyStationHasARecipeAndNoTwoRecipesShareIngredients() {
        Set<Station> used = new HashSet<>();
        Set<String> shapes = new HashSet<>();
        for (WorldRecipe recipe : WorldRecipes.all()) {
            used.add(recipe.station());
            assertTrue(shapes.add(recipe.station() + "" + recipe.ingredients()), recipe.id() + " duplicates another");
            assertTrue(recipe.count() >= 1 && recipe.xp() > 0, recipe.id());
        }
        assertEquals(Set.of(Station.values()), used);
    }

    @Test
    void theExactIngredientsMatchAndOneShortDoesNot() {
        for (WorldRecipe recipe : WorldRecipes.all()) {
            Map<String, Integer> exact = new HashMap<>(recipe.ingredients());
            assertEquals(recipe, WorldRecipes.match(recipe.station(), exact), recipe.id());
            String first = exact.keySet().iterator().next();
            exact.merge(first, -1, Integer::sum);
            WorldRecipe short1 = WorldRecipes.match(recipe.station(), exact);
            assertTrue(short1 != recipe, recipe.id() + " matched one ingredient short");
        }
    }

    @Test
    void extraItemsOnTheStationDoNotStopARecipe() {
        WorldRecipe stew = WorldRecipes.all().stream().filter(r -> r.id().equals("miners_stew")).findFirst().orElseThrow();
        Map<String, Integer> pot = new HashMap<>(stew.ingredients());
        pot.put("minecraft:dirt", 5);
        assertEquals(stew, WorldRecipes.match(Station.CAULDRON, pot));
        assertNull(WorldRecipes.match(Station.ANVIL, pot));
    }

    @Test
    void anUnknownRecipeIsSkippedForAKnownOne() {
        Map<String, Integer> pot = new HashMap<>();
        for (WorldRecipe recipe : WorldRecipes.all()) {
            if (recipe.station() == Station.CAULDRON) {
                recipe.ingredients().forEach((id, n) -> pot.merge(id, n, Integer::sum));
            }
        }
        WorldRecipe known = WorldRecipes.matchKnown(Station.CAULDRON, pot, r -> r.unlock().equals("camp_kitchen"));
        assertTrue(known != null && known.unlock().equals("camp_kitchen"));
    }

    @Test
    void everyResultAndEnchantmentHasAnEnglishName() throws IOException {
        String lang = Files.readString(Path.of("src/main/resources/assets/proficiency/lang/en_us.json"));
        // Forge 1.20.1: enchantments are registered classes, not data files (1.21's
        // data/proficiency/enchantment/*.json), so the definition is the registration.
        String enchantments = Files.readString(
                Path.of("src/main/java/dev/amman/proficiency/item/ProficiencyEnchantments.java"));
        for (WorldRecipe recipe : WorldRecipes.all()) {
            String key = recipe.enchantment() != null
                    ? "enchantment." + recipe.enchantment().replace(':', '.')
                    : "item." + recipe.result().replace(':', '.');
            if (key.startsWith("item.minecraft.")) {
                continue;
            }
            assertTrue(lang.contains("\"" + key + "\""), key + " has no name");
            if (recipe.enchantment() != null) {
                String path = recipe.enchantment().substring(recipe.enchantment().indexOf(':') + 1);
                assertTrue(enchantments.contains("ENCHANTMENTS.register(\"" + path + "\""),
                        recipe.enchantment() + " has no definition");
            }
        }
    }
}
