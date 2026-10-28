package dev.amman.proficiency.client;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Notices which synergies woke up between two looks at the player's skills. The first look after joining is
 * only a baseline (every synergy already awake then is old news), and the ids that wake together come back in
 * the order the caller listed them. Pure.
 */
public final class SynergyWatch {

    private Set<String> known;

    /**
     * @param awakeNow the ids awake now, in a stable order
     * @return the ones that were not awake at the last look; empty on the baseline look
     */
    public List<String> observe(Collection<String> awakeNow) {
        List<String> woke = new ArrayList<>();
        Set<String> now = new HashSet<>(awakeNow);
        if (known != null) {
            for (String id : awakeNow) {
                if (!known.contains(id)) {
                    woke.add(id);
                }
            }
        }
        known = now;
        return woke;
    }

    /** Leaving a world: the next look is a baseline again. */
    public void reset() {
        known = null;
    }
}
