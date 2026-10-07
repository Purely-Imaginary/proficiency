package dev.amman.proficiency;

import dev.amman.proficiency.perk.PerkEffect;
import dev.amman.proficiency.perk.Synergies;
import dev.amman.proficiency.perk.Synergy;
import dev.amman.proficiency.perk.Talent;
import dev.amman.proficiency.perk.Talents;
import dev.amman.proficiency.skill.PlayerSkills;
import dev.amman.proficiency.skill.Skill;
import dev.amman.proficiency.skill.SkillCategory;
import dev.amman.proficiency.skill.TacticianMath;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Idea 40: Tactician's front-line geometry, XP and anti-farm rules, the mark, and its talent numbers. */
class TacticianMathTest {

    // ---- Between you and the mob (Overwatch) ----------------------------------------------------

    @Test
    void aFriendOnTheLineBetweenYouAndTheMobIsBetween() {
        // You at 0, the mob 20 blocks away along +z, the friend 8 blocks out, 1 to the side.
        assertTrue(TacticianMath.between(0, 64, 0, 1, 64, 8, 0, 64, 20));
    }

    @Test
    void aFriendBesideTheLineBehindYouOrPastTheMobIsNot() {
        assertFalse(TacticianMath.between(0, 64, 0, 4, 64, 8, 0, 64, 20), "4 blocks to the side");
        assertFalse(TacticianMath.between(0, 64, 0, 0, 64, -5, 0, 64, 20), "behind you");
        assertFalse(TacticianMath.between(0, 64, 0, 0, 64, 25, 0, 64, 20), "past the mob (that is behind the mob)");
        assertFalse(TacticianMath.between(0, 64, 0, 0, 64, 19.5, 0, 64, 20), "standing on the mob");
        assertFalse(TacticianMath.between(0, 64, 0, 0, 64, 0.5, 0, 64, 20), "standing on you");
    }

    @Test
    void aShortLineHasNoRoomForAFriendAndHeightCounts() {
        assertFalse(TacticianMath.between(0, 64, 0, 0, 64, 1, 0, 64, 2), "a mob 2 blocks off");
        assertFalse(TacticianMath.between(0, 64, 0, 0, 80, 8, 0, 64, 20), "a friend 16 blocks up");
        assertTrue(TacticianMath.between(0, 64, 0, 0, 58, 8, 0, 70, 20), "a slope inside the margin");
    }

    @Test
    void theHeadIsTheTopOfTheMob() {
        assertTrue(TacticianMath.headshot(65.7, 65.74));
        assertTrue(TacticianMath.headshot(65.9, 65.74));
        assertFalse(TacticianMath.headshot(65.3, 65.74));
        assertFalse(TacticianMath.headshot(Double.NaN, 65.74));
    }

    // ---- XP -------------------------------------------------------------------------------------

    @Test
    void supportCountsTwentyAHitAndFortyAMob() {
        assertEquals(6.0, TacticianMath.supportCounted(6, 0), 1e-9);
        assertEquals(20.0, TacticianMath.supportCounted(50, 0), 1e-9);
        assertEquals(10.0, TacticianMath.supportCounted(15, 30), 1e-9);
        assertEquals(0.0, TacticianMath.supportCounted(15, 40), 1e-9);
        assertEquals(0.0, TacticianMath.supportCounted(-3, 0), 1e-9);
        assertEquals(0.0, TacticianMath.supportCounted(Double.NaN, 0), 1e-9);
        assertEquals(0.9, TacticianMath.supportXp(6, TacticianMath.DEFAULT_SUPPORT_XP_PER_DAMAGE), 1e-9);
        assertEquals(0.0, TacticianMath.supportXp(6, 0), 1e-9);
    }

    @Test
    void overwatchPaysFromSixBlocksCountedToThirty() {
        double rate = TacticianMath.DEFAULT_OVERWATCH_XP_PER_BLOCK;
        assertEquals(0.0, TacticianMath.overwatchXp(5.9, rate), 1e-9);
        assertEquals(6 * rate, TacticianMath.overwatchXp(6, rate), 1e-9);
        assertEquals(15 * rate, TacticianMath.overwatchXp(15, rate), 1e-9);
        assertEquals(30 * rate, TacticianMath.overwatchXp(80, rate), 1e-9);
        assertEquals(0.0, TacticianMath.overwatchXp(Double.NaN, rate), 1e-9);
    }

