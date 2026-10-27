package dev.amman.proficiency;

import dev.amman.proficiency.perk.Talents;
import dev.amman.proficiency.skill.HomebuilderMath;
import dev.amman.proficiency.skill.PlayerSkills;
import dev.amman.proficiency.skill.Skill;
import dev.amman.proficiency.skill.SkillCategory;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Homebuilder's rules (docs/HOMEBUILDER-SPEC.md), pure: the share, milestones, passive, magnet, talents, Homeward. */
class HomebuilderMathTest {

    private static final UUID A = new UUID(0, 1);
    private static final UUID B = new UUID(0, 2);
    private static final UUID C = new UUID(0, 3);

    @Test
    void theSkillIsConstructionAndLastSoNoOrdinalMoved() {
        assertSame(Skill.HOMEBUILDER, Skill.VALUES[34]);
        assertSame(SkillCategory.CONSTRUCTION, Skill.HOMEBUILDER.category());
        assertEquals(0.20, Skill.HOMEBUILDER.defaultMaxBonus(), 1e-12);
        assertEquals(0.20, Skill.HOMEBUILDER.defaultProcChance(), 1e-12);
    }

    @Test
    void theShareIsTheBaseAtTheSkillsRateTimesTheShare() {
        assertEquals(1.5, HomebuilderMath.share(10.0, 1.0, 1.0, 0.15), 1e-12);
        // That skill's own config rate counts, before any player multiplier.
        assertEquals(3.0, HomebuilderMath.share(10.0, 2.0, 1.0, 0.15), 1e-12);
        // An area tool's extra block pays its reduced share.
        assertEquals(0.375, HomebuilderMath.share(10.0, 1.0, 0.25, 0.15), 1e-12);
        assertEquals(0.0, HomebuilderMath.share(Double.NaN, 1.0, 1.0, 0.15));
        assertEquals(0.0, HomebuilderMath.share(10.0, 1.0, 1.0, 0.0));
        assertEquals(0.0, HomebuilderMath.share(-4.0, 1.0, 1.0, 0.15));
        assertEquals(0.0, HomebuilderMath.share(Double.POSITIVE_INFINITY, 1.0, 1.0, 0.15));
    }

    @Test
    void stagesPayByTheirNumber() {
        assertEquals(150.0, HomebuilderMath.stageXp(1, 150.0), 1e-12);
        assertEquals(900.0, HomebuilderMath.stageXp(6, 150.0), 1e-12);
        assertEquals(900.0, HomebuilderMath.stageXp(9, 150.0), 1e-12);
        assertEquals(0.0, HomebuilderMath.stageXp(0, 150.0), 1e-12);
    }

    @Test
    void aSiegeSplitsByKillsWithAFloorForEveryonePresent() {
        Map<UUID, Integer> kills = new LinkedHashMap<>();
        kills.put(A, 9);
        kills.put(B, 1);
        Map<UUID, Double> paid = HomebuilderMath.siegeSplit(kills, List.of(A, B, C), 300.0, 0.10);
        assertEquals(270.0, paid.get(A), 1e-9);
        assertEquals(30.0, paid.get(B), 1e-9);
        assertEquals(30.0, paid.get(C), 1e-9, "present with no kill: the 10% floor");
        // A killer who left range is still paid for the kills.
        Map<UUID, Double> left = HomebuilderMath.siegeSplit(Map.of(A, 4), List.of(B), 300.0, 0.10);
        assertEquals(300.0, left.get(A), 1e-9);
        assertEquals(30.0, left.get(B), 1e-9);
        // Nobody killed (pets did it): an equal split.
        Map<UUID, Double> pets = HomebuilderMath.siegeSplit(Map.of(), List.of(A, B), 300.0, 0.10);
        assertEquals(150.0, pets.get(A), 1e-9);
        assertTrue(HomebuilderMath.siegeSplit(kills, List.of(), 0.0, 0.10).isEmpty());
    }

    @Test
    void thePassiveIsSpeedReachAndAMagnetRadius() {
        assertEquals(0.20, HomebuilderMath.speed(0.20), 1e-12);
        assertEquals(2.0, HomebuilderMath.reach(0.20), 1e-12, "+2 blocks at level 100");
        assertEquals(6.0, HomebuilderMath.magnetRadius(0.20, 0), 1e-12, "6 blocks at level 100");
        assertEquals(9.0, HomebuilderMath.magnetRadius(0.20, 3), 1e-12, "Long Pull adds a block a rank");
        assertEquals(1.0, HomebuilderMath.magnetRadius(0.0, 0), 1e-12);
        assertEquals(0.0, HomebuilderMath.reach(-1.0), 1e-12);
    }

