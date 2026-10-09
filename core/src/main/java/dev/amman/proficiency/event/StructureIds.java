package dev.amman.proficiency.event;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Structure variants that are one discovery to a player. Vanilla has five village ids, seven ruined
 * portal ids and two ocean ruin ids, and paying Wayfaring XP for each made "found a village" pay
 * five times. Pure on purpose: no game classes, so a unit test can hold the table.
 */
public final class StructureIds {

    private static final String PREFIX = "structure:";

    /**
     * Whole structures that a mod swaps in for a vanilla one. They count as the vanilla discovery, so
     * a stronghold is a stronghold (and grand) whichever mod generated it, and nobody is paid twice
     * for the same idea. Ids from the Pandowo jars (Yung's Better series, Detailed and Terrain
     * replacements).
     */
    private static final Map<String, String> REPLACEMENTS = Map.of(
            "betterstrongholds:stronghold", "minecraft:stronghold",
            "betteroceanmonuments:ocean_monument", "minecraft:monument",
            "betterfortresses:fortress", "minecraft:fortress",
            "betterjungletemples:jungle_temple", "minecraft:jungle_pyramid",
            "betterwitchhuts:witch_hut", "minecraft:swamp_hut",
            "nova_structures:illager_manor", "minecraft:mansion");

    /**
     * Families of biome variants: an id that starts with the prefix is one discovery. Yung's Better
     * Mineshafts has 13 ids and Repurposed Structures about 100, each used to be a full Wayfaring
     * discovery. The prefix includes the underscore, so {@code mansion_} does not catch a
     * different structure that merely starts with the same letters.
     */
    private static final Map<String, String> FAMILIES = new LinkedHashMap<>();

    static {
        FAMILIES.put("bettermineshafts:mineshaft_", "minecraft:mineshaft");
        String rs = "repurposed_structures:";
        FAMILIES.put(rs + "ancient_city_", "minecraft:ancient_city");
        FAMILIES.put(rs + "fortress_", "minecraft:fortress");
        FAMILIES.put(rs + "igloo_", "minecraft:igloo");
        FAMILIES.put(rs + "mansion_", "minecraft:mansion");
        FAMILIES.put(rs + "mineshaft_", "minecraft:mineshaft");
        FAMILIES.put(rs + "monument_", "minecraft:monument");
        FAMILIES.put(rs + "outpost_", "minecraft:pillager_outpost");
        FAMILIES.put(rs + "pyramid_", "minecraft:desert_pyramid");
        FAMILIES.put(rs + "ruined_portal_", "minecraft:ruined_portal");
        FAMILIES.put(rs + "shipwreck_", "minecraft:shipwreck");
        FAMILIES.put(rs + "stronghold_", "minecraft:stronghold");
        FAMILIES.put(rs + "village_", "minecraft:village");
        FAMILIES.put(rs + "witch_hut_", "minecraft:swamp_hut");
        // The nether temples are their own thing, the ocean and taiga ones are jungle temples.
        FAMILIES.put(rs + "temple_nether_", rs + "temple_nether");
        FAMILIES.put(rs + "temple_", "minecraft:jungle_pyramid");
        FAMILIES.put(rs + "ruins_land_", rs + "ruins_land");
    }

    private StructureIds() {
    }

    /** The id a variant is counted under. Modded and unrelated vanilla ids come back unchanged. */
    public static String canonical(String id) {
        String replaced = REPLACEMENTS.get(id);
        if (replaced != null) {
            return replaced;
        }
        for (Map.Entry<String, String> family : FAMILIES.entrySet()) {
            if (id.startsWith(family.getKey())) {
                return family.getValue();
            }
        }
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
     * was paid before the collapse (any key whose canonical id is this one, so a variant that no
     * list ever named still counts), so nobody is paid twice for a village they already found.
     */
    public static boolean alreadySeen(String canonicalId, Collection<String> visitedKeys) {
        if (visitedKeys.contains(visitedKey(canonicalId))) {
            return true;
        }
        for (String key : visitedKeys) {
            if (key.startsWith(PREFIX) && canonical(key.substring(PREFIX.length())).equals(canonicalId)) {
                return true;
            }
        }
        return false;
    }

    /** Every canonical structure id this player has a key for, canonical or from before the collapse. */
    public static java.util.Set<String> seenCanonicals(Collection<String> visitedKeys) {
        java.util.Set<String> out = new java.util.HashSet<>();
        for (String key : visitedKeys) {
            if (key.startsWith(PREFIX)) {
                out.add(canonical(key.substring(PREFIX.length())));
            }
        }
        return out;
    }

    /**
     * Registry ids worth pointing a compass at: every id whose canonical type is unseen. Variants
     * all stay in, so the nearest village of any biome counts. Buried treasure is left out because
     * it has no visible start to walk to. The visited keys are folded once, not once per id.
     */
    public static List<String> unvisitedTargets(Collection<String> registryIds, Collection<String> visitedKeys) {
        java.util.Set<String> seen = seenCanonicals(visitedKeys);
        List<String> out = new ArrayList<>();
        for (String id : registryIds) {
            String canonical = canonical(id);
            if (canonical.equals("minecraft:buried_treasure") || seen.contains(canonical)) {
                continue;
            }
            out.add(id);
        }
        return out;
    }
}
