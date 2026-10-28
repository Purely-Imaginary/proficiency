package dev.amman.proficiency.haul;

import com.mojang.serialization.JsonOps;
import dev.amman.proficiency.skill.Skill;
import dev.amman.proficiency.skill.SkillsWire;
import org.junit.jupiter.api.Test;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Today's Haul: the session log, its save and wire forms, the rating ladder and the card's timeline. */
class HaulTest {

    private static final long T0 = 1_700_000_000_000L;

    // ---- The log ---------------------------------------------------------------------------------

    @Test
    void aGrantStartsARowAtWhereTheBarStood() {
        HaulLog log = new HaulLog();
        assertTrue(log.isEmpty());
        assertTrue(log.onGrant(Skill.MINING, 40f, 12.5f, 0, 0, 0, T0));
        assertTrue(log.onGrant(Skill.MINING, 60f, 12.9f, 1, 0, 1, T0 + 1000));
        HaulLog.Entry e = log.entry(Skill.MINING);
        assertEquals(100f, e.xp(), 1e-4);
        assertEquals(12.5f, e.from(), 1e-4, "the first gain fixes the start");
        assertEquals(1, e.levels());
        assertEquals(1, log.points());
        assertEquals(T0, log.since());
    }

    @Test
    void nothingAndNonsenseAreIgnored() {
        HaulLog log = new HaulLog();
        assertFalse(log.onGrant(Skill.MINING, 0f, 3f, 0, 0, 0, T0));
        assertFalse(log.onGrant(Skill.MINING, Float.NaN, 3f, 0, 0, 0, T0));
        assertFalse(log.onGrant(Skill.MINING, -5f, 3f, -1, -1, 0, T0));
        assertFalse(log.onGrant(null, 5f, 3f, 0, 0, 0, T0));
        assertFalse(log.onCharge(Double.NaN, T0));
        assertFalse(log.onCharge(-1, T0));
        assertFalse(log.onCharge(Double.POSITIVE_INFINITY, T0));
        assertFalse(log.onTempo(Skill.MINING, 3, 1000, T0), "x1.00 is no chain");
        assertTrue(log.isEmpty());
        assertEquals(0, log.since());
    }

    @Test
    void theBestTempoChainIsKept() {
        HaulLog log = new HaulLog();
        assertTrue(log.onTempo(Skill.MINING, 5, 1100, T0));
        assertTrue(log.onTempo(Skill.WOODCUTTING, 12, 1240, T0));
        assertFalse(log.onTempo(Skill.MINING, 8, 1180, T0), "lower than the best");
        assertFalse(log.onTempo(Skill.SWORDS, 12, 1240, T0), "a tie keeps the first");
        HaulSummary s = log.summary(HaulSummary.CARD, skill -> 0, T0);
        assertEquals(Skill.WOODCUTTING.ordinal(), s.tempoSkill());
        assertEquals(12, s.tempoSteps());
        assertEquals(1240, s.tempoPermille());
    }

    @Test
    void aSynergyCountsOnceAndDiscoveriesAndChargeAddUp() {
        HaulLog log = new HaulLog();
        assertTrue(log.onSynergy("forge_and_furnace", T0));
        assertFalse(log.onSynergy("forge_and_furnace", T0), "a respec and back is the same synergy");
        assertTrue(log.onSynergy("trail_companion", T0));
        log.onFound(T0);
        log.onFound(T0);
        log.onCharge(12.5, T0);
        log.onCharge(7.5, T0);
        assertEquals(2, log.synergies());
        assertEquals(2, log.found());
        assertEquals(20.0, log.charge(), 1e-9);
        assertFalse(log.isEmpty());
    }

