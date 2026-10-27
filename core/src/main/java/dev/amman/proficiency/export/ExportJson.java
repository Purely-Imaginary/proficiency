package dev.amman.proficiency.export;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.amman.proficiency.hearth.HearthPool;
import dev.amman.proficiency.skill.Skill;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * The text of {@code <world>/proficiency/export/players.json}. Shape (version 1):
 * <pre>
 * { "version":1, "generated":"2026-10-25T12:00:00Z", "generatedMs":..., "world":"name", "skills":["mining",...],
 *   "players":[ { "uuid","name","online","seenMs","totalLevels","totalStars","synergies","expertisePoints",
 *                 "expertiseSpent","deathsWeek","top":["mining",...],
 *                 "skills":{ "mining":{"level":42,"bar":0.37,"stars":0}, ... } } ],
 *   "hearths":[ { "dimension","x","y","z","loaded","owner":{"uuid","name"}|null,"stage","smouldering","lane","tier",
 *                 "radius","reachXp","contributors","pools":{"hearty":{"charge":..,"tier":..},...},
 *                 "build":{"on","stage","done","total"} } ] }
 * </pre>
 * Built by hand, no library, so the order is stable and a test can pin it.
 */
public final class ExportJson {

    public static final int VERSION = 1;

    private ExportJson() {
    }

    public static String render(long nowMs, String world, List<PlayerRow> players, List<HearthRow> hearths) {
        return render(nowMs, world, players, hearths, null);
    }

    /** {@code rawHearths}, when not null, is the JSON array text written in place of {@code hearths}. */
    public static String render(long nowMs, String world, List<PlayerRow> players, List<HearthRow> hearths,
            String rawHearths) {
        StringBuilder out = new StringBuilder(4096);
        out.append("{\n  \"version\": ").append(VERSION)
                .append(",\n  \"generated\": ").append(str(java.time.Instant.ofEpochMilli(nowMs).toString()))
                .append(",\n  \"generatedMs\": ").append(nowMs)
                .append(",\n  \"world\": ").append(str(world))
                .append(",\n  \"skills\": [");
        for (int i = 0; i < Skill.VALUES.length; i++) {
            out.append(i == 0 ? "" : ", ").append(str(Skill.VALUES[i].id()));
        }
        out.append("],\n  \"players\": [");
        for (int i = 0; i < players.size(); i++) {
            out.append(i == 0 ? "\n    " : ",\n    ");
            player(out, players.get(i));
        }
        out.append(players.isEmpty() ? "" : "\n  ").append("],\n  \"hearths\": ");
        if (rawHearths != null) {
            return out.append(rawHearths).append("\n}\n").toString();
        }
        out.append('[');
        for (int i = 0; i < hearths.size(); i++) {
            out.append(i == 0 ? "\n    " : ",\n    ");
            hearth(out, hearths.get(i));
        }
        out.append(hearths.isEmpty() ? "" : "\n  ").append("]\n}\n");
        return out.toString();
    }

    private static void player(StringBuilder out, PlayerRow p) {
        out.append("{\"uuid\": ").append(str(p.uuid())).append(", \"name\": ").append(str(p.name()))
                .append(", \"online\": ").append(p.online()).append(", \"seenMs\": ").append(p.seenMs())
                .append(", \"totalLevels\": ").append(p.totalLevels())
                .append(", \"totalStars\": ").append(p.totalStars())
                .append(", \"synergies\": ").append(p.synergies())
                .append(", \"expertisePoints\": ").append(p.expertisePoints())
                .append(", \"expertiseSpent\": ").append(p.expertiseSpent())
                .append(", \"deathsWeek\": ").append(p.deathsWeek()).append(", \"top\": [");
        for (int i = 0; i < p.top().size(); i++) {
            out.append(i == 0 ? "" : ", ").append(str(p.top().get(i)));
        }
        out.append("], \"skills\": {");
        for (Skill skill : Skill.VALUES) {
            int i = skill.ordinal();
            out.append(i == 0 ? "" : ", ").append(str(skill.id())).append(": {\"level\": ").append(p.levels()[i])
                    .append(", \"bar\": ").append(num(p.bar()[i])).append(", \"stars\": ").append(p.stars()[i])
                    .append('}');
        }
        out.append("}}");
    }

