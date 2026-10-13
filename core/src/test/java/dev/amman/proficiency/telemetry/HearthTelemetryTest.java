package dev.amman.proficiency.telemetry;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The Hearth's telemetry kinds: their own "hearth" lines, player and source fields, and the home XP kind. */
class HearthTelemetryTest {

    private static final UUID A = UUID.fromString("00000000-0000-0000-0000-0000000000a1");

    private static List<JsonObject> parse(List<String> lines) {
        List<JsonObject> out = new ArrayList<>();
        for (String line : lines) {
            out.add(JsonParser.parseString(line).getAsJsonObject());
        }
        return out;
    }

    @Test
    void everyKindOfTheSpecIsRegistered() {
        for (String kind : new String[] {"hearth_charge", "hearth_boon", "hearth_warm", "streak_paused",
                "hearth_absorb", "hearth_tales", "hearth_siege", "tale_rating", "homeward"}) {
            assertTrue(Telemetry.HEARTH_KINDS.contains(kind), kind);
        }
    }

    @Test
    void chargeLinesCarryPlayerPoolAndSource() {
        Telemetry t = new Telemetry();
        t.hearth(A, "Amman", Telemetry.HEARTH_CHARGE, "hearty", "mining", 1.5);
        t.hearth(A, "Amman", Telemetry.HEARTH_CHARGE, "hearty", "mining", 2.5);
        t.hearth(null, null, Telemetry.HEARTH_SIEGE, "rolls", "", 1);
        t.hearth(A, "Amman", "made_up_kind", "x", "y", 1);
        assertFalse(t.isEmpty());
        List<JsonObject> rows = parse(t.drain(1000, ""));
        assertEquals("meta", rows.get(0).get("type").getAsString());
        List<JsonObject> hearth = new ArrayList<>();
        for (JsonObject row : rows) {
            if ("hearth".equals(row.get("type").getAsString())) {
                hearth.add(row);
            }
        }
        assertEquals(2, hearth.size());
        JsonObject charge = hearth.get(0);
        assertEquals("hearth_charge", charge.get("kind").getAsString());
        assertEquals("Amman", charge.get("player").getAsString());
        assertEquals(A.toString(), charge.get("uuid").getAsString());
        assertEquals("hearty", charge.get("label").getAsString());
        assertEquals("mining", charge.get("source").getAsString());
        assertEquals(2, charge.get("n").getAsInt());
        assertEquals(4.0, charge.get("value").getAsDouble(), 1e-9);
        JsonObject siege = hearth.get(1);
        assertFalse(siege.has("player"));
        assertTrue(t.isEmpty());
    }

    @Test
    void homebuildersShareIsItsOwnPassiveKind() {
        assertEquals(Telemetry.KIND_HOME, Telemetry.kind("proficiency.xplog.source.home"));
        assertTrue(Telemetry.isPassive(Telemetry.KIND_HOME));
        // Other sources are unchanged.
        assertEquals(Telemetry.KIND_SHARE, Telemetry.kind("proficiency.xplog.source.darkness"));
    }
}
