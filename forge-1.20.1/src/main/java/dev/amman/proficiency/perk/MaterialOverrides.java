package dev.amman.proficiency.perk;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.amman.proficiency.Proficiency;
import dev.amman.proficiency.skill.Skill;
import net.minecraft.resources.ResourceLocation;

import java.io.IOException;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.function.Predicate;

/**
 * Per-pack replacements for talent material lists, read from {@code config/proficiency-materials.json}.
 *
 * <p>The shipped lists assume a normal world. A pack that changes what exists (Reclamation has no
 * ore, no living trees and no villagers at the start) ships this file next to its other configs and
 * swaps whole tiers for things its players can actually get. No file means the shipped lists.
 *
 * <pre>{@code
 * { "skills": { "woodcutting": { "1": [ {"item": "kubejs:dead_log", "count": 64} ] } } }
 * }</pre>
 *
 * Tier 1 is the root (level 10), 2 and 3 the two level-60 nodes, 4 the level-90 keystone. A tier
 * that is named replaces that whole list. Entries are plain item ids (no tags: a tag's display name
 * needs a lang key per tag, and no override has needed one).
 * Nothing about a player's save changes: nodes are paid all at once and keyed by name, so a node
 * already paid stays paid and one not yet paid simply asks for the new list.
 */
public final class MaterialOverrides {

    public static final String FILE_NAME = "proficiency-materials.json";

    /** What the parser made of a file: the tiers it can apply, and every problem it skipped. */
    public record Parsed(Map<Skill, Map<Integer, List<Requirement>>> tiers, List<String> errors) {

        public int tierCount() {
            return tiers.values().stream().mapToInt(Map::size).sum();
        }

        /** Every item id named, in file order, for checking against a registry. */
        public Set<ResourceLocation> itemIds() {
            Set<ResourceLocation> ids = new LinkedHashSet<>();
            tiers.values().forEach(bySkill -> bySkill.values().forEach(
                    list -> list.forEach(requirement -> ids.add(new ResourceLocation(requirement.itemId())))));
            return ids;
        }
    }

    private static Parsed loaded = new Parsed(Map.of(), List.of());
    private static Path loadedFrom;

    private MaterialOverrides() {
    }

    /** Parses without touching the game: unknown skills, bad tiers and bad entries become errors. */
    public static Parsed parse(Reader reader) {
        List<String> errors = new ArrayList<>();
        Map<Skill, Map<Integer, List<Requirement>>> tiers = new EnumMap<>(Skill.class);
        JsonElement root;
        try {
            root = JsonParser.parseReader(reader);
        } catch (RuntimeException e) {
            return new Parsed(Map.of(), List.of("not valid JSON: " + e.getMessage()));
        }
        if (!root.isJsonObject() || !root.getAsJsonObject().has("skills")
                || !root.getAsJsonObject().get("skills").isJsonObject()) {
            return new Parsed(Map.of(), List.of("no \"skills\" object at the top level"));
        }
        for (Map.Entry<String, JsonElement> skillEntry : root.getAsJsonObject().getAsJsonObject("skills").entrySet()) {
            Skill skill = Skill.byId(skillEntry.getKey());
            if (skill == null) {
                errors.add("unknown skill '" + skillEntry.getKey() + "'");
                continue;
            }
            if (!skillEntry.getValue().isJsonObject()) {
                errors.add(skill.id() + ": expected an object of tiers");
                continue;
            }
            Map<Integer, List<Requirement>> bySkill = new TreeMap<>();
            for (Map.Entry<String, JsonElement> tierEntry : skillEntry.getValue().getAsJsonObject().entrySet()) {
                String where = skill.id() + " tier " + tierEntry.getKey();
                int tier;
                try {
                    tier = Integer.parseInt(tierEntry.getKey());
                } catch (NumberFormatException e) {
                    errors.add(where + ": tier must be 1.." + Talents.TIERS);
                    continue;
                }
                if (tier < 1 || tier > Talents.TIERS) {
                    errors.add(where + ": tier must be 1.." + Talents.TIERS);
                    continue;
                }
                if (!tierEntry.getValue().isJsonArray()) {
                    errors.add(where + ": expected a list");
                    continue;
                }
                List<Requirement> list = parseList(tierEntry.getValue().getAsJsonArray(), where, errors);
                if (list != null) {
                    bySkill.put(tier, List.copyOf(list));
                }
            }
            if (!bySkill.isEmpty()) {
                tiers.put(skill, bySkill);
            }
        }
        return new Parsed(tiers, List.copyOf(errors));
    }

