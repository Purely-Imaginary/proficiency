package dev.amman.proficiency.hearth.comfort;

import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * What happened near a Hearth since its last scan, in memory only (a restart simply rescans): whether
 * something changed in range (a placement, a break, an explosion), when the next scan may be tried
 * after one that found a chunk unloaded, whether an empty frame or a bare stand is being watched, and
 * who placed which Comfort kind or block id that the Hearth has not seen yet, so a first-seen
 * milestone can name the placer even when the kind only counts later (the furniture goes in before the
 * roof). A note lives until its milestone fires, its block is broken, or its Hearth is gone. Server
 * thread only.
 */
public final class ComfortRequests {

    /** At most this many placer notes per Hearth in all; further ones are not attributed. */
    public static final int MAX_NOTES = 256;

    /** At most this many notes per kind or id: the newest ones win, because an old note is the likeliest to be stale. */
    public static final int MAX_NOTES_PER_KEY = 8;

    /** How long (game ticks) an empty frame or a bare stand keeps the Hearth looking again each cooldown: two minutes. */
    public static final long WATCH_TICKS = 2400L;

    private static final long NONE = Long.MIN_VALUE;

    private static final Map<String, Request> PENDING = new HashMap<>();

    private ComfortRequests() {
    }

    /** One placer note: the block position and who placed it. */
    public record Note(long pos, UUID player) {
    }

    /** One Hearth's pending change flag, watch, retry clock and placer notes. */
    public static final class Request {

        private boolean dirty;
        private long pendingSince = NONE;
        private long retryAt = NONE;
        private final Map<String, LinkedHashMap<Long, UUID>> notes = new HashMap<>();
        private int noteCount;

        /**
         * Remembers that {@code player} placed the block at {@code pos}, worth {@code key} ({@code kind:<kind>}
         * or {@code id:<entry>}). A second note at the same spot replaces the first (a new block, a new placer).
         */
        public void notePlacer(String key, long pos, UUID player) {
            LinkedHashMap<Long, UUID> forKey = notes.computeIfAbsent(key, k -> new LinkedHashMap<>());
            if (forKey.containsKey(pos)) {
                forKey.put(pos, player);
                return;
            }
            if (forKey.size() >= MAX_NOTES_PER_KEY) {
                Iterator<Long> oldest = forKey.keySet().iterator();
                oldest.next();
                oldest.remove();
                noteCount--;
            }
            if (noteCount >= MAX_NOTES) {
                if (forKey.isEmpty()) {
                    notes.remove(key);
                }
                return;
            }
            forKey.put(pos, player);
            noteCount++;
        }

        /** The notes for {@code key}, oldest first; a copy. */
        public List<Note> notes(String key) {
            LinkedHashMap<Long, UUID> forKey = notes.get(key);
            if (forKey == null) {
                return List.of();
            }
            List<Note> out = new ArrayList<>(forKey.size());
            forKey.forEach((pos, player) -> out.add(new Note(pos, player)));
            return out;
        }

        /** The milestone for {@code key} fired: its notes have done their job. */
        public void forget(String key) {
            LinkedHashMap<Long, UUID> gone = notes.remove(key);
            if (gone != null) {
                noteCount -= gone.size();
            }
        }

        /** The block at {@code pos} is gone: whoever was noted for it is not credited for what comes next. */
        public void forgetAt(long pos) {
            Iterator<Map.Entry<String, LinkedHashMap<Long, UUID>>> keys = notes.entrySet().iterator();
            while (keys.hasNext()) {
                LinkedHashMap<Long, UUID> forKey = keys.next().getValue();
                if (forKey.remove(pos) != null) {
                    noteCount--;
                }
                if (forKey.isEmpty()) {
                    keys.remove();
                }
            }
        }

        public int notes() {
            return noteCount;
        }

        private boolean idle() {
            return !dirty && pendingSince == NONE && retryAt == NONE && noteCount == 0;
        }
    }

    /**
     * Something changed in range of the Hearth: its request, created if there was none, flagged for a
     * rescan. A change also re-arms the watch of an empty frame or a bare stand (it may be new).
     */
    public static Request touch(String hearthKey) {
        Request request = PENDING.computeIfAbsent(hearthKey, k -> new Request());
        request.dirty = true;
        request.pendingSince = NONE;
        return request;
    }

    /** The Hearth's request (flagged or not), or null. */
    @Nullable
    public static Request get(String hearthKey) {
        return PENDING.get(hearthKey);
    }

    /** Whether something changed in range since the last scan. */
    public static boolean dirty(String hearthKey) {
        Request request = PENDING.get(hearthKey);
        return request != null && request.dirty;
    }

    /**
     * A scan answered the flag: it is cleared, and so is a retry clock. The entry goes when nothing is
     * left in it (no placer note, no watch).
     */
    public static void scanned(String hearthKey) {
        Request request = PENDING.get(hearthKey);
        if (request == null) {
            return;
        }
        request.dirty = false;
        request.retryAt = NONE;
        if (request.idle()) {
            PENDING.remove(hearthKey);
        }
    }

    /**
     * The last scan saw something that may count any moment (an empty frame, a bare stand): flag the next
     * rescan again, for at most {@link #WATCH_TICKS} after it first showed up (then the 5-minute rescan
     * has it). The window survives the scans it causes; only {@link #touch} or {@link #unwatch} resets it.
     */
    public static void watch(String hearthKey, long now) {
        Request request = PENDING.computeIfAbsent(hearthKey, k -> new Request());
        if (request.pendingSince == NONE) {
            request.pendingSince = now;
        }
        if (now - request.pendingSince <= WATCH_TICKS) {
            request.dirty = true;
        }
    }

    /** The last scan saw nothing pending. */
    public static void unwatch(String hearthKey) {
        Request request = PENDING.get(hearthKey);
        if (request == null) {
            return;
        }
        request.pendingSince = NONE;
        if (request.idle()) {
            PENDING.remove(hearthKey);
        }
    }

    /**
     * A scan could not run (a chunk of the range is not loaded): do not try again before game tick
     * {@code until}. The change flag stays, so the scan still happens once the chunk is there.
     */
    public static void defer(String hearthKey, long until) {
        PENDING.computeIfAbsent(hearthKey, k -> new Request()).retryAt = until;
    }

    /** Whether a scan of this Hearth must wait ({@link #defer}) at game tick {@code now}. */
    public static boolean deferred(String hearthKey, long now) {
        Request request = PENDING.get(hearthKey);
        return request != null && request.retryAt != NONE && now < request.retryAt;
    }

    /** The Hearth is gone (broken, replaced): nothing about it is remembered. */
    public static void drop(String hearthKey) {
        PENDING.remove(hearthKey);
    }

    public static void clear() {
        PENDING.clear();
    }

    public static int size() {
        return PENDING.size();
    }
}
