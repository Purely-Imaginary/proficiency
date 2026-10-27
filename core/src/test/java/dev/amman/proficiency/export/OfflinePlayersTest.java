package dev.amman.proficiency.export;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;
import com.mojang.serialization.JsonOps;
import dev.amman.proficiency.skill.PlayerSkills;
import dev.amman.proficiency.skill.Skill;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.zip.GZIPOutputStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Reading Proficiency's data out of a player's .dat without Minecraft: the three loaders' layouts, caching, the hub merge. */
class OfflinePlayersTest {

    private static final UUID A = UUID.fromString("00000000-0000-0000-0000-0000000000a1");
    private static final UUID B = UUID.fromString("00000000-0000-0000-0000-0000000000b2");
    private static final UUID C = UUID.fromString("00000000-0000-0000-0000-0000000000c3");

    private static PlayerSkills skills(int mining) {
        PlayerSkills s = new PlayerSkills();
        s.setLevel(Skill.MINING, mining);
        s.setLevel(Skill.COOKING, 7);
        return s;
    }

    /** A player file the way a game writes it: a tag with other things around the holder, in the holder's key. */
    private static byte[] playerFile(String holder, PlayerSkills s) throws IOException {
        JsonElement tag = PlayerSkills.CODEC.encodeStart(JsonOps.INSTANCE, s).getOrThrow(IllegalStateException::new);
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (DataOutputStream out = new DataOutputStream(new GZIPOutputStream(bytes))) {
            out.writeByte(10);
            out.writeUTF("");
            // noise before: a string, a list of compounds (inventory), an int array, a long array, a byte array
            out.writeByte(8); out.writeUTF("Dimension"); out.writeUTF("minecraft:overworld");
            out.writeByte(9); out.writeUTF("Inventory"); out.writeByte(10); out.writeInt(2);
            for (int i = 0; i < 2; i++) {
                out.writeByte(1); out.writeUTF("Slot"); out.writeByte(i);
                out.writeByte(8); out.writeUTF("id"); out.writeUTF("minecraft:stone");
                out.writeByte(0);
            }
            out.writeByte(11); out.writeUTF("UUID"); out.writeInt(4); for (int i = 0; i < 4; i++) { out.writeInt(i); }
            out.writeByte(12); out.writeUTF("Longs"); out.writeInt(1); out.writeLong(5L);
            out.writeByte(7); out.writeUTF("Bytes"); out.writeInt(3); out.write(new byte[3]);
            // a decoy holder without our tag, then the real one
            out.writeByte(10); out.writeUTF("neoforge:attachments".equals(holder) ? "ForgeCaps" : "neoforge:attachments");
            out.writeByte(8); out.writeUTF("other:mod"); out.writeUTF("x");
            out.writeByte(0);
            out.writeByte(10); out.writeUTF(holder);
            out.writeByte(10); out.writeUTF("proficiency:skills");
            writeBody(out, tag.getAsJsonObject());
            out.writeByte(0);
            out.writeUTF("");
            out.writeByte(0);
        }
        return bytes.toByteArray();
    }

    private static void writeBody(DataOutputStream out, JsonObject o) throws IOException {
        for (Map.Entry<String, JsonElement> e : o.entrySet()) {
            JsonElement v = e.getValue();
            if (v.isJsonObject()) {
                out.writeByte(10); out.writeUTF(e.getKey());
                writeBody(out, v.getAsJsonObject());
            } else if (v.isJsonArray()) {
                JsonArray a = v.getAsJsonArray();
                out.writeByte(9); out.writeUTF(e.getKey());
                if (a.isEmpty()) {
                    out.writeByte(0); out.writeInt(0);
                } else {
                    JsonElement first = a.get(0);
                    out.writeByte(first.isJsonObject() ? 10 : first.getAsJsonPrimitive().isString() ? 8 : 6);
                    out.writeInt(a.size());
                    for (JsonElement x : a) {
                        if (x.isJsonObject()) {
                            writeBody(out, x.getAsJsonObject());
                        } else if (x.getAsJsonPrimitive().isString()) {
                            out.writeUTF(x.getAsString());
                        } else {
                            out.writeDouble(x.getAsDouble());
                        }
                    }
                }
            } else {
                JsonPrimitive p = v.getAsJsonPrimitive();
                if (p.isString()) {
                    out.writeByte(8); out.writeUTF(e.getKey()); out.writeUTF(p.getAsString());
                } else if (p.isBoolean()) {
                    out.writeByte(1); out.writeUTF(e.getKey()); out.writeByte(p.getAsBoolean() ? 1 : 0);
                } else if (p.getAsDouble() == Math.rint(p.getAsDouble()) && Math.abs(p.getAsDouble()) < 1e9) {
                    out.writeByte(3); out.writeUTF(e.getKey()); out.writeInt(p.getAsInt());
                } else {
                    out.writeByte(6); out.writeUTF(e.getKey()); out.writeDouble(p.getAsDouble());
                }
            }
        }
        out.writeByte(0);
    }

    private static void put(Path dir, UUID id, byte[] data) throws IOException {
        Files.write(dir.resolve(id + ".dat"), data);
    }