    @Test
    void rescueIsWorthTheMob() {
        assertEquals(2.5, TacticianMath.rescueXp(2.5, 1.0), 1e-9);
        assertEquals(7.5, TacticianMath.rescueXp(2.5, 3.0), 1e-9);
        assertEquals(0.0, TacticianMath.rescueXp(0, 3.0), 1e-9);
    }

    @Test
    void aMobStaysEngagedFiveSecondsAfterItHurtAFriend() {
        assertTrue(TacticianMath.engaged(1000, 1000));
        assertTrue(TacticianMath.engaged(1000, 1100));
        assertFalse(TacticianMath.engaged(1000, 1101));
        assertFalse(TacticianMath.engaged(1000, 999), "a time from before a clock reset");
        assertFalse(TacticianMath.engaged(-1, 5));
    }

    @Test
    void oneSpotPaysFortyEightInFiveMinutes() {
        assertEquals(48.0, TacticianMath.spotRoom(0), 1e-9);
        assertEquals(8.0, TacticianMath.spotRoom(40), 1e-9);
        assertEquals(0.0, TacticianMath.spotRoom(60), 1e-9);
        assertTrue(TacticianMath.inSpotWindow(0, 5999));
        assertFalse(TacticianMath.inSpotWindow(0, 6000));
        assertFalse(TacticianMath.inSpotWindow(100, 50));
    }

    // ---- Called Shot ----------------------------------------------------------------------------

    @Test
    void theMarkLastsEightSecondsAndLongerWithTalents() {
        assertEquals(160, TacticianMath.markTicks(false, false));
        assertEquals(240, TacticianMath.markTicks(true, false));
        assertEquals(320, TacticianMath.markTicks(true, true));
        assertTrue(TacticianMath.markHolds(1160, 1159));
        assertFalse(TacticianMath.markHolds(1160, 1160));
    }

    @Test
    void friendsDealFifteenPercentMoreToTheMark() {
        assertEquals(1.15, TacticianMath.markMultiplier(1.0, false), 1e-9);
        assertEquals(1.0 + 0.15 * 1.3, TacticianMath.markMultiplier(1.3, false), 1e-9);
        assertEquals(1.20, TacticianMath.markMultiplier(1.0, true), 1e-9);
        assertEquals(1.15, TacticianMath.markMultiplier(0.2, false), 1e-9, "power under 1 never shrinks it");
    }

    @Test
    void hammerAndAnvilFirstBloodIsOneAndAHalfOrTwo() {
        assertEquals(1.0, TacticianMath.pairFirstBlood(false, true), 1e-9);
        assertEquals(1.5, TacticianMath.pairFirstBlood(true, false), 1e-9);
        assertEquals(2.0, TacticianMath.pairFirstBlood(true, true), 1e-9);
        assertEquals(2.0, TacticianMath.PAIR_XP, 1e-9);
    }

    // ---- Damage and the talents -----------------------------------------------------------------

    @Test
    void thePassiveHeadshotAndCrossfire() {
        assertEquals(1.25, TacticianMath.passiveMultiplier(0.25), 1e-9);
        assertEquals(1.0, TacticianMath.passiveMultiplier(-1), 1e-9);
        assertEquals(1.30, TacticianMath.headshotMultiplier(3), 1e-9);
        assertEquals(1.0, TacticianMath.crossfireMultiplier(3, 0), 1e-9);
        assertEquals(1.09, TacticianMath.crossfireMultiplier(3, 1), 1e-9);
        assertEquals(1.27, TacticianMath.crossfireMultiplier(3, 3), 1e-9);
        assertEquals(1.27, TacticianMath.crossfireMultiplier(3, 7), 1e-9, "at most 3 friends count");
    }

    @Test
    void quartermasterCoveringFireAndSuppressingFire() {
        assertEquals(0.0, TacticianMath.refundChance(0), 1e-9);
        assertEquals(1.0, TacticianMath.refundChance(3), 1e-9);
        assertEquals(0.75, TacticianMath.coverMultiplier(false), 1e-9);
        assertEquals(0.60, TacticianMath.coverMultiplier(true), 1e-9);
        assertEquals(60, TacticianMath.coverTicks(false));
        assertEquals(120, TacticianMath.coverTicks(true));
        assertEquals(1, TacticianMath.suppressAmplifier(false));
        assertEquals(2, TacticianMath.suppressAmplifier(true));
    }