    /** Null when the list is unusable; a half-read list would make a node cheaper than meant. */
    private static List<Requirement> parseList(JsonArray array, String where, List<String> errors) {
        List<Requirement> list = new ArrayList<>();
        Set<String> seen = new HashSet<>();
        for (JsonElement element : array) {
            if (!element.isJsonObject()) {
                errors.add(where + ": every entry needs {\"item\", \"count\"}");
                return null;
            }
            JsonObject entry = element.getAsJsonObject();
            int count = entry.has("count") && entry.get("count").isJsonPrimitive()
                    ? entry.get("count").getAsInt() : 0;
            if (count <= 0) {
                errors.add(where + ": count must be a positive number");
                return null;
            }
            String item = entry.has("item") && entry.get("item").isJsonPrimitive()
                    ? entry.get("item").getAsString() : null;
            ResourceLocation parsed = item == null ? null : ResourceLocation.tryParse(item);
            if (parsed == null) {
                errors.add(where + ": entry needs a valid \"item\" id");
                return null;
            }
            // The same item twice in one node would be counted twice and taken twice.
            if (!seen.add(item)) {
                errors.add(where + ": " + item + " is listed twice");
                return null;
            }
            list.add(new Requirement(parsed.toString(), null, count));
        }
        return list;
    }

    /**
     * Reads the file from the config directory, if there is one, and applies it. Called from the
     * mod constructor, so the trees are right before any screen or player sees them. Problems are
     * logged and the affected tier keeps its shipped list; a broken file never stops the game.
     */
    public static void load(Path configDir) {
        Path file = configDir.resolve(FILE_NAME);
        if (!Files.isRegularFile(file)) {
            return;
        }
        try (Reader reader = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
            Parsed parsed = parse(reader);
            for (String error : parsed.errors()) {
                Proficiency.LOG.warn("[proficiency] {}: {} (that entry keeps its default list)", FILE_NAME, error);
            }
            Talents.applyMaterialOverrides(parsed.tiers());
            loaded = parsed;
            loadedFrom = file;
            Proficiency.LOG.info("[proficiency] {}: replaced {} material lists in {} skills",
                    FILE_NAME, parsed.tierCount(), parsed.tiers().size());
        } catch (IOException | RuntimeException e) {
            Proficiency.LOG.error("[proficiency] could not read {}; using the default materials", file, e);
        }
    }

    /**
     * Run once the item registry is complete. Names every override id the registry does not know,
     * then every requirement still unavailable in this instance, so a pack author sees both the
     * typo and the gap without opening a tree screen.
     */
    public static void validate(Predicate<ResourceLocation> itemExists) {
        if (loadedFrom != null) {
            List<ResourceLocation> unknown = loaded.itemIds().stream().filter(id -> !itemExists.test(id)).toList();
            if (unknown.isEmpty()) {
                Proficiency.LOG.info("[proficiency] {}: all {} override item ids exist", FILE_NAME,
                        loaded.itemIds().size());
            } else {
                Proficiency.LOG.warn("[proficiency] {}: unknown item ids, those requirements will be skipped: {}",
                        FILE_NAME, unknown);
            }
        }
        Set<String> missing = new LinkedHashSet<>();
        for (Skill skill : Skill.VALUES) {
            for (int tier = 1; tier <= Talents.TIERS; tier++) {
                for (Requirement requirement : Talents.tierMaterials(skill, tier)) {
                    if (requirement.tag() == null && !itemExists.test(new ResourceLocation(requirement.itemId()))) {
                        missing.add(skill.id() + "/" + tier + ":" + requirement.itemId());
                    }
                }
            }
        }
        if (!missing.isEmpty()) {
            Proficiency.LOG.info("[proficiency] material requirements skipped in this instance (item not present): {}",
                    missing);
        }
    }
}
