package dev.amman.proficiency;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.mojang.serialization.JsonOps;
import dev.amman.proficiency.skill.PlayerRecords;
import dev.amman.proficiency.skill.PlayerSkills;
import dev.amman.proficiency.skill.RateWindow;
import dev.amman.proficiency.skill.RecordMetric;
import dev.amman.proficiency.skill.RecordRules;
import dev.amman.proficiency.skill.RecordTracker;
import dev.amman.proficiency.skill.Skill;
import dev.amman.proficiency.skill.TempoChain;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Personal records: the rolling rate, the toast thresholds, the cooldown and the save. */
class PersonalRecordsTest {

    private static final Skill MINING = Skill.MINING;

    @Test
    void theRateIsTheXpOfTheLastSixtySeconds() {
        RateWindow window = new RateWindow(RecordRules.WINDOW_TICKS);
        window.add(0, 10f);
        window.add(600, 20f);
        assertEquals(30f, window.sum(600), 0.001f);
        // The first grant leaves the window exactly 1200 ticks after it came in.
        assertEquals(30f, window.sum(1199), 0.001f);
        assertEquals(20f, window.sum(1200), 0.001f);
        assertEquals(0f, window.sum(1800), 0.001f);
    }

    @Test
    void aClockThatGoesBackEmptiesTheWindow() {
        RateWindow window = new RateWindow(RecordRules.WINDOW_TICKS);
        window.add(5000, 10f);
        assertEquals(0f, window.sum(100), 0f);
        window.add(100, 4f);
        assertEquals(4f, window.sum(100), 0.001f);
    }

    @Test
    void aFullWindowDropsTheOldestAndNeverOvercounts() {
        RateWindow window = new RateWindow(RecordRules.WINDOW_TICKS);
        for (int i = 0; i < 5000; i++) {
            window.add(i / 10, 1f);
        }
        assertTrue(window.size() <= 1024);
        assertTrue(window.sum(500) <= 1024f);
    }

    @Test
    void aToastNeedsTenPercentAndAnAbsoluteStep() {
        // 10% of 400 is 40: 430 is not enough, 440 is.
        assertFalse(RecordRules.worthAToast(RecordMetric.RATE, 400f, 430f));
        assertTrue(RecordRules.worthAToast(RecordMetric.RATE, 400f, 440f));
        // A tiny record needs the absolute step: 10 XP/min, 3 steps, 5 XP, 2 count.
        assertFalse(RecordRules.worthAToast(RecordMetric.RATE, 20f, 25f));
        assertTrue(RecordRules.worthAToast(RecordMetric.RATE, 20f, 30f));
        assertFalse(RecordRules.worthAToast(RecordMetric.CHAIN, 10, 12));
        assertTrue(RecordRules.worthAToast(RecordMetric.CHAIN, 10, 13));
        assertFalse(RecordRules.worthAToast(RecordMetric.GRANT, 40f, 44f));
        assertTrue(RecordRules.worthAToast(RecordMetric.GRANT, 40f, 45f));
        assertFalse(RecordRules.worthAToast(RecordMetric.EXTRA, 3, 4));
        assertTrue(RecordRules.worthAToast(RecordMetric.EXTRA, 3, 5));
    }

    @Test
    void theCooldownIsThirtySeconds() {
        assertTrue(RecordRules.cooledDown(Long.MIN_VALUE / 2, 0));
        assertFalse(RecordRules.cooledDown(1000, 1599));
        assertTrue(RecordRules.cooledDown(1000, 1600));
    }

    @Test
    void nothingIsAnnouncedInTheFirstMinuteButTheRecordsStillRise() {
        PlayerRecords records = new PlayerRecords();
        records.raise(MINING, RecordMetric.GRANT, 10f);
        RecordTracker tracker = new RecordTracker();
        for (int t = 0; t < 1199; t += 20) {
            RecordTracker.Outcome out = tracker.onGrant(records, MINING, t, 10f + t / 10f, 3, false, false);
            assertNull(out.toast(), "first minute, tick " + t);
        }
        assertTrue(records.value(MINING, RecordMetric.GRANT) > 100f, "the best still rose");
    }