    @Test
    void takeEmptiesTheLogAndTheSummaryKeepsIt() {
        HaulLog log = new HaulLog();
        log.onGrant(Skill.MINING, 300f, 10.2f, 2, 0, 2, T0);
        log.onGrant(Skill.FARMING, 50f, 4.0f, 0, 0, 0, T0);
        log.onGrant(Skill.SWORDS, 50f, 7.0f, 0, 0, 0, T0);
        HaulSummary s = log.take(skill -> skill == Skill.MINING ? 12.4 : skill == Skill.FARMING ? 4.6 : 7.5,
                T0 + 125 * 60_000L);
        assertTrue(log.isEmpty(), "the card was shown: the session starts over");
        assertEquals(125, s.minutes());
        assertEquals(3, s.rows().size());
        assertEquals(Skill.MINING.ordinal(), s.rows().get(0).skill(), "most XP first");
        assertTrue(s.rows().get(1).skill() < s.rows().get(2).skill(), "a tie keeps the skill order");
        assertEquals(10.2f, s.rows().get(0).from(), 1e-4);
        assertEquals(12.4f, s.rows().get(0).to(), 1e-4);
        assertEquals(400.0, s.totalXp(), 1e-3);
        assertEquals(2, s.totalLevels());
        assertEquals(2, s.points());
    }

    @Test
    void tiesFollowTheSkillOrder() {
        List<HaulSummary.Row> rows = new ArrayList<>();
        rows.add(new HaulSummary.Row(Skill.SWORDS.ordinal(), 50f, 1, 2, 0, 0));
        rows.add(new HaulSummary.Row(Skill.MINING.ordinal(), 50f, 1, 2, 0, 0));
        List<HaulSummary.Row> sorted = HaulSummary.sorted(rows);
        assertTrue(sorted.get(0).skill() < sorted.get(1).skill());
    }

    @Test
    void aBarNeverRunsBackwards() {
        HaulLog log = new HaulLog();
        log.onGrant(Skill.MINING, 30f, 20.5f, 0, 0, 0, T0);
        // A death wiped the bar since: the row still shows no loss.
        HaulSummary s = log.summary(HaulSummary.CARD, skill -> 20.0, T0);
        assertEquals(20.5f, s.rows().get(0).to(), 1e-4);
    }

    @Test
    void theSaveRoundTripKeepsEverything() {
        HaulLog log = new HaulLog();
        log.onGrant(Skill.MINING, 300f, 10.2f, 2, 0, 2, T0);
        log.onGrant(Skill.ARCHERY, 1200f, 100.4f, 0, 1, 0, T0);
        log.onTempo(Skill.MINING, 9, 1330, T0);
        log.onSynergy("forge_and_furnace", T0);
        log.onFound(T0);
        log.onCharge(44.0, T0);
        var json = HaulLog.CODEC.encodeStart(JsonOps.INSTANCE, log).getOrThrow(m -> new AssertionError(m));
        HaulLog back = HaulLog.CODEC.parse(JsonOps.INSTANCE, json).getOrThrow(m -> new AssertionError(m));
        HaulSummary a = log.summary(HaulSummary.CARD, skill -> 50, T0 + 60_000);
        HaulSummary b = back.summary(HaulSummary.CARD, skill -> 50, T0 + 60_000);
        assertEquals(a, b);
        assertEquals(T0, back.since());
    }

    @Test
    void anEmptySaveLoadsEmptyAndAnUnknownSkillIsDropped() {
        var empty = JsonOps.INSTANCE.createMap(java.util.Map.of());
        HaulLog log = HaulLog.CODEC.parse(JsonOps.INSTANCE, empty).getOrThrow(m -> new AssertionError(m));
        assertTrue(log.isEmpty());
        com.google.gson.JsonObject json = new com.google.gson.JsonObject();
        com.google.gson.JsonObject skills = new com.google.gson.JsonObject();
        com.google.gson.JsonObject gone = new com.google.gson.JsonObject();
        gone.addProperty("xp", 5f);
        skills.add("no_such_skill", gone);
        json.add("skills", skills);
        HaulLog dropped = HaulLog.CODEC.parse(JsonOps.INSTANCE, json).getOrThrow(m -> new AssertionError(m));
        assertTrue(dropped.isEmpty());
    }

    // ---- The wire --------------------------------------------------------------------------------

