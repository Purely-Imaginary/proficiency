package dev.amman.proficiency.skill;

import net.minecraft.server.level.ServerPlayer;
import org.jetbrains.annotations.Nullable;

import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.FileAlreadyExistsException;
import java.nio.file.Files;
import java.nio.file.StandardOpenOption;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Writes every XP gain of a player to a CSV on the server, for tuning. The grant path only
 * appends a line to a memory buffer; the sync tick writes the buffer out at most once a second,
 * so a burst of gains never does IO one by one. Independent of the on-screen feed.
 */
public final class XpFeedRecorder {

    public static final String HEADER =
            "epoch_ms,game_time,skill,source,base,final,factors,dimension,x,y,z";

    private static final long FLUSH_INTERVAL_MS = 1000;

    /** A day of heavy play is far below this; a forgotten recording must not fill the disk. */
    public static final int MAX_LINES = 200_000;
    private static final DateTimeFormatter STAMP = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss-SSS");

    private static final class Session {
        final Path file;
        final BufferedWriter writer;
        final StringBuilder pending = new StringBuilder();
        long lastFlush = System.currentTimeMillis();
        int lines;

        Session(Path file, BufferedWriter writer) {
            this.file = file;
            this.writer = writer;
        }
    }

    private static final Map<UUID, Session> SESSIONS = new ConcurrentHashMap<>();

    private XpFeedRecorder() {
    }

    // Pure helpers, unit tested.

    /** One CSV field: quoted when it holds a comma, quote or line break. */
    public static String field(String value) {
        if (value == null) {
            return "";
        }
        if (value.indexOf(',') < 0 && value.indexOf('"') < 0
                && value.indexOf('\n') < 0 && value.indexOf('\r') < 0) {
            return value;
        }
        return '"' + value.replace("\"", "\"\"") + '"';
    }

    private static String number(float value) {
        return String.format(Locale.ROOT, "%.4f", value);
    }

    /** One data line, without the line break. */
    public static String line(long epochMs, long gameTime, String skill, String source, float base,
            float finalXp, List<XpFactors.Factor> factors, String dimension, int x, int y, int z) {
        return epochMs + "," + gameTime + "," + field(skill) + "," + field(source) + ","
                + number(base) + "," + number(finalXp) + "," + field(XpFactors.encode(factors)) + ","
                + field(dimension) + "," + x + "," + y + "," + z;
    }

    /** {@code <player>-<yyyyMMdd-HHmmss-SSS>.csv} with anything odd in the name made safe. */
    public static String fileName(String player, LocalDateTime at) {
        return player.replaceAll("[^A-Za-z0-9_.-]", "_") + "-" + STAMP.format(at) + ".csv";
    }

    // Session handling.

    public static boolean isRecording(UUID player) {
        return SESSIONS.containsKey(player);
    }

    /** Starts recording; returns the file name, or null if already recording or the file fails. */
    @Nullable
    public static String start(ServerPlayer player) {
        if (SESSIONS.containsKey(player.getUUID())) {
            return null;
        }
        try {
            Path dir = player.server.getServerDirectory().resolve("logs").resolve("proficiency-xpfeed");
            Files.createDirectories(dir);
            String name = fileName(player.getGameProfile().getName(), LocalDateTime.now());
            Path file = null;
            BufferedWriter writer = null;
            // CREATE_NEW never truncates another recording; on a clash try -2, -3 and so on.
            for (int n = 1; writer == null; n++) {
                String candidate = n == 1 ? name : name.substring(0, name.length() - 4) + "-" + n + ".csv";
                try {
                    file = dir.resolve(candidate);
                    writer = Files.newBufferedWriter(file, StandardCharsets.UTF_8,
                            StandardOpenOption.CREATE_NEW, StandardOpenOption.WRITE);
                    name = candidate;
                } catch (FileAlreadyExistsException e) {
                    if (n >= 1000) {
                        return null;
                    }
                }
            }
            writer.write(HEADER + "\n");
            writer.flush();
            SESSIONS.put(player.getUUID(), new Session(file, writer));
            return name;
        } catch (IOException e) {
            return null;
        }
    }

    /** Closes the file; returns its name, or null if the player was not recording. */
    @Nullable
    public static String stop(UUID player) {
        Session session = SESSIONS.remove(player);
        if (session == null) {
            return null;
        }
        close(session);
        return session.file.getFileName().toString();
    }

    public static void stopAll() {
        for (UUID id : List.copyOf(SESSIONS.keySet())) {
            stop(id);
        }
    }

    /** Called from the grant path: a memory append only. */
    public static void record(ServerPlayer player, Skill skill, @Nullable String source, float base,
            float finalXp, List<XpFactors.Factor> factors) {
        Session session = SESSIONS.get(player.getUUID());
        if (session == null) {
            return;
        }
        String line = line(System.currentTimeMillis(), player.level().getGameTime(), skill.id(),
                source == null ? "" : source, base, finalXp, factors,
                player.level().dimension().location().toString(),
                player.getBlockX(), player.getBlockY(), player.getBlockZ());
        synchronized (session) {
            session.pending.append(line).append('\n');
            session.lines++;
        }
        if (session.lines >= MAX_LINES) {
            stop(player.getUUID());
            player.sendSystemMessage(net.minecraft.network.chat.Component.translatable(
                    "proficiency.command.xpfeed_record_limit"));
        }
    }

    /** Called from the sync tick: writes buffers that are at least a second old. */
    public static void flushDue() {
        if (SESSIONS.isEmpty()) {
            return;
        }
        long now = System.currentTimeMillis();
        for (Session session : SESSIONS.values()) {
            if (now - session.lastFlush >= FLUSH_INTERVAL_MS) {
                session.lastFlush = now;
                flush(session);
            }
        }
    }

    private static void flush(Session session) {
        String text;
        synchronized (session) {
            if (session.pending.length() == 0) {
                return;
            }
            text = session.pending.toString();
            session.pending.setLength(0);
        }
        try {
            session.writer.write(text);
            session.writer.flush();
        } catch (IOException ignored) {
            // A full disk must not break XP. The lines are lost, the game goes on.
        }
    }

    private static void close(Session session) {
        flush(session);
        try {
            session.writer.close();
        } catch (IOException ignored) {
            // Nothing left to save.
        }
    }
}
