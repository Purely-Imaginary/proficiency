package dev.amman.proficiency.hearth.client;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.amman.proficiency.Proficiency;
import dev.amman.proficiency.hearth.net.HearthStatePayload;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ServerData;
import net.minecraft.server.MinecraftServer;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * The Hearths this client has seen, for drawing their ranges on a map (docs/HEARTH-REACH-SPEC.md,
 * "Map"). Every state packet names a Hearth's position, dimension and radius; the newest wins. Kept
 * per world (singleplayer save or server address) in {@code config/proficiency-hearth-map.json},
 * so the map shows home after a relog too, together with whether the overlay is on.
 *
 * <p>Client thread only. {@link #version()} goes up on every change, so a map can redraw.
 */
public final class HearthMapMarks {

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final String FILE = "proficiency-hearth-map.json";

    /** dimension -> packed position -> radius, for the current world. */
    private static final Map<String, Map<Long, Integer>> MARKS = new HashMap<>();
    private static String world;
    private static boolean shown = true;
    private static int version;

    private HearthMapMarks() {
    }

    /** A state packet arrived: remember (or move) that Hearth's range. */
    public static void note(HearthStatePayload payload) {
        if (payload.reason() == HearthStatePayload.CAMPFIRE || payload.dimension().isEmpty()) {
            return;
        }
        ensureLoaded();
        int radius = Math.round(payload.radius());
        Integer old = MARKS.computeIfAbsent(payload.dimension(), d -> new LinkedHashMap<>()).put(payload.pos(), radius);
        if (old == null || old != radius) {
            version++;
            save();
        }
    }

    /** This dimension's Hearths and their radii; empty when the overlay is off. Do not modify. */
    public static Map<Long, Integer> in(String dimension) {
        ensureLoaded();
        if (!shown) {
            return Map.of();
        }
        Map<Long, Integer> marks = MARKS.get(dimension);
        return marks == null ? Map.of() : marks;
    }

    public static boolean shown() {
        ensureLoaded();
        return shown;
    }

    /** The key: flips the overlay and returns the new state. */
    public static boolean toggle() {
        ensureLoaded();
        shown = !shown;
        version++;
        save();
        return shown;
    }

    public static int version() {
        ensureLoaded();
        return version;
    }

    /** Forgets everything in memory (logout); the file stays. */
    public static void reset() {
        MARKS.clear();
        world = null;
        version++;
    }

    // ---- Per-world file ---------------------------------------------------------------------------

    private static String worldId() {
        Minecraft mc = Minecraft.getInstance();
        MinecraftServer server = mc.getSingleplayerServer();
        if (server != null) {
            return "sp:" + server.getWorldData().getLevelName();
        }
        ServerData data = mc.getCurrentServer();
        return data == null ? "" : "mp:" + data.ip;
    }

    private static void ensureLoaded() {
        String id = worldId();
        if (id.equals(world)) {
            return;
        }
        world = id;
        MARKS.clear();
        shown = true;
        version++;
        if (id.isEmpty()) {
            return;
        }
        try {
            Path path = file();
            if (!Files.exists(path)) {
                return;
            }
            JsonObject all = JsonParser.parseString(Files.readString(path, StandardCharsets.UTF_8)).getAsJsonObject();
            if (!all.has(id)) {
                return;
            }
            JsonObject mine = all.getAsJsonObject(id);
            shown = !mine.has("shown") || mine.get("shown").getAsBoolean();
            if (mine.has("hearths")) {
                for (Map.Entry<String, com.google.gson.JsonElement> dim : mine.getAsJsonObject("hearths").entrySet()) {
                    Map<Long, Integer> marks = MARKS.computeIfAbsent(dim.getKey(), d -> new LinkedHashMap<>());
                    for (Map.Entry<String, com.google.gson.JsonElement> e : dim.getValue().getAsJsonObject().entrySet()) {
                        marks.put(Long.parseLong(e.getKey()), e.getValue().getAsInt());
                    }
                }
            }
        } catch (Exception e) {
            Proficiency.LOG.warn("Hearth map marks: could not read {}: {}", FILE, e.toString());
        }
    }

    private static void save() {
        if (world == null || world.isEmpty()) {
            return;
        }
        try {
            Path path = file();
            JsonObject all = Files.exists(path)
                    ? JsonParser.parseString(Files.readString(path, StandardCharsets.UTF_8)).getAsJsonObject()
                    : new JsonObject();
            JsonObject mine = new JsonObject();
            mine.addProperty("shown", shown);
            JsonObject hearths = new JsonObject();
            for (Map.Entry<String, Map<Long, Integer>> dim : MARKS.entrySet()) {
                JsonObject marks = new JsonObject();
                dim.getValue().forEach((pos, radius) -> marks.addProperty(Long.toString(pos), radius));
                hearths.add(dim.getKey(), marks);
            }
            mine.add("hearths", hearths);
            all.add(world, mine);
            Files.writeString(path, GSON.toJson(all), StandardCharsets.UTF_8);
        } catch (Exception e) {
            Proficiency.LOG.warn("Hearth map marks: could not write {}: {}", FILE, e.toString());
        }
    }

    private static Path file() {
        return Minecraft.getInstance().gameDirectory.toPath().resolve("config").resolve(FILE);
    }
}