    private static final class Buffer implements SkillsWire.Out, SkillsWire.In {
        final ArrayDeque<Object> queue = new ArrayDeque<>();

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
    void theWireRoundTrips() {
        HaulLog log = new HaulLog();
        log.onGrant(Skill.MINING, 300f, 10.2f, 2, 0, 2, T0);
        log.onGrant(Skill.FARMING, 80f, 3.1f, 0, 0, 0, T0);
        log.onTempo(Skill.FARMING, 4, 1120, T0);
        log.onCharge(9.5, T0);
        HaulSummary s = log.summary(HaulSummary.LOGIN, skill -> 13, T0 + 600_000);
        Buffer buf = new Buffer();
        s.write(buf);
        HaulSummary read = HaulSummary.read(buf);
        assertTrue(buf.queue.isEmpty());
        assertEquals(s, read);
        assertEquals(HaulSummary.LOGIN, read.mode());
    }

    @Test
    void theWireDropsAnUnknownSkillAndClampsNonsense() {
        Buffer buf = new Buffer();
        buf.writeVarInt(HaulSummary.CARD);
        buf.writeVarInt(-4);
        buf.writeVarInt(2);
        buf.writeVarInt(9999);
        buf.writeFloat(5f);
        buf.writeFloat(1f);
        buf.writeFloat(2f);
        buf.writeVarInt(0);
        buf.writeVarInt(0);
        buf.writeVarInt(Skill.MINING.ordinal());
        buf.writeFloat(Float.NaN);
        buf.writeFloat(5f);
        buf.writeFloat(3f);
        buf.writeVarInt(-2);
        buf.writeVarInt(0);
        buf.writeVarInt(-1);
        buf.writeVarInt(0);
        buf.writeVarInt(0);
        buf.writeVarInt(0);
        buf.writeVarInt(0);
        buf.writeVarInt(0);
        buf.writeFloat(Float.POSITIVE_INFINITY);
        HaulSummary s = HaulSummary.read(buf);
        assertEquals(1, s.rows().size());
        HaulSummary.Row row = s.rows().get(0);
        assertEquals(0f, row.xp());
        assertEquals(5f, row.to(), "a bar never runs backwards");
        assertEquals(0, row.levels());
        assertEquals(0, s.minutes());
        assertEquals(0f, s.charge());
        assertEquals(-1, s.tempoSkill());
    }

    @Test
    void topEightAndMore() {
        List<HaulSummary.Row> rows = new ArrayList<>();
        for (int i = 0; i < 11; i++) {
            rows.add(new HaulSummary.Row(i, 100 - i, 1, 2, 0, 0));
        }
        HaulSummary s = new HaulSummary(0, 0, rows, 0, -1, 0, 0, 0, 0, 0f);
        assertEquals(8, s.shown(HaulSummary.MAX_ROWS).size());
        assertEquals(3, s.more(HaulSummary.MAX_ROWS));
        assertEquals(1, s.shown(0).size(), "always at least one");
        HaulSummary none = new HaulSummary(0, 0, List.of(), 0, -1, 0, 0, 0, 0, 0f);
        assertTrue(none.isEmpty());
        assertEquals(0, none.shown(8).size());
        assertEquals(0, none.more(8));
    }

    // ---- The rating ------------------------------------------------------------------------------

