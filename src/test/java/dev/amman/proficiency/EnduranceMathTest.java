package dev.amman.proficiency;

import dev.amman.proficiency.event.EnduranceMath;
import dev.amman.proficiency.event.EnduranceMath.Kind;
import dev.amman.proficiency.skill.PlayerSkills;
import dev.amman.proficiency.skill.Skill;
import dev.amman.proficiency.skill.SkillMath;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** What a hit is worth to Endurance, and the arithmetic of its tree. */
class EnduranceMathTest {

    @Test
    void aLivingAttackerPaysInFull() {
        assertEquals(Kind.ATTACKER, EnduranceMath.classify(true, false, "mob_attack"));
        assertEquals(Kind.ATTACKER, EnduranceMath.classify(true, false, "player_attack"));
        // A skeleton's arrow is a living attacker too, whatever the damage type says.
        assertEquals(Kind.ATTACKER, EnduranceMath.classify(true, false, "arrow"));
        assertEquals(3.0, EnduranceMath.xpFor(Kind.ATTACKER, 3.0, true, true), 1e-9);
    }

    @Test
    void theWorldPaysHalf() {
        for (String type : new String[] {"fall", "in_fire", "on_fire", "lava", "cactus",
                "sweet_berry_bush", "drown", "magic", "wither", "explosion"}) {
            assertEquals(Kind.ENVIRONMENT, EnduranceMath.classify(false, false, type), type);
        }
        assertEquals(2.0, EnduranceMath.xpFor(Kind.ENVIRONMENT, 4.0, true, true), 1e-9);
    }

    @Test
    void voidKillStarvationAndGenericPayNothing() {
        for (String type : new String[] {"fell_out_of_world", "generic_kill", "outside_border", "starve", "generic"}) {
            assertEquals(Kind.NONE, EnduranceMath.classify(false, false, type), type);
            // Even with something alive nearby getting the credit.
            assertEquals(Kind.NONE, EnduranceMath.classify(true, false, type), type);
        }
        assertEquals(0.0, EnduranceMath.xpFor(Kind.NONE, 10.0, true, true), 1e-9);
    }

    /** Your own arrow, your own TNT, your own firework: hurting yourself is not a hardship. */
    @Test
    void selfInflictedPaysNothing() {
        assertEquals(Kind.NONE, EnduranceMath.classify(false, true, "player_explosion"));
        assertEquals(Kind.NONE, EnduranceMath.classify(false, true, "arrow"));
    }

    @Test
    void anUnknownModdedTypeCountsAsTheWorld() {
        assertEquals(Kind.ENVIRONMENT, EnduranceMath.classify(false, false, "some_mod_acid"));
        assertEquals(Kind.ENVIRONMENT, EnduranceMath.classify(false, false, null));
    }

    @Test
    void onlySurvivedHitsInSurvivalCount() {
        assertEquals(0.0, EnduranceMath.xpFor(Kind.ATTACKER, 5.0, false, true), 1e-9, "a killing blow pays nothing");
        assertEquals(0.0, EnduranceMath.xpFor(Kind.ATTACKER, 5.0, true, false), 1e-9, "creative or spectator");
        assertEquals(0.0, EnduranceMath.xpFor(Kind.ATTACKER, 0.0, true, true), 1e-9, "all absorbed: no health lost");
        assertEquals(0.0, EnduranceMath.xpFor(Kind.ATTACKER, -2.0, true, true), 1e-9);
        assertEquals(0.0, EnduranceMath.xpFor(Kind.ATTACKER, Double.NaN, true, true), 1e-9);
        assertEquals(0.0, EnduranceMath.xpFor(Kind.ATTACKER, Double.POSITIVE_INFINITY, true, true), 1e-9);
    }

    @Test
    void oneHitIsCapped() {
        assertEquals(EnduranceMath.MAX_XP_PER_HIT, EnduranceMath.xpFor(Kind.ATTACKER, 500.0, true, true), 1e-9);
    }

    /** A cactus loop hits the cap and gets nothing more until a minute after the first payment. */
    @Test
    void theEnvironmentalBudgetCapsARollingMinute() {
        EnduranceMath.Budget budget = new EnduranceMath.Budget();
        double total = 0;
        for (long tick = 0; tick < 600; tick += 10) {
            total += budget.take(tick, 0.5);
        }
        assertEquals(EnduranceMath.ENVIRONMENT_XP_CAP, total, 1e-9);
        assertEquals(0.0, budget.take(1100, 5.0), 1e-9, "still inside the first payment's minute");
        // The first payment (tick 0) has left the window; exactly that much comes free again.
        assertEquals(0.5, budget.take(EnduranceMath.ENVIRONMENT_WINDOW_TICKS, 5.0), 1e-9);
    }

    @Test
    void theBudgetGrantsPartOfAHitThatCrossesTheCap() {
        EnduranceMath.Budget budget = new EnduranceMath.Budget(10.0, 1200);
        assertEquals(8.0, budget.take(0, 8.0), 1e-9);
        assertEquals(2.0, budget.take(5, 8.0), 1e-9);
        assertEquals(0.0, budget.take(6, 1.0), 1e-9);
        assertEquals(0.0, budget.take(7, Double.NaN), 1e-9);
        assertEquals(10.0, budget.take(2000, 50.0), 1e-9, "a quiet minute later the whole cap is back");
    }

    @Test
    void scarsStackFadeAndCap() {
        assertEquals(1.0, EnduranceMath.scarMultiplier(0, 3), 1e-9);
        assertEquals(0.94, EnduranceMath.scarMultiplier(1, 3), 1e-9);
        assertEquals(0.70, EnduranceMath.scarMultiplier(5, 3), 1e-9);
        assertEquals(0.70, EnduranceMath.scarMultiplier(50, 3), 1e-9, "five scars at most");
        assertEquals(3, EnduranceMath.scarsAt(3, 100, 100 + EnduranceMath.SCAR_FADE_TICKS));
        assertEquals(0, EnduranceMath.scarsAt(3, 100, 101 + EnduranceMath.SCAR_FADE_TICKS));
    }

    @Test
    void leanTimesAndIndomitableStayInBounds() {
        assertEquals(0.45, EnduranceMath.leanRefund(3), 1e-9);
        assertEquals(0.9, EnduranceMath.leanRefund(20), 1e-9);
        assertEquals(0.0, EnduranceMath.leanRefund(-1), 1e-9);
        assertEquals(2.0f, EnduranceMath.indomitableAbsorption(0f), 1e-6);
        assertEquals(8.0f, EnduranceMath.indomitableAbsorption(7f), 1e-6);
        assertEquals(12.0f, EnduranceMath.indomitableAbsorption(12f), 1e-6, "never takes away a golden apple's hearts");
    }

    /** The passive: +100% of base health at level 100, linear like every other skill. */
    @Test
    void thePassiveIsTenHeartsAtOneHundred() {
        PlayerSkills skills = new PlayerSkills();
        assertEquals(0.0, skills.bonus(Skill.ENDURANCE), 1e-9);
        skills.setLevel(Skill.ENDURANCE, 50);
        assertEquals(0.5, skills.bonus(Skill.ENDURANCE), 1e-9);
        skills.setLevel(Skill.ENDURANCE, SkillMath.MAX_LEVEL);
        assertEquals(1.0, skills.bonus(Skill.ENDURANCE), 1e-9);
        assertTrue(Skill.byId("endurance") == Skill.ENDURANCE);
    }
}
