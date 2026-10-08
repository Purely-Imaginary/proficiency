package dev.amman.proficiency.recipe;

import dev.amman.proficiency.skill.Skill;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Every station recipe. A talent node teaches them; {@link StationEvents} makes them.
 *
 * <p>Pure data and matching, so the tests need no game. Ingredients are item ids.
 */
public final class WorldRecipes {

    private static final List<WorldRecipe> ALL = new ArrayList<>();

    static {
        // Cooking, Camp Kitchen (level 10)
        add("miners_stew", Station.CAULDRON, Skill.COOKING, "camp_kitchen", "proficiency:miners_stew", 1, null, 6,
                "minecraft:bowl", 1, "minecraft:cooked_beef", 1, "minecraft:baked_potato", 1, "minecraft:brown_mushroom", 1);
        add("trail_ration", Station.CAULDRON, Skill.COOKING, "camp_kitchen", "proficiency:trail_ration", 3, null, 4,
                "minecraft:bread", 1, "minecraft:dried_kelp", 2, "minecraft:sweet_berries", 2);
        // Cooking, Family Recipe (level 60)
        add("warriors_pierogi", Station.CAULDRON, Skill.COOKING, "family_recipe", "proficiency:warriors_pierogi", 4, null, 8,
                "minecraft:wheat", 3, "minecraft:cooked_porkchop", 1, "minecraft:egg", 1);
        add("fishermans_chowder", Station.CAULDRON, Skill.COOKING, "family_recipe", "proficiency:fishermans_chowder", 1, null, 8,
                "minecraft:bowl", 1, "minecraft:cooked_cod", 1, "minecraft:cooked_salmon", 1, "minecraft:kelp", 1);
        // Smithing, Weaponwright (level 60)
        add("butchers_cleaver", Station.ANVIL, Skill.SMITHING, "weaponwright", "proficiency:butchers_cleaver", 1, null, 20,
                "minecraft:iron_sword", 1, "minecraft:iron_ingot", 3, "minecraft:leather", 1);
        add("duelists_rapier", Station.ANVIL, Skill.SMITHING, "weaponwright", "proficiency:duelists_rapier", 1, null, 20,
                "minecraft:iron_sword", 1, "minecraft:gold_ingot", 2, "proficiency:masterwork_ingot", 1);
        // Alchemy, Runescribe (level 60). The book in your hand is the base, so it is not listed.
        add("lifedrinker", Station.ENCHANTING_TABLE, Skill.ALCHEMY, "runescribe", "minecraft:enchanted_book", 1,
                "proficiency:lifedrinker", 15,
                "minecraft:ghast_tear", 1, "minecraft:glistering_melon_slice", 2, "minecraft:lapis_lazuli", 4);
        add("magnetism", Station.ENCHANTING_TABLE, Skill.ALCHEMY, "runescribe", "minecraft:enchanted_book", 1,
                "proficiency:magnetism", 15,
                "minecraft:iron_ingot", 4, "minecraft:redstone", 4, "minecraft:lapis_lazuli", 4);
    }

    private WorldRecipes() {
    }

    private static void add(String id, Station station, Skill skill, String unlock, String result, int count,
            @Nullable String enchantment, double xp, Object... pairs) {
        Map<String, Integer> ingredients = new LinkedHashMap<>();
        for (int i = 0; i < pairs.length; i += 2) {
            ingredients.put((String) pairs[i], (Integer) pairs[i + 1]);
        }
        ALL.add(new WorldRecipe(id, station, skill, unlock, Collections.unmodifiableMap(ingredients),
                result, count, enchantment, xp));
    }

    public static List<WorldRecipe> all() {
        return Collections.unmodifiableList(ALL);
    }

    /**
     * The recipe these items make at this station, known or not. When several fit, the one that
     * uses the most items wins, so a bigger recipe is never hidden by a smaller one inside it.
     */
    @Nullable
    public static WorldRecipe match(Station station, Map<String, Integer> available) {
        return ALL.stream()
                .filter(recipe -> recipe.station() == station && recipe.fits(available))
                .max(Comparator.comparingInt(WorldRecipe::ingredientCount))
                .orElse(null);
    }

    /** Same, but only among the recipes {@code known} says the player has learned. */
    @Nullable
    public static WorldRecipe matchKnown(Station station, Map<String, Integer> available,
            java.util.function.Predicate<WorldRecipe> known) {
        return ALL.stream()
                .filter(recipe -> recipe.station() == station && recipe.fits(available) && known.test(recipe))
                .max(Comparator.comparingInt(WorldRecipe::ingredientCount))
                .orElse(null);
    }
}
