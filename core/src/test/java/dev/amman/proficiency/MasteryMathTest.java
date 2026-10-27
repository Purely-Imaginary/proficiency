package dev.amman.proficiency;

import dev.amman.proficiency.skill.Mastery;
import dev.amman.proficiency.skill.MasteryEffects;
import dev.amman.proficiency.skill.MasteryMath;
import dev.amman.proficiency.skill.Skill;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The arithmetic under the Mastery riders: dashes, launches, stretches, caps. */
class MasteryMathTest {

    /** The distance a dash really covers: speed summed over every tick of drag. */
    private static double travelled(double speed, double drag) {
        double sum = 0;
        for (double v = speed; v > 0.001; v *= drag) {
            sum += v;
        }
        return sum;
    }

    @Test
    void aDashCarriesAboutTheTableDistance() {
        for (Skill skill : new Skill[] {Skill.RUNNING, Skill.BLOCKING}) {
            for (int star = 1; star <= Mastery.MAX_STARS; star++) {
                double blocks = MasteryEffects.value(skill, star);
                double speed = MasteryMath.dashSpeed(blocks, MasteryMath.GROUND_DRAG);
                assertEquals(blocks, travelled(speed, MasteryMath.GROUND_DRAG), 0.1, skill + " star " + star);
            }
        }
        for (int star = 1; star <= Mastery.MAX_STARS; star++) {
            double blocks = MasteryEffects.value(Skill.SWIMMING, star);
            double speed = MasteryMath.dashSpeed(blocks, MasteryMath.WATER_DRAG);
            assertEquals(blocks, travelled(speed, MasteryMath.WATER_DRAG), 0.1, "swimming star " + star);
        }
    }

    @Test
    void noDashStartsFasterThanTheServerTolerates() {
        assertTrue(MasteryMath.MAX_DASH_SPEED < 10.0, "the server rejects moves of ten blocks a tick");
        assertEquals(MasteryMath.MAX_DASH_SPEED, MasteryMath.dashSpeed(500, MasteryMath.GROUND_DRAG));
        assertEquals(0.0, MasteryMath.dashSpeed(-3, MasteryMath.GROUND_DRAG));
    }

    @Test
    void aReboundReachesTheTableHeight() {
        // Vanilla's own jump (0.42) clears about 1.25 blocks.
        assertEquals(1.25, MasteryMath.peakHeight(0.42), 0.05);
        for (int star = 1; star <= Mastery.MAX_STARS; star++) {
            double blocks = MasteryEffects.value(Skill.JUMPING, star);
            assertEquals(blocks, MasteryMath.peakHeight(MasteryMath.launchSpeed(blocks)), 0.05, "star " + star);
        }
        assertTrue(MasteryMath.launchSpeed(6) > MasteryMath.launchSpeed(2));
    }

    @Test
    void reagentEchoStretchesWithinItsBounds() {
        assertEquals(MasteryMath.ECHO_MIN_ADD_TICKS, MasteryMath.echoAdd(100, 10), "a short effect still gains 5 s");
        assertEquals(1200, MasteryMath.echoAdd(6000, 20));
        assertEquals(MasteryMath.ECHO_MAX_ADD_TICKS, MasteryMath.echoAdd(20000, 50), "never more than 2 minutes at once");
        assertEquals(0, MasteryMath.echoAdd(MasteryMath.ECHO_CAP_TICKS, 50), "nothing past 20 minutes");
        assertEquals(40, MasteryMath.echoAdd(MasteryMath.ECHO_CAP_TICKS - 40, 50), "stops at the cap");
    }

    @Test
    void quenchRepairsAPercentOfTheMaximum() {
        assertEquals(24, MasteryMath.repairAmount(480, 5));
        assertEquals(120, MasteryMath.repairAmount(480, 25));
        assertEquals(1, MasteryMath.repairAmount(3, 5), "even a tiny tool gets one point back");
    }

    @Test
    void shrugItOffOpensOneKindAtATime() {
        assertEquals(0, MasteryMath.shrugKinds(0).length);
        assertEquals("slowness", MasteryMath.shrugKinds(1)[0]);
        assertEquals(2, MasteryMath.shrugKinds(2).length);
        assertEquals(MasteryMath.SHRUG_ORDER.length, MasteryMath.shrugKinds(99).length);
        assertTrue(MasteryEffects.value(Skill.ENDURANCE, Mastery.MAX_STARS) <= MasteryMath.SHRUG_ORDER.length);
        for (String kind : MasteryMath.SHRUG_ORDER) {
            assertTrue(!kind.equals("wither") && !kind.equals("instant_damage"),
                    "Shrug It Off must never wash off the effects that kill: " + kind);
        }
    }

    @Test
    void theNumbersThatStandBesideTheTableAreBounded() {
        assertEquals(20, MasteryMath.calfGrowthSeconds(1));
        assertEquals(100, MasteryMath.calfGrowthSeconds(5));
        assertEquals(5.0f, MasteryMath.warmBite(5));
        assertEquals(0, MasteryMath.calfGrowthSeconds(-1));
        assertTrue(MasteryMath.MAX_MARKS <= 16 && MasteryMath.MAX_PULLED <= 32, "bounded world effects");
        assertTrue(MasteryMath.MAX_VICTIMS >= 4 && MasteryMath.MAX_VICTIMS <= 12, "an area rider hits a bounded crowd");
        // The heaviest single rider hit is a Flurry of six jabs; none can one-shot a boss from nothing.
        assertTrue(MasteryMath.JAB_DAMAGE * MasteryEffects.value(Skill.UNARMED, Mastery.MAX_STARS) <= 12.0);
        assertTrue(MasteryEffects.value(Skill.SWORDS, Mastery.MAX_STARS) <= 12.0);
    }

    @Test
    void everyRiderBelongsToItsSkillsOwnTable() {
        // The wealth rules: a count of items never passes a handful, a radius stays on the screen.
        assertTrue(MasteryEffects.value(Skill.COMMERCE, Mastery.MAX_STARS) <= 5, "bread");
        assertTrue(MasteryEffects.value(Skill.FISHING, Mastery.MAX_STARS) <= 5, "fish");
        assertTrue(MasteryEffects.value(Skill.WOODCUTTING, Mastery.MAX_STARS) <= 6, "saplings");
        assertTrue(MasteryEffects.value(Skill.EXCAVATION, Mastery.MAX_STARS) <= 12, "ground blocks");
        assertTrue(MasteryEffects.value(Skill.SPELUNKING, Mastery.MAX_STARS) <= 32, "ping radius");
    }
}