    @Test
    void everyLoadersLayoutIsRead(@TempDir Path dir) throws IOException {
        put(dir, A, playerFile("neoforge:attachments", skills(42)));
        put(dir, B, playerFile("fabric:attachments", skills(17)));
        put(dir, C, playerFile("ForgeCaps", skills(99)));
        List<PlayerRow> rows = new OfflinePlayers().scan(dir, id -> id.equals(A) ? "Amman" : null);
        assertEquals(3, rows.size());
        for (PlayerRow r : rows) {
            assertFalse(r.online());
            int expected = r.uuid().equals(A.toString()) ? 42 : r.uuid().equals(B.toString()) ? 17 : 99;
            assertEquals(expected, r.levels()[Skill.MINING.ordinal()]);
            assertEquals(7, r.levels()[Skill.COOKING.ordinal()]);
            assertEquals(r.uuid().equals(A.toString()) ? "Amman" : r.uuid(), r.name(), "name or the uuid as fallback");
        }
    }

    @Test
    void aFileWithoutTheTagOrBrokenIsSkipped(@TempDir Path dir) throws IOException {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (DataOutputStream out = new DataOutputStream(new GZIPOutputStream(bytes))) {
            out.writeByte(10); out.writeUTF(""); out.writeByte(8); out.writeUTF("Dimension"); out.writeUTF("x"); out.writeByte(0);
        }
        put(dir, A, bytes.toByteArray());
        put(dir, B, new byte[] {1, 2, 3});
        Files.writeString(dir.resolve("not-a-uuid.dat"), "x");
        put(dir, C, playerFile("neoforge:attachments", skills(5)));
        List<PlayerRow> rows = new OfflinePlayers().scan(dir, id -> null);
        assertEquals(1, rows.size());
        assertEquals(C.toString(), rows.get(0).uuid());
        assertTrue(new OfflinePlayers().scan(dir.resolve("missing"), id -> null).isEmpty());
    }

    @Test
    void aFileIsReadAgainOnlyWhenItsTimeChanges(@TempDir Path dir) throws IOException {
        Path file = dir.resolve(A + ".dat");
        put(dir, A, playerFile("neoforge:attachments", skills(10)));
        Files.setLastModifiedTime(file, FileTime.fromMillis(1_000_000L));
        OfflinePlayers scanner = new OfflinePlayers();
        assertEquals(10, scanner.scan(dir, id -> null).get(0).levels()[Skill.MINING.ordinal()]);
        // Same time and size, other content (zeros: a re-read would find nothing): the cache answers.
        put(dir, A, new byte[(int) Files.size(file)]);
        Files.setLastModifiedTime(file, FileTime.fromMillis(1_000_000L));
        assertEquals(10, scanner.scan(dir, id -> null).get(0).levels()[Skill.MINING.ordinal()]);
        put(dir, A, playerFile("neoforge:attachments", skills(11)));
        Files.setLastModifiedTime(file, FileTime.fromMillis(2_000_000L));
        PlayerRow row = scanner.scan(dir, id -> null).get(0);
        assertEquals(11, row.levels()[Skill.MINING.ordinal()]);
        assertEquals(2_000_000L, row.seenMs());
    }

    @Test
    void theHubKeepsOfflinePlayersAndNeverLetsAnOldFileOverwriteALiveRow(@TempDir Path dir) throws IOException {
        Path export = dir.resolve("export");
        long now = 1_800_000_000_000L;
        ExportHub.start(export, "w", now, 300_000L);
        try {
            PlayerRow fileA = PlayerRow.of(A, A.toString(), false, now - 5000, skills(3), 0);
            PlayerRow fileB = PlayerRow.of(B, "Rowan", false, now - 5000, skills(9), 0);
            ExportHub.mergeOffline(List.of(fileA, fileB));
            // A is online now with a newer row; the old file row must not replace it.
            PlayerRow live = PlayerRow.of(A, "Amman", true, now, skills(50), 0);
            ExportHub.write(now, List.of(live), List.of());
            ExportHub.mergeOffline(List.of(fileA, fileB));
            ExportHub.write(now + 1, List.of(live), List.of());
            com.google.gson.JsonArray players = com.google.gson.JsonParser
                    .parseString(Files.readString(export.resolve(ExportHub.FILE))).getAsJsonObject().getAsJsonArray("players");
            assertEquals(2, players.size());
            for (JsonElement e : players) {
                JsonObject o = e.getAsJsonObject();
                boolean isA = o.get("uuid").getAsString().equals(A.toString());
                assertEquals(isA, o.get("online").getAsBoolean());
                assertEquals(isA ? "Amman" : "Rowan", o.get("name").getAsString());
            }
            // A real name arriving later replaces the uuid fallback, and a uuid fallback never replaces a name.
            ExportHub.mergeOffline(List.of(PlayerRow.of(B, B.toString(), false, now + 10_000, skills(9), 0)));
            ExportHub.write(now + 2, List.of(live), List.of());
            assertTrue(Files.readString(export.resolve(ExportHub.FILE)).contains("\"name\": \"Rowan\""));
        } finally {
            ExportHub.stop();
        }
    }
}
