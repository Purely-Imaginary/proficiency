package dev.amman.proficiency;

import dev.amman.proficiency.skill.TeachingLedger;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The Social XP owed to teachers: pending credit, paid once, saved, and lost with a dead student. */
class TeachingLedgerTest {

    private static final UUID TEACHER = UUID.fromString("00000000-0000-0000-0000-0000000000b1");
    private static final UUID STUDENT = UUID.fromString("00000000-0000-0000-0000-0000000000b2");
    private static final UUID STUDENT_2 = UUID.fromString("00000000-0000-0000-0000-0000000000b3");

    @Test
    void creditIsPaidOnceAndThenGone() {
        TeachingLedger ledger = new TeachingLedger();
        ledger.add(TEACHER, STUDENT, 2.5);
        ledger.add(TEACHER, STUDENT_2, 1.5);
        assertEquals(4.0, ledger.owedTo(TEACHER), 1e-9);
        assertEquals(4.0, ledger.take(TEACHER), 1e-9);
        assertEquals(0.0, ledger.take(TEACHER));
        assertTrue(ledger.isEmpty());
    }

    @Test
    void aStudentsDeathLosesTheCreditStillWaitingOnThem() {
        TeachingLedger ledger = new TeachingLedger();
        ledger.add(TEACHER, STUDENT, 2.5);
        ledger.add(TEACHER, STUDENT_2, 1.5);
        assertEquals(2.5, ledger.wipeStudent(STUDENT), 1e-9);
        assertEquals(1.5, ledger.owedTo(TEACHER), 1e-9);
        assertEquals(0.0, ledger.wipeStudent(STUDENT));
    }

    @Test
    void creditForAnOfflineTeacherSurvivesARestart(@TempDir Path dir) throws Exception {
        TeachingLedger ledger = new TeachingLedger();
        ledger.add(TEACHER, STUDENT, 2.5);
        Path file = dir.resolve("proficiency").resolve("teaching_credit.txt");
        ledger.save(file);
        TeachingLedger loaded = TeachingLedger.load(file);
        assertEquals(2.5, loaded.owedTo(TEACHER), 1e-9);
        assertFalse(loaded.takeDirty(), "a fresh load has nothing to save");
        // The student is still remembered, so their death can still cancel it.
        assertEquals(2.5, loaded.wipeStudent(STUDENT), 1e-9);
    }

    @Test
    void brokenInputIsIgnored(@TempDir Path dir) throws Exception {
        TeachingLedger ledger = new TeachingLedger();
        ledger.add(TEACHER, STUDENT, Double.NaN);
        ledger.add(TEACHER, STUDENT, -1);
        ledger.add(TEACHER, TEACHER, 5);
        ledger.add(null, STUDENT, 5);
        assertTrue(ledger.isEmpty());
        Path file = dir.resolve("bad.txt");
        java.nio.file.Files.writeString(file, "garbage\n" + TEACHER + " " + STUDENT + " 3.0\nx y z\n");
        assertEquals(3.0, TeachingLedger.load(file).owedTo(TEACHER), 1e-9);
        assertTrue(TeachingLedger.load(dir.resolve("missing.txt")).isEmpty());
    }

    @Test
    void anAddMarksItDirtyOnce() {
        TeachingLedger ledger = new TeachingLedger();
        ledger.add(TEACHER, STUDENT, 1);
        assertTrue(ledger.takeDirty());
        assertFalse(ledger.takeDirty());
    }
}
