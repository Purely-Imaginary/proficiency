package dev.amman.proficiency;

import dev.amman.proficiency.skill.TrialRules;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class TrialRulesTest {

    @Test
    void onlyATrialMobGetsTheFactor() {
        assertEquals(1.0, TrialRules.mobFactor(false, true, 1.5, 2.0));
        assertEquals(1.5, TrialRules.mobFactor(true, false, 1.5, 2.0));
        assertEquals(2.0, TrialRules.mobFactor(true, true, 1.5, 2.0));
    }

    @Test
    void aFactorBelowOneNeverCutsXp() {
        assertEquals(1.0, TrialRules.mobFactor(true, false, 0.5, 2.0));
        assertEquals(1.0, TrialRules.mobFactor(true, true, 2.0, Double.NaN));
    }

    @Test
    void aWavePaysInProportionToTheKillsAndOminousPaysMore() {
        assertEquals(0.0, TrialRules.waveXp(15, 0, false));
        assertEquals(2.5, TrialRules.waveXp(15, 1, false), 1e-9);
        assertEquals(15.0, TrialRules.waveXp(15, TrialRules.FULL_SHARE_KILLS, false), 1e-9);
        assertEquals(15.0, TrialRules.waveXp(15, 500, false), 1e-9);
        assertEquals(30.0, TrialRules.waveXp(15, 500, true), 1e-9);
        assertEquals(0.0, TrialRules.waveXp(0, 10, false));
        assertEquals(0.0, TrialRules.waveXp(-3, 10, true));
    }

    @Test
    void aVaultPaysOnceTheBaseAndOminousDoubles() {
        assertEquals(30.0, TrialRules.vaultXp(30, false));
        assertEquals(60.0, TrialRules.vaultXp(30, true));
        assertEquals(0.0, TrialRules.vaultXp(0, true));
    }

    @Test
    void theMostUsedSkillWinsAndTiesGoToTheLowestIndex() {
        assertEquals(-1, TrialRules.mostUsed(new int[] {0, 0, 0}));
        assertEquals(-1, TrialRules.mostUsed(new int[0]));
        assertEquals(2, TrialRules.mostUsed(new int[] {1, 3, 4}));
        assertEquals(1, TrialRules.mostUsed(new int[] {1, 4, 4}));
    }
}