    @Test
    void aBeatenRecordAfterTheFirstMinuteToastsOnceThenWaitsForTheCooldown() {
        PlayerRecords records = new PlayerRecords();
        records.raise(MINING, RecordMetric.GRANT, 50f);
        // A rate and a chain far out of reach, so only the grant record can be beaten here.
        records.raise(MINING, RecordMetric.RATE, 100_000f);
        records.raise(MINING, RecordMetric.CHAIN, 1000f);
        RecordTracker tracker = new RecordTracker();
        // A minute of steady 5 XP grants: the grant record is not touched, the rate record rises from nothing.
        long t = 0;
        for (; t < 1300; t += 20) {
            assertNull(tracker.onGrant(records, MINING, t, 5f, 2, false, false).toast());
        }
        // A big grant beats 50 XP by well over 10%.
        RecordTracker.Outcome big = tracker.onGrant(records, MINING, t, 70f, 2, false, false);
        assertEquals(RecordMetric.GRANT, big.toast());
        assertEquals(70f, big.toastValue(), 0.001f);
        assertTrue(big.changed());
        // A bigger one 5 seconds later is stored but not announced (30 s cooldown).
        t += 100;
        RecordTracker.Outcome bigger = tracker.onGrant(records, MINING, t, 90f, 2, false, false);
        assertNull(bigger.toast());
        assertEquals(90f, records.value(MINING, RecordMetric.GRANT), 0.001f);
        // After the cooldown it can announce again, against the last announced 70.
        t += 600;
        RecordTracker.Outcome later = tracker.onGrant(records, MINING, t, 5f, 2, false, false);
        assertEquals(RecordMetric.GRANT, later.toast());
        assertEquals(90f, later.toastValue(), 0.001f);
    }

    @Test
    void aSmallImprovementIsStoredButNotAnnounced() {
        PlayerRecords records = new PlayerRecords();
        records.raise(MINING, RecordMetric.GRANT, 100f);
        records.raise(MINING, RecordMetric.RATE, 100_000f);
        records.raise(MINING, RecordMetric.CHAIN, 1000f);
        RecordTracker tracker = new RecordTracker();
        long t = 0;
        for (; t < 1300; t += 20) {
            tracker.onGrant(records, MINING, t, 5f, 2, false, false);
        }
        RecordTracker.Outcome out = tracker.onGrant(records, MINING, t, 105f, 2, false, false);
        assertNull(out.toast());
        assertEquals(105f, records.value(MINING, RecordMetric.GRANT), 0.001f);
    }

    @Test
    void aFirstEverValueIsABaselineNotANewBest() {
        PlayerRecords records = new PlayerRecords();
        RecordTracker tracker = new RecordTracker();
        long t = 0;
        for (; t < 3000; t += 20) {
            assertNull(tracker.onGrant(records, MINING, t, 5f, 4, false, false).toast());
        }
        assertTrue(records.value(MINING, RecordMetric.RATE) > 0f);
    }

    @Test
    void beastslayingCountsKillsInOneChainAndAChainBreakStartsOver() {
        PlayerRecords records = new PlayerRecords();
        RecordTracker tracker = new RecordTracker();
        Skill skill = Skill.BEASTSLAYING;
        tracker.onGrant(records, skill, 0, 5f, 1, true, false);
        tracker.onGrant(records, skill, 20, 5f, 2, true, false);
        tracker.onGrant(records, skill, 40, 5f, 3, false, false);
        tracker.onGrant(records, skill, 60, 5f, 4, true, false);
        assertEquals(3, (int) records.value(skill, RecordMetric.EXTRA));
        // The chain broke: the count starts again from this kill, and the stored best stays.
        tracker.onGrant(records, skill, 900, 5f, 1, true, false);
        assertEquals(3, (int) records.value(skill, RecordMetric.EXTRA));
    }

