package dev.amman.proficiency.event;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class MovementPayoutTest {

    @Test
    void steadySprintPaysAboutOncePerSecond() {
        MovementPayout m = new MovementPayout();
        double perTick = 5.6 / 20 * 0.05; // sprint metres per tick times XP per metre
        int grants = 0;
        double total = 0;
        int longestGap = 0;
        int gap = 0;
        for (int tick = 0; tick < 200; tick++) { // ten seconds
            double paid = m.add(perTick);
            gap++;
            if (paid > 0) {
                grants++;
                total += paid;
                longestGap = Math.max(longestGap, gap);
                gap = 0;
            }
        }
        assertTrue(grants >= 8 && grants <= 14, "grants: " + grants);
        assertTrue(longestGap <= 40, "gap in ticks: " + longestGap); // well inside the 60 tick window
        assertEquals(2.8, total + m.pending(), 1e-9);
    }

    @Test
    void nothingIsLostOrInvented() {
        MovementPayout m = new MovementPayout();
        double paid = 0;
        double earned = 0;
        for (int i = 0; i < 1000; i++) {
            earned += 0.013;
            paid += m.add(0.013);
        }
        assertEquals(earned, paid + m.pending(), 1e-9);
    }

    @Test
    void belowStepPaysNothingAndBadInputIsIgnored() {
        MovementPayout m = new MovementPayout();
        assertEquals(0.0, m.add(0.1));
        assertEquals(0.0, m.add(-1));
        assertEquals(0.0, m.add(Double.NaN));
        assertEquals(0.4, m.add(0.3), 1e-9);
        assertEquals(0.0, m.pending());
    }

    @Test
    void procRollsStayOnePerWholePoint() {
        MovementPayout m = new MovementPayout();
        int total = 0;
        for (int i = 0; i < 40; i++) {
            total += m.procRolls(0.25);
        }
        assertEquals(10, total);
        assertEquals(0, m.procRolls(0.5));
        assertEquals(1, m.procRolls(0.5));
    }
}
