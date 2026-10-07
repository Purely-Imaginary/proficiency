package dev.amman.proficiency.recipe;

import dev.amman.proficiency.skill.Skill;
import org.jetbrains.annotations.Nullable;

import java.util.Map;

/**
 * One station recipe.
 *
 * @param ingredients item id to count, all of which must be in or on the station
 * @param unlock      the talent special that teaches it, in {@code skill}'s tree
 * @param enchantment for an enchanting-table recipe, the enchantment id the book gets; else null
 */
public record WorldRecipe(
        String id,
        Station station,
        Skill skill,
        String unlock,
        Map<String, Integer> ingredients,
        String result,
        int count,
        @Nullable String enchantment,
        double xp) {

    public int ingredientCount() {
        int total = 0;
        for (int n : ingredients.values()) {
            total += n;
        }
        return total;
    }

    /** True when {@code available} holds at least every ingredient. */
    public boolean fits(Map<String, Integer> available) {
        for (Map.Entry<String, Integer> need : ingredients.entrySet()) {
            if (available.getOrDefault(need.getKey(), 0) < need.getValue()) {
                return false;
            }
        }
        return true;
    }
}
