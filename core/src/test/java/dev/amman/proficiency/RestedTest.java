package dev.amman.proficiency;

import com.google.gson.JsonElement;
import com.mojang.serialization.JsonOps;
import dev.amman.proficiency.skill.Mastery;
import dev.amman.proficiency.skill.PlayerSkills;
import dev.amman.proficiency.skill.RestedMath;
import dev.amman.proficiency.skill.RestedPool;
import dev.amman.proficiency.skill.Skill;
import dev.amman.proficiency.skill.SkillMath;
import dev.amman.proficiency.skill.SkillTuning;
import dev.amman.proficiency.skill.SkillsWire;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayDeque;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Rested XP: the pool, the cap, the fill, the spend, the death wipe, teaching and the save. */
class RestedTest {

    private static final Skill S = Skill.MINING;
    private static final long WINDOW = 6000L;
    private static final UUID TEACHER = UUID.fromString("00000000-0000-0000-0000-0000000000a1");
    private static final UUID OTHER = UUID.fromString("00000000-0000-0000-0000-0000000000a2");

    @AfterEach
    void restoreTuning() {
        SkillTuning.install(SkillTuning.DEFAULTS);
    }

    private static PlayerSkills at(int level) {
        PlayerSkills skills = new PlayerSkills();
        skills.setLevel(S, level);
        return skills;
    }

    /** A player who earned XP at game time {@code now}: the streak's own "not AFK". */
    private static PlayerSkills active(int level, long now) {
        PlayerSkills skills = at(level);
        skills.noteActive(now);
        return skills;
    }

    // ---- the cap ---------------------------------------------------------------------------------

    @Test
    void theCapIsOneAndAHalfTimesTheCurrentLevelsCost() {
        for (int level : new int[] {0, 10, 50, 99}) {
            assertEquals(SkillMath.xpToNext(level) * 1.5f, RestedMath.cap(level, 0), 1e-3f);
        }
        assertTrue(RestedMath.cap(60, 0) > RestedMath.cap(10, 0), "the cap grows with the level");
    }

    @Test
    void atLevelOneHundredTheCapFollowsTheNextStar() {
        assertEquals(Mastery.starCost(1) * 1.5f, RestedMath.cap(100, 0), 1e-3f);
        assertEquals(Mastery.starCost(3) * 1.5f, RestedMath.cap(100, 2), 1e-3f);
        assertEquals(0f, RestedMath.cap(100, Mastery.maxStars()), "nothing left to earn");
    }

    @Test
    void theCapIsConfigurableAndZeroSwitchesItOff() {
        SkillTuning.install(new Tuned(2.0, 9.0));
        assertEquals(SkillMath.xpToNext(20) * 2f, RestedMath.cap(20, 0), 1e-3f);
        SkillTuning.install(new Tuned(0.0, 9.0));
        assertFalse(RestedMath.enabled());
        assertEquals(0f, RestedMath.cap(20, 0));
        PlayerSkills skills = active(20, 100);
        assertEquals(0f, RestedMath.rest(skills, 100, WINDOW, 3600));
    }

    private record Tuned(double cap, double hours) implements SkillTuning {
        @Override public double curveFloor() { return DEFAULTS.curveFloor(); }
        @Override public double curveBase() { return DEFAULTS.curveBase(); }
        @Override public double curveExponent() { return DEFAULTS.curveExponent(); }
        @Override public boolean enabled(Skill skill) { return true; }
        @Override public double maxBonus(Skill skill) { return DEFAULTS.maxBonus(skill); }
        @Override public boolean procsEnabled() { return true; }
        @Override public int procUnlockLevel() { return 25; }
        @Override public double procFloor() { return 0.05; }
        @Override public double procChance(Skill skill) { return DEFAULTS.procChance(skill); }
        @Override public double restedCapFactor() { return cap; }
        @Override public double restedFullHours() { return hours; }
    }

    // ---- filling ---------------------------------------------------------------------------------

    @Test
    void anActivePlayerFillsAnUnusedSkillAndItIsFullAfterTheConfiguredHours() {
        PlayerSkills skills = active(30, 0);
        // 9 active hours, one step a second.
        for (int second = 0; second < 9 * 3600; second++) {
            skills.noteActive(second * 20L);
            RestedMath.rest(skills, second * 20L, WINDOW, 1.0);
        }
        assertEquals(skills.restedCap(S), skills.rested(S), skills.restedCap(S) * 0.01f);
        // And never past it.
        for (int second = 0; second < 3600; second++) {
            skills.noteActive(second * 20L);
            RestedMath.rest(skills, second * 20L, WINDOW, 1.0);
        }
        assertEquals(skills.restedCap(S), skills.rested(S), 1e-2f);
    }

