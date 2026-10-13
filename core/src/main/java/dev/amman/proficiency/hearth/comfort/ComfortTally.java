package dev.amman.proficiency.hearth.comfort;

import dev.amman.proficiency.hearth.HearthData;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * One scan's findings, pure: which Comfort kinds were present and which Character entries. Each
 * counts once however many blocks stand for it. {@link #result} orders the kinds as the data lists
 * them (the panel's order) and caps Character at {@code character.max}.
 */
public final class ComfortTally {

    private final Set<String> kinds = new LinkedHashSet<>();
    private final Set<String> entries = new LinkedHashSet<>();
    private boolean pending;

    /** Records a kind as present; true the first time in this scan. */
    public boolean addKind(String kind) {
        return kinds.add(kind);
    }

    public boolean hasKind(String kind) {
        return kinds.contains(kind);
    }

    /** Records a Character entry (an item id, or a family); true the first time in this scan. */
    public boolean addEntry(String entry) {
        return entries.add(entry);
    }

    public int entryCount() {
        return entries.size();
    }

    /**
     * Something of a Comfort kind stands here but does not count yet (an empty item frame, a bare armour
     * stand): nothing reports the moment it is filled, so the Hearth looks again soon.
     */
    public void markPending() {
        pending = true;
    }

    /**
     * The result against the data. Kinds the data does not list are dropped (a kind a pack removed),
     * the rest come in the data's order; Character is the number of entries, at most the data's max.
     */
    public Result result(HearthData data) {
        List<String> ordered = new ArrayList<>();
        for (String kind : data.comfort().kinds()) {
            if (kinds.contains(kind) && !ordered.contains(kind)) {
                ordered.add(kind);
            }
        }
        int character = Math.min(entries.size(), Math.max(0, data.character().max()));
        return new Result(Collections.unmodifiableList(ordered), Collections.unmodifiableSet(new LinkedHashSet<>(entries)),
                character, pending);
    }

    /**
     * @param kinds     the kinds present, in the data's order; {@code kinds.size()} is the Comfort
     * @param entries   every Character entry found, uncapped (for the first-seen milestones)
     * @param character the Character: the entries, at most {@code character.max}
     * @param pending   an empty item frame or a bare armour stand was seen: it may count any moment
     */
    public record Result(List<String> kinds, Set<String> entries, int character, boolean pending) {

        public int comfort() {
            return kinds.size();
        }
    }
}