    @Test
    void fishingCountsCatchesInAMinuteAndOtherSkillsKeepNoExtra() {
        PlayerRecords records = new PlayerRecords();
        RecordTracker fishing = new RecordTracker();
        fishing.onGrant(records, Skill.FISHING, 0, 3f, 1, false, true);
        fishing.onGrant(records, Skill.FISHING, 200, 3f, 2, false, true);
        fishing.onGrant(records, Skill.FISHING, 400, 12f, 3, false, false);
        assertEquals(2, (int) records.value(Skill.FISHING, RecordMetric.EXTRA));
        RecordTracker mining = new RecordTracker();
        mining.onGrant(records, MINING, 0, 3f, 1, true, true);
        assertEquals(0, (int) records.value(MINING, RecordMetric.EXTRA));
        assertEquals(RecordRules.ExtraKind.NONE, RecordRules.extraKind(MINING));
    }

    @Test
    void theChainCountsActionsPastTheTempoCap() {
        TempoChain chain = new TempoChain();
        for (int i = 0; i < 40; i++) {
            chain.advance(i * 10L, 60, 25, 1200);
        }
        assertEquals(25, chain.steps);
        assertEquals(40, chain.actions);
        // A pause longer than the window breaks it.
        chain.advance(40 * 10L + 500, 60, 25, 1200);
        assertEquals(1, chain.actions);
        assertEquals(0, chain.steps);
        assertEquals(1, chain.copy().actions);
    }

    @Test
    void recordsSurviveASaveAndLoadAndADeathAndACopy() {
        PlayerSkills skills = new PlayerSkills();
        skills.setLevel(MINING, 40);
        skills.addXp(MINING, 1000f);
        skills.records().set(MINING, new PlayerRecords.Entry(312f, 41, 96.5f, 0));
        skills.records().set(Skill.FISHING, new PlayerRecords.Entry(0f, 0, 0f, 6));

        JsonElement encoded = PlayerSkills.CODEC.encodeStart(JsonOps.INSTANCE, skills)
                .getOrThrow(message -> new AssertionError("encode failed: " + message));
        assertTrue(((JsonObject) encoded).has("records"));
        PlayerSkills restored = PlayerSkills.CODEC.parse(JsonOps.INSTANCE, encoded)
                .getOrThrow(message -> new AssertionError("decode failed: " + message));
        assertEquals(new PlayerRecords.Entry(312f, 41, 96.5f, 0), restored.records().get(MINING));
        assertEquals(6, restored.records().get(Skill.FISHING).extra());
        // The lode shares a slot with the records: it must still round-trip.
        skills.setLode(2.5);
        PlayerSkills again = PlayerSkills.CODEC.parse(JsonOps.INSTANCE, PlayerSkills.CODEC
                .encodeStart(JsonOps.INSTANCE, skills).getOrThrow(AssertionError::new)).getOrThrow(AssertionError::new);
        assertEquals(2.5, again.lode(), 0.001);
        assertEquals(312f, again.records().get(MINING).rate(), 0f);

        // Death wipes the bar and never a record.
        restored.applyDeathPenalty();
        assertEquals(312f, restored.records().get(MINING).rate(), 0f);
        // A respawn copies them.
        PlayerSkills copy = new PlayerSkills();
        copy.copyFrom(restored);
        assertEquals(41, copy.records().get(MINING).chain());
        // A full /skills reset is not a records reset.
        copy.reset();
        assertEquals(41, copy.records().get(MINING).chain());
        copy.records().reset(MINING);
        assertTrue(copy.records().isEmpty(MINING));
        assertFalse(copy.records().isEmpty(Skill.FISHING));
    }

    @Test
    void aSaveWithoutRecordsLoadsEmptyAndGarbageIsClamped() {
        PlayerSkills old = PlayerSkills.CODEC.parse(JsonOps.INSTANCE, new JsonObject())
                .getOrThrow(message -> new AssertionError(message));
        assertTrue(old.records().isEmpty());
        PlayerRecords records = new PlayerRecords();
        records.set(MINING, new PlayerRecords.Entry(Float.NaN, -4, Float.POSITIVE_INFINITY, -1));
        assertTrue(records.isEmpty(MINING));
        assertNotNull(records.get(MINING));
        assertFalse(records.raise(MINING, RecordMetric.RATE, Float.NaN));
        assertTrue(records.raise(MINING, RecordMetric.RATE, 5f));
        assertFalse(records.raise(MINING, RecordMetric.RATE, 5f));
    }
}
