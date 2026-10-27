package dev.amman.proficiency.hearth.stages;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * The materials of a stage design as the guide lists them ("Stone 24 · Wood 32 · Iron bars 4"): the cells
 * of the whole structure grouped by material. Stone and its shapes are one group, logs, planks and wooden
 * shapes another. The air, the blank and the fire are not materials.
 */
public final class StageMaterials {

    /** Group ids in the order the guide prints them. */
    public static final String[] GROUPS = {"stone", "wood", "banner", "iron", "copper", "gold", "iron_bars", "chain",
            "lantern", "anvil", "cauldron", "bell", "lightning_rod", "lodestone",
            "flower_pot", "storage", "candle", "carpet", "glass", "skull", "ice", "snow", "moss", "magma", "obsidian",
            "leaves", "water", "lava"};

    private StageMaterials() {
    }

    /** The group of a role letter, or null for air, the fire, the blank and unknown letters. */
    public static String group(char role) {
        return switch (role) {
            case 'S', 's' -> "stone";
            case 'W', 'P', 'w', 'p' -> "wood";
            case 'N' -> "banner";
            case 'i' -> "iron";
            case 'c' -> "copper";
            case 'g' -> "gold";
            case 'b' -> "iron_bars";
            case 'h' -> "chain";
            case 'l' -> "lantern";
            case 'a' -> "anvil";
            case 'u' -> "cauldron";
            case 'B' -> "bell";
            case 'r' -> "lightning_rod";
            case 'k' -> "lodestone";
            case 'v' -> "water";
            case 'V' -> "lava";
            case 'f' -> "flower_pot";
            case 'x' -> "storage";
            case 'C' -> "candle";
            case 'm' -> "carpet";
            case 'G' -> "glass";
            case 'z' -> "skull";
            case 'I' -> "ice";
            case 'n' -> "snow";
            case 'q' -> "moss";
            case 'Y' -> "magma";
            case 'O' -> "obsidian";
            case 'L' -> "leaves";
            default -> null;
        };
    }

    /** Whether a group is counted in buckets (a fluid source block is one bucket), not in blocks. */
    public static boolean inBuckets(String group) {
        return "water".equals(group) || "lava".equals(group);
    }

    /** Group id to block count for the whole stage, in {@link #GROUPS} order; groups with none are left out. */
    public static Map<String, Integer> count(HearthDesigns.Stage stage) {
        int[] counts = new int[GROUPS.length];
        for (HearthDesigns.Cell cell : stage.cells()) {
            String group = group(cell.role());
            if (group == null) {
                continue;
            }
            for (int i = 0; i < GROUPS.length; i++) {
                if (GROUPS[i].equals(group)) {
                    counts[i]++;
                    break;
                }
            }
        }
        Map<String, Integer> out = new LinkedHashMap<>();
        for (int i = 0; i < GROUPS.length; i++) {
            if (counts[i] > 0) {
                out.put(GROUPS[i], counts[i]);
            }
        }
        return out;
    }
}
