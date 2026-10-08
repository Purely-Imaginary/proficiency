package dev.amman.proficiency;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.amman.proficiency.client.DeathRecapHud;
import dev.amman.proficiency.client.LevelUpFx;
import dev.amman.proficiency.client.RemainTrack;
import dev.amman.proficiency.client.StreakBadge;
import dev.amman.proficiency.net.DeathRecapPayload;
import dev.amman.proficiency.skill.PlayerSkills;
import dev.amman.proficiency.skill.Skill;
import io.netty.buffer.Unpooled;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import net.minecraft.network.FriendlyByteBuf;
import org.junit.jupiter.api.Test;

/** The four HUD effects: their timing, the packet that feeds the recap, and the synced frenzy. */
class HudEffectsTest {

    @Test
    void everyTenthLevelIsTheGoldOneAndRunsLonger() {
        assertFalse(LevelUpFx.isGold(0));
        assertFalse(LevelUpFx.isGold(9));
        assertTrue(LevelUpFx.isGold(10));
        assertTrue(LevelUpFx.isGold(100));
        assertEquals(LevelUpFx.NORMAL_MS, LevelUpFx.durationMs(7));
        assertEquals(LevelUpFx.GOLD_MS, LevelUpFx.durationMs(20));
    }

    @Test
    void theLevelUpMomentStartsRunsAndEnds() {
        LevelUpFx.reset();
        assertFalse(LevelUpFx.active(0));
        LevelUpFx.start(Skill.MINING, 6, 7, 1000, false);
        assertTrue(LevelUpFx.active(1000));
        assertTrue(LevelUpFx.active(1599));
        assertFalse(LevelUpFx.active(1600));
        assertEquals(1f, LevelUpFx.flash(1000), 1e-6);
        assertEquals(0f, LevelUpFx.flash(1600), 1e-6);
        assertEquals(0f, LevelUpFx.roll(1000), 1e-6);
        assertEquals(1f, LevelUpFx.roll(1000 + LevelUpFx.ROLL_MS), 1e-6);
        // Only a borrowed line is held up past the effect itself.
        assertFalse(LevelUpFx.lineUp(1100));
        LevelUpFx.start(Skill.MINING, 9, 10, 5000, true);
        assertTrue(LevelUpFx.gold());
        assertTrue(LevelUpFx.active(6300));
        assertTrue(LevelUpFx.lineUp(7900));
        assertFalse(LevelUpFx.lineUp(5000 + LevelUpFx.BORROW_GOLD_MS));
        LevelUpFx.reset();
    }

    @Test
    void aQueuedLevelUpIsTakenOnce() {
        LevelUpFx.reset();
        LevelUpFx.queue(Skill.FISHING, 12);
        assertEquals(Skill.FISHING, LevelUpFx.takeQueuedSkill());
        assertEquals(12, LevelUpFx.queuedLevel());
        assertNull(LevelUpFx.takeQueuedSkill());
    }

    @Test
    void theRingAndTheSweepTakeTheirWholeFromTheFirstValueSeen() {
        RemainTrack track = new RemainTrack(4);
        assertEquals(0f, track.fraction(1, 0f));
        assertEquals(1f, track.fraction(1, 400f), 1e-6);
        assertEquals(0.5f, track.fraction(1, 200f), 1e-6);
        assertEquals(0f, track.fraction(1, 0f));
        // A fresh run after it read zero starts from its own whole.
        assertEquals(1f, track.fraction(1, 100f), 1e-6);
        // Another slot is untouched.
        assertEquals(1f, track.fraction(2, 50f), 1e-6);
    }

    @Test
    void theStreakFlaresOnlyWhenItGrowsPastAMark() {
        assertTrue(StreakBadge.crossesFlare(9, 10));
        assertTrue(StreakBadge.crossesFlare(24, 25));
        assertTrue(StreakBadge.crossesFlare(49, 50));
        assertFalse(StreakBadge.crossesFlare(10, 11));
        assertFalse(StreakBadge.crossesFlare(12, 9));
        // An empty sheet handed over after a respawn must not read as growth.
        assertFalse(StreakBadge.crossesFlare(0, 12));
        assertFalse(StreakBadge.crossesFlare(5, 30));
    }

    @Test
    void theBadgeFillsInRowsOfTheGlyph() {
        assertEquals(0, StreakBadge.fillRows(0f));
        assertEquals(7, StreakBadge.fillRows(1f));
        assertEquals(7, StreakBadge.fillRows(3f));
        assertEquals(4, StreakBadge.fillRows(0.5f));
    }

    @Test
    void aDeadStreakStaysWholeThenBreaks() {
        StreakBadge.reset();
        StreakBadge.startBreak(12, 1000);
        assertTrue(StreakBadge.holding(1000));
        assertTrue(StreakBadge.breakT(1100) < 0f);
        assertTrue(StreakBadge.breakT(1000 + StreakBadge.BREAK_DELAY_MS + 400) > 0f);
        assertTrue(StreakBadge.holding(1000 + StreakBadge.BREAK_DELAY_MS + StreakBadge.BREAK_MS - 1));
        assertFalse(StreakBadge.holding(1000 + StreakBadge.BREAK_DELAY_MS + StreakBadge.BREAK_MS));
        assertEquals(12, StreakBadge.breakPercent());
        StreakBadge.reset();
    }