    // ---- The skill and its tree ---------------------------------------------------------------

    @Test
    void tacticianIsACombatSkillAddedAfterCharger() {
        assertSame(Skill.TACTICIAN, Skill.VALUES[33]);
        assertSame(Skill.CHARGER, Skill.VALUES[32]);
        assertSame(SkillCategory.COMBAT, Skill.TACTICIAN.category());
        assertSame(Skill.TACTICIAN, Skill.byId("tactician"));
    }

    @Test
    void theTreeFillsAtLevelOneHundred() {
        assertEquals(92, Talents.fullTreeCost(Skill.TACTICIAN));
        PlayerSkills skills = new PlayerSkills();
        skills.setLevel(Skill.TACTICIAN, 100);
        skills.fillTree(Skill.TACTICIAN);
        Talents.of(Skill.TACTICIAN).forEach(talent -> assertTrue(skills.isFull(talent), talent.key()));
    }

    @Test
    void clearLineOpensAtLevelTen() {
        Talent clear = Talents.get(Skill.TACTICIAN, "clear_line");
        assertEquals("clear_line", clear.special());
        assertEquals(10, clear.requiredLevel());
        assertEquals(1, clear.maxRank());
    }

    @Test
    void hammerAndAnvilBridgesCharger() {
        Synergy pair = Synergies.byId("hammer_and_anvil");
        PlayerSkills skills = new PlayerSkills();
        skills.setRank(Talents.get(Skill.TACTICIAN, "clear_line"), 1);
        assertFalse(Synergies.isActive(skills, pair));
        skills.setRank(Talents.get(Skill.CHARGER, "first_in"), 5);
        assertTrue(Synergies.isActive(skills, pair));
        assertTrue(Synergies.hasSpecial(skills, "hammer_and_anvil"));
        assertEquals(1.10, Synergies.multiplier(skills, Skill.TACTICIAN, PerkEffect.XP_RATE), 1e-9);
    }

    @Test
    void coveringFireBridgesGuardian() {
        Synergy pair = Synergies.byId("covering_fire");
        PlayerSkills skills = new PlayerSkills();
        skills.setRank(Talents.get(Skill.TACTICIAN, "crossfire"), 3);
        assertFalse(Synergies.isActive(skills, pair));
        skills.setRank(Talents.get(Skill.GUARDIAN, "cover"), 3);
        assertTrue(Synergies.isActive(skills, pair));
        assertTrue(Synergies.hasSpecial(skills, "covering_fire"));
    }

    @Test
    void nothingInTheTreeCopiesArcheryCrossbowsSneakingOrCharger() {
        // Archery and Crossbows own raw ranged damage (Hunter's Mark, Longshot), Sneaking owns hits
        // from behind a mob (Backstab), Charger owns the front line.
        Set<String> theirs = new HashSet<>();
        for (Skill other : new Skill[] {Skill.ARCHERY, Skill.CROSSBOWS, Skill.SNEAKING, Skill.CHARGER}) {
            for (Talent talent : Talents.of(other)) {
                if (talent.special() != null) {
                    theirs.add(talent.special());
                }
            }
        }
        for (Talent talent : Talents.of(Skill.TACTICIAN)) {
            assertFalse(talent.special() != null && theirs.contains(talent.special()), talent.key());
        }
    }

    /** Every Tactician string in en_us is in pl_pl too. */
    @Test
    void everyTacticianStringIsInBothLanguages() throws Exception {
        Path dir = Path.of("src/main/resources/assets/proficiency/lang");
        String en = Files.readString(dir.resolve("en_us.json"));
        String pl = Files.readString(dir.resolve("pl_pl.json"));
        Matcher keys = Pattern.compile("\"(proficiency\\.[a-z_.]*tactician[a-z_.]*|proficiency\\.synergy\\.(hammer_and_anvil|covering_fire)[a-z_.]*)\"\\s*:")
                .matcher(en);
        int found = 0;
        while (keys.find()) {
            found++;
            assertTrue(pl.contains("\"" + keys.group(1) + "\""), keys.group(1) + " is missing from pl_pl");
        }
        assertTrue(found >= 30, "only " + found + " Tactician strings in en_us");
    }
}
