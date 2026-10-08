package dev.amman.proficiency;

import dev.amman.proficiency.skill.KillXp;
import dev.amman.proficiency.skill.SkillService;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

/** The kill bonus formula: clamp(base + maxHealth / divisor, base, cap), boss multiplier after the cap. */
class KillXpTest {

    private static double bonus(double health, boolean boss) {
        return KillXp.bonus(health, boss, 2.0, 5.0, 20.0, 2.0);
    }

    @Test
    void aTwentyHealthMobPaysSix() {
        assertEquals(6.0, bonus(20, false), 1e-9);
    }

    @Test
    void aChickenPaysLessAndZeroHealthPaysTheBase() {
        assertEquals(3.2, bonus(6, false), 1e-9);
        assertEquals(2.0, bonus(0, false), 1e-9);
        assertEquals(2.0, bonus(-5, false), 1e-9);
        assertEquals(2.0, bonus(Double.NaN, false), 1e-9);
    }

    @Test
    void theCapStopsBigMobs() {
        assertEquals(20.0, bonus(90, false), 1e-9);
        assertEquals(20.0, bonus(300, false), 1e-9);
    }

    @Test
    void aBossPaysDoubleAfterTheCap() {
        assertEquals(12.0, bonus(20, true), 1e-9);
        assertEquals(40.0, bonus(300, true), 1e-9);
    }

    @Test
    void configValuesMoveEveryTerm() {
        assertEquals(5.0 + 40 / 10.0, KillXp.bonus(40, false, 5.0, 10.0, 50.0, 3.0), 1e-9);
        assertEquals(10.0, KillXp.bonus(1000, false, 5.0, 10.0, 10.0, 3.0), 1e-9);
        assertEquals(30.0, KillXp.bonus(1000, true, 5.0, 10.0, 10.0, 3.0), 1e-9);
        // A cap below the base can never push a kill under the base.
        assertEquals(5.0, KillXp.bonus(10, false, 5.0, 10.0, 1.0, 1.0), 1e-9);
        assertEquals(0.0, KillXp.bonus(100, true, 0.0, 5.0, 0.0, 2.0), 1e-9);
    }

    @Test
    void aKillLineIsNotAKindSoItNeverPaysFirstTimeByItself() {
        assertFalse(SkillService.isKind(KillXp.KILL_PREFIX + "entity.minecraft.zombie"));
    }
}