    @Test
    void aSkillUsedInTheLastMinuteDoesNotFill() {
        PlayerSkills skills = active(30, 1000);
        skills.noteUsed(S, 1000, 20_000L);
        RestedMath.rest(skills, 1000 + 600, WINDOW, 60.0);
        assertEquals(0f, skills.rested(S), "used half a minute ago");
        // Other skills still rest while this one is in use.
        skills.setLevel(Skill.SWORDS, 30);
        RestedMath.rest(skills, 1000 + 600, WINDOW, 60.0);
        assertTrue(skills.rested(Skill.SWORDS) > 0f);
        // A whole quiet minute later it rests too.
        skills.noteActive(1000 + 1300);
        assertTrue(RestedMath.rest(skills, 1000 + 1300, WINDOW, 60.0) > 0f);
        assertTrue(skills.rested(S) > 0f);
    }

    @Test
    void anAfkPlayerFillsNothing() {
        PlayerSkills skills = active(30, 0);
        // Last XP at tick 0; the window is 6000 ticks, so at 7000 the player is AFK.
        assertEquals(0f, RestedMath.rest(skills, 7000, WINDOW, 60.0));
        assertEquals(0f, skills.rested(S));
        // And one who never earned anything this session (just joined) is not active either.
        assertEquals(0f, RestedMath.rest(new PlayerSkills(), 100, WINDOW, 60.0));
    }

    @Test
    void aCommandGrantDoesNotCountAsActivity() {
        // The service never calls noteActive for an op's addxp; a fresh player stays AFK.
        PlayerSkills skills = new PlayerSkills();
        skills.setLevel(S, 20);
        assertEquals(0f, RestedMath.rest(skills, 50, WINDOW, 60.0));
    }

    // ---- spending --------------------------------------------------------------------------------

    @Test
    void spendingDoublesAGrantAndTakesTheExtraFromThePool() {
        PlayerSkills skills = at(30);
        skills.fillRested(S, 100f);
        RestedMath.Spend spend = RestedMath.spend(skills, S, 10f, 1.0, id -> 0);
        assertEquals(10f, spend.extra(), 1e-4f);
        assertEquals(90f, skills.rested(S), 1e-3f);
    }

    @Test
    void theExtraIsLimitedByWhatIsLeftAndThenTheBonusStops() {
        PlayerSkills skills = at(30);
        skills.fillRested(S, 4f);
        assertEquals(4f, RestedMath.spend(skills, S, 10f, 1.0, id -> 0).extra(), 1e-4f);
        assertEquals(0f, skills.rested(S));
        assertEquals(0f, RestedMath.spend(skills, S, 10f, 1.0, id -> 0).extra());
    }

    @Test
    void restedAddsToTheSurvivalBonusInsteadOfMultiplyingIt() {
        PlayerSkills skills = at(30);
        skills.fillRested(S, 100f);
        // A grant that is 10 XP plain, with a streak of x1.5, arrives as 15.
        RestedMath.Spend spend = RestedMath.spend(skills, S, 15f, 1.5, id -> 0);
        // Plain x (streak + 1) = 25, so the extra is 10, not the 15 a doubling would give.
        assertEquals(10f, spend.extra(), 1e-3f);
        assertEquals(25f, 15f + spend.extra(), 1e-3f);
    }

    @Test
    void extraHandlesBrokenNumbers() {
        assertEquals(0f, RestedMath.extra(Float.NaN, 1.0, 10f));
        assertEquals(0f, RestedMath.extra(10f, Double.NaN, 0f));
        assertEquals(10f, RestedMath.extra(10f, Double.NaN, 50f), 1e-4f, "a broken streak reads as 1");
        assertEquals(0f, RestedMath.extra(-3f, 1.0, 10f));
    }

    @Test
    void atLevelOneHundredTheExtraFeedsTheStarBar() {
        PlayerSkills skills = at(SkillMath.MAX_LEVEL);
        skills.fillRested(S, 50f);
        RestedMath.Spend spend = RestedMath.spend(skills, S, 20f, 1.0, id -> 0);
        assertEquals(20f, spend.extra(), 1e-3f);
        skills.addXpMastery(S, 20f + spend.extra());
        assertEquals(40f, skills.overflow(S), 1e-2f);
    }

