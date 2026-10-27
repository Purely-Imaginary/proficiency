package dev.amman.proficiency;

import com.mojang.serialization.JsonOps;
import dev.amman.proficiency.skill.Codex;
import dev.amman.proficiency.skill.PlayerSkills;
import dev.amman.proficiency.skill.Skill;
import dev.amman.proficiency.skill.SkillsWire;
import org.junit.jupiter.api.Test;

import java.util.ArrayDeque;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The Codex: kinds counted per skill, milestones at 10/25/50/100, bounded cosmetic titles. */
class CodexTest {

    private static PlayerSkills withKinds(Skill skill, int n) {
        PlayerSkills skills = new PlayerSkills();
        for (int i = 0; i < n; i++) {
            skills.markVisited("first:" + skill.id() + ":block.minecraft.kind" + i);
        }
        return skills;
    }

    @Test
    void countsDistinctKindsPerSkillOnly() {
        PlayerSkills skills = withKinds(Skill.MINING, 12);
        skills.markVisited("first:" + Skill.FARMING.id() + ":block.minecraft.wheat");
        skills.markVisited("biome:minecraft:plains");
        assertEquals(12, skills.kindsFound(Skill.MINING));
        assertEquals(1, skills.kindsFound(Skill.FARMING));
        assertEquals(0, skills.kindsFound(Skill.SWORDS));
    }

    @Test
    void aVariantAndItsCanonicalKeyCountOnce() {
        PlayerSkills skills = new PlayerSkills();
        skills.markVisited("first:" + Skill.MINING.id() + ":block.minecraft.iron_ore");
        skills.markVisited("first:" + Skill.MINING.id() + ":block.minecraft.deepslate_iron_ore");
        assertEquals(1, skills.kindsFound(Skill.MINING), "the legacy raw key must not double the count");
    }

    @Test
    void milestonesAndNext() {
        assertEquals(List.of(), Codex.reached(9));
        assertEquals(List.of(10), Codex.reached(10));
        assertEquals(List.of(10, 25, 50), Codex.reached(60));
        assertEquals(List.of(10, 25, 50, 100), Codex.reached(500));
        assertEquals(10, Codex.next(0));
        assertEquals(25, Codex.next(10));
        assertEquals(100, Codex.next(99));
        assertEquals(0, Codex.next(100), "nothing left to chase after the last one");
        assertEquals(0, Codex.best(9));
        assertEquals(25, Codex.best(49));
    }

    @Test
    void theTitleListIsBoundedAndDistinct() {
        List<Codex.Title> all = Codex.all();
        assertEquals(Skill.VALUES.length * Codex.MILESTONES.length, all.size());
        Set<String> ids = new HashSet<>();
        for (Codex.Title t : all) {
            assertTrue(ids.add(t.id()), "duplicate title " + t.id());
            assertEquals(t, Codex.parse(t.id()));
        }
    }

    @Test
    void parseRejectsGarbage() {
        assertNull(Codex.parse(null));
        assertNull(Codex.parse(""));
        assertNull(Codex.parse("mining"));
        assertNull(Codex.parse("mining:26"));
        assertNull(Codex.parse("nothing:10"));
        assertNull(Codex.parse("mining:ten"));
    }

    @Test
    void aTitleCanOnlyBePickedOnceEarnedAndCleared() {
        PlayerSkills skills = withKinds(Skill.MINING, 10);
        assertFalse(skills.setCodexTitle("mining:25"), "not earned");
        assertFalse(skills.setCodexTitle("farming:10"), "other skill");
        assertFalse(skills.setCodexTitle("bogus"), "not a title");
        assertEquals("", skills.codexTitle());
        assertTrue(skills.setCodexTitle("mining:10"));
        assertEquals("mining:10", skills.codexTitle());
        assertTrue(skills.setCodexTitle(""));
        assertEquals("", skills.codexTitle());
    }

    @Test
    void markCodexSeenIsOnceAndRejectsNonTitles() {
        PlayerSkills skills = new PlayerSkills();
        assertTrue(skills.markCodexSeen("mining:10"));
        assertFalse(skills.markCodexSeen("mining:10"));
        assertFalse(skills.markCodexSeen("mining:11"));
    }

    @Test
    void saveRoundTripKeepsTitleAndSeen() {
        PlayerSkills original = withKinds(Skill.MINING, 25);
        original.markCodexSeen("mining:10");
        original.markCodexSeen("mining:25");
        original.setCodexTitle("mining:25");
        var encoded = PlayerSkills.CODEC.encodeStart(JsonOps.INSTANCE, original)
                .getOrThrow(m -> new AssertionError(m));
        PlayerSkills restored = PlayerSkills.CODEC.parse(JsonOps.INSTANCE, encoded)
                .getOrThrow(m -> new AssertionError(m));
        assertEquals("mining:25", restored.codexTitle());
        assertTrue(restored.codexSeen("mining:10"));
        assertTrue(restored.codexSeen("mining:25"));
        assertEquals(25, restored.kindsFound(Skill.MINING));
    }

    @Test
    void anOldSaveWithNoCodexFieldLoadsEmpty() {
        PlayerSkills empty = new PlayerSkills();
        var encoded = PlayerSkills.CODEC.encodeStart(JsonOps.INSTANCE, empty).getOrThrow(m -> new AssertionError(m));
        var obj = encoded.getAsJsonObject();
        obj.remove("codex");
        PlayerSkills restored = PlayerSkills.CODEC.parse(JsonOps.INSTANCE, obj).getOrThrow(m -> new AssertionError(m));
        assertEquals("", restored.codexTitle());
    }

    @Test
    void copyFromCarriesTheTitleThroughDeath() {
        PlayerSkills source = withKinds(Skill.MINING, 10);
        source.markCodexSeen("mining:10");
        source.setCodexTitle("mining:10");
        PlayerSkills dest = new PlayerSkills();
        dest.copyFrom(source);
        assertEquals("mining:10", dest.codexTitle());
        assertTrue(dest.codexSeen("mining:10"));
    }

    @Test
    void deathDoesNotTouchKindsOrTitle() {
        PlayerSkills skills = withKinds(Skill.MINING, 10);
        skills.markCodexSeen("mining:10");
        skills.setCodexTitle("mining:10");
        skills.setLevel(Skill.MINING, 40);
        skills.applyDeathPenalty();
        assertEquals("mining:10", skills.codexTitle());
        assertEquals(10, skills.kindsFound(Skill.MINING));
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
    void theSyncPacketCarriesTheTitle() {
        PlayerSkills skills = withKinds(Skill.FARMING, 50);
        skills.setCodexTitle("farming:50");
        Buffer buffer = new Buffer();
        skills.write(buffer);
        PlayerSkills read = PlayerSkills.read(buffer);
        assertTrue(buffer.queue.isEmpty());
        assertEquals("farming:50", read.codexTitle());
    }
}
