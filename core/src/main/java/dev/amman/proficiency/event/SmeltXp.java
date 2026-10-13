package dev.amman.proficiency.event;

/**
 * Smithing XP per item taken from a furnace, from the recipe's own experience value.
 *
 * <p>Every non-food smelt used to pay a flat 1 XP an item, so a furnace full of logs turned into
 * charcoal paid exactly what iron ore did. On Reclamation that was 74% of all smelting XP in a week
 * (balance report 2026-10-12). Vanilla already ranks smelts by worth in the recipe's experience:
 * charcoal 0.15, stone and glass 0.1, iron and copper 0.7, gold 1.0. Iron stays at 1 XP an item;
 * the rest scale from it, between a floor and a ceiling. A modded smelt with no recipe found pays
 * as iron, which is what every smelt paid before.
 */
public final class SmeltXp {

    /** The recipe experience that pays 1 XP an item (iron, copper). */
    public static final double REFERENCE = 0.7;
    public static final double MIN_PER_ITEM = 0.1;
    public static final double MAX_PER_ITEM = 1.5;

    private SmeltXp() {
    }

    /**
     * @param recipeXp the highest experience of a smelting or blasting recipe that makes the item,
     *                 or a negative number when none was found
     * @return Smithing XP for one item
     */
    public static double perItem(double recipeXp) {
        if (Double.isNaN(recipeXp) || recipeXp < 0) {
            return 1.0;
        }
        return Math.max(MIN_PER_ITEM, Math.min(MAX_PER_ITEM, recipeXp / REFERENCE));
    }
}
