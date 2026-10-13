package dev.amman.proficiency.hearth;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.amman.proficiency.skill.Skill;
import dev.amman.proficiency.skill.SkillCategory;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Reads and merges {@code data/<ns>/proficiency/hearth/*.json} into one {@link HearthData}. Pure:
 * the game hands over the file texts, sorted with {@link #ordered}, and logs the messages that come
 * back.
 *
 * <p>Merging works on the JSON before anything is read. Objects merge key by key, so a pack file
 * {@code {"siege": {"pMax": 0.05}}} changes that one number and keeps the rest; arrays and plain
 * values replace. {@code "replace": true} at the top of a file drops everything merged before it,
 * so every key that file leaves out falls back to {@link HearthData#DEFAULTS}. A file that is not a
 * JSON object is reported and skipped; a bad value is reported and its default kept, so one typo
 * never switches the Hearth off.
 */
public final class HearthDataLoader {

    /** One file: a name for messages ({@code namespace:hearth/name}) and its text. */
    public record SourceFile(String name, String text) {
    }

    public enum Level { ERROR, WARN }

    public record Message(Level level, String text) {
    }

    public record Result(HearthData data, int files, int skippedFiles, List<Message> messages) {

        /** The one line logged at load. */
        public String summary() {
            return "Loaded Hearth data from " + files + " file" + (files == 1 ? "" : "s")
                    + (skippedFiles > 0 ? " (" + skippedFiles + " skipped as malformed)" : "")
                    + (messages.isEmpty() ? "" : ", " + messages.size() + " problem"
                            + (messages.size() == 1 ? "" : "s"));
        }
    }

    /** This mod's namespace: its files load before every other namespace's. */
    public static final String OWN_NAMESPACE = "proficiency";

    /** The shipped defaults file, {@code data/proficiency/proficiency/hearth/default.json}: always first. */
    public static final String DEFAULT_FILE = OWN_NAMESPACE + ":hearth/default";

    /**
     * The load order over file names ({@code namespace:hearth/path}): the shipped
     * {@link #DEFAULT_FILE} first, then this mod's other files by path (a lane's
     * {@code proficiency:hearth/comfort} merges over the defaults, never under them), then every other
     * namespace alphabetically, each by path. Later files override earlier ones.
     */
    public static final Comparator<String> ORDER = Comparator
            .comparing((String name) -> !DEFAULT_FILE.equals(name))
            .thenComparing(name -> !OWN_NAMESPACE.equals(namespace(name)))
            .thenComparing(HearthDataLoader::namespace)
            .thenComparing(HearthDataLoader::path);

    /** The files sorted into {@link #ORDER}; {@link #load} merges in the order it is given. */
    public static List<SourceFile> ordered(List<SourceFile> files) {
        List<SourceFile> sorted = new ArrayList<>(files);
        sorted.sort(Comparator.comparing(SourceFile::name, ORDER));
        return sorted;
    }

    private static String namespace(String name) {
        int colon = name.indexOf(':');
        return colon < 0 ? "" : name.substring(0, colon);
    }

    private static String path(String name) {
        int colon = name.indexOf(':');
        return colon < 0 ? name : name.substring(colon + 1);
    }

    private static final Set<String> TOP_KEYS = Set.of("pools", "thresholds", "mirrorShare", "combatShare",
            "hourlyCap", "comfortForTier", "decay", "warmth", "combatSeconds", "tales", "provisioned",
            "character", "chronicle", "feast", "bell", "stages", "comfort", "siege", "replace", "comment",
            "_comment", "description");
    private static final Set<String> POOL_KEYS = Set.of("feeds", "boon", "values", "hold", "absorptionPerTier",
            "comfortCap", "refund", "warmthSeconds", "provisionedMinutes", "comment", "_comment");
    private static final Set<String> SIEGE_KEYS = Set.of("requiresFirstHorn", "cooldownPlayHours", "cooldownDays",
            "rampHours", "pMax", "pityHours", "hornCooldownPlayHours", "postponeNights", "waves", "baseMobs",
            "perExtraPlayer", "burst", "streakBonus", "streakCap", "dousedLoss", "warningTicks",
            "hornMinDelaySeconds", "nextWaveAt", "rollSeconds", "mobs", "douseRadius", "dousePerMobSecond",
            "douseMax", "abandonGraceSeconds", "spawnClearance", "onePerWave", "comment", "_comment");

    private HearthDataLoader() {
    }

    public static Result load(List<SourceFile> files) {
        List<Message> messages = new ArrayList<>();
        JsonObject merged = new JsonObject();
        int loaded = 0;
        int skipped = 0;
        for (SourceFile file : files) {
            JsonObject root;
            try {
                JsonElement parsed = JsonParser.parseString(file.text());
                if (!parsed.isJsonObject()) {
                    throw new IllegalStateException("the top level must be a JSON object");
                }
                root = parsed.getAsJsonObject();
            } catch (RuntimeException e) {
                messages.add(new Message(Level.ERROR, file.name() + ": not valid JSON, file skipped ("
                        + oneLine(e.getMessage()) + ")"));
                skipped++;
                continue;
            }
            loaded++;
            JsonElement replace = root.get("replace");
            if (replace != null) {
                if (replace.isJsonPrimitive() && replace.getAsJsonPrimitive().isBoolean()) {
                    if (replace.getAsBoolean()) {
                        merged = new JsonObject();
                    }
                } else {
                    messages.add(new Message(Level.ERROR, file.name() + ": \"replace\" must be true or false"));
                }
            }
            for (String key : root.keySet()) {
                if (!TOP_KEYS.contains(key)) {
                    messages.add(new Message(Level.WARN, file.name() + ": unknown key \"" + key + "\" ignored"));
                }
            }
            merge(merged, root);
        }
        merged.remove("replace");
        HearthData data = new Reader(messages).read(merged);
        return new Result(data, loaded, skipped, List.copyOf(messages));
    }

    /** Parses one text on its own, defaults filling every gap: tests and the shipped-file check. */
    public static Result parse(String name, String text) {
        return load(List.of(new SourceFile(name, text)));
    }

    /** Deep merge: objects key by key, everything else replaced. */
    static void merge(JsonObject into, JsonObject from) {
        for (Map.Entry<String, JsonElement> entry : from.entrySet()) {
            JsonElement existing = into.get(entry.getKey());
            JsonElement incoming = entry.getValue();
            if (existing != null && existing.isJsonObject() && incoming.isJsonObject()) {
                merge(existing.getAsJsonObject(), incoming.getAsJsonObject());
            } else {
                into.add(entry.getKey(), incoming.deepCopy());
            }
        }
    }

    private static String oneLine(String text) {
        if (text == null) {
            return "no detail";
        }
        String line = text.replace('\n', ' ').trim();
        return line.length() > 160 ? line.substring(0, 160) + "..." : line;
    }

    /** Reads the merged JSON into typed data, a default for every missing or bad value. */
    private static final class Reader {

        private final List<Message> messages;

        Reader(List<Message> messages) {
            this.messages = messages;
        }

        private void error(String path, String what) {
            messages.add(new Message(Level.ERROR, "hearth: " + path + " " + what + ", default kept"));
        }

        private void warn(String text) {
            messages.add(new Message(Level.WARN, "hearth: " + text));
        }

        HearthData read(JsonObject root) {
            HearthData d = HearthData.DEFAULTS;
            Map<HearthPool, HearthData.PoolSpec> pools = new EnumMap<>(HearthPool.class);
            JsonObject poolsJson = object(root, "pools", "pools");
            for (HearthPool pool : HearthPool.VALUES) {
                JsonObject one = poolsJson == null ? null : object(poolsJson, pool.id(), "pools." + pool.id());
                pools.put(pool, pool(pool, one, d.pool(pool)));
            }
            if (poolsJson != null) {
                for (String id : poolsJson.keySet()) {
                    if (HearthPool.byId(id) == null) {
                        warn("pools." + id + ": unknown pool ignored (pools: hearty, regen, fortified, workshop, homecoming)");
                    }
                }
            }
            List<Double> thresholds = tierList(root, "thresholds", "thresholds", d.thresholds(), 0.0,
                    Double.MAX_VALUE, false, true);
            double mirrorShare = num(root, "mirrorShare", "mirrorShare", d.mirrorShare(), 0, 1);
            double combatShare = num(root, "combatShare", "combatShare", d.combatShare(), 0, 1);
            double hourlyCap = num(root, "hourlyCap", "hourlyCap", d.hourlyCap(), 0.000001, Double.MAX_VALUE);
            Map<Integer, Integer> comfortForTier = tierMap(root, "comfortForTier", "comfortForTier",
                    d.comfortForTier(), 1, 6, 0, 1000);

            JsonObject decayJson = object(root, "decay", "decay");
            HearthData.Decay decay = new HearthData.Decay(
                    num(decayJson, "perHour", "decay.perHour", d.decay().perHour(), 0, 1),
                    num(decayJson, "graceHours", "decay.graceHours", d.decay().graceHours(), 0, 100_000));

            JsonObject warmthJson = object(root, "warmth", "warmth");
            HearthData.Warmth warmth = new HearthData.Warmth(
                    num(warmthJson, "riseSeconds", "warmth.riseSeconds", d.warmth().riseSeconds(), 0.05, 100_000),
                    num(warmthJson, "drainSeconds", "warmth.drainSeconds", d.warmth().drainSeconds(), 0.05, 100_000));
            double combatSeconds = num(root, "combatSeconds", "combatSeconds", d.combatSeconds(), 0, 3600);

            JsonObject talesJson = object(root, "tales", "tales");
            HearthData.Tales tales = new HearthData.Tales(
                    num(talesJson, "share", "tales.share", d.tales().share(), 0, 100),
                    feeds(talesJson, "feeds", "tales.feeds", d.tales().feeds()),
                    bool(talesJson, "feedAll", "tales.feedAll", d.tales().feedAll()));

            JsonObject provJson = object(root, "provisioned", "provisioned");
            HearthData.Provisioned provisioned = new HearthData.Provisioned(
                    num(provJson, "minutes", "provisioned.minutes", d.provisioned().minutes(), 0, 100_000));

            JsonObject charJson = object(root, "character", "character");
            HearthData.CharacterRules character = new HearthData.CharacterRules(
                    whole(charJson, "max", "character.max", d.character().max(), 1, 100_000),
                    tierMap(charJson, "forStage", "character.forStage", d.character().forStage(), 1, 6, 0, 100_000));

            JsonObject chronJson = object(root, "chronicle", "chronicle");
            HearthData.Chronicle chronicle = new HearthData.Chronicle(
                    num(chronJson, "writeBonus", "chronicle.writeBonus", d.chronicle().writeBonus(), 0, 100),
                    num(chronJson, "perStar", "chronicle.perStar", d.chronicle().perStar(), 0, 100));

            JsonObject feastJson = object(root, "feast", "feast");
            HearthData.Feast feast = new HearthData.Feast(
                    num(feastJson, "windowSeconds", "feast.windowSeconds", d.feast().windowSeconds(), 1, 100_000),
                    num(feastJson, "dinnerBellSeconds", "feast.dinnerBellSeconds", d.feast().dinnerBellSeconds(), 1, 100_000),
                    num(feastJson, "chargePerExtraEater", "feast.chargePerExtraEater",
                            d.feast().chargePerExtraEater(), 0, 1_000_000),
                    num(feastJson, "dinnerTriggerSeconds", "feast.dinnerTriggerSeconds",
                            d.feast().dinnerTriggerSeconds(), 0, 100_000));

            JsonObject bellJson = object(root, "bell", "bell");
            HearthData.Bell b = d.bell();
            HearthData.Bell bell = new HearthData.Bell(
                    num(bellJson, "alarmGlowSeconds", "bell.alarmGlowSeconds", b.alarmGlowSeconds(), 0, 3600),
                    num(bellJson, "alarmCooldown", "bell.alarmCooldown", b.alarmCooldown(), 0, 100_000),
                    num(bellJson, "comeHomeSeconds", "bell.comeHomeSeconds", b.comeHomeSeconds(), 0, 3600),
                    num(bellJson, "comeHomeCooldown", "bell.comeHomeCooldown", b.comeHomeCooldown(), 0, 100_000),
                    num(bellJson, "homewardChannelSeconds", "bell.homewardChannelSeconds",
                            b.homewardChannelSeconds(), 0, 3600));

            List<HearthData.Stage> stages = stages(root, d.stages());

            JsonObject comfortJson = object(root, "comfort", "comfort");
            HearthData.Comfort comfort = new HearthData.Comfort(
                    strings(comfortJson, "kinds", "comfort.kinds", d.comfort().kinds(), true),
                    num(comfortJson, "scanCooldownSeconds", "comfort.scanCooldownSeconds",
                            d.comfort().scanCooldownSeconds(), 0, 100_000),
                    num(comfortJson, "rescanSeconds", "comfort.rescanSeconds", d.comfort().rescanSeconds(), 1, 100_000));

            HearthData.Siege siege = siege(object(root, "siege", "siege"), d.siege());

            return new HearthData(Collections.unmodifiableMap(pools), thresholds, mirrorShare, combatShare, hourlyCap,
                    comfortForTier, decay, warmth, combatSeconds, tales, provisioned, character, chronicle, feast,
                    bell, stages, comfort, siege);
        }

        private HearthData.PoolSpec pool(HearthPool pool, JsonObject json, HearthData.PoolSpec def) {
            if (json == null) {
                return def;
            }
            String path = "pools." + pool.id();
            for (String key : json.keySet()) {
                if (!POOL_KEYS.contains(key)) {
                    warn(path + ": unknown key \"" + key + "\" ignored");
                }
            }
            BoonType boon = def.boon();
            JsonElement boonJson = json.get("boon");
            if (boonJson != null) {
                BoonType parsed = boonJson.isJsonPrimitive() ? BoonType.byId(boonJson.getAsString()) : null;
                if (parsed == null) {
                    error(path + ".boon", "is not a boon type (hunger_slow, regen_out_of_combat, "
                            + "mob_damage_reduction, station_speed, refund_chance, readiness)");
                } else {
                    boon = parsed;
                }
            }
            HearthData.ComfortCap cap = def.comfortCap();
            JsonElement capJson = json.get("comfortCap");
            if (capJson != null) {
                HearthData.ComfortCap parsed = capJson.isJsonPrimitive()
                        ? HearthData.ComfortCap.byId(capJson.getAsString()) : null;
                if (parsed == null) {
                    error(path + ".comfortCap", "must be \"tiers\" or \"half\"");
                } else {
                    cap = parsed;
                }
            }
            return new HearthData.PoolSpec(pool,
                    feeds(json, "feeds", path + ".feeds", def.feeds()),
                    boon,
                    tierList(json, "values", path + ".values", def.values(), 0, Double.MAX_VALUE, true, false),
                    whole(json, "hold", path + ".hold", def.holdSeconds(), 0, 86_400),
                    num(json, "absorptionPerTier", path + ".absorptionPerTier", def.absorptionPerTier(), 0, 10),
                    cap,
                    tierList(json, "refund", path + ".refund", def.refund(), 0, 1, true, false),
                    tierList(json, "warmthSeconds", path + ".warmthSeconds", def.warmthSeconds(), 0.05, 100_000, true, false),
                    tierList(json, "provisionedMinutes", path + ".provisionedMinutes", def.provisionedMinutes(),
                            0, 100_000, true, false));
        }

        private HearthData.Siege siege(JsonObject json, HearthData.Siege d) {
            if (json == null) {
                return d;
            }
            for (String key : json.keySet()) {
                if (!SIEGE_KEYS.contains(key)) {
                    warn("siege: unknown key \"" + key + "\" ignored");
                }
            }
            return new HearthData.Siege(
                    bool(json, "requiresFirstHorn", "siege.requiresFirstHorn", d.requiresFirstHorn()),
                    num(json, "cooldownPlayHours", "siege.cooldownPlayHours", d.cooldownPlayHours(), 0, 100_000),
                    num(json, "cooldownDays", "siege.cooldownDays", d.cooldownDays(), 0, 100_000),
                    num(json, "rampHours", "siege.rampHours", d.rampHours(), 0, 100_000),
                    num(json, "pMax", "siege.pMax", d.pMax(), 0, 1),
                    num(json, "pityHours", "siege.pityHours", d.pityHours(), 0, 100_000),
                    num(json, "hornCooldownPlayHours", "siege.hornCooldownPlayHours", d.hornCooldownPlayHours(), 0, 100_000),
                    whole(json, "postponeNights", "siege.postponeNights", d.postponeNights(), 0, 100),
                    whole(json, "waves", "siege.waves", d.waves(), 1, 100),
                    tierMap(json, "baseMobs", "siege.baseMobs", d.baseMobs(), 1, SiegeRules.MAX_KEY, 0, 1000),
                    num(json, "perExtraPlayer", "siege.perExtraPlayer", d.perExtraPlayer(), 0, 100),
                    num(json, "burst", "siege.burst", d.burst(), 0, 100),
                    num(json, "streakBonus", "siege.streakBonus", d.streakBonus(), 0, 100),
                    whole(json, "streakCap", "siege.streakCap", d.streakCap(), 0, 1000),
                    num(json, "dousedLoss", "siege.dousedLoss", d.dousedLoss(), 0, 1),
                    (long) num(json, "warningTicks", "siege.warningTicks", d.warningTicks(), 0, 10_000_000),
                    num(json, "hornMinDelaySeconds", "siege.hornMinDelaySeconds", d.hornMinDelaySeconds(), 0, 100_000),
                    num(json, "nextWaveAt", "siege.nextWaveAt", d.nextWaveAt(), 0, 1),
                    num(json, "rollSeconds", "siege.rollSeconds", d.rollSeconds(), 1, 100_000),
                    mobs(json, d.mobs()),
                    num(json, "douseRadius", "siege.douseRadius", d.douseRadius(), 0, 64),
                    num(json, "dousePerMobSecond", "siege.dousePerMobSecond", d.dousePerMobSecond(), 0, 100_000),
                    num(json, "douseMax", "siege.douseMax", d.douseMax(), 1, 100_000),
                    num(json, "abandonGraceSeconds", "siege.abandonGraceSeconds", d.abandonGraceSeconds(), 0, 3_600),
                    num(json, "spawnClearance", "siege.spawnClearance", d.spawnClearance(), 0, 64),
                    strings(json, "onePerWave", "siege.onePerWave", d.onePerWave(), false));
        }

        private Map<Integer, List<String>> mobs(JsonObject json, Map<Integer, List<String>> def) {
            JsonElement raw = json.get("mobs");
            if (raw == null) {
                return def;
            }
            if (!raw.isJsonObject()) {
                error("siege.mobs", "must be an object of ceiling to entity id list");
                return def;
            }
            Map<Integer, List<String>> out = new LinkedHashMap<>();
            for (Map.Entry<String, JsonElement> entry : raw.getAsJsonObject().entrySet()) {
                Integer ceiling = tierKey(entry.getKey());
                if (ceiling == null) {
                    error("siege.mobs." + entry.getKey(), "is not a siege key 1..7 (3..6 = ceilings III..VI, 7 = level V)");
                    return def;
                }
                List<String> ids = strings(raw.getAsJsonObject(), entry.getKey(), "siege.mobs." + entry.getKey(),
                        null, false);
                if (ids == null) {
                    return def;
                }
                out.put(ceiling, ids);
            }
            return Collections.unmodifiableMap(out);
        }

        private List<HearthData.Stage> stages(JsonObject root, List<HearthData.Stage> def) {
            JsonElement raw = root.get("stages");
            if (raw == null) {
                return def;
            }
            if (!raw.isJsonArray() || raw.getAsJsonArray().size() != HearthData.TIERS) {
                error("stages", "must be an array of exactly " + HearthData.TIERS + " stages");
                return def;
            }
            List<HearthData.Stage> out = new ArrayList<>();
            JsonArray array = raw.getAsJsonArray();
            for (int i = 0; i < array.size(); i++) {
                JsonElement one = array.get(i);
                if (!one.isJsonObject() || !isString(one.getAsJsonObject().get("build"))
                        || !isString(one.getAsJsonObject().get("offering"))) {
                    error("stages[" + i + "]", "must be {\"build\": \"<block tag>\", \"offering\": \"<item id>\"}");
                    return def;
                }
                out.add(new HearthData.Stage(one.getAsJsonObject().get("build").getAsString(),
                        one.getAsJsonObject().get("offering").getAsString()));
            }
            return List.copyOf(out);
        }

        private static boolean isString(JsonElement element) {
            return element != null && element.isJsonPrimitive() && element.getAsJsonPrimitive().isString();
        }

        private JsonObject object(JsonObject parent, String key, String path) {
            if (parent == null) {
                return null;
            }
            JsonElement raw = parent.get(key);
            if (raw == null) {
                return null;
            }
            if (!raw.isJsonObject()) {
                error(path, "must be an object");
                return null;
            }
            return raw.getAsJsonObject();
        }

        private Double number(JsonElement raw) {
            if (raw != null && raw.isJsonPrimitive() && raw.getAsJsonPrimitive().isNumber()) {
                double value = raw.getAsDouble();
                return Double.isFinite(value) ? value : null;
            }
            return null;
        }

        private double num(JsonObject parent, String key, String path, double def, double min, double max) {
            if (parent == null || !parent.has(key)) {
                return def;
            }
            Double value = number(parent.get(key));
            if (value == null || value < min || value > max) {
                error(path, "must be a number from " + fmt(min) + " to " + fmt(max));
                return def;
            }
            return value;
        }

        private int whole(JsonObject parent, String key, String path, int def, int min, int max) {
            if (parent == null || !parent.has(key)) {
                return def;
            }
            Double value = number(parent.get(key));
            if (value == null || value != Math.rint(value) || value < min || value > max) {
                error(path, "must be a whole number from " + min + " to " + max);
                return def;
            }
            return value.intValue();
        }

        private boolean bool(JsonObject parent, String key, String path, boolean def) {
            if (parent == null || !parent.has(key)) {
                return def;
            }
            JsonElement raw = parent.get(key);
            if (raw.isJsonPrimitive() && raw.getAsJsonPrimitive().isBoolean()) {
                return raw.getAsBoolean();
            }
            error(path, "must be true or false");
            return def;
        }

        /** Six numbers in range (or none, when {@code allowEmpty}); {@code increasing} demands each above the last. */
        private List<Double> tierList(JsonObject parent, String key, String path, List<Double> def, double min,
                double max, boolean allowEmpty, boolean increasing) {
            if (parent == null || !parent.has(key)) {
                return def;
            }
            JsonElement raw = parent.get(key);
            if (!raw.isJsonArray()) {
                error(path, "must be an array of " + HearthData.TIERS + " numbers");
                return def;
            }
            JsonArray array = raw.getAsJsonArray();
            if (array.size() == 0 && allowEmpty) {
                return List.of();
            }
            if (array.size() != HearthData.TIERS) {
                error(path, "must hold exactly " + HearthData.TIERS + " numbers, tier I first");
                return def;
            }
            List<Double> out = new ArrayList<>(HearthData.TIERS);
            for (int i = 0; i < array.size(); i++) {
                Double value = number(array.get(i));
                if (value == null || value < min || value > max
                        || increasing && !out.isEmpty() && value <= out.get(out.size() - 1)) {
                    error(path, "must hold numbers from " + fmt(min) + " to " + fmt(max)
                            + (increasing ? ", each above the one before" : ""));
                    return def;
                }
                out.add(value);
            }
            return Collections.unmodifiableList(out);
        }

        private static Integer tierKey(String key) {
            try {
                int tier = Integer.parseInt(key);
                // One above the six tiers: siege level V's key (SiegeRules.MAX_KEY); each caller narrows it.
                return tier >= 1 && tier <= SiegeRules.MAX_KEY ? tier : null;
            } catch (NumberFormatException e) {
                return null;
            }
        }

        /** An object of tier ("1".."6") to whole number. */
        private Map<Integer, Integer> tierMap(JsonObject parent, String key, String path, Map<Integer, Integer> def,
                int minKey, int maxKey, int min, int max) {
            if (parent == null || !parent.has(key)) {
                return def;
            }
            JsonElement raw = parent.get(key);
            if (!raw.isJsonObject()) {
                error(path, "must be an object of tier to number, like {\"5\": 8}");
                return def;
            }
            Map<Integer, Integer> out = new LinkedHashMap<>();
            for (Map.Entry<String, JsonElement> entry : raw.getAsJsonObject().entrySet()) {
                Integer tier = tierKey(entry.getKey());
                Double value = number(entry.getValue());
                if (tier == null || tier < minKey || tier > maxKey || value == null || value != Math.rint(value)
                        || value < min || value > max) {
                    error(path + "." + entry.getKey(), "must map a tier " + minKey + ".." + maxKey
                            + " to a whole number from " + min + " to " + max);
                    return def;
                }
                out.put(tier, value.intValue());
            }
            return Collections.unmodifiableMap(out);
        }

        private List<String> strings(JsonObject parent, String key, String path, List<String> def, boolean nonEmpty) {
            if (parent == null || !parent.has(key)) {
                return def;
            }
            JsonElement raw = parent.get(key);
            if (!raw.isJsonArray()) {
                error(path, "must be an array of strings");
                return def;
            }
            List<String> out = new ArrayList<>();
            for (JsonElement element : raw.getAsJsonArray()) {
                if (!isString(element) || element.getAsString().isEmpty()) {
                    error(path, "must be an array of strings");
                    return def;
                }
                out.add(element.getAsString());
            }
            if (nonEmpty && out.isEmpty()) {
                error(path, "must not be empty");
                return def;
            }
            return List.copyOf(out);
        }

        private List<HearthData.Feed> feeds(JsonObject parent, String key, String path, List<HearthData.Feed> def) {
            List<String> tokens = strings(parent, key, path, null, false);
            if (tokens == null) {
                return def;
            }
            List<HearthData.Feed> out = new ArrayList<>();
            for (String token : tokens) {
                HearthData.Feed feed = feed(token);
                if (feed == null) {
                    // A skill this version does not have (a newer pack): skipped, the rest still feed.
                    warn(path + ": \"" + token + "\" is no skill, category, #tales or #feast; skipped");
                } else {
                    out.add(feed);
                }
            }
            return List.copyOf(out);
        }

        /** {@code #category:<id>}, {@code #tales}, {@code #feast}, a skill id, or else a category id. */
        static HearthData.Feed feed(String token) {
            if (HearthData.Feed.TALES_TOKEN.equals(token)) {
                return HearthData.Feed.tales();
            }
            if (HearthData.Feed.FEAST_TOKEN.equals(token)) {
                return HearthData.Feed.feast();
            }
            if (token.startsWith(HearthData.Feed.CATEGORY_PREFIX)) {
                String id = token.substring(HearthData.Feed.CATEGORY_PREFIX.length());
                return category(id) ? HearthData.Feed.category(id) : null;
            }
            if (Skill.byId(token) != null) {
                return HearthData.Feed.skill(token);
            }
            return category(token) ? HearthData.Feed.category(token) : null;
        }

        private static boolean category(String id) {
            for (SkillCategory category : SkillCategory.values()) {
                if (category.id().equals(id)) {
                    return true;
                }
            }
            return false;
        }

        private static String fmt(double value) {
            if (value == Double.MAX_VALUE) {
                return "any";
            }
            return value == Math.rint(value) ? Long.toString((long) value) : Double.toString(value);
        }
    }
}
