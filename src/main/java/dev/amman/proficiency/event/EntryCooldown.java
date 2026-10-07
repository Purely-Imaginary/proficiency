package dev.amman.proficiency.event;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Per-player, per-structure cooldown for the re-entry banner. In memory only: a logout drops it,
 * so the banner is never stale after a restart. Pure (times are passed in) so a test can drive it.
 */
public final class EntryCooldown {

    private final Map<UUID, Map<String, Long>> nextAllowed = new HashMap<>();

    /** One structure start: its id and the chunk its start sits in, so two villages are two keys. */
    public static String key(String structureId, int startChunkX, int startChunkZ) {
        return structureId + "@" + startChunkX + "," + startChunkZ;
    }

    /** True if the banner may show now. Does not record anything. */
    public boolean ready(UUID player, String key, long now) {
        Map<String, Long> keys = nextAllowed.get(player);
        if (keys == null) {
            return true;
        }
        Long until = keys.get(key);
        return until == null || now >= until;
    }

    /** Starts the cooldown. A length of 0 or less records nothing. */
    public void record(UUID player, String key, long now, long cooldown) {
        if (cooldown <= 0) {
            return;
        }
        nextAllowed.computeIfAbsent(player, id -> new HashMap<>()).put(key, now + cooldown);
    }

    /** Drops one player's entries. Called on logout. */
    public void forget(UUID player) {
        nextAllowed.remove(player);
    }

    /** Drops entries that ran out, so a long session in a big world does not grow the map. */
    public void prune(UUID player, long now) {
        Map<String, Long> keys = nextAllowed.get(player);
        if (keys != null) {
            keys.values().removeIf(until -> now >= until);
            if (keys.isEmpty()) {
                nextAllowed.remove(player);
            }
        }
    }
}
