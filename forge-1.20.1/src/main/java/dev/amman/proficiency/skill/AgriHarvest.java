package dev.amman.proficiency.skill;

/**
 * The pure decisions of the AgriCraft soft compat (Forge 1.20.1 only), kept free of Minecraft and
 * of AgriCraft so they are unit tested. The game side reads a {@link Snapshot} of a crop's block
 * entity before and after an interaction and asks these methods what happened.
 *
 * <p>AgriCraft crops are one block ({@code agricraft:crop}) whose plant, growth stage and
 * maturity live in a block entity. The usual harvest is a right-click that drops the produce and
 * sets the plant back a stage; nothing is broken, so only a before/after comparison can see it.
 */
public final class AgriHarvest {

    private AgriHarvest() {
    }

    /** What a crop block entity said at one moment. {@code stage} is AgriCraft's growth index. */
    public record Snapshot(boolean hasPlant, String plantId, int stage, boolean mature) {
    }

    /**
     * A harvest: the plant was mature, is still the same plant, and its growth stage went down.
     * A plant that vanished is not a harvest (a trowel lifting it, weeds killing it, a clipper
     * taking cuttings), so picking up and replanting a mature plant pays nothing.
     */
    public static boolean isHarvest(Snapshot before, Snapshot after) {
        return before != null && after != null
                && before.hasPlant() && before.mature()
                && after.hasPlant()
                && before.plantId() != null && before.plantId().equals(after.plantId())
                && after.stage() < before.stage();
    }

    /** Planting: the spot had no plant (or was not a crop yet) and now has one. */
    public static boolean isPlanting(Snapshot before, Snapshot after) {
        return after != null && after.hasPlant() && (before == null || !before.hasPlant());
    }

    /** Breaking pays only a mature plant, as breaking a ripe vanilla crop does. */
    public static boolean breakPays(Snapshot at) {
        return at != null && at.hasPlant() && at.mature();
    }

    /**
     * The translation key AgriCraft gives a plant, which is also the XP log label and the
     * first-time-bonus kind: {@code minecraft:wheat} is {@code plant.agricraft.minecraft.wheat}.
     * A bare id (no namespace) is read as AgriCraft's own.
     */
    public static String plantKey(String plantId) {
        if (plantId == null || plantId.isEmpty()) {
            return "plant.agricraft.agricraft.unknown";
        }
        int colon = plantId.indexOf(':');
        String namespace = colon < 0 ? "agricraft" : plantId.substring(0, colon);
        String path = colon < 0 ? plantId : plantId.substring(colon + 1);
        return "plant.agricraft." + namespace + "." + path.replace('/', '.');
    }

    /** Keys the "paid this tick" guard: one dimension, one position, one game tick. */
    public static String payKey(String dimension, long packedPos, long gameTime) {
        return dimension + "|" + packedPos + "|" + gameTime;
    }
}
