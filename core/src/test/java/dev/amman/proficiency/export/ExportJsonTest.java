package dev.amman.proficiency.export;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.amman.proficiency.hearth.HearthData;
import dev.amman.proficiency.hearth.HearthLedger;
import dev.amman.proficiency.hearth.HearthPool;
import dev.amman.proficiency.hearth.HearthState;
import dev.amman.proficiency.skill.PlayerSkills;
import dev.amman.proficiency.skill.Skill;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The dashboard export: the JSON shape, the top three, deaths over a week, the atomic write and the offline carry-over. */
class ExportJsonTest {

    private static final UUID AMMAN = UUID.fromString("00000000-0000-0000-0000-0000000000a1");
    private static final UUID ROWAN = UUID.fromString("00000000-0000-0000-0000-0000000000a2");
    private static final long NOW = 1_800_000_000_000L;

    private static PlayerSkills amman() {
        PlayerSkills s = new PlayerSkills();
        s.setLevel(Skill.MINING, 100);
        s.setStars(Skill.MINING, 2);
        s.setLevel(Skill.COOKING, 60);
        s.setLevel(Skill.GUARDIAN, 60);
        s.setLevel(Skill.FARMING, 12);
        return s;
    }

    private static HearthRow hearth() {
        HearthState state = new HearthState();
        state.setStage(3);
        state.addUncapped(HearthPool.HEARTY, 40);
        state.ledger().add(AMMAN, "Amman", HearthPool.HEARTY, "cooking", 40, 100);
        state.ledger().add(ROWAN, "Rowan", HearthPool.REGEN, "mining", 5, 100);
        return HearthRow.of("minecraft:overworld", 10, 64, -20, state, HearthData.DEFAULTS, "cottage", 24, true, 4, 30, 120);
    }

    @Test
    void theShapeHasEveryFieldTheDashboardReads() {
        PlayerRow row = PlayerRow.of(AMMAN, "Amman \"Q\"", true, NOW, amman(), 2);
        String text = ExportJson.render(NOW, "Pandowo", List.of(row), List.of(hearth(), HearthRow.unloaded("minecraft:the_nether", 1, 2, 3, 20)));
        JsonObject root = JsonParser.parseString(text).getAsJsonObject();
        assertEquals(1, root.get("version").getAsInt());
        assertEquals("Pandowo", root.get("world").getAsString());
        assertEquals(Skill.VALUES.length, root.getAsJsonArray("skills").size());
        JsonObject p = root.getAsJsonArray("players").get(0).getAsJsonObject();
        for (String key : List.of("uuid", "name", "online", "seenMs", "totalLevels", "totalStars", "synergies",
                "expertisePoints", "expertiseSpent", "deathsWeek", "top", "skills")) {
            assertTrue(p.has(key), key);
        }
        assertEquals("Amman \"Q\"", p.get("name").getAsString());
        assertEquals(2, p.get("deathsWeek").getAsInt());
        assertEquals(Skill.VALUES.length, p.getAsJsonObject("skills").size());
        JsonObject mining = p.getAsJsonObject("skills").getAsJsonObject("mining");
        assertEquals(100, mining.get("level").getAsInt());
        assertEquals(2, mining.get("stars").getAsInt());
        assertTrue(mining.get("bar").getAsDouble() >= 0 && mining.get("bar").getAsDouble() <= 1);
        assertEquals(100 + 60 + 60 + 12, p.get("totalLevels").getAsInt());
        JsonArray hearths = root.getAsJsonArray("hearths");
        JsonObject h = hearths.get(0).getAsJsonObject();
        for (String key : List.of("dimension", "x", "y", "z", "loaded", "owner", "stage", "smouldering", "lane",
                "tier", "radius", "reachXp", "contributors", "pools", "build")) {
            assertTrue(h.has(key), key);
        }
        assertEquals("Amman", h.getAsJsonObject("owner").get("name").getAsString());
        assertEquals(HearthPool.COUNT, h.getAsJsonObject("pools").size());
        assertEquals(40, h.getAsJsonObject("pools").getAsJsonObject("hearty").get("charge").getAsDouble(), 1e-9);
        assertTrue(h.getAsJsonObject("build").get("on").getAsBoolean());
        assertEquals(120, h.getAsJsonObject("build").get("total").getAsInt());
        JsonObject far = hearths.get(1).getAsJsonObject();
        assertFalse(far.get("loaded").getAsBoolean());
        assertTrue(far.get("owner").isJsonNull());
    }