    @Test
    void houseProudDoublesYourOwnAndAFriendsShareIsNeverAdded() {
        assertEquals(0.20, HomebuilderMath.effectiveBonus(0.20, false, 0.0), 1e-12);
        assertEquals(0.40, HomebuilderMath.effectiveBonus(0.20, true, 0.0), 1e-12);
        // A friend's undoubled share: the larger of the two, never the sum.
        assertEquals(0.20, HomebuilderMath.effectiveBonus(0.05, false, 0.20), 1e-12);
        assertEquals(0.40, HomebuilderMath.effectiveBonus(0.20, true, 0.30), 1e-12);
        assertEquals(0.30, HomebuilderMath.effectiveBonus(0.0, false, 0.30), 1e-12);
    }

    @Test
    void aPulledItemArcsInAndNeverOutrunsTheSpeed() {
        double[] far = HomebuilderMath.pull(10.0, 0.0, 0.0, false, true);
        assertEquals(HomebuilderMath.MAGNET_SPEED, far[0], 1e-12, "capped at the speed");
        assertEquals(0.0, far[2], 1e-12);
        assertEquals(HomebuilderMath.MAGNET_HOP, far[1], 1e-12, "a hop off the floor");
        double[] fast = HomebuilderMath.pull(10.0, 0.0, 0.0, true, false);
        assertEquals(2 * HomebuilderMath.MAGNET_SPEED, fast[0], 1e-12, "Tidy Home 3 doubles it");
        double[] near = HomebuilderMath.pull(0.8, 0.0, 0.0, false, false);
        assertEquals(0.2, near[0], 1e-12, "slows down near the player");
        double[] none = HomebuilderMath.pull(0.0, 0.0, 0.0, false, false);
        assertEquals(0.0, none[0] + none[1] + none[2], 1e-12);
    }

    @Test
    void scaffolderTakesThreeFiveAndEightBlocksOff() {
        assertEquals(0, HomebuilderMath.scaffolderBlocks(0));
        assertEquals(3, HomebuilderMath.scaffolderBlocks(1));
        assertEquals(5, HomebuilderMath.scaffolderBlocks(2));
        assertEquals(8, HomebuilderMath.scaffolderBlocks(3));
        assertEquals(8, HomebuilderMath.scaffolderBlocks(7));
        // A 6-block fall (3 damage) does nothing at rank 1; a 15-block fall (12) still hurts at rank 3.
        assertEquals(0f, HomebuilderMath.scaffolderDamage(3f, 1), 1e-6);
        assertEquals(4f, HomebuilderMath.scaffolderDamage(12f, 3), 1e-6);
        assertEquals(12f, HomebuilderMath.scaffolderDamage(12f, 0), 1e-6);
    }

    @Test
    void warmWelcomeAndStorytellerAreTenPercentARank() {
        assertEquals(0.0, HomebuilderMath.warmWelcome(0), 1e-12);
        assertEquals(0.30, HomebuilderMath.warmWelcome(3), 1e-12);
        assertEquals(0.30, HomebuilderMath.warmWelcome(5), 1e-12);
        assertEquals(0.20, HomebuilderMath.storyteller(2), 1e-12);
    }

    @Test
    void nightWatchHearsTheHornInTheHourBeforeTheSiegesDusk() {
        long due = 100_000L;
        // Due passed, one in-game hour before dusk (day time 12000..12999): the horn.
        assertTrue(HomebuilderMath.nightWatchWarns(due + 5, due, 24_000L * 7 + 12_000L));
        assertTrue(HomebuilderMath.nightWatchWarns(due + 5, due, 12_999L));
        // At dusk the siege itself starts: not early any more.
        assertFalse(HomebuilderMath.nightWatchWarns(due + 5, due, 13_000L));
        assertFalse(HomebuilderMath.nightWatchWarns(due + 5, due, 6_000L));
        // Still more than an hour before the due time: the warning day has not passed.
        assertFalse(HomebuilderMath.nightWatchWarns(due - 2_000, due, 12_500L));
        // Due inside the last hour counts.
        assertTrue(HomebuilderMath.nightWatchWarns(due - 500, due, 12_500L));
        assertFalse(HomebuilderMath.nightWatchWarns(due, Long.MIN_VALUE, 12_500L));
    }

