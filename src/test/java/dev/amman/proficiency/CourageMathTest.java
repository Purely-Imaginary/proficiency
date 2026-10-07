package dev.amman.proficiency;

import dev.amman.proficiency.perk.PerkEffect;
import dev.amman.proficiency.perk.Synergies;
import dev.amman.proficiency.perk.Synergy;
import dev.amman.proficiency.perk.Talents;
import dev.amman.proficiency.skill.CourageMath;
import dev.amman.proficiency.skill.CourageMath.Fight;
import dev.amman.proficiency.skill.PlayerSkills;
import dev.amman.proficiency.skill.Skill;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Idea 37: Courage's odds, its XP sources, its anti-farm rules and its talent numbers. */
class CourageMathTest {

    /** One zombie, you at full health with an iron sword (6 attack) and iron armour (15). */
    private static Fight evenFight() {
        return new Fight(1, 20, 3, false, 20, 6, 20, 15);
    }

    // ---- The odds -----------------------------------------------------------------------------

    @Test
    void evenOrFavourableOddsPayNothing() {
        assertEquals(0.0, CourageMath.odds(evenFight()), "one zombie against a sword");
        assertEquals(0.0, CourageMath.odds(new Fight(0, 20, 3, false, 20, 6, 20, 15)),
                "a mob that is not after you");
        assertEquals(0.0, CourageMath.odds(new Fight(1, 4, 0, false, 20, 1, 20, 0)),
                "a chicken-sized foe, even naked and bare-handed");
        assertEquals(0.0, CourageMath.odds(new Fight(1, 20, 3, false, 20, 6, 10, 0)),
                "exactly half health is not low yet");
    }

    @Test
    void armourAloneNeverMakesAFightBrave() {
        Fight naked = new Fight(1, 20, 3, false, 20, 6, 20, 0);
        assertEquals(0.0, CourageMath.odds(naked));
        assertEquals(1.0, CourageMath.armourScale(20));
        assertEquals(1.0, CourageMath.armourScale(30), "more than full diamond is still full");
        assertEquals(1.5, CourageMath.armourScale(0));
        assertEquals(1.25, CourageMath.armourScale(10), 1e-9);
    }

    @Test
    void beingOutnumberedPaysMoreForEachFoeUpToFive() {
        assertEquals(0.0, CourageMath.crowd(1));
        assertEquals(0.5, CourageMath.crowd(2));
        assertEquals(1.5, CourageMath.crowd(4));
        assertEquals(2.0, CourageMath.crowd(5));
        assertEquals(2.0, CourageMath.crowd(40), "capped");
        Fight three = new Fight(3, 20, 3, false, 20, 6, 20, 20);
        assertEquals(1.0, CourageMath.odds(three), 1e-9);
    }

    @Test
    void aStrongerFoeByHealthOrAttackCounts() {
        // A ravager: 100 health against your 20 is five times stronger, capped at 1.5.
        assertEquals(5.0, CourageMath.strengthRatio(100, 12, 20, 6), 1e-9);
        assertEquals(1.5, CourageMath.strength(5.0, false), 1e-9);
        // A vindicator hits for 13 against your 6: stronger by attack alone.
        double ratio = CourageMath.strengthRatio(24, 13, 20, 6);
        assertEquals(13.0 / 6.0, ratio, 1e-9);
        assertTrue(CourageMath.strength(ratio, false) > 0);
        assertEquals(0.0, CourageMath.strength(1.0, false), "equal is even");
        assertEquals(0.0, CourageMath.strength(0.5, false), "weaker is favourable");
        // A broken "yours" never reads as infinitely stronger.
        assertEquals(0.0, CourageMath.strengthRatio(20, 3, 0, 0));
    }

    @Test
    void aBossIsBig() {
        assertEquals(2.0, CourageMath.strength(0.5, true), 1e-9);
        Fight wither = new Fight(1, 300, 8, true, 20, 8, 20, 20);
        assertEquals(3.5, CourageMath.odds(wither), 1e-9);
        Fight nakedWither = new Fight(3, 300, 8, true, 20, 8, 4, 0);
        assertEquals(CourageMath.ODDS_CAP, CourageMath.odds(nakedWither), "capped at 4");
    }

