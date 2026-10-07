package dev.amman.proficiency.event;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.function.Predicate;

/**
 * Structure variants that are one discovery to a player. Vanilla has five village ids, seven ruined
 * portal ids and two ocean ruin ids, and paying Wayfaring XP for each made "found a village" pay
 * five times. Pure on purpose: no game classes, so a unit test can hold the table.
 */
public final class StructureIds {

    private static final String PREFIX = "structure:";

    /** Every variant id that shipped a visited key before variants were collapsed. */
    private static final List<String> LEGACY_VARIANTS = List.of(
            "minecraft:village_plains", "minecraft:village_desert", "minecraft:village_savanna",
            "minecraft:village_snowy", "minecraft:village_taiga",
            "minecraft:ruined_portal_desert", "minecraft:ruined_portal_jungle",
            "minecraft:ruined_portal_swamp", "minecraft:ruined_portal_mountain",
            "minecraft:ruined_portal_ocean", "minecraft:ruined_portal_nether",
            "minecraft:shipwreck_beached",
            "minecraft:ocean_ruin_cold", "minecraft:ocean_ruin_warm",
            "minecraft:mineshaft_mesa");

    private StructureIds() {
    }

    /** The id a variant is counted under. Modded and unrelated vanilla ids come back unchanged. */
    public static String canonical(String id) {
        if (id.startsWith("minecraft:ruined_portal")) {
            return "minecraft:ruined_portal";
        }
        if (id.startsWith("minecraft:village_")) {
            return "minecraft:village";
        }
        if (id.startsWith("minecraft:ocean_ruin_")) {
            return "minecraft:ocean_ruin";
        }
        return switch (id) {
            case "minecraft:shipwreck_beached" -> "minecraft:shipwreck";
            case "minecraft:mineshaft_mesa" -> "minecraft:mineshaft";
            default -> id;
        };
    }

    /** The visited-set key for a canonical id. */
    public static String visitedKey(String canonicalId) {
        return PREFIX + canonicalId;
    }

    /**
     * Whether the player has been here already: the canonical key, or the key of any variant that
     * was paid before the collapse, so nobody is paid twice for a village they already found.
     */
    public static boolean alreadySeen(String canonicalId, Predicate<String> visited) {
        if (visited.test(visitedKey(canonicalId))) {
            return true;
        }
        for (String variant : LEGACY_VARIANTS) {
            if (canonical(variant).equals(canonicalId) && visited.test(visitedKey(variant))) {
                return true;
            }
        }
        return false;
    }

    /**
     * Registry ids worth pointing a compass at: every id whose canonical type is unseen. Variants
     * all stay in, so the nearest village of any biome counts. Buried treasure is left out because
     * it has no visible start to walk to.
     */
    public static List<String> unvisitedTargets(Collection<String> registryIds, Predicate<String> visited) {
        List<String> out = new ArrayList<>();
        for (String id : registryIds) {
            String canonical = canonical(id);
            if (canonical.equals("minecraft:buried_treasure") || alreadySeen(canonical, visited)) {
                continue;
            }
            out.add(id);
        }
        return out;
    }
}
