package dev.amman.proficiency.hearth.effects;

import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Which crafting recipes are reversible (docs/HEARTH-SPEC.md, "Reversible recipes"): a recipe whose
 * output crafts back into its input. Storage blocks, nuggets and ingots, hay bales, bone blocks.
 * Workshop's refund never applies to them, or a block and its ingots would print material in a loop.
 *
 * <p>Only <b>uniform</b> recipes count: every ingredient slot accepts the same set of items (nine
 * iron ingots, one iron block). A uniform recipe {@code A -> B} is reversible when some uniform
 * recipe takes {@code B} and gives one of {@code A}'s items. Both directions are marked. Worked out
 * once per datapack reload from the server's recipes.
 */
public final class ReversibleRecipes {

    /**
     * One crafting recipe, as ids: the recipe id, the result item and the item set each non-empty
     * ingredient slot accepts.
     */
    public record Shape(String id, String result, List<Set<String>> inputs) {

        /** The one item set every slot shares, or null when the slots differ (or there are none). */
        public Set<String> uniformInput() {
            if (inputs == null || inputs.isEmpty() || result == null || result.isEmpty()) {
                return null;
            }
            Set<String> first = inputs.get(0);
            if (first == null || first.isEmpty()) {
                return null;
            }
            for (Set<String> slot : inputs) {
                if (!first.equals(slot)) {
                    return null;
                }
            }
            return first;
        }
    }

    /** The answer: reversible recipe ids, and every (result, input item) pair of them. */
    public record Result(Set<String> ids, Set<String> pairs) {

        public static final Result NONE = new Result(Set.of(), Set.of());

        public boolean isReversible(String recipeId) {
            return recipeId != null && ids.contains(recipeId);
        }

        /** For a craft whose recipe is unknown: the result and an input item of a reversible pair. */
        public boolean isReversiblePair(String result, String input) {
            return pairs.contains(pair(result, input));
        }
    }

    private ReversibleRecipes() {
    }

    static String pair(String result, String input) {
        return result + "|" + input;
    }

    public static Result find(Collection<Shape> recipes) {
        // For every uniform recipe: what each input item can be crafted into.
        Map<String, Set<String>> into = new HashMap<>();
        for (Shape shape : recipes) {
            Set<String> input = shape.uniformInput();
            if (input == null) {
                continue;
            }
            for (String item : input) {
                into.computeIfAbsent(item, k -> new HashSet<>()).add(shape.result());
            }
        }
        Set<String> ids = new HashSet<>();
        Set<String> pairs = new HashSet<>();
        for (Shape shape : recipes) {
            Set<String> input = shape.uniformInput();
            if (input == null) {
                continue;
            }
            Set<String> back = into.getOrDefault(shape.result(), Set.of());
            boolean reversible = false;
            for (String item : input) {
                if (back.contains(item)) {
                    reversible = true;
                    break;
                }
            }
            if (reversible) {
                ids.add(shape.id());
                for (String item : input) {
                    pairs.add(pair(shape.result(), item));
                }
            }
        }
        return new Result(Collections.unmodifiableSet(ids), Collections.unmodifiableSet(pairs));
    }
}
