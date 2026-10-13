package dev.amman.proficiency.hearth.sieges;

import dev.amman.proficiency.hearth.HearthData;
import dev.amman.proficiency.hearth.HearthDataLoader;
import dev.amman.proficiency.hearth.SiegeRules;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.SplittableRandom;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Siege levels I..V and the five horns (docs/HEARTH-SPEC.md, "The Siege Horns"). */
class SiegeLevelsTest {

    private static final HearthData.Siege S = HearthData.DEFAULTS.siege();

    @Test
    void levelsMapToDataKeysAndCeilings() {
        assertEquals(3, SiegeRules.levelKey(1));
        assertEquals(7, SiegeRules.levelKey(5));
        assertEquals(7, SiegeRules.levelKey(9), "clamped to V");
        assertEquals(0, SiegeRules.levelForCeiling(2));
        assertEquals(1, SiegeRules.levelForCeiling(3));
        assertEquals(4, SiegeRules.levelForCeiling(6));
        assertEquals(List.of(3, 4, 5, 6, 6), List.of(SiegeRules.ceilingForLevel(1), SiegeRules.ceilingForLevel(2),
                SiegeRules.ceilingForLevel(3), SiegeRules.ceilingForLevel(4), SiegeRules.ceilingForLevel(5)));
        assertEquals(6, SiegeRules.trophyTier(5));
        assertEquals(3, SiegeRules.trophyTier(1));
    }

    @Test
    void eachHornNeedsItsStage() {
        for (int level = 1; level <= SiegeRules.MAX_LEVEL; level++) {
            int need = SiegeRules.ceilingForLevel(level);
            assertEquals(HornCheck.Result.CEILING, HornCheck.check(level, true, false, true, false, need - 1, false, 9, S),
                    "level " + level + " one stage short");
            assertEquals(HornCheck.Result.OK, HornCheck.check(level, true, false, true, false, need, false, 9, S),
                    "level " + level + " at its stage");
        }
        // The old signature is the first horn.
        assertEquals(HornCheck.Result.OK, HornCheck.check(true, false, true, false, 3, false, 9, S));
    }

    @Test
    void levelFiveIsTheWarhost() {
        assertEquals(15, SiegeRules.waveSize(7, 1, S));
        assertEquals(12, SiegeRules.waveSize(6, 1, S));
        assertTrue(SiegeRules.mobsFor(7, S).contains("minecraft:ravager"));
        assertTrue(!SiegeRules.mobsFor(6, S).contains("minecraft:ravager"), "no ravager below level V");
        SplittableRandom random = new SplittableRandom(11);
        for (int i = 0; i < 50; i++) {
            List<String> wave = SiegeWaves.compose(15, 7, S, random);
            assertEquals(15, wave.size());
            assertEquals(1, wave.stream().filter("minecraft:ravager"::equals).count(), wave.toString());
            assertEquals(1, wave.stream().filter("minecraft:evoker"::equals).count(), wave.toString());
        }
        // Room for one leader only: the first listed leads, a common mob still comes.
        List<String> two = SiegeWaves.compose(2, 7, S, random);
        assertEquals(2, two.size());
        assertEquals(1, two.stream().filter(id -> S.onePerWave().contains(id)).count(), two.toString());
    }

    @Test
    void theBurstFollowsTheLevelAgainstTheCeiling() {
        assertEquals(1.0, SiegeRules.burstScale(4, 6), 1e-9);
        assertEquals(1.25, SiegeRules.burstScale(5, 6), 1e-9);
        assertEquals(0.25, SiegeRules.burstScale(1, 6), 1e-9);
        assertEquals(1.0, SiegeRules.burstScale(1, 3), 1e-9);
        HearthData d = HearthData.DEFAULTS;
        assertEquals(SiegeRules.burst(700, 1, d) * 1.25, SiegeRules.burst(700, 1, d, 1.25), 1e-9);
    }

    @Test
    void theLoaderTakesKeySevenAndRefusesEight() {
        HearthDataLoader.Result ok = HearthDataLoader.parse("t",
                "{\"siege\": {\"baseMobs\": {\"7\": 20}, \"mobs\": {\"7\": [\"minecraft:ravager\"]}}}");
        assertEquals(20, ok.data().siege().baseMobs(7));
        HearthDataLoader.Result bad = HearthDataLoader.parse("t", "{\"siege\": {\"baseMobs\": {\"8\": 20}}}");
        assertEquals(15, bad.data().siege().baseMobs(7), "a bad map keeps the defaults");
    }
}