    @Test
    void topThreeIsLevelThenStarsAndLeavesUntouchedSkillsOut() {
        PlayerRow row = PlayerRow.of(AMMAN, "Amman", true, NOW, amman(), 0);
        assertEquals(3, row.top().size());
        assertEquals("mining", row.top().get(0));
        PlayerRow empty = PlayerRow.of(ROWAN, "Rowan", true, NOW, new PlayerSkills(), 0);
        assertTrue(empty.top().isEmpty());
    }

    @Test
    void deathsCountOverARollingWeek() {
        DeathLog log = new DeathLog();
        log.add("a", NOW - DeathLog.WEEK_MS - 1);
        log.add("a", NOW - 1000);
        log.add("a", NOW);
        assertEquals(2, log.count("a", NOW));
        assertEquals(0, log.count("b", NOW));
        DeathLog back = new DeathLog();
        back.parse(log.render(NOW));
        assertEquals(2, back.count("a", NOW));
        back.parse("not json");
        assertEquals(2, back.count("a", NOW));
    }

    @Test
    void theFileIsWrittenWholeAndOfflinePlayersSurviveARestart(@TempDir Path dir) throws Exception {
        Path export = dir.resolve("proficiency").resolve("export");
        ExportHub.start(export, "Pandowo", NOW, 300_000);
        assertFalse(ExportHub.due(NOW + 29_000, 300_000));
        assertTrue(ExportHub.due(NOW + 30_000, 300_000));
        ExportHub.death(AMMAN, NOW);
        PlayerRow a = PlayerRow.of(AMMAN, "Amman", true, NOW, amman(), 0);
        PlayerRow r = PlayerRow.of(ROWAN, "Rowan", true, NOW, new PlayerSkills(), 0);
        ExportHub.write(NOW + 1, List.of(a, r), List.of(hearth()));
        assertFalse(Files.exists(export.resolve("players.json.tmp")));
        ExportHub.left(NOW + 2, r, List.of(a));
        JsonObject root = JsonParser.parseString(Files.readString(export.resolve("players.json"))).getAsJsonObject();
        JsonArray players = root.getAsJsonArray("players");
        assertEquals(2, players.size());
        JsonObject amman = players.get(0).getAsJsonObject();
        assertEquals("Amman", amman.get("name").getAsString());
        assertTrue(amman.get("online").getAsBoolean());
        assertEquals(1, amman.get("deathsWeek").getAsInt());
        assertFalse(players.get(1).getAsJsonObject().get("online").getAsBoolean());
        ExportHub.stop();

        // A new session on the same folder keeps both, offline, and the death.
        ExportHub.start(export, "Pandowo", NOW + 10, 300_000);
        ExportHub.write(NOW + 11, List.of(), List.of());
        JsonObject again = JsonParser.parseString(Files.readString(export.resolve("players.json"))).getAsJsonObject();
        assertEquals(2, again.getAsJsonArray("players").size());
        assertFalse(again.getAsJsonArray("players").get(0).getAsJsonObject().get("online").getAsBoolean());
        assertEquals(1, again.getAsJsonArray("players").get(0).getAsJsonObject().get("deathsWeek").getAsInt());
        assertNotNull(again.get("hearths"));
        ExportHub.stop();
        // Written when stopped: nothing.
        ExportHub.write(NOW + 12, List.of(a), List.of());
    }

    @Test
    void damagedInputGivesNoPlayersNotAnException() {
        assertTrue(ExportJson.parsePlayers("").isEmpty());
        assertTrue(ExportJson.parsePlayers("{\"version\":99}").isEmpty());
        assertTrue(ExportJson.parsePlayers("{\"version\":1,\"players\":[{}]}").isEmpty());
    }

    @Test
    void oneBadRowIsSkippedAndAnUnreadableFileIsKept(@TempDir Path dir) throws Exception {
        PlayerRow a = PlayerRow.of(AMMAN, "Amman", true, NOW, amman(), 0);
        String good = ExportJson.render(NOW, "W", List.of(a), List.of(hearth()));
        String twoRows = good.replace("\"players\": [", "\"players\": [{\"uuid\": 5},");
        List<PlayerRow> rows = ExportJson.parsePlayers(twoRows);
        assertEquals(1, rows.size());
        assertEquals("Amman", rows.get(0).name());

        Path export = dir.resolve("export");
        Files.createDirectories(export);
        Files.writeString(export.resolve("players.json"), "{\"version\":99,\"players\":[]}");
        ExportHub.start(export, "W", NOW, 300_000);
        assertEquals("{\"version\":99,\"players\":[]}", Files.readString(export.resolve("players.json.bak")));
        ExportHub.stop();
    }

