package dev.amman.proficiency.recipe;

/**
 * Where a station recipe is made, and what you use on it. The ingredients go in or on the block
 * as dropped items. Using the tool on the block with them there makes the recipe.
 */
public enum Station {
    /** A water cauldron, stirred with a Ladle. The ingredients go into the water. */
    CAULDRON("cauldron", "proficiency:ladle"),
    /** Any anvil, struck with a Smithing Hammer. The ingredients lie on top. */
    ANVIL("anvil", "proficiency:smithing_hammer"),
    /** An enchanting table, used with a Book. The ingredients lie on top; the book becomes the result. */
    ENCHANTING_TABLE("enchanting_table", "minecraft:book");

    private final String id;
    private final String tool;

    Station(String id, String tool) {
        this.id = id;
        this.tool = tool;
    }

    public String id() {
        return id;
    }

    /** The item id you hold to work this station. */
    public String tool() {
        return tool;
    }

    public String translationKey() {
        return "proficiency.station." + id;
    }
}
