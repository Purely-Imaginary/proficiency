package dev.amman.proficiency;

import dev.amman.proficiency.net.XpLogPayload;
import dev.amman.proficiency.skill.XpLog;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The recent-XP lists: per-skill rings, merging, the dirty skills, and the trip over the wire. */
class XpLogTest {

    private static final int MINING = 8;
    private static final int SMITHING = 17;

    @Test
    void consecutiveGainsOfOneSkillAndSourceMerge() {
        XpLog log = new XpLog();
        for (int i = 0; i < 5; i++) {
            log.add(MINING, "block.minecraft.iron_ore", 2.4f, 2.0f, "tempo=1.200", 1000L + i * 500L);
        }
        List<XpLog.Entry> entries = log.entries(MINING);
        assertEquals(1, entries.size());
        assertEquals(12.0f, entries.get(0).amount(), 1e-4);
        assertEquals(10.0f, entries.get(0).base(), 1e-4, "the base is summed like the amount");
        assertEquals(5, entries.get(0).count());
        assertEquals(1000L, entries.get(0).first(), "the line keeps its oldest gain's time");
        assertEquals(3000L, entries.get(0).at(), "the line carries the newest gain's time");
        assertEquals("tempo=1.200", entries.get(0).factors());
    }

    @Test
    void aMergeKeepsTheNewestGainsFactors() {
        XpLog log = new XpLog();
        log.add(MINING, "iron", 1f, 1f, "tempo=1.100", 0L);
        log.add(MINING, "iron", 1f, 1f, "tempo=1.300;streak=1.050", 1L);
        assertEquals("tempo=1.300;streak=1.050", log.entries(MINING).get(0).factors());
    }

    /** The bug this shape fixes: a Wayfaring run must not push Mining's history out. */
    @Test
    void eachSkillHasItsOwnRing() {
        XpLog log = new XpLog();
        log.add(MINING, "iron", 1f, 0L);
        for (int i = 0; i < 20; i++) {
            log.add(SMITHING, "source" + i, 1f, 1L + i);
        }
        assertEquals(1, log.entries(MINING).size(), "Mining keeps its line");
        assertEquals(XpLog.CAPACITY, log.entries(SMITHING).size());
        assertEquals("source19", log.entries(SMITHING).get(0).source());
        assertEquals("source12", log.entries(SMITHING).get(XpLog.CAPACITY - 1).source());
        assertTrue(log.entries(3).isEmpty(), "a skill that earned nothing has no lines");
    }

    @Test
    void anotherSkillInBetweenDoesNotBreakAMerge() {
        XpLog log = new XpLog();
        log.add(MINING, "iron", 1f, 0L);
        log.add(SMITHING, "ingot", 1f, 1L);
        log.add(MINING, "iron", 1f, 2L);
        assertEquals(1, log.entries(MINING).size());
        assertEquals(2, log.entries(MINING).get(0).count());
    }

    /** Only the skill's newest line merges: iron, stone, iron is three lines, not two. */
    @Test
    void onlyTheNewestLineMerges() {
        XpLog log = new XpLog();
        log.add(MINING, "iron", 1f, 0L);
        log.add(MINING, "stone", 1f, 1L);
        log.add(MINING, "iron", 1f, 2L);
        assertEquals(3, log.entries(MINING).size());
    }

    @Test
    void anOldLineDoesNotSoakUpANewGain() {
        XpLog log = new XpLog();
        log.add(MINING, "iron", 1f, 0L);
        log.add(MINING, "iron", 1f, XpLog.MERGE_WINDOW_MILLIS + 1);
        assertEquals(2, log.entries(MINING).size());
    }

    @Test
    void allEntriesAreNewestFirstAcrossSkills() {
        XpLog log = new XpLog();
        log.add(MINING, "iron", 1f, 0L);
        log.add(SMITHING, "ingot", 1f, 5L);
        log.add(MINING, "stone", 1f, 9L);
        List<XpLog.Entry> all = log.entries();
        assertEquals(3, all.size());
        assertEquals("stone", all.get(0).source());
        assertEquals("ingot", all.get(1).source());
        assertEquals("iron", all.get(2).source());
    }

    @Test
    void noSourceIsAnEmptyStringAndStillMerges() {
        XpLog log = new XpLog();
        log.add(MINING, null, 1f, 0L);
        log.add(MINING, "", 1f, 1L);
        assertEquals(1, log.entries(MINING).size());
        assertEquals("", log.entries(MINING).get(0).source());
    }

    @Test
    void nothingOrNonsenseIsNotLogged() {
        XpLog log = new XpLog();
        log.add(MINING, "x", 0f, 0L);
        log.add(MINING, "x", -1f, 0L);
        log.add(MINING, "x", Float.NaN, 0L);
        log.add(MINING, "x", Float.POSITIVE_INFINITY, 0L);
        assertTrue(log.entries().isEmpty());
        assertTrue(log.takeDirty().isEmpty());
    }

    @Test
    void dirtyNamesTheChangedSkillsAndIsClearedByAsking() {
        XpLog log = new XpLog();
        assertTrue(log.takeDirty().isEmpty());
        log.add(SMITHING, "x", 1f, 0L);
        log.add(MINING, "x", 1f, 0L);
        assertEquals(List.of(MINING, SMITHING), log.takeDirty());
        assertTrue(log.takeDirty().isEmpty(), "a second ask with nothing new sends nothing");
        log.add(MINING, "x", 1f, 1L);
        assertEquals(List.of(MINING), log.takeDirty(), "a merge is a change too, of that skill only");
    }

    @Test
    void thePayloadCarriesOnlyTheNamedSkillsWithAgesAndSurvivesTheWire() {
        XpLog log = new XpLog();
        log.add(MINING, "block.minecraft.iron_ore", 2.5f, 2.0f, "tempo=1.250", 1_000L);
        log.add(MINING, "block.minecraft.iron_ore", 2.5f, 2.0f, "tempo=1.250", 2_000L);
        log.add(MINING, "block.minecraft.stone", 1f, 4_000L);
        log.add(SMITHING, "item.minecraft.iron_ingot", 1f, 5_000L);
        XpLogPayload sent = XpLogPayload.of(log, List.of(MINING), 10_000L);
        net.minecraft.network.FriendlyByteBuf buf =
                new net.minecraft.network.FriendlyByteBuf(io.netty.buffer.Unpooled.buffer());
        XpLogPayload.STREAM_CODEC.encode(buf, sent);
        XpLogPayload received = XpLogPayload.STREAM_CODEC.decode(buf);
        assertEquals(sent, received);
        assertEquals(List.of(MINING), received.skills());
        assertEquals(2, received.lines().size(), "Smithing was not named, so it is not sent");
        assertEquals(6_000L, received.lines().get(0).ageMillis());
        XpLogPayload.Line iron = received.lines().get(1);
        assertEquals("block.minecraft.iron_ore", iron.source());
        assertEquals(9_000L, iron.firstAgeMillis());
        assertEquals(8_000L, iron.ageMillis());
        assertEquals(4.0f, iron.base(), 1e-4);
        assertEquals(2, iron.count());
        assertEquals("tempo=1.250", iron.factors());
    }
}