    @Test
    void anEarlyLogoutKeepsTheOldHearthsAndTheClock(@TempDir Path dir) throws Exception {
        Path export = dir.resolve("export");
        ExportHub.start(export, "W", NOW, 300_000);
        PlayerRow a = PlayerRow.of(AMMAN, "Amman", true, NOW, amman(), 0);
        ExportHub.write(NOW, List.of(a), List.of(hearth()));
        ExportHub.stop();

        ExportHub.start(export, "W", NOW + 1000, 300_000);
        ExportHub.left(NOW + 2000, a, List.of());
        JsonObject root = JsonParser.parseString(Files.readString(export.resolve("players.json"))).getAsJsonObject();
        assertEquals(1, root.getAsJsonArray("hearths").size());
        assertFalse(root.getAsJsonArray("players").get(0).getAsJsonObject().get("online").getAsBoolean());
        // The logout did not move the write clock: the first write is still due 30 s after the start.
        assertTrue(ExportHub.due(NOW + 1000 + 30_000, 300_000));
        ExportHub.stop();
    }

    @Test
    void aBackwardsClockStepIsDueAndDeathsBeforeStartAreKept(@TempDir Path dir) throws Exception {
        UUID early = UUID.fromString("00000000-0000-0000-0000-0000000000e1");
        ExportHub.death(early, NOW);
        Path export = dir.resolve("export");
        ExportHub.start(export, "W", NOW, 300_000);
        ExportHub.write(NOW, List.of(PlayerRow.of(early, "Early", true, NOW, new PlayerSkills(), 0)), List.of());
        assertEquals(1, ExportHub.deathsWeek(early, NOW));
        assertFalse(ExportHub.due(NOW + 10, 300_000));
        assertTrue(ExportHub.due(NOW - 3_600_000L, 300_000));
        ExportHub.death(early, NOW + 5);
        ExportHub.flushDeaths();
        assertTrue(Files.readString(export.resolve("deaths.json")).contains(early.toString()));
        ExportHub.stop();
    }

    @Test
    void leavingAllAtOnceWritesOnceAndMarksEveryoneOffline(@TempDir Path dir) throws Exception {
        Path export = dir.resolve("export");
        ExportHub.start(export, "W", NOW, 300_000);
        PlayerRow a = PlayerRow.of(AMMAN, "Amman", true, NOW, amman(), 0);
        PlayerRow r = PlayerRow.of(ROWAN, "Rowan", true, NOW, new PlayerSkills(), 0);
        ExportHub.write(NOW, List.of(a, r), List.of());
        ExportHub.leftAll(NOW + 1, List.of(a, r));
        JsonObject root = JsonParser.parseString(Files.readString(export.resolve("players.json"))).getAsJsonObject();
        for (var row : root.getAsJsonArray("players")) {
            assertFalse(row.getAsJsonObject().get("online").getAsBoolean());
        }
        ExportHub.stop();
    }

    /** Writes the sample the dashboard is built against (a fixture: real shape, invented players). */
    @Test
    void writesTheSampleFixture() throws Exception {
        Path out = Path.of("build", "export-sample");
        ExportHub.start(out, "Pandowo (fixture)", NOW, 300_000);
        PlayerSkills rowan = new PlayerSkills();
        rowan.setLevel(Skill.FARMING, 83);
        rowan.setLevel(Skill.COOKING, 71);
        rowan.setLevel(Skill.HOMEBUILDER, 64);
        rowan.setLevel(Skill.MINING, 20);
        ExportHub.write(NOW, List.of(PlayerRow.of(AMMAN, "Amman", true, NOW, amman(), 1),
                PlayerRow.of(ROWAN, "Rowan", false, NOW - 86_400_000L, rowan, 0)), List.of(hearth()));
        ExportHub.stop();
        assertTrue(Files.size(out.resolve("players.json")) > 1000);
    }
}
