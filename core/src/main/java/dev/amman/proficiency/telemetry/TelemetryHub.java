package dev.amman.proficiency.telemetry;

import dev.amman.proficiency.skill.SkillTuning;

import java.nio.file.Path;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * The one running {@link Telemetry} of a server session and where it writes. The loader glue only
 * calls {@link #start} when a server is up, {@link #flushIfDue} from the tick, and {@link #stop}
 * when it goes down. The folder is {@code <world>/proficiency/telemetry}.
 */
public final class TelemetryHub {

    /** Real time between flushes. */
    public static final long FLUSH_EVERY_MS = 5L * 60_000L;

    private static final Telemetry DATA = new Telemetry();
    private static volatile Path dir;
    private static long lastFlushMs;
    private static LocalDate lastPruneDay;
    private static int retention;
    /** Lines a failed write could not append; they go first on the next flush. */
    private static final List<String> PENDING = new ArrayList<>();
    /** Cap on {@link #PENDING}, so a disk that stays broken cannot grow memory without bound. */
    static final int PENDING_MAX_LINES = 20_000;

    private TelemetryHub() {
    }

    public static Telemetry data() {
        return DATA;
    }

    public static boolean running() {
        return dir != null;
    }

    /** Whether a session is open on exactly this folder. A different world's folder means stale state. */
    public static boolean runningOn(Path telemetryDir) {
        return telemetryDir.equals(dir);
    }

    /**
     * Opens a session: remembers the folder and prunes day files past the retention. Counts left
     * over from an earlier session are dropped: in a single-player JVM the statics outlive the
     * world, and a crash skips {@link #stop}, so the next world must not inherit them.
     */
    public static synchronized void start(Path telemetryDir, int retentionDays, LocalDate today, long nowMs) {
        DATA.reset();
        PENDING.clear();
        dir = telemetryDir;
        lastFlushMs = nowMs;
        retention = retentionDays;
        lastPruneDay = today;
        TelemetryFiles.prune(telemetryDir, today, retentionDays);
    }

    public static synchronized boolean flushIfDue(long nowMs, LocalDate day) {
        if (dir == null || nowMs - lastFlushMs < FLUSH_EVERY_MS) {
            return false;
        }
        flush(nowMs, day);
        return true;
    }

    /** Re-queues are bounded; true when nothing is waiting from a failed write. */
    static synchronized int pendingLines() {
        return PENDING.size();
    }

    public static synchronized void flush(long nowMs, LocalDate day) {
        lastFlushMs = nowMs;
        Path target = dir;
        if (target == null) {
            return;
        }
        // A new day: old files may now be past the retention, and a server can run for weeks.
        if (lastPruneDay != null && day.isAfter(lastPruneDay)) {
            lastPruneDay = day;
            TelemetryFiles.prune(target, day, retention);
        }
        List<String> lines = DATA.drain(nowMs, meta());
        if (!PENDING.isEmpty()) {
            lines.addAll(0, PENDING);
            PENDING.clear();
        }
        if (!TelemetryFiles.append(target, day, lines)) {
            // The disk said no. Keep the lines for the next flush instead of losing five minutes.
            System.err.println("[proficiency] telemetry write to " + target + " failed; will retry ("
                    + lines.size() + " lines held)");
            PENDING.addAll(lines);
            while (PENDING.size() > PENDING_MAX_LINES) {
                PENDING.remove(0);
            }
        }
    }

    /** Writes what is left and closes the session. */
    public static synchronized void stop(long nowMs, LocalDate day) {
        flush(nowMs, day);
        dir = null;
    }

    /** The numbers a report needs and the log lines cannot carry: the curve and the proc unlock. */
    static String meta() {
        SkillTuning t = SkillTuning.current();
        return String.format(Locale.ROOT, "\"curve\":[%s,%s,%s],\"proc_unlock\":%d",
                Telemetry.num(t.curveFloor()), Telemetry.num(t.curveBase()),
                Telemetry.num(t.curveExponent()), t.procUnlockLevel());
    }
}
