package dev.amman.proficiency.skill;

import dev.amman.proficiency.event.StructureIds;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.function.Function;
import java.util.function.UnaryOperator;

/**
 * The counting and grouping behind the Discovery journal. Pure on purpose (no game classes), so a
 * unit test can hold it; the screen only draws what this returns.
 */
public final class JournalData {

    /** Nothing can walk into it, so it must not count against "x of N". */
    private static final String UNREACHABLE = "minecraft:the_void";

    /** {@code label} is null while the entry is unfound: the screen shows "???" and no name. */
    public record Entry(String id, boolean found, String label) {
    }

    public record Group(String namespace, List<Entry> entries) {
        public int found() {
            int n = 0;
            for (Entry entry : entries) {
                n += entry.found() ? 1 : 0;
            }
            return n;
        }
    }

    public record Tab(int found, int total, List<Group> groups) {
    }

    private JournalData() {
    }

    /** The ids in {@code visited} that start with {@code prefix}, prefix removed. */
    public static Set<String> withPrefix(Collection<String> visited, String prefix) {
        Set<String> out = new LinkedHashSet<>();
        for (String key : visited) {
            if (key.startsWith(prefix) && key.length() > prefix.length()) {
                out.add(key.substring(prefix.length()));
            }
        }
        return out;
    }

    /** Structure ids collapsed to one per family, so "village_taiga" and "village" are one entry. */
    public static UnaryOperator<String> structureCanon() {
        return StructureIds::canonical;
    }

    /**
     * Builds one tab. The universe is what the game has; anything found that is not in it (a mod
     * removed since) is added, so the count can never read "5 of 4". Found entries sort by name,
     * unfound ones after them by id, and Minecraft's group comes before the mods'.
     */
    public static Tab tab(Collection<String> universe, Collection<String> foundIds,
            UnaryOperator<String> canon, Function<String, String> name) {
        Set<String> found = new LinkedHashSet<>();
        for (String id : foundIds) {
            found.add(canon.apply(id));
        }
        Set<String> all = new LinkedHashSet<>();
        for (String id : universe) {
            String canonical = canon.apply(id);
            if (!canonical.equals(UNREACHABLE)) {
                all.add(canonical);
            }
        }
        all.addAll(found);

        Map<String, List<Entry>> byNamespace = new TreeMap<>(
                Comparator.comparing((String ns) -> !ns.equals("minecraft")).thenComparing(ns -> ns));
        for (String id : all) {
            boolean isFound = found.contains(id);
            byNamespace.computeIfAbsent(namespace(id), ns -> new ArrayList<>())
                    .add(new Entry(id, isFound, isFound ? name.apply(id) : null));
        }
        List<Group> groups = new ArrayList<>();
        int foundCount = 0;
        for (Map.Entry<String, List<Entry>> group : byNamespace.entrySet()) {
            group.getValue().sort(Comparator.comparing((Entry e) -> !e.found())
                    .thenComparing(e -> e.found() ? e.label().toLowerCase(java.util.Locale.ROOT) : e.id())
                    .thenComparing(Entry::id));
            Group built = new Group(group.getKey(), List.copyOf(group.getValue()));
            foundCount += built.found();
            groups.add(built);
        }
        return new Tab(foundCount, all.size(), List.copyOf(groups));
    }

    /** "mod:thing" gives "mod"; a bare id belongs to Minecraft. */
    public static String namespace(String id) {
        int colon = id.indexOf(':');
        return colon < 0 ? "minecraft" : id.substring(0, colon);
    }

    /**
     * The first-time kinds per skill from {@code first:<skill>:<kind>} keys. The skill id has no
     * colon but a kind can (a namespaced block), so only the first colon after the prefix splits.
     * Skills come out sorted by id, kinds sorted within each.
     */
    public static Map<String, List<String>> firstKinds(Collection<String> visited) {
        String prefix = "first:";
        List<String> sorted = new ArrayList<>();
        for (String key : visited) {
            if (key.startsWith(prefix)) {
                sorted.add(key.substring(prefix.length()));
            }
        }
        sorted.sort(null);
        Map<String, List<String>> out = new TreeMap<>();
        for (String rest : sorted) {
            int colon = rest.indexOf(':');
            if (colon <= 0 || colon == rest.length() - 1) {
                continue;
            }
            out.computeIfAbsent(rest.substring(0, colon), s -> new ArrayList<>())
                    .add(rest.substring(colon + 1));
        }
        return out;
    }
}