    @Test
    void lowHealthCountsBelowHalf() {
        assertEquals(0.0, CourageMath.lowHealth(20, 20));
        assertEquals(0.0, CourageMath.lowHealth(10, 20));
        assertEquals(0.5, CourageMath.lowHealth(5, 20), 1e-9);
        assertEquals(1.0, CourageMath.lowHealth(0, 20), 1e-9);
        assertEquals(0.0, CourageMath.lowHealth(5, 0), "no max health, no pressure");
    }

    @Test
    void lowHealthAloneIsNotUnevenOdds() {
        // One weak mob, you at 2 health and bare: still even odds, so no farm on single weak mobs.
        assertEquals(0.0, CourageMath.odds(new Fight(1, 8, 1, false, 20, 6, 2, 0)));
        assertEquals(0.0, CourageMath.odds(new Fight(1, 20, 3, false, 20, 6, 0, 0)));
        // The same low health on top of a crowd of two adds: (0.5 + 0.9) x 1.5.
        assertEquals((0.5 + 0.9) * 1.5, CourageMath.odds(new Fight(2, 8, 1, false, 20, 6, 1, 0)), 1e-9);
    }

    // ---- XP source 1: damage dealt ------------------------------------------------------------

    @Test
    void aHitPaysDamageTimesRateTimesOdds() {
        assertEquals(6 * 0.2 * 1.5, CourageMath.hitXp(6, 20, 1.5, 0.2), 1e-9);
        assertEquals(0.0, CourageMath.hitXp(6, 20, 0.0, 0.2), "even odds");
        assertEquals(0.0, CourageMath.hitXp(Double.NaN, 20, 1.0, 0.2));
        assertEquals(0.0, CourageMath.hitXp(6, 20, 1.0, 0.0), "rate 0 switches it off");
    }

    @Test
    void oneHitCountsAtMostTwentyDamage() {
        assertEquals(CourageMath.MAX_DAMAGE_PER_HIT, CourageMath.countedDamage(500, 1000));
    }

    @Test
    void aMobPaysForAtMostItsOwnHealthBar() {
        // A trapped mob that heals: the first 20 damage pay, after that nothing.
        assertEquals(6.0, CourageMath.countedDamage(6, 20));
        assertEquals(2.0, CourageMath.countedDamage(6, 2), "only what is left of its budget");
        assertEquals(0.0, CourageMath.countedDamage(6, 0));
        assertEquals(0.0, CourageMath.countedDamage(6, -4));
        assertEquals(0.0, CourageMath.hitXp(6, 0, 4.0, 0.2));
    }

    // ---- XP source 2: kills -------------------------------------------------------------------

    @Test
    void aKillPaysTheKillRateTimesOdds() {
        assertEquals(3.0, CourageMath.killXp(1.5, 2.0), 1e-9);
        assertEquals(0.0, CourageMath.killXp(0.0, 2.0), "an even kill pays nothing");
    }

    // ---- The fight window ---------------------------------------------------------------------

    @Test
    void onlyInsideTheFightWindow() {
        assertFalse(CourageMath.inFight(-1, 1000, 400), "never hurt");
        assertTrue(CourageMath.inFight(1000, 1000, 400));
        assertTrue(CourageMath.inFight(600, 1000, 400));
        assertFalse(CourageMath.inFight(599, 1000, 400), "hurt too long ago");
        assertFalse(CourageMath.inFight(2000, 1000, 400), "a time from the future is not a fight");
    }

    // ---- The passive, the proc, the ability ---------------------------------------------------

    @Test
    void thePassiveGrowsWithEachFoeAfterTheFirst() {
        assertEquals(1.0, CourageMath.passiveMultiplier(0.4, 1, false));
        assertEquals(1.1, CourageMath.passiveMultiplier(0.4, 2, false), 1e-9);
        assertEquals(1.4, CourageMath.passiveMultiplier(0.4, 5, false), 1e-9);
        assertEquals(1.4, CourageMath.passiveMultiplier(0.4, 12, false), 1e-9);
        assertEquals(1.4, CourageMath.passiveMultiplier(0.4, 12, true), 1e-9, "Lionheart never adds more bonus");
        assertEquals(1.2, CourageMath.passiveMultiplier(0.4, 2, true), 1e-9, "Lionheart: half at 2 foes");
        assertEquals(1.4, CourageMath.passiveMultiplier(0.4, 3, true), 1e-9, "Lionheart: full at 3 foes");
        // Full tree (Hot Blood 5, Lionheart): 0.40 x 1.40 = 0.56 is the most the passive gives.
        assertEquals(1.56, CourageMath.passiveMultiplier(0.56, 40, true), 1e-9);
        assertEquals(1.0, CourageMath.passiveMultiplier(0.0, 5, false), "level 0");
    }