    private static void hearth(StringBuilder out, HearthRow h) {
        out.append("{\"dimension\": ").append(str(h.dimension())).append(", \"x\": ").append(h.x())
                .append(", \"y\": ").append(h.y()).append(", \"z\": ").append(h.z())
                .append(", \"loaded\": ").append(h.loaded()).append(", \"owner\": ");
        if (h.ownerUuid() == null) {
            out.append("null");
        } else {
            out.append("{\"uuid\": ").append(str(h.ownerUuid())).append(", \"name\": ")
                    .append(str(h.ownerName() == null ? "" : h.ownerName())).append('}');
        }
        out.append(", \"stage\": ").append(h.stage()).append(", \"smouldering\": ").append(h.smouldering())
                .append(", \"lane\": ").append(str(h.lane())).append(", \"tier\": ").append(h.tier())
                .append(", \"radius\": ").append(h.radius()).append(", \"reachXp\": ").append(num(h.reachXp()))
                .append(", \"contributors\": ").append(h.contributors()).append(", \"pools\": {");
        for (HearthPool pool : HearthPool.VALUES) {
            int i = pool.ordinal();
            out.append(i == 0 ? "" : ", ").append(str(pool.id())).append(": {\"charge\": ")
                    .append(num(h.charge()[i])).append(", \"tier\": ").append(h.poolTier()[i]).append('}');
        }
        out.append("}, \"build\": {\"on\": ").append(h.buildOn()).append(", \"stage\": ").append(h.buildStage())
                .append(", \"done\": ").append(h.buildDone()).append(", \"total\": ").append(h.buildTotal())
                .append("}}");
    }

    /**
     * Reads the players back from a file this class wrote (offline players survive a restart that way).
     * Anything unreadable gives an empty list, never an exception.
     */
    public static List<PlayerRow> parsePlayers(String text) {
        List<PlayerRow> out = new ArrayList<>();
        JsonObject root = root(text);
        if (root == null) {
            return out;
        }
        JsonArray rows;
        try {
            rows = root.getAsJsonArray("players");
        } catch (RuntimeException e) {
            return out;
        }
        for (JsonElement el : rows) {
            try {
                JsonObject p = el.getAsJsonObject();
                int count = Skill.VALUES.length;
                int[] levels = new int[count];
                float[] bar = new float[count];
                int[] stars = new int[count];
                JsonObject skills = p.getAsJsonObject("skills");
                for (Skill skill : Skill.VALUES) {
                    JsonObject s = skills.has(skill.id()) ? skills.getAsJsonObject(skill.id()) : null;
                    if (s != null) {
                        levels[skill.ordinal()] = s.get("level").getAsInt();
                        bar[skill.ordinal()] = s.get("bar").getAsFloat();
                        stars[skill.ordinal()] = s.get("stars").getAsInt();
                    }
                }
                List<String> top = new ArrayList<>();
                JsonArray topArr = p.getAsJsonArray("top");
                for (JsonElement t : topArr) {
                    top.add(t.getAsString());
                }
                out.add(new PlayerRow(p.get("uuid").getAsString(), p.get("name").getAsString(), false,
                        p.get("seenMs").getAsLong(), levels, bar, stars, p.get("synergies").getAsInt(),
                        p.get("expertisePoints").getAsInt(), p.get("expertiseSpent").getAsInt(),
                        p.get("totalLevels").getAsInt(), p.get("totalStars").getAsInt(), top,
                        p.get("deathsWeek").getAsInt()));
            } catch (RuntimeException e) {
                // One damaged row is skipped; the others are kept.
            }
        }
        return out;
    }

    /** True when the text is a file of this version. */
    public static boolean readable(String text) {
        return root(text) != null;
    }

    /** The compact JSON text of the file's hearths array, or null when it has none. */
    public static String hearthsRaw(String text) {
        JsonObject root = root(text);
        try {
            return root != null && root.get("hearths") != null ? root.getAsJsonArray("hearths").toString() : null;
        } catch (RuntimeException e) {
            return null;
        }
    }

    private static JsonObject root(String text) {
        try {
            JsonObject root = JsonParser.parseString(text).getAsJsonObject();
            return root.get("version") != null && root.get("version").getAsInt() == VERSION ? root : null;
        } catch (RuntimeException e) {
            return null;
        }
    }

    private static String num(double value) {
        if (!Double.isFinite(value)) {
            return "0";
        }
        if (value == Math.rint(value) && Math.abs(value) < 1e15) {
            return Long.toString((long) value);
        }
        return String.format(Locale.ROOT, "%.4f", value).replaceAll("0+$", "").replaceAll("\\.$", "");
    }

    private static String str(String value) {
        StringBuilder out = new StringBuilder(value.length() + 2).append('"');
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            switch (c) {
                case '"' -> out.append("\\\"");
                case '\\' -> out.append("\\\\");
                case '\n' -> out.append("\\n");
                case '\r' -> out.append("\\r");
                case '\t' -> out.append("\\t");
                default -> {
                    if (c < 0x20) {
                        out.append(String.format("\\u%04x", (int) c));
                    } else {
                        out.append(c);
                    }
                }
            }
        }
        return out.append('"').toString();
    }
}