    @Test
    void theLadderIsBoundedAndMonotonic() {
        assertEquals(HaulRating.SCRAPS, HaulRating.of(0));
        assertEquals(HaulRating.SCRAPS, HaulRating.of(-50));
        assertEquals(HaulRating.SCRAPS, HaulRating.of(Double.NaN));
        assertEquals(HaulRating.SCRAPS, HaulRating.of(99.9));
        assertEquals(HaulRating.HONEST_WORK, HaulRating.of(100));
        assertEquals(HaulRating.GOOD_HAUL, HaulRating.of(400));
        assertEquals(HaulRating.HEAVY_HAUL, HaulRating.of(1200));
        assertEquals(HaulRating.GRAND_HAUL, HaulRating.of(3000));
        assertEquals(HaulRating.LEGENDARY, HaulRating.of(7500));
        assertEquals(HaulRating.LEGENDARY, HaulRating.of(1e12));
        assertEquals(HaulRating.LEGENDARY, HaulRating.of(Double.POSITIVE_INFINITY));
        assertEquals(6, HaulRating.VALUES.length);
        HaulRating last = HaulRating.SCRAPS;
        for (double xp = 0; xp < 20000; xp += 7.3) {
            HaulRating r = HaulRating.of(xp);
            assertTrue(r.ordinal() >= last.ordinal(), "never drops as XP grows");
            last = r;
        }
        for (int i = 1; i < HaulRating.VALUES.length; i++) {
            assertTrue(HaulRating.VALUES[i].minXp() > HaulRating.VALUES[i - 1].minXp());
        }
        assertEquals(0, HaulRating.SCRAPS.burstTier());
        assertEquals(1, HaulRating.GOOD_HAUL.burstTier());
        assertEquals(2, HaulRating.LEGENDARY.burstTier());
    }

    @Test
    void everyTierHasALangKey() throws Exception {
        java.nio.file.Path file = java.nio.file.Path.of("..", "common", "src", "main", "resources", "assets",
                "proficiency", "lang", "en_us.json");
        if (!java.nio.file.Files.exists(file)) {
            return;
        }
        String json = java.nio.file.Files.readString(file);
        for (HaulRating r : HaulRating.VALUES) {
            assertTrue(json.contains("\"" + r.translationKey() + "\""), r.translationKey());
        }
        for (String total : HaulMath.TOTALS) {
            assertTrue(json.contains("\"proficiency.haul.total." + total + "\""), total);
        }
    }

    // ---- Units and the timeline ------------------------------------------------------------------

    @Test
    void unitsReadLikeTheBar() {
        assertEquals(23.4, HaulMath.units(23, 0.4f, 0, 5, 0f), 1e-6);
        assertTrue(HaulMath.units(23, 1.0f, 0, 5, 0f) < 24, "a full bar is not the next level");
        assertEquals(102.5, HaulMath.units(100, 1f, 2, 5, 0.5f), 1e-6);
        assertEquals(105.0, HaulMath.units(100, 1f, 5, 5, 0.7f), 1e-6, "every star: the bar reads full");
        assertEquals(100.0, HaulMath.units(100, 1f, 0, 0, 0.7f), 1e-6, "stars off");
        assertEquals(0.0, HaulMath.units(0, Float.NaN, 0, 5, 0f), 1e-6);
        assertEquals(3, HaulMath.crossings(10.2, 13.1));
        assertEquals(0, HaulMath.crossings(10.2, 10.9));
        assertEquals(0, HaulMath.crossings(10.9, 10.2));
        assertEquals(37, HaulMath.levelAt(37.6));
        assertEquals(100, HaulMath.levelAt(102.3));
        assertEquals(2, HaulMath.starsAt(102.3));
        assertEquals(0, HaulMath.starsAt(99.9));
    }

    @Test
    void rowsGoOneAfterAnotherAndTheStampComesLast() {
        double[] from = {10.2, 4.0, 7.5};
        double[] to = {13.1, 4.6, 7.9};
        HaulMath.Timeline t = HaulMath.timeline(from, to, true);
        assertEquals(HaulMath.INTRO_MS, t.start()[0]);
        assertEquals(HaulMath.MAX_BURSTS, t.bursts()[0], "three levels: capped");
        assertEquals(1, t.bursts()[1]);
        for (int i = 1; i < from.length; i++) {
            assertTrue(t.start()[i] > t.launch(i - 1, t.bursts()[i - 1] - 1), "the next row waits for the last launch");
        }
        long lastSettled = t.launch(2, t.bursts()[2] - 1) + HaulMath.SETTLE_MS;
        assertTrue(t.stampAt() >= lastSettled, "the stamp waits for every bar");
        assertTrue(t.stampAt() >= t.totalsAt() + HaulMath.TOTALS_MS, "and for the totals");
        assertEquals(t.stampAt() + HaulMath.STAMP_MS, t.end());
        assertTrue(t.end() < 6000, "three rows are over in a few seconds: " + t.end());
        assertEquals(-1, t.activeRow(0));
        assertEquals(2, t.activeRow(t.end()));
    }

