package dev.amman.proficiency;

import dev.amman.proficiency.skill.XpFactors;
import dev.amman.proficiency.skill.XpFeedRecorder;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The factor list encoding and the recording CSV's line format. */
class XpFeedFactorsTest {

    @Test
    void onlyFactorsThatAreNotOneAreKept() {
        List<XpFactors.Factor> list = new ArrayList<>();
        XpFactors.add(list, XpFactors.RATE, 1.0);
        XpFactors.add(list, XpFactors.TEMPO, 1.3);
        XpFactors.add(list, XpFactors.PERK, 1.0001);
        XpFactors.add(list, XpFactors.STREAK, Double.NaN);
        XpFactors.add(list, XpFactors.TIER, 2.0);
        assertEquals(2, list.size());
        assertEquals("tempo=1.300;tier=2.000", XpFactors.encode(list));
    }

    @Test
    void encodeAndDecodeRoundTrip() {
        List<XpFactors.Factor> list = List.of(new XpFactors.Factor("tempo", 1.3f),
                new XpFactors.Factor("company", 1.1f));
        List<XpFactors.Factor> back = XpFactors.decode(XpFactors.encode(list));
        assertEquals(2, back.size());
        assertEquals("company", back.get(1).id());
        assertEquals(1.1f, back.get(1).value(), 1e-3);
    }

    @Test
    void decodeSkipsBadPieces() {
        assertEquals(1, XpFactors.decode("junk;tempo=1.5;x=abc;=2").size());
        assertTrue(XpFactors.decode("").isEmpty());
        assertTrue(XpFactors.decode(null).isEmpty());
    }

    @Test
    void csvLineHasElevenColumnsAndQuotesOddFields() {
        String line = XpFeedRecorder.line(1000L, 24000L, "mining", "block.minecraft.iron_ore", 1.5f,
                2.25f, List.of(new XpFactors.Factor("tempo", 1.3f), new XpFactors.Factor("streak", 1.12f)),
                "minecraft:overworld", 10, 64, -20);
        assertEquals("1000,24000,mining,block.minecraft.iron_ore,1.5000,2.2500,"
                + "tempo=1.300;streak=1.120,minecraft:overworld,10,64,-20", line);
        assertEquals(XpFeedRecorder.HEADER.split(",").length, line.split(",").length);
        assertEquals("\"a,b\"", XpFeedRecorder.field("a,b"));
        assertEquals("\"say \"\"hi\"\"\"", XpFeedRecorder.field("say \"hi\""));
        assertEquals("", XpFeedRecorder.field(null));
    }

    @Test
    void fileNameIsSafeAndStamped() {
        assertEquals("Steve_1-20260929-153045-123.csv",
                XpFeedRecorder.fileName("Steve 1", LocalDateTime.of(2026, 9, 29, 15, 30, 45, 123_000_000)));
    }
}
