package dev.amman.proficiency.hearth;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.amman.proficiency.hearth.net.HearthTalesPayload;
import io.netty.buffer.Unpooled;
import net.minecraft.network.FriendlyByteBuf;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The tales lane's wire format and its strings: one payload, and every key in both shipped languages. */
class HearthTalesWireTest {

    private static final Path LANG = Path.of("src/main/resources/assets/proficiency/lang");

    /** Every key the lane's Java reads (TalesText, ChronicleCommand, TalesPayInHud), without the common prefix. */
    private static final List<String> KEYS = List.of(
            "paid.both", "paid.walked", "paid.places", "paid.plain", "kept", "lost", "allpools", "capped",
            "feast.started", "feast.started.charge", "feast.joined", "feast.joined.charge",
            "bell.alarm", "bell.dinner", "bell.come_home", "bell.cooldown", "bell.alarm.chat", "bell.dinner.chat",
            "bell.come_home.chat", "bell.come_home.chat.far", "bell.compass",
            "chronicle.filed", "chronicle.filed.plain", "retold", "retold.untitled",
            "command.unknown", "command.already", "command.deferred", "command.nohearth", "command.rated",
            "panel.paid", "panel.chronicle", "panel.retold", "panel.blocks");

    private static HearthTalesPayload roundTrip(HearthTalesPayload payload) {
        FriendlyByteBuf buf = new FriendlyByteBuf(Unpooled.buffer());
        HearthTalesPayload.STREAM_CODEC.encode(buf, payload);
        HearthTalesPayload back = HearthTalesPayload.STREAM_CODEC.decode(buf);
        assertEquals(0, buf.readableBytes(), "the codec read everything it wrote");
        return back;
    }

    @Test
    void theTalesPayloadRoundTrips() {
        HearthTalesPayload paid = new HearthTalesPayload(HearthTalesPayload.PAID, HearthPool.HOMECOMING.ordinal(), 142.5f,
                3200, -1, List.of("structure.minecraft.stronghold", "dimension.minecraft.the_nether"), "");
        assertEquals(paid, roundTrip(paid));
        HearthTalesPayload retold = new HearthTalesPayload(HearthTalesPayload.RETOLD, 4, 20f, 0, 4, List.of(),
                "The stronghold at dusk");
        assertEquals(retold, roundTrip(retold));
        HearthTalesPayload page = new HearthTalesPayload(HearthTalesPayload.CHRONICLE, 4, 25f, 0, -1, List.of(), "");
        assertEquals(page, roundTrip(page));
    }

    @Test
    void aPayloadFromAFutureVersionWithTooManyPlacesStillDecodes() {
        List<String> many = new ArrayList<>();
        for (int i = 0; i < 10; i++) {
            many.add("biome.minecraft.b" + i);
        }
        HearthTalesPayload back = roundTrip(new HearthTalesPayload(0, 4, 1f, 5, -1, many, ""));
        assertEquals(HearthTalesPayload.MAX_PLACES, back.places().size(), "at most three places cross the wire");
    }

    @Test
    void theTalesPayloadHasItsOwnChannelAndIsAPlayToClientOnly() {
        assertEquals("proficiency:hearth_tales", HearthTalesPayload.TYPE.id().toString());
    }

    // ---- The strings ------------------------------------------------------------------------------

    private static JsonObject lang(String file) throws IOException {
        try (Reader reader = Files.newBufferedReader(LANG.resolve(file), StandardCharsets.UTF_8)) {
            return JsonParser.parseReader(reader).getAsJsonObject();
        }
    }

    private static Set<String> placeholders(String text) {
        Set<String> out = new TreeSet<>();
        Matcher m = Pattern.compile("%(\\d+\\$)?s").matcher(text);
        while (m.find()) {
            out.add(m.group(1) == null ? "%s" : m.group(1));
        }
        return out;
    }

    @Test
    void everyTalesKeyIsInEnglishAndPolishWithTheSamePlaceholders() throws IOException {
        JsonObject en = lang("en_us.json");
        JsonObject pl = lang("pl_pl.json");
        for (String suffix : KEYS) {
            String key = "proficiency.hearth.tales." + suffix;
            assertTrue(en.has(key), "en_us lacks " + key);
            assertTrue(pl.has(key), "pl_pl lacks " + key);
            String english = en.get(key).getAsString();
            String polish = pl.get(key).getAsString();
            assertFalse(english.isBlank() || polish.isBlank(), key);
            assertEquals(placeholders(english), placeholders(polish), "the placeholders of " + key + " differ");
            assertFalse(english.contains("\u2014") || polish.contains("\u2014"), "no em dash in " + key);
            assertFalse(english.contains("`") || polish.contains("`"), "no backtick in " + key);
        }
    }

    @Test
    void theHomecomingLineHasAVariantForEveryTripShape() throws IOException {
        JsonObject en = lang("en_us.json");
        // paid.* are all called with (blocks, places, amount, pool): every variant may use any of the four.
        for (String shape : List.of("paid.both", "paid.walked", "paid.places", "paid.plain")) {
            String text = en.get("proficiency.hearth.tales." + shape).getAsString();
            assertTrue(text.contains("%3$s") && text.contains("%4$s"), shape + " shows what the Hearth took");
        }
    }
}
