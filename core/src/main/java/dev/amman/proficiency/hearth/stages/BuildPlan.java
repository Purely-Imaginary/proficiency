package dev.amman.proficiency.hearth.stages;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * The self-build's plan as the plan screen shows it: per role, how many blocks the stage still needs, the block the
 * build takes and how many of it the sources hold for this role. Two roles may take the same block (a dark oak slab
 * for the wooden shapes and for the lantern posts): the stock goes to the rows in order, so a block counted for one
 * row is not counted again for the next. Pure: the game counts the stock.
 */
public final class BuildPlan {

    /** One row: the role, blocks still needed, the chosen block id ("" for none) and what is on hand for it. */
    public record Row(char role, int need, String block, int have, int missing) {
    }

    private BuildPlan() {
    }

    /** Blocks still needed per role (the MISSING cells of the diff), in first-seen scan order. */
    public static Map<Character, Integer> needs(List<StageGhost.Cell> diff) {
        Map<Character, Integer> out = new java.util.LinkedHashMap<>();
        for (StageGhost.Cell cell : diff) {
            if (cell.kind() == StageGhost.Kind.MISSING) {
                out.merge(cell.role(), 1, Integer::sum);
            }
        }
        return out;
    }

    /**
     * The rows: {@code chosen} maps a role to its block id, {@code stock} a block id to the count in all sources.
     * {@code have} is what is left of that block for this row after the rows above took theirs, capped at the need.
     */
    public static List<Row> rows(Map<Character, Integer> needs, Map<Character, String> chosen, Map<String, Integer> stock) {
        Map<String, Integer> left = new HashMap<>(stock);
        List<Row> out = new ArrayList<>();
        for (Map.Entry<Character, Integer> entry : needs.entrySet()) {
            char role = entry.getKey();
            int need = entry.getValue();
            String block = chosen.getOrDefault(role, "");
            int onHand = block.isEmpty() ? 0 : left.getOrDefault(block, 0);
            int have = Math.min(need, onHand);
            if (!block.isEmpty()) {
                left.put(block, onHand - have);
            }
            out.add(new Row(role, need, block, have, need - have));
        }
        return out;
    }

    /** Blocks the stage still needs in all, and how many of them are missing from the sources. */
    public static int[] totals(List<Row> rows) {
        int need = 0;
        int missing = 0;
        for (Row row : rows) {
            need += row.need();
            missing += row.missing();
        }
        return new int[] {need, missing};
    }
}