    @Test
    void aPoolOnAMaxedSkillIsNothingToSpendOn() {
        PlayerSkills skills = at(SkillMath.MAX_LEVEL);
        skills.setStars(S, Mastery.maxStars());
        assertEquals(0f, skills.fillRested(S, 50f));
    }

    // ---- death -----------------------------------------------------------------------------------

    @Test
    void aDeathEmptiesEverySkillsPoolTeacherPartsIncluded() {
        PlayerSkills skills = at(40);
        skills.setLevel(Skill.SWORDS, 40);
        skills.fillRested(S, 30f);
        skills.fillRestedTaught(Skill.SWORDS, TEACHER, 25f);
        skills.fillRested(Skill.SWORDS, 5f);
        var lost = skills.loseRested();
        assertEquals(30f, lost.get(S), 1e-3f);
        assertEquals(30f, lost.get(Skill.SWORDS), 1e-3f);
        assertEquals(0f, skills.rested(S));
        assertEquals(0f, skills.rested(Skill.SWORDS));
        assertTrue(skills.restedTaught(Skill.SWORDS).isEmpty());
        assertTrue(skills.loseRested().isEmpty(), "a second death loses nothing more");
    }

    @Test
    void aWarddoesNotKeepRestedXp() {
        // Death Ward keeps a share of the XP bar; the pool is not a bar and is always emptied.
        PlayerSkills skills = at(40);
        skills.fillRested(S, 30f);
        skills.applyDeathPenalty();
        skills.loseRested();
        assertEquals(0f, skills.rested(S));
    }

    // ---- teaching --------------------------------------------------------------------------------

    @Test
    void aTeacherFillsThreeTimesAsFastAndTheCapStillHolds() {
        PlayerSkills idle = at(30);
        PlayerSkills taught = at(30);
        idle.noteActive(0);
        RestedMath.rest(idle, 0, WINDOW, 600.0);
        float taughtAdded = RestedMath.teach(taught, S, TEACHER, 0, 600.0);
        assertEquals(idle.rested(S) * 3f, taughtAdded, idle.rested(S) * 0.001f);
        // Hours of teaching never go past the cap.
        for (int i = 0; i < 20; i++) {
            RestedMath.teach(taught, S, TEACHER, 0, 3600.0);
        }
        assertEquals(taught.restedCap(S), taught.rested(S), 1e-2f);
    }

    @Test
    void quickStudyRaisesTheTeachingRate() {
        PlayerSkills a = at(30);
        PlayerSkills b = at(30);
        float base = RestedMath.teach(a, S, TEACHER, 0, 60.0);
        float quick = RestedMath.teach(b, S, TEACHER, 2, 60.0);
        assertEquals(base * 1.3f, quick, base * 0.001f);
    }

    @Test
    void theTeacherNeedsTheMentorGap() {
        assertTrue(RestedMath.canTeach(50, 30, 20));
        assertFalse(RestedMath.canTeach(49, 30, 20));
        assertTrue(RestedMath.canTeach(30, 30, 0), "Heart of the Group: anyone as good counts");
    }

    @Test
    void everyPartRemembersItsTeacherAndTheCreditFollowsTheSpend() {
        PlayerSkills student = at(30);
        student.fillRestedTaught(S, TEACHER, 40f);
        student.fillRestedTaught(S, OTHER, 20f);
        student.fillRested(S, 10f);
        // Taught parts go first, oldest first: 40 from TEACHER, then 5 of OTHER's.
        RestedMath.Spend spend = RestedMath.spend(student, S, 45f, 1.0, id -> 0);
        assertEquals(45f, spend.extra(), 1e-3f);
        assertEquals(2, spend.credits().size());
        assertEquals(TEACHER, spend.credits().get(0).teacher());
        assertEquals(40 * 0.25, spend.credits().get(0).socialXp(), 1e-4);
        assertEquals(OTHER, spend.credits().get(1).teacher());
        assertEquals(5 * 0.25, spend.credits().get(1).socialXp(), 1e-4);
        assertEquals(25f, student.rested(S), 1e-3f);
    }

    @Test
    void idleRestedXpPaysNoTeacher() {
        PlayerSkills student = at(30);
        student.fillRested(S, 40f);
        assertTrue(RestedMath.spend(student, S, 30f, 1.0, id -> 0).credits().isEmpty());
    }

