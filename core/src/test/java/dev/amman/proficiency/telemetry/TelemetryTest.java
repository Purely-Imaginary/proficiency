package dev.amman.proficiency.telemetry;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.amman.proficiency.skill.Skill;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class TelemetryTest {

    private final UUID id = UUID.fromString("00000000-0000-0000-0000-000000000001");

    private static List<JsonObject> parse(List<String> lines) {
        List<JsonObject> out = new ArrayList<>();
        for (String line : lines) {
            out.add(JsonParser.parseString(line).getAsJsonObject());
        }
        return out;
    }

    private static String text(JsonObject row, String key) {
        return row.has(key) ? row.get(key).getAsString() : null;
    }

    private static JsonObject find(List<JsonObject> rows, String type, String skill, String kind) {
        for (JsonObject row : rows) {
            if (type.equals(text(row, "type"))
                    && (skill == null || skill.equals(text(row, "skill")))
                    && (kind == null || kind.equals(text(row, "kind")))) {
                return row;
            }
        }
        return null;
    }

    @Test
    void sourceKindsMatchTheXpLogLabels() {
        assertEquals("block", Telemetry.kind("block.minecraft.stone"));
        assertEquals("mob", Telemetry.kind("entity.minecraft.zombie"));
        assertEquals("kill", Telemetry.kind("kill|entity.minecraft.zombie"));
        assertEquals("first-time", Telemetry.kind("first|block.minecraft.iron_ore"));
        assertEquals("discovery", Telemetry.kind("biome.minecraft.plains"));
        assertEquals("discovery", Telemetry.kind("structure.minecraft.village"));
        assertEquals("movement", Telemetry.kind("proficiency.xplog.source.sprinting"));
        assertEquals("damage", Telemetry.kind("proficiency.xplog.damage.fall"));
        assertEquals("share", Telemetry.kind("proficiency.xplog.source.company"));
        assertEquals("role", Telemetry.kind("proficiency.xplog.source.guardian_block"));
        assertEquals("command", Telemetry.kind("proficiency.xplog.source.command"));
        assertEquals("action", Telemetry.kind("proficiency.xplog.source.timber"));
        assertEquals("other", Telemetry.kind(null));
        assertEquals("other", Telemetry.kind(""));
    }

    @Test
    void grantsAggregatePerPlayerSkillAndKind() {
        Telemetry t = new Telemetry();
        t.grant(id, "Amman", Skill.MINING, "block.minecraft.stone", 1.0, 1.5, 3, 1000);
        t.grant(id, "Amman", Skill.MINING, "block.minecraft.coal_ore", 2.0, 3.0, 3, 2000);
        t.grant(id, "Amman", Skill.MINING, "first|block.minecraft.coal_ore", 15.0, 20.0, 4, 2000);
        t.levelUps(id, "Amman", Skill.MINING, 1, 4);
        List<JsonObject> rows = parse(t.drain(5000, "\"curve\":[8,2,1.35]"));

        JsonObject block = find(rows, "xp", "mining", "block");
        assertEquals(2, block.get("n").getAsInt());
        assertEquals(3.0, block.get("base").getAsDouble(), 1e-9);
        assertEquals(4.5, block.get("xp").getAsDouble(), 1e-9);
        assertEquals(1, find(rows, "xp", "mining", "first-time").get("n").getAsInt());
        JsonObject skill = find(rows, "skill", "mining", null);
        assertEquals(4, skill.get("level").getAsInt());
        assertEquals(1, skill.get("levelups").getAsInt());
        assertTrue(skill.get("need").getAsDouble() > 0);
        assertEquals(8, find(rows, "meta", null, null).get("curve").getAsJsonArray().get(0).getAsInt());
        assertEquals("Amman", find(rows, "player", null, null).get("player").getAsString());
        for (JsonObject row : rows) {
            assertEquals(Telemetry.SCHEMA, row.get("v").getAsInt());
        }
    }

    @Test
    void aMaxedSkillWritesNoHugeNeed() {
        Telemetry t = new Telemetry();
        t.grant(id, "Amman", Skill.MINING, "block.x", 1, 1, 100, 0);
        JsonObject skill = find(parse(t.drain(1, "")), "skill", "mining", null);
        assertEquals(0, skill.get("need").getAsDouble(), 0);
    }

    @Test
    void drainResetsTheCounters() {
        Telemetry t = new Telemetry();
        t.grant(id, "Amman", Skill.MINING, "block.minecraft.stone", 1, 1, 1, 0);
        assertFalse(t.drain(1, "").isEmpty());
        assertTrue(t.isEmpty());
        assertTrue(t.drain(2, "").isEmpty());
    }

    @Test
    void activeTimeCountsOnlyWhileActiveAndOnlyForSkillsInUse() {
        Telemetry t = new Telemetry();
        t.grant(id, "Amman", Skill.MINING, "block.minecraft.stone", 1, 1, 1, 0);
        for (int s = 1; s <= 10; s++) {
            t.tick(id, "Amman", true, 1000, s * 1000L);
        }
        // AFK: online time grows, active time does not.
        for (int s = 11; s <= 20; s++) {
            t.tick(id, "Amman", false, 1000, s * 1000L);
        }
        // Active again but Mining has not paid for over a minute: not engaged.
        for (int s = 100; s < 105; s++) {
            t.tick(id, "Amman", true, 1000, s * 1000L);
        }
        List<JsonObject> rows = parse(t.drain(200_000, ""));
        JsonObject player = find(rows, "player", null, null);
        assertEquals(25, player.get("online_s").getAsDouble(), 1e-9);
        assertEquals(15, player.get("active_s").getAsDouble(), 1e-9);
        assertEquals(10, find(rows, "skill", "mining", null).get("active_s").getAsDouble(), 1e-9);
    }

    @Test
    void procsRollsAndDeathsAreCounted() {
        Telemetry t = new Telemetry();
        t.roll(id, "Amman", Skill.MINING, 0.2, true);
        t.roll(id, "Amman", Skill.MINING, 0.2, false);
        t.proc(id, "Amman", Skill.MINING);
        t.proc(id, "Amman", Skill.MINING);
        double[] lost = new double[Skill.VALUES.length];
        int[] levels = new int[Skill.VALUES.length];
        lost[Skill.MINING.ordinal()] = 12.5;
        levels[Skill.MINING.ordinal()] = 30;
        t.death(id, "Amman", lost, levels, 7);
        List<JsonObject> rows = parse(t.drain(1, ""));
        JsonObject skill = find(rows, "skill", "mining", null);
        assertEquals(2, skill.get("rolls").getAsInt());
        assertEquals(1, skill.get("roll_hits").getAsInt());
        assertEquals(0.4, skill.get("chance_sum").getAsDouble(), 1e-9);
        assertEquals(2, skill.get("procs").getAsInt());
        assertEquals(1, skill.get("deaths").getAsInt());
        assertEquals(12.5, skill.get("xp_lost").getAsDouble(), 1e-9);
        JsonObject player = find(rows, "player", null, null);
        assertEquals(1, player.get("deaths").getAsInt());
        assertEquals(7, player.get("streak_lost").getAsInt());
    }

    @Test
    void namesAreEscapedIntoValidJson() {
        Telemetry t = new Telemetry();
        t.grant(id, "We\"ird\\na\nme", Skill.MINING, "block.x", 1, 1, 1, 0);
        List<JsonObject> rows = parse(t.drain(1, ""));
        assertEquals("We\"ird\\na\nme", find(rows, "player", null, null).get("player").getAsString());
    }

    /** 10,000 grants must be cheap enough to ignore on the server thread. */
    @Test
    void tenThousandGrantsAggregateWithinABudget() {
        Telemetry t = new Telemetry();
        String[] sources = {"block.minecraft.stone", "entity.minecraft.zombie", "item.minecraft.bread",
                "proficiency.xplog.source.sprinting", "first|block.minecraft.dirt"};
        // A running server is already warm, so warm the JIT first, then measure.
        for (int i = 0; i < 5_000; i++) {
            t.grant(id, "Amman", Skill.VALUES[i % Skill.VALUES.length], sources[i % 5], 1, 1, 5, i);
        }
        t.drain(0, "");
        long start = System.nanoTime();
        for (int i = 0; i < 10_000; i++) {
            t.grant(id, "Amman", Skill.VALUES[i % Skill.VALUES.length], sources[i % 5], 1.0, 1.2, 5, i);
        }
        long micros = (System.nanoTime() - start) / 1000;
        System.out.println("telemetry: 10000 grants in " + micros + " us");
        assertTrue(micros < 50_000, "10k grants took " + micros + " us");
        assertFalse(t.drain(1, "").isEmpty());
    }

    @Test
    void filesAppendAndPruneByAge(@TempDir Path dir) throws IOException {
        Path folder = dir.resolve("proficiency").resolve("telemetry");
        LocalDate today = LocalDate.of(2026, 10, 8);
        assertTrue(TelemetryFiles.append(folder, today, List.of("{\"a\":1}")));
        assertTrue(TelemetryFiles.append(folder, today, List.of("{\"a\":2}", "{\"a\":3}")));
        assertEquals(List.of("{\"a\":1}", "{\"a\":2}", "{\"a\":3}"),
                Files.readAllLines(folder.resolve("2026-10-08.jsonl")));
        Files.writeString(folder.resolve("2026-08-01.jsonl"), "old\n");
        Files.writeString(folder.resolve("2026-08-09.jsonl"), "edge\n");
        Files.writeString(folder.resolve("notes.jsonl"), "keep\n");
        Files.writeString(folder.resolve("readme.txt"), "keep\n");
        // 60 days before 10-08 is 08-09: that day stays, the older one goes.
        assertEquals(1, TelemetryFiles.prune(folder, today, 60));
        assertFalse(Files.exists(folder.resolve("2026-08-01.jsonl")));
        assertTrue(Files.exists(folder.resolve("2026-08-09.jsonl")));
        assertTrue(Files.exists(folder.resolve("notes.jsonl")));
        assertEquals(0, TelemetryFiles.prune(folder, today, 0));
    }

    @Test
    void hubWritesAFlushAndAFinalFlushOnStop(@TempDir Path dir) throws IOException {
        Path folder = dir.resolve("t");
        LocalDate day = LocalDate.of(2026, 10, 8);
        TelemetryHub.start(folder, 60, day, 0);
        TelemetryHub.data().grant(id, "Amman", Skill.MINING, "block.minecraft.stone", 1, 1, 1, 0);
        assertFalse(TelemetryHub.flushIfDue(1000, day), "not yet due");
        assertFalse(Files.exists(folder));
        assertTrue(TelemetryHub.flushIfDue(TelemetryHub.FLUSH_EVERY_MS, day));
        int first = Files.readAllLines(folder.resolve("2026-10-08.jsonl")).size();
        TelemetryHub.data().grant(id, "Amman", Skill.MINING, "block.minecraft.stone", 1, 1, 1, 0);
        TelemetryHub.stop(TelemetryHub.FLUSH_EVERY_MS + 5, day);
        assertTrue(Files.readAllLines(folder.resolve("2026-10-08.jsonl")).size() > first);
        assertFalse(TelemetryHub.running());
    }
}
