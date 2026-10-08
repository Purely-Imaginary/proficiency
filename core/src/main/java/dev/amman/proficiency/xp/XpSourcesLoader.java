package dev.amman.proficiency.xp;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.amman.proficiency.skill.Skill;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * Parses xp_sources files and merges them, in the order given, into one {@link XpTable}. Pure: the
 * game side hands over the file texts and a registry oracle, and logs the messages that come back.
 *
 * <p>Merging: a later file overrides an earlier one. A rule with the same match (and kind) in the
 * same domain replaces the earlier rule outright. A file with {@code "replace": true} first
 * discards every earlier rule of each domain it contains. A malformed file is reported and skipped,
 * so everything loaded before it stays. A bad rule is reported and skipped, its neighbours stay.
 * An id no registry knows is reported once per load and its rule skipped.
 */
public final class XpSourcesLoader {

    /** One file's name (for messages) and text. */
    public record SourceFile(String name, String text) {
    }

    /** What the registries know. Anything unknown to the oracle is treated as existing. */
    public interface Registries {
        /** Whether the registry ("block", "item", "entity", "structure", "biome") holds the id. */
        boolean knowsId(String registry, String id);

        /** Whether the registry has a tag of that name. */
        boolean knowsTag(String registry, String tag);

        /** Accepts everything. */
        Registries ANY = new Registries() {
            @Override
            public boolean knowsId(String registry, String id) {
                return true;
            }

            @Override
            public boolean knowsTag(String registry, String tag) {
                return true;
            }
        };
    }

    public enum Level { ERROR, WARN }

    public record Message(Level level, String text) {
    }

    public static final class Result {
        public final XpTable table;
        /** Files that contributed (parsed as JSON objects), not counting the ones skipped. */
        public final int files;
        public final int skippedFiles;
        /** Rules in the merged table. */
        public final int sources;
        public final List<Message> messages;

        Result(XpTable table, int files, int skippedFiles, int sources, List<Message> messages) {
            this.table = table;
            this.files = files;
            this.skippedFiles = skippedFiles;
            this.sources = sources;
            this.messages = messages;
        }

        /** The one line logged at load. */
        public String summary() {
            return "Loaded " + sources + " XP sources from " + files + " files"
                    + (skippedFiles > 0 ? " (" + skippedFiles + " skipped as malformed)" : "");
        }
    }

    private static final Pattern ID = Pattern.compile("[a-z0-9_.-]+:[a-z0-9_./-]+");
    private static final Pattern NAMESPACE = Pattern.compile("[a-z0-9_.-]+");

    private static final Set<String> TOP_KEYS = Set.of("replace", "comment", "_comment", "description");
    private static final Set<String> RULE_KEYS = Set.of("match", "priority", "skill", "xp", "also",
            "flags", "tool", "kind", "min_health", "multiplier", "boss", "exclude", "comment",
            "_comment", "why");
    private static final Set<String> TOOLS = Set.of("any", "pickaxe", "shovel", "axe", "hoe");
    private static final Set<String> KINDS = Set.of("block", "item", "entity");

    /** The namespace of this mod's own files: their unknown tags are not worth a message. */
    private static final String OWN = "proficiency";

    private XpSourcesLoader() {
    }

    private static final class RuleError extends Exception {
        RuleError(String message) {
            super(message);
        }
    }

