package dev.amman.proficiency;

import dev.amman.proficiency.skill.BrushRules;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BrushRulesTest {

    @Test
    void chancesScaleWithRankAndAreZeroWithout() {
        assertEquals(0.0, BrushRules.extraStrokeChance(0));
        assertEquals(0.6, BrushRules.extraStrokeChance(3), 1e-9);
        assertEquals(0.0, BrushRules.secondRollChance(0));
        assertEquals(0.24, BrushRules.secondRollChance(3), 1e-9);
        assertEquals(0.0, BrushRules.extraStrokeChance(-2));
    }

    @Test
    void noRankNeverProcs() {
        assertFalse(BrushRules.extraStroke(0, 3, 0.0));
        assertFalse(BrushRules.secondRoll(0, 0.0));
    }

    @Test
    void anExtraStrokeNeverFinishesTheBlock() {
        for (int strokes = 1; strokes < BrushRules.REQUIRED_STROKES - 1; strokes++) {
            assertTrue(BrushRules.extraStroke(3, strokes, 0.0), "stroke " + strokes);
        }
        assertFalse(BrushRules.extraStroke(3, BrushRules.REQUIRED_STROKES - 1, 0.0));
        assertFalse(BrushRules.extraStroke(3, BrushRules.REQUIRED_STROKES, 0.0));
        assertFalse(BrushRules.extraStroke(3, 0, 0.0));
    }

    @Test
    void theRollDecides() {
        assertTrue(BrushRules.extraStroke(1, 2, 0.19));
        assertFalse(BrushRules.extraStroke(1, 2, 0.21));
        assertTrue(BrushRules.secondRoll(1, 0.07));
        assertFalse(BrushRules.secondRoll(1, 0.09));
    }
}
