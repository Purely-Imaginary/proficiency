package dev.amman.proficiency.net;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * What one player's client already knows of their visited set, so a growing set is not resent
 * every time it changes. The first call gives the whole set; later calls give only the keys added
 * since. If a key vanished (a reset command, a wiped save) the client's copy is wrong in a way a
 * delta cannot fix, so that call is a full send again. Pure on purpose: no game classes.
 */
public final class VisitedDelta {

    /** {@code full} means "replace what you have"; otherwise the keys are additions. */
    public record Change(boolean full, List<String> keys) {
    }

    private final Set<String> sent = new HashSet<>();
    private boolean sentOnce;

    /** The change to send now, or null when the client is already up to date. */
    public Change next(Collection<String> current) {
        if (!sentOnce) {
            sentOnce = true;
            return replace(current);
        }
        List<String> added = new ArrayList<>();
        for (String key : current) {
            if (!sent.contains(key)) {
                added.add(key);
            }
        }
        // Every sent key is still present exactly when the sizes add up, so a removal shows here
        // without a second pass over the set.
        if (sent.size() + added.size() != current.size()) {
            return replace(current);
        }
        if (added.isEmpty()) {
            return null;
        }
        sent.addAll(added);
        added.sort(null);
        return new Change(false, added);
    }

    private Change replace(Collection<String> current) {
        sent.clear();
        sent.addAll(current);
        List<String> all = new ArrayList<>(sent);
        all.sort(null);
        return new Change(true, all);
    }
}
