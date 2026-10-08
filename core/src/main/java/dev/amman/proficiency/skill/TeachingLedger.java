package dev.amman.proficiency.skill;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * The Social XP teachers are owed for rested XP their students spent, and have not been paid yet.
 * Pure data and plain text files, so it is unit tested. One entry per teacher and student, in
 * base Social XP (before Social's own multipliers).
 *
 * <p>It is kept per student on purpose. A teacher's credit is a share of what one student spent,
 * so when that student dies the credit still waiting on it is lost with the rest of the student's
 * rested XP: a death empties the pool and the pending credit together.
 *
 * <p>Saved as one line per entry, {@code teacher student xp}, so a teacher who is offline when a
 * student spends is paid at the next login, even across a server restart.
 */
public final class TeachingLedger {

    /** The most one teacher can have waiting. A sanity bound, far above any real session. */
    public static final double MAX_OWED = 1_000_000.0;

    private final Map<UUID, Map<UUID, Double>> owed = new HashMap<>();
    private boolean dirty;

    /** Adds credit owed to {@code teacher} for what {@code student} spent. Ignores broken numbers. */
    public synchronized void add(UUID teacher, UUID student, double xp) {
        if (teacher == null || student == null || !(xp > 0) || Double.isInfinite(xp)
                || teacher.equals(student)) {
            return;
        }
        Map<UUID, Double> byStudent = owed.computeIfAbsent(teacher, id -> new HashMap<>());
        double next = byStudent.getOrDefault(student, 0.0) + xp;
        byStudent.put(student, next);
        if (owedTo(teacher) > MAX_OWED) {
            byStudent.put(student, Math.max(0.0, next - (owedTo(teacher) - MAX_OWED)));
        }
        dirty = true;
    }

    /** Everything waiting for this teacher. */
    public synchronized double owedTo(UUID teacher) {
        Map<UUID, Double> byStudent = owed.get(teacher);
        if (byStudent == null) {
            return 0.0;
        }
        double sum = 0.0;
        for (double value : byStudent.values()) {
            sum += value;
        }
        return sum;
    }

    /** Pays out: returns everything waiting for this teacher and forgets it. */
    public synchronized double take(UUID teacher) {
        Map<UUID, Double> byStudent = owed.remove(teacher);
        if (byStudent == null) {
            return 0.0;
        }
        double sum = 0.0;
        for (double value : byStudent.values()) {
            sum += value;
        }
        dirty = true;
        return sum;
    }

    /**
     * A student's death: every credit that student's spending earned and nobody has been paid yet
     * is lost. Returns the base XP that went.
     */
    public synchronized double wipeStudent(UUID student) {
        double lost = 0.0;
        for (var teachers = owed.entrySet().iterator(); teachers.hasNext(); ) {
            Map<UUID, Double> byStudent = teachers.next().getValue();
            Double gone = byStudent.remove(student);
            if (gone != null) {
                lost += gone;
                dirty = true;
            }
            if (byStudent.isEmpty()) {
                teachers.remove();
            }
        }
        return lost;
    }

    public synchronized boolean isEmpty() {
        return owed.isEmpty();
    }

    /** True once since the last time this was asked, when something changed that is not saved yet. */
    public synchronized boolean takeDirty() {
        boolean was = dirty;
        dirty = false;
        return was;
    }

    public synchronized void markDirty() {
        dirty = true;
    }

    /** The saved form: one line per entry. */
    public synchronized List<String> toLines() {
        List<String> lines = new ArrayList<>();
        owed.forEach((teacher, byStudent) -> byStudent.forEach((student, xp) -> {
            if (xp > 0) {
                lines.add(teacher + " " + student + " " + xp);
            }
        }));
        java.util.Collections.sort(lines);
        return lines;
    }

    /** Reads lines written by {@link #toLines}; a broken line is skipped, not fatal. */
    public synchronized void loadLines(List<String> lines) {
        owed.clear();
        for (String line : lines) {
            String[] parts = line.trim().split("\\s+");
            if (parts.length != 3) {
                continue;
            }
            try {
                UUID teacher = UUID.fromString(parts[0]);
                UUID student = UUID.fromString(parts[1]);
                double xp = Double.parseDouble(parts[2]);
                if (xp > 0 && !Double.isInfinite(xp) && !Double.isNaN(xp)) {
                    owed.computeIfAbsent(teacher, id -> new HashMap<>()).merge(student, xp, Double::sum);
                }
            } catch (IllegalArgumentException ignored) {
                // A bad line is a lost entry.
            }
        }
        dirty = false;
    }

    /** Writes the file through a temp file so a crash never leaves half of it. */
    public void save(Path file) throws IOException {
        List<String> lines = toLines();
        Path parent = file.getParent();
        if (parent != null) {
            Files.createDirectories(parent);
        }
        Path temp = file.resolveSibling(file.getFileName() + ".tmp");
        Files.write(temp, lines, StandardCharsets.UTF_8);
        try {
            Files.move(temp, file, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        } catch (java.nio.file.AtomicMoveNotSupportedException e) {
            Files.move(temp, file, StandardCopyOption.REPLACE_EXISTING);
        }
    }

    /** Loads a file; a missing or unreadable one is an empty ledger. */
    public static TeachingLedger load(Path file) {
        TeachingLedger ledger = new TeachingLedger();
        try {
            if (Files.isRegularFile(file)) {
                ledger.loadLines(Files.readAllLines(file, StandardCharsets.UTF_8));
            }
        } catch (IOException | RuntimeException e) {
            // Unreadable: start empty. Credit is a bonus, and losing it must never stop a server.
        }
        return ledger;
    }
}