    @Test
    void teachersPrideRaisesTheCut() {
        PlayerSkills student = at(30);
        student.fillRestedTaught(S, TEACHER, 40f);
        RestedMath.Spend spend = RestedMath.spend(student, S, 40f, 1.0, id -> id.equals(TEACHER) ? 2 : 0);
        assertEquals(40 * 0.35, spend.credits().get(0).socialXp(), 1e-4);
    }

    // ---- the pool itself -------------------------------------------------------------------------

    @Test
    void aTeacherAddsToTheirOwnPartAndTheLastSlotsStopNewTeachers() {
        RestedPool pool = new RestedPool();
        pool.fillTaught(TEACHER, 10f, 1000f);
        pool.fillTaught(TEACHER, 5f, 1000f);
        assertEquals(1, pool.taught().size());
        assertEquals(15f, pool.taught().get(0).xp(), 1e-4f);
        for (int i = 0; i < RestedPool.MAX_PARTS + 3; i++) {
            pool.fillTaught(new UUID(1, i), 1f, 1000f);
        }
        assertEquals(RestedPool.MAX_PARTS, pool.taught().size());
    }

    @Test
    void clampingCutsIdleFirstThenTheNewestTeacher() {
        RestedPool pool = new RestedPool();
        pool.fillIdle(10f, 1000f);
        pool.fillTaught(TEACHER, 10f, 1000f);
        pool.fillTaught(OTHER, 10f, 1000f);
        pool.clampTo(25f);
        assertEquals(5f, pool.idle(), 1e-4f, "the idle part is cut first");
        assertEquals(10f, pool.taught().get(0).xp(), 1e-4f);
        assertEquals(10f, pool.taught().get(1).xp(), 1e-4f);
        pool.clampTo(15f);
        assertEquals(0f, pool.idle(), 1e-4f);
        assertEquals(2, pool.taught().size());
        assertEquals(10f, pool.taught().get(0).xp(), 1e-4f, "the oldest teacher's part is kept whole");
        assertEquals(5f, pool.taught().get(1).xp(), 1e-4f, "the newest part gives up the rest");
        pool.clampTo(8f);
        assertEquals(1, pool.taught().size(), "a part cut to nothing goes");
        assertEquals(TEACHER, pool.taught().get(0).teacher());
        assertEquals(8f, pool.taught().get(0).xp(), 1e-4f);
    }

    // ---- saving and syncing ----------------------------------------------------------------------

    @Test
    void thePoolSurvivesASaveAndLoadTeachersAndDaysIncluded() {
        PlayerSkills skills = at(30);
        skills.setLevel(Skill.SWORDS, 30);
        skills.fillRested(S, 12f);
        skills.fillRestedTaught(S, TEACHER, 20f);
        skills.noteUsed(Skill.SWORDS, 5, RestedMath.today() - 9);
        JsonElement json = PlayerSkills.CODEC.encodeStart(JsonOps.INSTANCE, skills)
                .getOrThrow(message -> new AssertionError(message));
        PlayerSkills back = PlayerSkills.CODEC.parse(JsonOps.INSTANCE, json)
                .getOrThrow(message -> new AssertionError(message));
        assertEquals(32f, back.rested(S), 1e-3f);
        assertEquals(1, back.restedTaught(S).size());
        assertEquals(TEACHER, back.restedTaught(S).get(0).teacher());
        assertEquals(9, back.daysIdle(Skill.SWORDS));
    }

    @Test
    void anOldSaveWithoutTheFieldsLoadsEmpty() {
        JsonElement json = new com.google.gson.JsonParser()
                .parse("{\"skills\":{\"mining\":{\"level\":12,\"xp\":3.0}}}");
        PlayerSkills skills = PlayerSkills.CODEC.parse(JsonOps.INSTANCE, json)
                .getOrThrow(message -> new AssertionError(message));
        assertEquals(12, skills.level(S));
        assertEquals(0f, skills.rested(S));
        assertEquals(0, skills.daysIdle(S));
        assertTrue(skills.stampUnusedDays(RestedMath.today()), "a played skill gets today's date");
        assertEquals(0, skills.daysIdle(S));
    }

