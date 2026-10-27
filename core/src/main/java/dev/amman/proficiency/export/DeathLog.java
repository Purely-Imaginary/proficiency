package dev.amman.proficiency.export;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** Death times per player over a rolling seven days (not a calendar week). Pure; the hub saves it. */
public final class DeathLog {

    public static final long WEEK_MS = 7L * 24 * 3600 * 1000;
    /** A cap per player so a death loop cannot grow the file without bound. */
    static final int MAX_PER_PLAYER = 200;

    private final Map<String, List<Long>> deaths = new HashMap<>();

    public void add(String uuid, long nowMs) {
        List<Long> list = deaths.computeIfAbsent(uuid, k -> new ArrayList<>());
        list.add(nowMs);
        prune(list, nowMs);
    }

    public int count(String uuid, long nowMs) {
        List<Long> list = deaths.get(uuid);
        if (list == null) {
            return 0;
        }
        int n = 0;
        for (long at : list) {
            if (nowMs - at < WEEK_MS && at <= nowMs) {
                n++;
            }
        }
        return n;
    }

    public void clear() {
        deaths.clear();
    }

    private static void prune(List<Long> list, long nowMs) {
        list.removeIf(at -> nowMs - at >= WEEK_MS);
        while (list.size() > MAX_PER_PLAYER) {
            list.remove(0);
        }
    }

    public String render(long nowMs) {
        StringBuilder out = new StringBuilder("{");
        boolean first = true;
        for (Map.Entry<String, List<Long>> e : deaths.entrySet()) {
            prune(e.getValue(), nowMs);
            if (e.getValue().isEmpty()) {
                continue;
            }
            out.append(first ? "" : ",").append("\n  \"").append(e.getKey()).append("\": ").append(e.getValue());
            first = false;
        }
        return out.append(first ? "" : "\n").append("}\n").toString();
    }

    public void parse(String text) {
        try {
            JsonObject root = JsonParser.parseString(text).getAsJsonObject();
            for (Map.Entry<String, JsonElement> e : root.entrySet()) {
                JsonArray arr = e.getValue().getAsJsonArray();
                List<Long> list = new ArrayList<>();
                for (JsonElement t : arr) {
                    list.add(t.getAsLong());
                }
                deaths.put(e.getKey(), list);
            }
        } catch (RuntimeException ignored) {
            // Missing or damaged: start empty.
        }
    }
}