    @Test
    void eightBusyRowsStayWatchable() {
        double[] from = new double[8];
        double[] to = new double[8];
        for (int i = 0; i < 8; i++) {
            from[i] = 10.5;
            to[i] = 20.5;
        }
        HaulMath.Timeline t = HaulMath.timeline(from, to, true);
        assertTrue(t.end() < 7500, "the longest card: " + t.end());
        HaulMath.Timeline none = HaulMath.timeline(new double[0], new double[0], true);
        assertTrue(none.end() > 0 && none.end() < 2500);
    }

    @Test
    void burstsSplitTheBarEvenlyAndEndAtTheTarget() {
        assertEquals(11.0, HaulMath.Timeline.burstTo(10, 13, 0, 3), 1e-9);
        assertEquals(12.0, HaulMath.Timeline.burstTo(10, 13, 1, 3), 1e-9);
        assertEquals(13.0, HaulMath.Timeline.burstTo(10, 13, 2, 3), 1e-9);
        assertEquals(13.0, HaulMath.Timeline.burstTo(10, 13, 7, 3), 1e-9);
    }

    @Test
    void totalsCountUpAndSettle() {
        HaulMath.Timeline t = HaulMath.timeline(new double[] {1.0}, new double[] {1.5}, true);
        assertEquals(0.0, t.totals(t.totalsAt() - 1), 1e-9);
        double mid = t.totals(t.totalsAt() + HaulMath.TOTALS_MS / 2);
        assertTrue(mid > 0.5 && mid < 1.0, "eases out: " + mid);
        assertEquals(1.0, t.totals(t.totalsAt() + HaulMath.TOTALS_MS), 1e-9);
        assertEquals(0, HaulMath.countUp(500, 0));
        assertEquals(250, HaulMath.countUp(500, 0.5));
        assertEquals(500, HaulMath.countUp(500, 2));
    }

    @Test
    void theStampFallsSquashesAndSettles() {
        assertEquals(0f, HaulMath.stampScale(-1));
        assertEquals(HaulMath.STAMP_FROM, HaulMath.stampScale(0), 1e-6);
        float prev = HaulMath.stampScale(0);
        long hit = Math.round(HaulMath.STAMP_MS * 0.55);
        for (long ms = 10; ms < hit; ms += 10) {
            float s = HaulMath.stampScale(ms);
            assertTrue(s <= prev, "falls");
            prev = s;
        }
        float min = 1f;
        for (long ms = hit; ms <= HaulMath.STAMP_MS; ms += 5) {
            min = Math.min(min, HaulMath.stampScale(ms));
        }
        assertTrue(min < 1f && min > 0.9f, "a small squash: " + min);
        assertEquals(1f, HaulMath.stampScale(HaulMath.STAMP_MS), 1e-6);
        assertFalse(HaulMath.stampHit(hit - 1));
        assertTrue(HaulMath.stampHit(hit));
        assertEquals(1f, HaulMath.stampAlpha(HaulMath.STAMP_MS), 1e-6);
    }

    @Test
    void rowsThatFitLeaveRoomForMore() {
        assertEquals(0, HaulMath.rowsThatFit(0, 200, 20, 10));
        assertEquals(5, HaulMath.rowsThatFit(5, 200, 20, 10));
        assertEquals(8, HaulMath.rowsThatFit(12, 200, 20, 10), "capped at eight");
        assertEquals(4, HaulMath.rowsThatFit(12, 90, 20, 10), "the more line takes a slot");
        assertEquals(1, HaulMath.rowsThatFit(12, 5, 20, 10), "always one");
    }
}