    @Test
    void homewardIsOncePerInGameDay() {
        assertEquals(0L, HomebuilderMath.day(23_999L));
        assertEquals(1L, HomebuilderMath.day(24_000L));
        assertTrue(HomebuilderMath.homewardReady(Long.MIN_VALUE, 3L));
        assertFalse(HomebuilderMath.homewardReady(3L, 3L));
        assertTrue(HomebuilderMath.homewardReady(3L, 4L));
    }

    @Test
    void homewardComesBackAfterADayOfGameTimeWhenTheClockStandsStill() {
        // doDaylightCycle false: the day number never moves, a day of game time still counts.
        assertFalse(HomebuilderMath.homewardReady(3L, 3L, 10_000L, 10_000L + 23_999L));
        assertTrue(HomebuilderMath.homewardReady(3L, 3L, 10_000L, 10_000L + 24_000L));
        // Sleeping moves the day on before a full day of game time passed.
        assertTrue(HomebuilderMath.homewardReady(3L, 4L, 10_000L, 10_500L));
        // A use from before the game time was kept: only the day number decides.
        assertFalse(HomebuilderMath.homewardReady(3L, 3L, Long.MIN_VALUE, 99_999_999L));
        assertTrue(HomebuilderMath.homewardReady(Long.MIN_VALUE, 3L, Long.MIN_VALUE, 0L));
    }

    @Test
    void theChannelBreaksOnAHitOrAStepAndLandsWhenTheTimeIsUp() {
        HomebuilderMath.Channel channel = new HomebuilderMath.Channel(10.0, 64.0, 10.0, 1000L, 200);
        assertEquals(HomebuilderMath.Step.RUNNING, channel.step(1100L, 10.2, 64.0, 10.1, false));
        assertEquals(0.5, channel.progress(1100L), 1e-12);
        assertEquals(HomebuilderMath.Step.HURT, channel.step(1100L, 10.0, 64.0, 10.0, true));
        assertEquals(HomebuilderMath.Step.MOVED, channel.step(1100L, 10.6, 64.0, 10.0, false));
        assertEquals(HomebuilderMath.Step.MOVED, channel.step(1100L, 10.0, 65.5, 10.0, false), "fell or jumped off");
        assertEquals(HomebuilderMath.Step.ARRIVED, channel.step(1200L, 10.0, 64.0, 10.0, false));
        // The bell's short channel.
        HomebuilderMath.Channel bell = new HomebuilderMath.Channel(0, 0, 0, 0L, 100);
        assertEquals(HomebuilderMath.Step.ARRIVED, bell.step(100L, 0, 0, 0, false));
    }

    @Test
    void theTreeTagsAreReadFromTheTree() {
        PlayerSkills skills = new PlayerSkills();
        assertEquals(0, HomebuilderMath.rank(skills, HomebuilderMath.TIDY_HOME));
        skills.setLevel(Skill.HOMEBUILDER, 100);
        skills.fillTree(Skill.HOMEBUILDER);
        assertEquals(3, HomebuilderMath.rank(skills, HomebuilderMath.TIDY_HOME));
        assertEquals(3, HomebuilderMath.rank(skills, HomebuilderMath.SCAFFOLDER));
        assertEquals(3, HomebuilderMath.rank(skills, HomebuilderMath.LONG_PULL));
        assertEquals(3, HomebuilderMath.rank(skills, HomebuilderMath.WARM_WELCOME));
        assertEquals(3, HomebuilderMath.rank(skills, HomebuilderMath.STORYTELLER));
        assertEquals(3, HomebuilderMath.rank(skills, HomebuilderMath.OPEN_HOUSE));
        assertEquals(1, HomebuilderMath.rank(skills, HomebuilderMath.NIGHT_WATCH));
        assertEquals(1, HomebuilderMath.rank(skills, HomebuilderMath.HOMEWARD));
        assertTrue(Talents.fullTreeCost(Skill.HOMEBUILDER) <= 100, "at most 100 points");
        // A full tree: Light Step and the capstone raise the passive past 0.20.
        assertTrue(skills.bonus(Skill.HOMEBUILDER) > 0.20);
    }

    @Test
    void heartOfTheHomeNeedsOpenHouseAndSocialsCapstone() {
        PlayerSkills skills = new PlayerSkills();
        skills.setLevel(Skill.HOMEBUILDER, 100);
        skills.fillTree(Skill.HOMEBUILDER);
        assertFalse(HomebuilderMath.heartOfHome(skills));
        skills.setLevel(Skill.SOCIAL, 100);
        skills.fillTree(Skill.SOCIAL);
        assertTrue(HomebuilderMath.heartOfHome(skills));
    }
}
