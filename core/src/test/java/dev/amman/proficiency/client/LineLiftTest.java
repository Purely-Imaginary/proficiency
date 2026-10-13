package dev.amman.proficiency.client;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LineLiftTest {

    @Test
    void theDefaultBarsLeaveTheLineWhereItIs() {
        // ForgeGui starts both stacks at 39; one row of hearts and the food bar make 49 each.
        assertEquals(0, LineLift.forLeftRight(49, 49));
        // Two rows of hearts with no armour: Forge's action bar row is still 68.
        assertEquals(0, LineLift.forLeftRight(59, 49));
    }

    @Test
    void everyRowAboveThatLiftsTheLineByItsHeight() {
        assertEquals(10, LineLift.forLeftRight(69, 49));
        assertEquals(10, LineLift.forLeftRight(49, 69));
        assertEquals(20, LineLift.forLeftRight(79, 59));
    }

    @Test
    void theLineSlidesToItsNewRowAndLands() {
        double y = 0;
        y = LineLift.ease(y, 10, 16);
        assertTrue(y > 0 && y < 10, "one frame moves part of the way: " + y);
        for (int i = 0; i < 30; i++) {
            y = LineLift.ease(y, 10, 16);
        }
        assertEquals(10.0, y, "it lands exactly");
        assertEquals(10.0, LineLift.ease(10.0, 10, 0), "no time, no move");
        assertEquals(0.0, LineLift.ease(10.0, 0, 5000), "a long pause lands at once");
    }
}
