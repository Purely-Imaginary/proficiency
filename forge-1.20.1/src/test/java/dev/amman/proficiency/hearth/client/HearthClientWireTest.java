package dev.amman.proficiency.hearth.client;

import dev.amman.proficiency.hearth.HearthData;
import dev.amman.proficiency.hearth.HearthPool;
import dev.amman.proficiency.hearth.HearthState;
import dev.amman.proficiency.hearth.net.HearthStatePayload;
import dev.amman.proficiency.hearth.net.PoolRow;
import io.netty.buffer.Unpooled;
import net.minecraft.network.FriendlyByteBuf;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The client lane's part of the state payload: the extra data the panel and the tracker draw with. */
class HearthClientWireTest {

    private static HearthStatePayload roundTrip(HearthStatePayload value) {
        FriendlyByteBuf buf = new FriendlyByteBuf(Unpooled.buffer());
        HearthStatePayload.STREAM_CODEC.encode(buf, value);
        HearthStatePayload back = HearthStatePayload.STREAM_CODEC.decode(buf);
        assertEquals(0, buf.readableBytes(), "the codec read everything it wrote");
        return back;
    }

    private static HearthState hall() {
        HearthState state = new HearthState();
        state.setStage(4);
        state.setCharacter(20);
        state.setComfortKinds(Set.of("bed", "light", "seat", "carpet", "books", "fire", "plant", "art"));
        state.setCharge(HearthPool.HEARTY, 1500);
        state.setCharge(HearthPool.WORKSHOP, 900);
        state.setCharge(HearthPool.HOMECOMING, 250);
        return state;
    }

    @Test
    void theExtraRoundTrips() {
        HearthState state = hall();
        HearthStatePayload.Extra extra = HearthStatePayload.Extra.of(state, HearthData.DEFAULTS, 64);
        HearthStatePayload s = new HearthStatePayload(HearthStatePayload.PANEL, 77L, "minecraft:overworld", 4, 4,
                false, 8, List.copyOf(state.comfortKinds()), 20, 24, PoolRow.of(state, HearthData.DEFAULTS), 2, true,
                0, 6.5f, 24f, 8, 16, extra);
        assertEquals(s, roundTrip(s));
    }

    @Test
    void theExtraCarriesTheBoonNumbersAtTheEffectiveTier() {
        HearthState state = hall();
        HearthStatePayload.Extra extra = HearthStatePayload.Extra.of(state, HearthData.DEFAULTS, 64);
        assertEquals(List.of(200f, 600f, 1400f, 2800f, 5000f, 8000f), extra.thresholds());
        assertEquals(12, extra.comfortAll().size());
        assertEquals(50, extra.characterMax());
        assertEquals("minecraft:diamond_block", extra.nextOffering(), "stage IV built: V asks for a diamond block");
        assertEquals(64, extra.spacing());
        assertEquals(24_000L, extra.warningTicks());
        assertEquals(HearthPool.COUNT, extra.boons().size());

        HearthStatePayload.Boon hearty = extra.boons().get(HearthPool.HEARTY.ordinal());
        assertEquals("hunger_slow", hearty.boon());
        int tier = state.effectiveTier(HearthPool.HEARTY, HearthData.DEFAULTS);
        assertEquals(3, tier);
        assertEquals(0.22f, hearty.value(), 1e-6);
        assertEquals(0.28f, hearty.next(), 1e-6);

        HearthStatePayload.Boon workshop = extra.boons().get(HearthPool.WORKSHOP.ordinal());
        assertEquals("station_speed", workshop.boon());
        assertEquals(0.15f, workshop.value(), 1e-6);
        assertEquals(0.03f, workshop.extra(), 1e-6, "the refund rides as the second number");

        HearthStatePayload.Boon home = extra.boons().get(HearthPool.HOMECOMING.ordinal());
        assertEquals("readiness", home.boon());
        assertEquals(55f, home.value(), 1e-6, "Warmth fills in 55 s at tier I");
        assertEquals(16f, home.extra(), 1e-6, "Provisioned lasts 16 min at tier I");

        HearthStatePayload.Boon regen = extra.boons().get(HearthPool.REGEN.ordinal());
        assertEquals(0f, regen.value(), 1e-6, "no charge, no boon");
        assertTrue(regen.next() > 0, "the next tier's number is there to show");
    }

    @Test
    void theOldConstructorSendsNoExtra() {
        HearthStatePayload s = new HearthStatePayload(HearthStatePayload.ENTER, 1L, "", 1, 1, false, 0, List.of(), 0,
                0, List.of(), 0, false, 0, -1f, 24f, 8, 16);
        assertEquals(HearthStatePayload.Extra.NONE, s.extra());
        assertEquals(s, roundTrip(s));
    }

    @Test
    void theChatFilterKnowsTheChargeLanesTierUpLine() {
        java.util.UUID who = new java.util.UUID(1, 2);
        assertTrue(HearthChat.isTierUp(dev.amman.proficiency.hearth.charge.ChargeText.tierUp(HearthPool.HEARTY, 3,
                null, List.of())), "the line with nobody named");
        assertTrue(HearthChat.isTierUp(dev.amman.proficiency.hearth.charge.ChargeText.tierUp(HearthPool.WORKSHOP, 6,
                new dev.amman.proficiency.hearth.charge.KeeperRows.Top(who, "Amman", 120, "smithing"), List.of())),
                "the line that names the top feeder");
        org.junit.jupiter.api.Assertions.assertFalse(HearthChat.isTierUp(net.minecraft.network.chat.Component
                .translatable("proficiency.hearth.charge.tier_up.hover")), "another charge line");
        org.junit.jupiter.api.Assertions.assertFalse(HearthChat.isTierUp(net.minecraft.network.chat.Component
                .literal("Hearty reached III")), "a player typing the words");
    }

    @Test
    void theTrackersIconsInitialise() {
        // Static fields initialise in textual order: an icon built before its cache map crashed the first
        // client to touch the tracker (the layout demo's world load, 2026-10-09).
        assertEquals("proficiency:textures/gui/hearth/comfort.png", TrackerHud.COMFORT_ICON.toString());
        assertEquals("proficiency:textures/gui/hearth/pool/hearty.png",
                TrackerHud.poolIcon(HearthPool.HEARTY.ordinal()).toString());
        assertTrue(TrackerHud.icon("stage/4") == TrackerHud.icon("stage/4"), "cached");
    }
}
