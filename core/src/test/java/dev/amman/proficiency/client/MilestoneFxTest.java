package dev.amman.proficiency.client;

import dev.amman.proficiency.skill.MilestoneTiers;
import dev.amman.proficiency.skill.Skill;
import org.junit.jupiter.api.Test;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Milestone detection (which tier plays) and the ceremony script (each tier bigger than the last). */
class MilestoneFxTest {

    private static final Set<Integer> NONE = Set.of();

    @Test
    void aJumpOverSeveralLevelsPlaysOnlyTheHighestTier() {
        assertEquals(25, MilestoneTiers.fresh(5, 30, NONE::contains), "10 and 25 crossed: only the 25");
        assertEquals(100, MilestoneTiers.fresh(0, 100, NONE::contains));
        assertEquals(50, MilestoneTiers.fresh(24, 74, NONE::contains));
        assertEquals(10, MilestoneTiers.fresh(9, 10, NONE::contains));
    }

    @Test
    void aGainThatCrossesNothingPlaysNothing() {
        assertEquals(0, MilestoneTiers.fresh(10, 24, NONE::contains));
        assertEquals(0, MilestoneTiers.fresh(11, 11, NONE::contains));
        assertEquals(0, MilestoneTiers.fresh(30, 20, NONE::contains), "going down never plays");
    }

    @Test
    void aCelebratedTierNeverPlaysAgain() {
        Set<Integer> seen = new HashSet<>(Set.of(10, 25));
        assertEquals(0, MilestoneTiers.fresh(9, 30, seen::contains), "both already celebrated");
        assertEquals(10, MilestoneTiers.fresh(9, 30, Set.of(25)::contains), "the highest unseen one still plays");
        assertEquals(0, MilestoneTiers.fresh(99, 100, Set.of(100)::contains), "Mastered never replays on a relog");
    }

    @Test
    void setCommandsMarkEveryTierTheyJumpOverSoNothingOldReplays() {
        // /proficiency set mining 60 marks 10, 25 and 50; the next real gain 60 -> 61 crosses none,
        // and a later climb after the level was set lower still finds those tiers seen.
        int[] crossed = MilestoneTiers.crossed(0, 60);
        assertArrayEquals(new int[] {10, 25, 50}, crossed);
        Set<Integer> seen = new HashSet<>();
        for (int tier : crossed) {
            seen.add(tier);
        }
        assertEquals(0, MilestoneTiers.fresh(5, 60, seen::contains));
        assertEquals(75, MilestoneTiers.fresh(60, 75, seen::contains));
        assertArrayEquals(new int[] {}, MilestoneTiers.crossed(60, 60));
    }

    @Test
    void everyTierOpensSomethingAndTheHundredOpensTheStars() {
        for (Skill skill : Skill.VALUES) {
            for (int tier : MilestoneTiers.TIERS) {
                assertTrue(MilestoneTiers.opened(skill, tier, 25, 50).lines() > 0,
                        skill.id() + " " + tier + " opened nothing");
            }
            assertTrue(MilestoneTiers.opened(skill, 100, 25, 50).stars());
            assertTrue(MilestoneTiers.opened(skill, 25, 25, 50).signature());
            assertTrue(MilestoneTiers.opened(skill, 50, 25, 50).ability());
            assertFalse(MilestoneTiers.opened(skill, 50, 25, 50).signature());
        }
    }

    @Test
    void eachTierIsBiggerThanTheLast() {
        int prevBursts = 0;
        int prevCues = 0;
        long prevDuration = 0;
        float prevScale = 0f;
        for (int tier : MilestoneTiers.TIERS) {
            int bursts = MilestoneFx.bursts(tier).size();
            int cues = MilestoneFx.cues(tier).size();
            assertTrue(bursts > prevBursts, "bursts at " + tier);
            assertTrue(cues > prevCues, "cues at " + tier);
            assertTrue(MilestoneFx.durationMs(tier) > prevDuration, "duration at " + tier);
            assertTrue(MilestoneFx.scale(tier) > prevScale, "scale at " + tier);
            assertTrue(bursts <= MilestoneFx.MAX_BURSTS);
            prevBursts = bursts;
            prevCues = cues;
            prevDuration = MilestoneFx.durationMs(tier);
            prevScale = MilestoneFx.scale(tier);
        }
    }

    @Test
    void theScriptStaysInsideTheCeremonyAndTheChimesRise() {
        for (int tier : MilestoneTiers.TIERS) {
            long dur = MilestoneFx.durationMs(tier);
            for (MilestoneFx.Burst b : MilestoneFx.bursts(tier)) {
                assertTrue(b.atMs() >= 0 && b.atMs() < dur - 1500, "burst time at " + tier);
                assertTrue(b.radius() <= 6f && b.height() <= 6f, "burst stays near the player");
                assertTrue(b.shape() >= 0 && b.shape() <= 4);
            }
            List<MilestoneFx.Cue> cues = MilestoneFx.cues(tier);
            float last = 0f;
            for (MilestoneFx.Cue cue : cues) {
                assertTrue(cue.atMs() < dur);
                if (cue.sound() == MilestoneFx.Sound.CHIME) {
                    assertTrue(cue.pitch() > last, "chimes climb at " + tier);
                    assertTrue(cue.pitch() >= 0.5f && cue.pitch() <= 2.0f, "a valid note pitch");
                    last = cue.pitch();
                }
            }
        }
        assertTrue(MilestoneFx.cues(100).stream().anyMatch(c -> c.sound() == MilestoneFx.Sound.CROWD));
        assertTrue(MilestoneFx.cues(100).stream().anyMatch(c -> c.sound() == MilestoneFx.Sound.BELL));
        assertFalse(MilestoneFx.cues(10).stream().anyMatch(c -> c.sound() == MilestoneFx.Sound.CROWD));
    }

    @Test
    void theBannerFadesInHoldsAndOut() {
        assertEquals(0f, MilestoneFx.alpha(50, 0));
        assertEquals(1f, MilestoneFx.alpha(50, 2000));
        assertEquals(0f, MilestoneFx.alpha(50, MilestoneFx.durationMs(50)));
        assertTrue(MilestoneFx.alpha(50, 100) > 0f && MilestoneFx.alpha(50, 100) < 1f);
    }

    @Test
    void theMasteredShineRepeatsAndTheStampLands() {
        assertEquals(-1f, MilestoneFx.shine(100, 100));
        assertTrue(MilestoneFx.shine(100, 1500) > 0f);
        assertTrue(MilestoneFx.shine(100, 4300) > 0f, "it sweeps again");
        assertFalse(MilestoneFx.stampVisible(100));
        assertTrue(MilestoneFx.stampScale(MilestoneFx.STAMP_AT - 100) > 1.2f, "big before it lands");
        assertTrue(MilestoneFx.stampScale(MilestoneFx.STAMP_AT + 2000) > 0.99f);
    }
}