    public static Result load(List<SourceFile> files, Registries registries) {
        List<Message> messages = new ArrayList<>();
        Set<String> unknownSeen = new HashSet<>();
        Map<XpDomain, Map<String, XpRule>> merged = new EnumMap<>(XpDomain.class);
        for (XpDomain domain : XpDomain.values()) {
            merged.put(domain, new LinkedHashMap<>());
        }
        int loaded = 0;
        int skipped = 0;
        int order = 0;
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
            boolean own = file.name().startsWith(OWN + ":");
            boolean replace = false;
            if (root.has("replace")) {
                try {
                    replace = root.get("replace").getAsBoolean();
                } catch (RuntimeException e) {
                    messages.add(new Message(Level.ERROR, file.name() + ": \"replace\" must be true or false"));
                }
            }
            for (Map.Entry<String, JsonElement> entry : root.entrySet()) {
                String key = entry.getKey();
                XpDomain domain = XpDomain.byKey(key);
                if (domain == null) {
                    if (!TOP_KEYS.contains(key)) {
                        messages.add(new Message(Level.WARN, file.name() + ": unknown key \"" + key
                                + "\" ignored (domains: " + domainList() + ")"));
                    }
                    continue;
                }
                if (!entry.getValue().isJsonArray()) {
                    messages.add(new Message(Level.ERROR, file.name() + ": \"" + key
                            + "\" must be an array of rules, skipped"));
                    continue;
                }
                Map<String, XpRule> rules = merged.get(domain);
                if (replace) {
                    rules.clear();
                }
                JsonArray array = entry.getValue().getAsJsonArray();
                for (int i = 0; i < array.size(); i++) {
                    String where = file.name() + ": " + key + "[" + i + "]";
                    try {
                        if (!array.get(i).isJsonObject()) {
                            throw new RuleError("a rule must be a JSON object");
                        }
                        List<XpRule> parsed = parseRule(domain, array.get(i).getAsJsonObject(),
                                file.name(), registries, unknownSeen, messages, where, own);
                        for (XpRule rule : parsed) {
                            XpRule ordered = rule.withOrder(order++);
                            rules.remove(ordered.identity());
                            rules.put(ordered.identity(), ordered);
                        }
                    } catch (RuleError e) {
                        messages.add(new Message(Level.ERROR, where + ": " + e.getMessage() + ", rule skipped"));
                    }
                }
            }
        }
        Map<XpDomain, List<XpRule>> finalRules = new EnumMap<>(XpDomain.class);
        int total = 0;
        for (Map.Entry<XpDomain, Map<String, XpRule>> entry : merged.entrySet()) {
            finalRules.put(entry.getKey(), new ArrayList<>(entry.getValue().values()));
            total += entry.getValue().size();
        }
        return new Result(new XpTable(finalRules, loaded), loaded, skipped, total, messages);
    }

    private static String domainList() {
        List<String> keys = new ArrayList<>();
        for (XpDomain domain : XpDomain.values()) {
            keys.add(domain.key());
        }
        return String.join(", ", keys);
    }

    private static String oneLine(String text) {
        if (text == null) {
            return "no detail";
        }
        String line = text.replace('\n', ' ').trim();
        return line.length() > 160 ? line.substring(0, 160) + "..." : line;
    }

    // ---- one rule ----------------------------------------------------------------------------

    private static List<XpRule> parseRule(XpDomain domain, JsonObject json, String file,
            Registries registries, Set<String> unknownSeen, List<Message> messages, String where,
            boolean own) throws RuleError {
        for (String key : json.keySet()) {
            if (!RULE_KEYS.contains(key)) {
                messages.add(new Message(Level.WARN, where + ": unknown field \"" + key + "\" ignored"));
            }
        }
        XpRule.Builder b = new XpRule.Builder();
        b.domain = domain;
        b.sourceFile = file;
        b.priority = intField(json, "priority", 0);

        String kind = null;
        if (json.has("kind")) {
            kind = string(json, "kind").toLowerCase(Locale.ROOT);
            if (!KINDS.contains(kind)) {
                throw new RuleError("\"kind\" must be block, item or entity");
            }
            if (domain != XpDomain.CAST && domain != XpDomain.FIRST_TIME) {
                throw new RuleError("\"kind\" is only for cast and first_time rules");
            }
        }
        b.kind = kind;

        b.tool = null;
        if (json.has("tool")) {
            String tool = string(json, "tool").toLowerCase(Locale.ROOT);
            if (domain != XpDomain.BREAK) {
                throw new RuleError("\"tool\" is only for break rules");
            }
            if (!TOOLS.contains(tool)) {
                throw new RuleError("unknown tool \"" + tool + "\" (any, pickaxe, shovel, axe, hoe)");
            }
            b.tool = "any".equals(tool) ? null : tool;
        }

        b.minHealth = json.has("min_health") ? number(json, "min_health") : 0;
        if (b.minHealth < 0) {
            throw new RuleError("\"min_health\" cannot be negative");
        }

        // flags
        Set<String> flags = new LinkedHashSet<>();
        if (json.has("flags")) {
            if (!json.get("flags").isJsonArray()) {
                throw new RuleError("\"flags\" must be an array of strings");
            }
            for (JsonElement flag : json.getAsJsonArray("flags")) {
                String name = flag.isJsonPrimitive() ? flag.getAsString().toLowerCase(Locale.ROOT) : "";
                if (!domain.flags().contains(name)) {
                    throw new RuleError("flag \"" + name + "\" does not exist for " + domain.key()
                            + " rules (allowed: " + (domain.flags().isEmpty() ? "none" : domain.flags()) + ")");
                }
                flags.add(name);
            }
        }
        b.flags = flags;

        // skill and xp
        boolean none = false;
        Skill skill = null;
        if (json.has("skill")) {
            String raw = string(json, "skill").toLowerCase(Locale.ROOT);
            if (raw.equals("none")) {
                none = true;
            } else {
                skill = Skill.byId(raw);
                if (skill == null) {
                    throw new RuleError("unknown skill \"" + raw + "\"");
                }
            }
        }
        XpSpec xp = json.has("xp") ? spec(json.get("xp"), "xp") : null;
        switch (domain) {
            case BREAK, PLACE, CRAFT, CAST -> {
                if (!none && skill == null) {
                    throw new RuleError("a \"skill\" is required (or \"none\" to pay nothing)");
                }
                if (!none && xp == null) {
                    throw new RuleError("\"xp\" is required");
                }
            }
            case KILL -> {
                if (skill == null && !none) {
                    skill = Skill.BEASTSLAYING;
                }
                if (!none && xp == null) {
                    throw new RuleError("\"xp\" is required (or \"skill\": \"none\" to pay nothing)");
                }
            }
            case KILL_BONUS -> {
                if (skill != null) {
                    throw new RuleError("the kill bonus goes to the weapon skill, so no \"skill\" here (only \"none\")");
                }
                if (!none && xp == null) {
                    throw new RuleError("\"xp\" is required (or \"skill\": \"none\" for no bonus)");
                }
            }
            case BOSS -> {
                if (skill != null || none || xp != null) {
                    throw new RuleError("boss rules take only \"boss\": true or false");
                }
            }
            case STRUCTURE, BIOME, DIMENSION -> {
                if (skill != null && skill != Skill.WAYFARING) {
                    throw new RuleError("discoveries pay Wayfaring only (or \"none\")");
                }
                skill = none ? null : Skill.WAYFARING;
            }
            case FIRST_TIME -> {
                if (skill != null || none || xp != null) {
                    throw new RuleError("first_time rules take a \"multiplier\", not a skill or xp");
                }
            }
        }
        b.skill = skill;
        b.paysNothing = none;
        b.xp = xp;

        if (json.has("also")) {
            if (domain != XpDomain.PLACE) {
                throw new RuleError("\"also\" is only for place rules");
            }
            if (!json.get("also").isJsonObject()) {
                throw new RuleError("\"also\" must be an object with a skill and xp");
            }
            JsonObject also = json.getAsJsonObject("also");
            Skill second = Skill.byId(string(also, "skill").toLowerCase(Locale.ROOT));
            if (second == null || !also.has("xp")) {
                throw new RuleError("\"also\" needs a known skill and xp");
            }
            b.also = new XpRule.Also(second, spec(also.get("xp"), "also.xp"));
        }

        if (json.has("multiplier")) {
            double multiplier = number(json, "multiplier");
            if (domain != XpDomain.FIRST_TIME) {
                throw new RuleError("\"multiplier\" is only for first_time rules");
            }
            if (multiplier < 0) {
                throw new RuleError("\"multiplier\" cannot be negative");
            }
            b.multiplier = multiplier;
        } else if (domain == XpDomain.FIRST_TIME) {
            throw new RuleError("\"multiplier\" is required");
        }
        if (json.has("boss")) {
            if (domain != XpDomain.BOSS) {
                throw new RuleError("\"boss\" is only for boss rules");
            }
            if (!json.get("boss").isJsonPrimitive() || !json.get("boss").getAsJsonPrimitive().isBoolean()) {
                throw new RuleError("\"boss\" must be true or false");
            }
            b.boss = json.get("boss").getAsBoolean();
        } else if (domain == XpDomain.BOSS) {
            throw new RuleError("\"boss\": true or false is required");
        }

        b.exclude = json.has("exclude") ? exclude(json.get("exclude")) : XpRule.Exclude.NONE;

        // match: one string or an array of them; each becomes its own rule.
        if (!json.has("match")) {
            throw new RuleError("\"match\" is required");
        }
        List<String> matches = new ArrayList<>();
        JsonElement match = json.get("match");
        if (match.isJsonArray()) {
            for (JsonElement element : match.getAsJsonArray()) {
                matches.add(primitiveString(element, "match"));
            }
        } else {
            matches.add(primitiveString(match, "match"));
        }
        if (matches.isEmpty()) {
            throw new RuleError("\"match\" is empty");
        }
        List<XpRule> out = new ArrayList<>();
        for (String text : matches) {
            XpRule.Builder copy = new XpRule.Builder();
            copy.domain = b.domain;
            copy.sourceFile = b.sourceFile;
            copy.priority = b.priority;
            copy.skill = b.skill;
            copy.paysNothing = b.paysNothing;
            copy.xp = b.xp;
            copy.also = b.also;
            copy.flags = b.flags;
            copy.tool = b.tool;
            copy.kind = b.kind;
            copy.minHealth = b.minHealth;
            copy.multiplier = b.multiplier;
            copy.boss = b.boss;
            copy.exclude = b.exclude;
            try {
                applyMatch(copy, text);
            } catch (RuleError e) {
                messages.add(new Message(Level.ERROR, where + ": " + e.getMessage() + ", match skipped"));
                continue;
            }
            if (!known(copy, domain, registries, unknownSeen, messages, where, own)) {
                continue;
            }
            out.add(new XpRule(copy));
        }
        return out;
    }

    private static void applyMatch(XpRule.Builder b, String text) throws RuleError {
        String t = text.trim();
        if (t.equals("*")) {
            b.matcher = XpRule.Matcher.ANY;
            b.key = "*";
        } else if (t.startsWith("#")) {
            String tag = withNamespace(t.substring(1));
            if (!ID.matcher(tag).matches()) {
                throw new RuleError("\"" + text + "\" is not a valid tag");
            }
            b.matcher = XpRule.Matcher.TAG;
            b.key = tag;
        } else if (t.endsWith(":*")) {
            String ns = t.substring(0, t.length() - 2);
            if (!NAMESPACE.matcher(ns).matches()) {
                throw new RuleError("\"" + text + "\" is not a valid mod wildcard");
            }
            b.matcher = XpRule.Matcher.NAMESPACE;
            b.key = ns;
        } else {
            String id = withNamespace(t);
            if (!ID.matcher(id).matches()) {
                throw new RuleError("\"" + text + "\" is not a valid id");
            }
            b.matcher = XpRule.Matcher.ID;
            b.key = id;
        }
    }

    private static String withNamespace(String id) {
        return id.indexOf(':') < 0 ? "minecraft:" + id : id;
    }

    /** The registry an id of this rule lives in, or null when it could be in several. */
    private static String registryOf(XpDomain domain, String kind) {
        return switch (domain) {
            case BREAK, PLACE -> "block";
            case CRAFT -> "item";
            case KILL, KILL_BONUS, BOSS -> "entity";
            case STRUCTURE -> "structure";
            case BIOME -> "biome";
            case DIMENSION -> "dimension";
            case CAST, FIRST_TIME -> kind;
        };
    }

    private static boolean known(XpRule.Builder b, XpDomain domain, Registries registries,
            Set<String> unknownSeen, List<Message> messages, String where, boolean own) {
        String registry = registryOf(domain, b.kind);
        List<String> candidates = registry != null ? List.of(registry) : List.of("block", "item", "entity");
        if (b.matcher == XpRule.Matcher.ID) {
            for (String candidate : candidates) {
                if (registries.knowsId(candidate, b.key)) {
                    return true;
                }
            }
            if (unknownSeen.add("id|" + domain + "|" + b.key)) {
                messages.add(new Message(Level.WARN, where + ": unknown " + String.join("/", candidates)
                        + " id " + b.key + ", rule skipped (mod not installed, or a typo?)"));
            }
            return false;
        }
        if (b.matcher == XpRule.Matcher.TAG && !own) {
            boolean any = false;
            for (String candidate : candidates) {
                any |= registries.knowsTag(candidate, b.key);
            }
            if (!any && unknownSeen.add("tag|" + domain + "|" + b.key)) {
                messages.add(new Message(Level.WARN, where + ": no tag #" + b.key
                        + " exists, the rule stays but matches nothing until one does"));
            }
        }
        return true;
    }

    private static XpRule.Exclude exclude(JsonElement element) throws RuleError {
        if (!element.isJsonObject()) {
            throw new RuleError("\"exclude\" must be an object with mods, ids and tags");
        }
        JsonObject obj = element.getAsJsonObject();
        Set<String> namespaces = new LinkedHashSet<>();
        Set<String> ids = new LinkedHashSet<>();
        Set<String> tags = new LinkedHashSet<>();
        for (String key : obj.keySet()) {
            switch (key) {
                case "mods", "namespaces" -> {
                    for (String value : stringList(obj, key)) {
                        if (!NAMESPACE.matcher(value).matches()) {
                            throw new RuleError("\"" + value + "\" is not a valid mod id");
                        }
                        namespaces.add(value);
                    }
                }
                case "ids" -> {
                    for (String value : stringList(obj, key)) {
                        String id = withNamespace(value);
                        if (!ID.matcher(id).matches()) {
                            throw new RuleError("\"" + value + "\" is not a valid id");
                        }
                        ids.add(id);
                    }
                }
                case "tags" -> {
                    for (String value : stringList(obj, key)) {
                        String tag = withNamespace(value.startsWith("#") ? value.substring(1) : value);
                        if (!ID.matcher(tag).matches()) {
                            throw new RuleError("\"" + value + "\" is not a valid tag");
                        }
                        tags.add(tag);
                    }
                }
                default -> throw new RuleError("unknown exclude key \"" + key + "\" (mods, ids, tags)");
            }
        }
        return new XpRule.Exclude(Set.copyOf(namespaces), Set.copyOf(ids), Set.copyOf(tags));
    }

    private static List<String> stringList(JsonObject obj, String key) throws RuleError {
        JsonElement element = obj.get(key);
        if (!element.isJsonArray()) {
            throw new RuleError("\"" + key + "\" must be an array of strings");
        }
        List<String> out = new ArrayList<>();
        for (JsonElement item : element.getAsJsonArray()) {
            out.add(primitiveString(item, key));
        }
        return out;
    }

    private static XpSpec spec(JsonElement element, String name) throws RuleError {
        if (element.isJsonPrimitive() && element.getAsJsonPrimitive().isNumber()) {
            double value = element.getAsDouble();
            checkFinite(value, name);
            return XpSpec.flat(value);
        }
        if (!element.isJsonObject()) {
            throw new RuleError("\"" + name + "\" must be a number or an object with base, per_hardness, per_health, min, max");
        }
        JsonObject obj = element.getAsJsonObject();
        for (String key : obj.keySet()) {
            if (!Set.of("base", "per_hardness", "per_health", "min", "max").contains(key)) {
                throw new RuleError("unknown key \"" + key + "\" in \"" + name + "\"");
            }
        }
        double base = obj.has("base") ? number(obj, "base") : 0;
        double perHardness = obj.has("per_hardness") ? number(obj, "per_hardness") : 0;
        double perHealth = obj.has("per_health") ? number(obj, "per_health") : 0;
        double min = obj.has("min") ? number(obj, "min") : 0;
        double max = obj.has("max") ? number(obj, "max") : Double.POSITIVE_INFINITY;
        for (double value : new double[] {base, perHardness, perHealth, min}) {
            checkFinite(value, name);
        }
        if (base < 0 || min < 0 || perHardness < 0 || perHealth < 0) {
            throw new RuleError("\"" + name + "\" numbers cannot be negative");
        }
        return new XpSpec(base, perHardness, perHealth, min, max);
    }

    private static void checkFinite(double value, String name) throws RuleError {
        if (!Double.isFinite(value)) {
            throw new RuleError("\"" + name + "\" must be a finite number");
        }
        if (value < 0) {
            throw new RuleError("\"" + name + "\" cannot be negative");
        }
    }

    private static String primitiveString(JsonElement element, String name) throws RuleError {
        if (!element.isJsonPrimitive() || !element.getAsJsonPrimitive().isString()) {
            throw new RuleError("\"" + name + "\" holds a non-string value");
        }
        return element.getAsString();
    }

    private static String string(JsonObject obj, String key) throws RuleError {
        return primitiveString(obj.get(key), key);
    }

    private static double number(JsonObject obj, String key) throws RuleError {
        JsonElement element = obj.get(key);
        if (!element.isJsonPrimitive() || !element.getAsJsonPrimitive().isNumber()) {
            throw new RuleError("\"" + key + "\" must be a number");
        }
        return element.getAsDouble();
    }

    private static int intField(JsonObject obj, String key, int fallback) throws RuleError {
        if (!obj.has(key)) {
            return fallback;
        }
        double value = number(obj, key);
        if (value != Math.rint(value) || Math.abs(value) > 1_000_000) {
            throw new RuleError("\"" + key + "\" must be a whole number");
        }
        return (int) value;
    }
}