    @Test
    void theRecapDrainsEachBarInTurnAndFadesOut() {
        assertEquals(0f, DeathRecapHud.drain(0, 0), 1e-6);
        assertEquals(0f, DeathRecapHud.drain(0, DeathRecapHud.DRAIN_DELAY_MS), 1e-6);
        assertEquals(1f, DeathRecapHud.drain(0, DeathRecapHud.DRAIN_DELAY_MS + DeathRecapHud.DRAIN_MS), 1e-6);
        // A later row starts later.
        assertEquals(0f, DeathRecapHud.drain(3, DeathRecapHud.DRAIN_DELAY_MS + 3 * DeathRecapHud.ROW_STAGGER_MS),
                1e-6);
        assertTrue(DeathRecapHud.drain(3, 1000) < DeathRecapHud.drain(0, 1000));
        assertEquals(0f, DeathRecapHud.opacity(0), 1e-6);
        assertEquals(1f, DeathRecapHud.opacity(2000), 1e-6);
        assertEquals(0f, DeathRecapHud.opacity(DeathRecapHud.TOTAL_MS), 1e-6);
        assertTrue(DeathRecapHud.opacity(DeathRecapHud.TOTAL_MS - 100) < 0.2f);
    }

    @Test
    void theRecapPacketRoundTripsAndKeepsTheBiggestLossesFirst() {
        PlayerSkills after = new PlayerSkills();
        after.setLevel(Skill.MINING, 31);
        after.setLevel(Skill.FARMING, 8);
        Map<Skill, Float> lost = new EnumMap<>(Skill.class);
        lost.put(Skill.MINING, 0.25f);
        lost.put(Skill.FARMING, 0.8f);
        float[] before = new float[Skill.VALUES.length];
        before[Skill.MINING.ordinal()] = 0.25f;
        before[Skill.FARMING.ordinal()] = 0.8f;
        DeathRecapPayload payload = DeathRecapPayload.of(lost, before, after, 14, 14);
        assertEquals(Skill.FARMING.ordinal(), payload.rows().get(0).skillOrdinal());
        assertEquals(0, payload.more());

        FriendlyByteBuf buf = new FriendlyByteBuf(Unpooled.buffer());
        DeathRecapPayload.STREAM_CODEC.encode(buf, payload);
        DeathRecapPayload decoded = DeathRecapPayload.STREAM_CODEC.decode(buf);
        assertEquals(0, buf.readableBytes());
        assertEquals(payload, decoded);
        assertFalse(decoded.isEmpty());
        assertTrue(new DeathRecapPayload(List.of(), 0, 0, 0).isEmpty());
    }

    @Test
    void theRecapIsCappedAtEightRows() {
        PlayerSkills after = new PlayerSkills();
        Map<Skill, Float> lost = new EnumMap<>(Skill.class);
        float[] before = new float[Skill.VALUES.length];
        for (int i = 0; i < 12; i++) {
            lost.put(Skill.VALUES[i], 0.1f + i * 0.01f);
        }
        DeathRecapPayload payload = DeathRecapPayload.of(lost, before, after, 0, 0);
        assertEquals(DeathRecapPayload.MAX_ROWS, payload.rows().size());
        assertEquals(4, payload.more(), "the four bars that did not fit are counted for the +N more line");
    }

    @Test
    void aRecapWithTooManyRowsStaysInStepAndABadCountFailsCleanly() {
        FriendlyByteBuf buf = new FriendlyByteBuf(Unpooled.buffer());
        buf.writeVarInt(12);
        for (int i = 0; i < 12; i++) {
            buf.writeVarInt(i);
            buf.writeFloat(0.5f);
            buf.writeFloat(0f);
        }
        buf.writeVarInt(0);
        buf.writeVarInt(7);
        buf.writeVarInt(7);
        DeathRecapPayload decoded = DeathRecapPayload.STREAM_CODEC.decode(buf);
        assertEquals(0, buf.readableBytes(), "every row is read, so the tail is not shifted");
        assertEquals(DeathRecapPayload.MAX_ROWS, decoded.rows().size());
        assertEquals(7, decoded.streakStacks());

        FriendlyByteBuf negative = new FriendlyByteBuf(Unpooled.buffer());
        negative.writeVarInt(-1);
        org.junit.jupiter.api.Assertions.assertThrows(io.netty.handler.codec.DecoderException.class,
                () -> DeathRecapPayload.STREAM_CODEC.decode(negative));
    }

    @Test
    void theTrackKeepsItsWholeForTheFadeIn() {
        RemainTrack track = new RemainTrack(2);
        track.fraction(0, 400f);
        track.fraction(0, 390f);
        assertEquals(400f, track.total(0), 1e-6);
        track.fraction(0, 0f);
        assertEquals(0f, track.total(0), 1e-6);
    }

    @Test
    void aRunningFrenzyIsSyncedToTheClientButNotSaved() {
        PlayerSkills original = new PlayerSkills();
        original.beginFrenzy(Skill.SWORDS, 1_400L, 7_000L);
        FriendlyByteBuf buf = new FriendlyByteBuf(Unpooled.buffer());
        dev.amman.proficiency.net.SyncSkillsPayload.SKILLS_CODEC.encode(buf, original);
        PlayerSkills decoded = dev.amman.proficiency.net.SyncSkillsPayload.SKILLS_CODEC.decode(buf);
        assertEquals(0, buf.readableBytes());
        assertEquals(400L, decoded.frenzyRemaining(Skill.SWORDS, 1_000L));
        assertEquals(6_000L, decoded.cooldownRemaining(Skill.SWORDS, 1_000L));
        // A copy (the respawn clone) keeps it; a death clears it.
        PlayerSkills copy = new PlayerSkills();
        copy.copyFrom(original);
        assertEquals(400L, copy.frenzyRemaining(Skill.SWORDS, 1_000L));
        copy.clearFrenzy();
        assertEquals(0L, copy.frenzyRemaining(Skill.SWORDS, 1_000L));
    }
}