    @Test
    void aBrokenTeacherIdInASaveDropsOnlyThatPart() {
        JsonElement json = new com.google.gson.JsonParser().parse(
                "{\"skills\":{\"mining\":{\"level\":30,\"xp\":0.0}},\"rested\":{\"mining\":{\"idle\":7.0,"
                        + "\"taught\":[{\"teacher\":\"not-a-uuid\",\"xp\":5.0},"
                        + "{\"teacher\":\"" + TEACHER + "\",\"xp\":3.0}]}}}");
        PlayerSkills skills = PlayerSkills.CODEC.parse(JsonOps.INSTANCE, json)
                .getOrThrow(message -> new AssertionError(message));
        assertEquals(10f, skills.rested(S), 1e-3f);
        assertEquals(1, skills.restedTaught(S).size());
    }

    @Test
    void aSavedPoolAboveItsCapIsCutOnLoad() {
        JsonElement json = new com.google.gson.JsonParser().parse(
                "{\"skills\":{\"mining\":{\"level\":3,\"xp\":0.0}},\"rested\":{\"mining\":{\"idle\":99999.0}}}");
        PlayerSkills skills = PlayerSkills.CODEC.parse(JsonOps.INSTANCE, json)
                .getOrThrow(message -> new AssertionError(message));
        assertEquals(skills.restedCap(S), skills.rested(S), 1e-3f);
    }

    private static final class Buffer implements SkillsWire.Out, SkillsWire.In {
        private final ArrayDeque<Object> queue = new ArrayDeque<>();

        @Override public void writeVarInt(int value) { queue.add(value); }
        @Override public void writeFloat(float value) { queue.add(value); }
        @Override public void writeUtf(String value) { queue.add(value); }
        @Override public void writeVarLong(long value) { queue.add(value); }
        @Override public int readVarInt() { return (Integer) queue.remove(); }
        @Override public float readFloat() { return (Float) queue.remove(); }
        @Override public String readUtf() { return (String) queue.remove(); }
        @Override public long readVarLong() { return (Long) queue.remove(); }
    }

    @Test
    void theSyncPacketCarriesThePoolAndTheDaysUnused() {
        PlayerSkills skills = at(30);
        skills.fillRested(S, 12f);
        skills.fillRestedTaught(S, TEACHER, 20f);
        skills.setLevel(Skill.SWORDS, 5);
        skills.noteUsed(Skill.SWORDS, 1, RestedMath.today() - 8);
        Buffer buffer = new Buffer();
        skills.write(buffer);
        PlayerSkills read = PlayerSkills.read(buffer);
        assertTrue(buffer.queue.isEmpty(), "the reader consumes exactly what the writer wrote");
        assertEquals(32f, read.rested(S), 1e-3f);
        assertEquals(8, read.daysIdle(Skill.SWORDS));
        assertTrue(RestedMath.rusty(read.daysIdle(Skill.SWORDS)));
        assertFalse(RestedMath.rusty(6));
    }

    @Test
    void copyFromCarriesThePoolAndResetEmptiesIt() {
        PlayerSkills skills = at(30);
        skills.fillRestedTaught(S, TEACHER, 20f);
        PlayerSkills copy = new PlayerSkills();
        copy.copyFrom(skills);
        assertEquals(20f, copy.rested(S), 1e-3f);
        assertEquals(TEACHER, copy.restedTaught(S).get(0).teacher());
        copy.reset();
        assertEquals(0f, copy.rested(S));
    }

    @Test
    void loweringALevelCutsThePoolToTheNewCap() {
        PlayerSkills skills = at(60);
        skills.fillRested(S, skills.restedCap(S));
        skills.setLevel(S, 10);
        assertTrue(skills.rested(S) <= skills.restedCap(S) + 1e-3f);
    }

    @Test
    void theReachIsThePoolOverTheBarInFrontOfThePlayer() {
        float need = SkillMath.xpToNext(30);
        assertEquals(0.5f, RestedMath.reach(need / 2, 30, 0), 1e-4f);
        assertEquals(1f, RestedMath.reach(need * 1.5f, 30, 0), "never past the end of the bar");
        assertEquals(0f, RestedMath.reach(10f, 100, Mastery.maxStars()));
    }

    @Test
    void aSkillUnusedForAWeekIsRustyAndNeverBeforeItHasADate() {
        assertEquals(0, RestedMath.daysSince(0, 20_000));
        assertEquals(7, RestedMath.daysSince(19_993, 20_000));
        assertTrue(RestedMath.rusty(7));
        assertFalse(RestedMath.rusty(0));
        List<Skill> none = List.of();
        assertTrue(none.isEmpty());
    }
}
