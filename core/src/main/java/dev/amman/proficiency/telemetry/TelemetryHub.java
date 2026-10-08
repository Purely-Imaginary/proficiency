package dev.amman.proficiency.telemetry;

import dev.amman.proficiency.skill.SkillTuning;

import java.nio.file.Path;
import java.time.LocalDate;
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

    private TelemetryHub() {
    }

    public static Telemetry data() {
        return DATA;
    }

    public static boolean running() {
        return dir != null;
    }

    /** Opens a session: remembers the folder and prunes day files past the retention. */
    public static synchronized void start(Path telemetryDir, int retentionDays, LocalDate today, long nowMs) {
        dir = telemetryDir;
        lastFlushMs = nowMs;
        TelemetryFiles.prune(telemetryDir, today, retentionDays);
    }

    public static synchronized boolean flushIfDue(long nowMs, LocalDate day) {
        if (dir == null || nowMs - lastFlushMs < FLUSH_EVERY_MS) {
            return false;
        }
        flush(nowMs, day);
        return true;
    }

    public static synchronized void flush(long nowMs, LocalDate day) {
        lastFlushMs = nowMs;
        Path target = dir;
        if (target == null) {
            return;
        }
        List<String> lines = DATA.drain(nowMs, meta());
        TelemetryFiles.append(target, day, lines);
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
