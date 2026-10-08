package dev.amman.proficiency.telemetry;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.List;

/** The telemetry folder: one {@code YYYY-MM-DD.jsonl} per day, appended to, old days pruned. */
public final class TelemetryFiles {

    public static final String SUFFIX = ".jsonl";

    private TelemetryFiles() {
    }

    public static Path fileFor(Path dir, LocalDate day) {
        return dir.resolve(day + SUFFIX);
    }

    /** Appends the lines to the day's file, creating the folder. Returns false on an IO failure. */
    public static boolean append(Path dir, LocalDate day, List<String> lines) {
        if (lines.isEmpty()) {
            return true;
        }
        StringBuilder text = new StringBuilder(lines.size() * 120);
        for (String line : lines) {
            text.append(line).append('\n');
        }
        try {
            Files.createDirectories(dir);
            Files.writeString(fileFor(dir, day), text, StandardCharsets.UTF_8,
                    StandardOpenOption.CREATE, StandardOpenOption.APPEND);
            return true;
        } catch (IOException e) {
            // A full or read-only disk must not break the game; the counts are simply lost.
            return false;
        }
    }

    /**
     * Deletes day files older than {@code retentionDays} before {@code today}. Anything that is not
     * a {@code YYYY-MM-DD.jsonl} file is left alone. A retention of 0 or less keeps everything.
     * Returns how many files went.
     */
    public static int prune(Path dir, LocalDate today, int retentionDays) {
        if (retentionDays <= 0 || !Files.isDirectory(dir)) {
            return 0;
        }
        LocalDate cutoff = today.minusDays(retentionDays);
        int removed = 0;
        try (DirectoryStream<Path> stream = Files.newDirectoryStream(dir, "*" + SUFFIX)) {
            for (Path file : stream) {
                String name = file.getFileName().toString();
                try {
                    LocalDate day = LocalDate.parse(name.substring(0, name.length() - SUFFIX.length()));
                    if (day.isBefore(cutoff)) {
                        Files.deleteIfExists(file);
                        removed++;
                    }
                } catch (DateTimeParseException | IOException ignored) {
                    // Not one of ours, or locked: leave it.
                }
            }
        } catch (IOException ignored) {
            // Nothing to prune.
        }
        return removed;
    }
}
