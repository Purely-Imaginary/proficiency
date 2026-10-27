package dev.amman.proficiency.export;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * The dashboard export of one server session: who was seen (online now or marked offline), the deaths of the
 * last seven days, and the one file {@code <world>/proficiency/export/players.json} written whole through a
 * temporary file and a rename, so a reader never sees half of it. Loader glue calls {@link #start}, then
 * {@link #due} from the once-a-second tick, then {@link #write}; a logout calls {@link #left}.
 */
public final class ExportHub {

    public static final String FILE = "players.json";
    /** Delay of the first write after a start. */
    public static final long FIRST_WRITE_MS = 30_000L;
    public static final String DEATHS_FILE = "deaths.json";

    private static final Map<String, PlayerRow> KNOWN = new LinkedHashMap<>();
    private static final DeathLog DEATHS = new DeathLog();
    private static volatile Path dir;
    private static long lastWriteMs;
    private static List<HearthRow> lastHearths = new ArrayList<>();
    private static String world = "";
    /** The Hearths of the file this session found, kept until the first real write so an early logout cannot blank them. */
    private static String oldHearths;
    private static boolean hearthsKnown;
    private static boolean deathsDirty;
    /** Deaths that came before {@link #start} (the first tick has not run yet): applied when it does. */
    private static final List<Object[]> PENDING = new ArrayList<>();
    private static final int MAX_PENDING = 1000;

    private ExportHub() {
    }

    public static synchronized boolean runningOn(Path folder) {
        return folder.equals(dir);
    }

    public static synchronized boolean running() {
        return dir != null;
    }

    /** Opens a session: forgets the last world's rows and reads the offline players and deaths this folder holds. */
    public static synchronized void start(Path folder, String worldName, long nowMs, long intervalMs) {
        KNOWN.clear();
        DEATHS.clear();
        lastHearths = new ArrayList<>();
        hearthsKnown = false;
        deathsDirty = false;
        dir = folder;
        world = worldName;
        // The first file comes 30 s after the start (chunks around the Hearths are loaded by then), not a full interval.
        lastWriteMs = nowMs - Math.max(0L, intervalMs - FIRST_WRITE_MS);
        String old = read(folder.resolve(FILE));
        if (!old.isBlank() && !ExportJson.readable(old)) {
            // Another version or a damaged file: keep it beside the new one rather than overwrite it silently.
            try {
                Files.copy(folder.resolve(FILE), folder.resolve(FILE + ".bak"), StandardCopyOption.REPLACE_EXISTING);
            } catch (IOException ignored) {
                // Nothing more to do.
            }
        }
        oldHearths = ExportJson.hearthsRaw(old);
        for (PlayerRow row : ExportJson.parsePlayers(old)) {
            KNOWN.put(row.uuid(), row);
        }
        DEATHS.parse(read(folder.resolve(DEATHS_FILE)));
        for (Object[] d : PENDING) {
            DEATHS.add((String) d[0], (Long) d[1]);
            deathsDirty = true;
        }
        PENDING.clear();
    }

    public static synchronized void stop() {
        flushDeaths();
        dir = null;
        oldHearths = null;
        KNOWN.clear();
        DEATHS.clear();
        lastHearths = new ArrayList<>();
    }

    public static synchronized boolean due(long nowMs, long intervalMs) {
        long elapsed = nowMs - lastWriteMs;
        // A negative elapsed time is a wall clock that stepped back: write now rather than wait out the step.
        return dir != null && (elapsed >= intervalMs || elapsed < 0);
    }

    /**
     * A death, recorded now. The file of deaths is written by the next {@link #flushDeaths} (the once-a-second
     * tick), never inside the death event; before {@link #start} the death waits and is applied by it.
     */
    public static synchronized void death(UUID id, long nowMs) {
        if (dir == null) {
            if (PENDING.size() < MAX_PENDING) {
                PENDING.add(new Object[] {id.toString(), nowMs});
            }
            return;
        }
        DEATHS.add(id.toString(), nowMs);
        deathsDirty = true;
    }

    /** Writes the deaths file when a death has come since the last write. */
    public static synchronized void flushDeaths() {
        if (dir == null || !deathsDirty) {
            return;
        }
        deathsDirty = false;
        long now = System.currentTimeMillis();
        writeAtomic(dir.resolve(DEATHS_FILE), DEATHS.render(now));
    }

    public static synchronized int deathsWeek(UUID id, long nowMs) {
        return DEATHS.count(id.toString(), nowMs);
    }

    /**
     * Players found in the world's player files (offline, {@code seenMs} the file's time). A player the hub
     * does not know is added; a known one is replaced only by a newer snapshot, so a live row or a logout
     * snapshot is never overwritten by an older file. A uuid used as a name never replaces a real name.
     */
    public static synchronized void mergeOffline(List<PlayerRow> scanned) {
        if (dir == null) {
            return;
        }
        for (PlayerRow row : scanned) {
            PlayerRow cur = KNOWN.get(row.uuid());
            if (cur == null) {
                KNOWN.put(row.uuid(), row.asOffline());
                continue;
            }
            boolean newer = row.seenMs() > cur.seenMs();
            PlayerRow next = newer ? row.asOffline() : cur;
            boolean realName = !row.name().equals(row.uuid());
            if (realName && next.name().equals(next.uuid())) {
                next = next.withName(row.name());
            } else if (newer && !realName && !cur.name().equals(cur.uuid())) {
                next = next.withName(cur.name());
            }
            KNOWN.put(row.uuid(), next);
        }
    }

    /** Replaces everything: the online rows are fresh, the rest stay as they were, marked offline. */
    public static synchronized void write(long nowMs, List<PlayerRow> online, List<HearthRow> hearths) {
        if (dir == null) {
            return;
        }
        for (PlayerRow row : online) {
            KNOWN.put(row.uuid(), row);
        }
        lastHearths = new ArrayList<>(hearths);
        hearthsKnown = true;
        lastWriteMs = nowMs;
        flush(nowMs, online);
    }

    /** A player left: their last snapshot is kept as offline and the file is written with the last Hearths. */
    public static synchronized void left(long nowMs, PlayerRow last, List<PlayerRow> stillOnline) {
        if (dir == null) {
            return;
        }
        KNOWN.put(last.uuid(), last.asOffline());
        flush(nowMs, stillOnline);
    }

    /** Several players leave at once (the server stops): all are kept as offline and the file is written once. */
    public static synchronized void leftAll(long nowMs, List<PlayerRow> last) {
        if (dir == null) {
            return;
        }
        for (PlayerRow row : last) {
            KNOWN.put(row.uuid(), row.asOffline());
        }
        flush(nowMs, List.of());
    }

    private static void flush(long nowMs, List<PlayerRow> online) {
        List<String> live = new ArrayList<>();
        for (PlayerRow row : online) {
            live.add(row.uuid());
        }
        List<PlayerRow> rows = new ArrayList<>();
        for (PlayerRow row : KNOWN.values()) {
            PlayerRow shown = live.contains(row.uuid()) ? row : row.online() ? row.asOffline() : row;
            rows.add(shown.withDeaths(DEATHS.count(shown.uuid(), nowMs)));
        }
        rows.sort((a, b) -> a.name().compareToIgnoreCase(b.name()));
        // The write clock is the interval's alone: a logout flush must not postpone or hasten the next one.
        flushDeaths();
        String raw = !hearthsKnown ? oldHearths : null;
        writeAtomic(dir.resolve(FILE), ExportJson.render(nowMs, world, rows, lastHearths, raw));
    }

    static String read(Path file) {
        try {
            return Files.exists(file) ? Files.readString(file, StandardCharsets.UTF_8) : "";
        } catch (IOException e) {
            return "";
        }
    }

    /** Writes {@code text} to a sibling temporary file and renames it over {@code target}. False on an IO failure. */
    public static boolean writeAtomic(Path target, String text) {
        Path tmp = target.resolveSibling(target.getFileName() + ".tmp");
        try {
            Files.createDirectories(target.getParent());
            Files.writeString(tmp, text, StandardCharsets.UTF_8);
            try {
                Files.move(tmp, target, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
            } catch (AtomicMoveNotSupportedException e) {
                Files.move(tmp, target, StandardCopyOption.REPLACE_EXISTING);
            }
            return true;
        } catch (IOException e) {
            // A full or read-only disk must not break the game; the dashboard simply keeps the last file.
            try {
                Files.deleteIfExists(tmp);
            } catch (IOException ignored) {
                // Nothing more to do.
            }
            return false;
        }
    }
}
