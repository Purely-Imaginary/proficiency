package dev.amman.proficiency.hearth.stages;

import dev.amman.proficiency.hearth.HearthData;
import dev.amman.proficiency.hearth.HearthState;
import dev.amman.proficiency.hearth.net.HearthStatePayload;
import io.netty.buffer.Unpooled;
import net.minecraft.network.FriendlyByteBuf;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The six offerings ride on the state payload's extra (stages lane): a dedicated server's datapack
 * never reaches its clients otherwise, and the client swallows a click with one of them on the Hearth.
 */
class OfferingsWireTest {

    private static HearthStatePayload.Extra roundTrip(HearthStatePayload.Extra extra) {
        HearthStatePayload payload = new HearthStatePayload(HearthStatePayload.ENTER, 5L, "minecraft:overworld", 2, 2,
                false, 0, List.of(), 0, 0, List.of(), 0, false, 0, -1f, 24f, 8, 16, extra);
        FriendlyByteBuf buf = new FriendlyByteBuf(Unpooled.buffer());
        HearthStatePayload.STREAM_CODEC.encode(buf, payload);
        HearthStatePayload back = HearthStatePayload.STREAM_CODEC.decode(buf);
        assertEquals(0, buf.readableBytes(), "the codec read everything it wrote");
        return back.extra();
    }

    @Test
    void theExtraCarriesAllSixOfferings() {
        HearthStatePayload.Extra extra = HearthStatePayload.Extra.of(new HearthState(), HearthData.DEFAULTS, 64);
        List<String> expected = new ArrayList<>();
        for (HearthData.Stage stage : HearthData.DEFAULTS.stages()) {
            expected.add(stage.offering());
        }
        assertEquals(6, extra.offerings().size());
        assertEquals(expected, extra.offerings());
        assertEquals("minecraft:flint_and_steel", extra.offerings().get(0));
    }

    @Test
    void theOfferingsRoundTrip() {
        HearthStatePayload.Extra extra = HearthStatePayload.Extra.of(new HearthState(), HearthData.DEFAULTS, 64);
        assertEquals(extra, roundTrip(extra));
    }

    @Test
    void anExtraBuiltWithoutOfferingsSendsNone() {
        HearthStatePayload.Extra extra = new HearthStatePayload.Extra(List.of(200f), List.of(), List.of("bed"), 50,
                "minecraft:copper_block", 64, 600L);
        assertTrue(extra.offerings().isEmpty());
        assertEquals(extra, roundTrip(extra));
        assertTrue(roundTrip(HearthStatePayload.Extra.NONE).offerings().isEmpty());
    }
}