    @Test
    void rallyNeedsTwoFoes() {
        assertFalse(CourageMath.outnumbered(1));
        assertTrue(CourageMath.outnumbered(2));
        assertEquals(200, CourageMath.rallyTicks(1.0));
        assertEquals(260, CourageMath.rallyTicks(1.3));
    }

    @Test
    void standYourGroundAddsFivePercentPerFoeUpToSix() {
        assertEquals(1.0, CourageMath.standMultiplier(0));
        assertEquals(1.15, CourageMath.standMultiplier(3), 1e-9);
        assertEquals(1.30, CourageMath.standMultiplier(20), 1e-9);
    }

    // ---- The tree -----------------------------------------------------------------------------

    @Test
    void holdTheLineNeedsThreeFoes() {
        assertEquals(1.0, CourageMath.holdTheLine(3, 2));
        assertEquals(0.85, CourageMath.holdTheLine(3, 3), 1e-9);
        assertEquals(1.0, CourageMath.holdTheLine(0, 9));
    }

    @Test
    void giantSlayerOnlyOnBossesAndElites() {
        assertEquals(1.0, CourageMath.giantSlayer(3, false, 20));
        assertEquals(1.24, CourageMath.giantSlayer(3, false, 40), 1e-9);
        assertEquals(1.24, CourageMath.giantSlayer(3, true, 10), 1e-9);
        assertEquals(1.0, CourageMath.giantSlayer(0, true, 300));
    }

    @Test
    void spoilsOfValorOnlyAgainstAStrongerFoe() {
        assertEquals(0f, CourageMath.valorHeal(3, 1.0, false));
        assertEquals(6f, CourageMath.valorHeal(3, 1.5, false));
        assertEquals(2f, CourageMath.valorHeal(1, 0.2, true));
        assertEquals(0f, CourageMath.valorHeal(0, 5.0, true));
    }

    @Test
    void unshakenShortensThenBlocks() {
        assertEquals(300, CourageMath.unshakenTicks(300, 0));
        assertEquals(200, CourageMath.unshakenTicks(300, 1));
        assertEquals(100, CourageMath.unshakenTicks(300, 2));
        assertEquals(0, CourageMath.unshakenTicks(300, 3));
    }

    @Test
    void challengeReachGrowsPerRank() {
        assertEquals(0.0, CourageMath.challengeRadius(0));
        assertEquals(12.0, CourageMath.challengeRadius(3));
    }

    @Test
    void theTreeFillsAtLevelOneHundredAndIsNotACopy() {
        assertTrue(Talents.fullTreeCost(Skill.COURAGE) <= 100, "tree costs " + Talents.fullTreeCost(Skill.COURAGE));
        PlayerSkills skills = new PlayerSkills();
        skills.setLevel(Skill.COURAGE, 100);
        skills.fillTree(Skill.COURAGE);
        Talents.of(Skill.COURAGE).forEach(talent -> assertTrue(skills.isFull(talent), talent.key()));
    }

    @Test
    void fearlessHeartBridgesEnduranceAndCourage() {
        Synergy heart = Synergies.byId("fearless_heart");
        PlayerSkills skills = new PlayerSkills();
        skills.setRank(Talents.get(Skill.ENDURANCE, "stubborn"), 5);
        assertFalse(Synergies.isActive(skills, heart));
        skills.setRank(Talents.get(Skill.COURAGE, "hold_the_line"), 3);
        assertTrue(Synergies.isActive(skills, heart));
        assertTrue(Synergies.hasSpecial(skills, "fearless_heart"));
        assertEquals(1.10, Synergies.multiplier(skills, Skill.COURAGE, PerkEffect.PROC_CHANCE), 1e-9);
        assertEquals(1.10, Synergies.multiplier(skills, Skill.ENDURANCE, PerkEffect.XP_RATE), 1e-9);
    }

    @Test
    void nothingInTheTreeOverlapsEndurance() {
        // Endurance owns taking hits and living: no Courage node heals on a hit taken or cheats death.
        for (var talent : Talents.of(Skill.COURAGE)) {
            String special = talent.special();
            assertFalse("last_stand".equals(special) || "indomitable".equals(special)
                    || "hurt_regen".equals(special) || "scar_tissue".equals(special), talent.key());
        }
    }
}
