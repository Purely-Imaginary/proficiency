package dev.amman.proficiency.hearth;

import dev.amman.proficiency.config.ProficiencyClientConfig;
import dev.amman.proficiency.config.ProficiencyConfig;
import dev.amman.proficiency.hearth.net.HearthGainPayload;
import dev.amman.proficiency.hearth.net.HearthHudPayload;
import dev.amman.proficiency.hearth.net.HearthLedgerPayload;
import dev.amman.proficiency.hearth.net.HearthPingPayload;
import dev.amman.proficiency.hearth.net.HearthRequestPayload;
import dev.amman.proficiency.hearth.net.HearthStatePayload;
import dev.amman.proficiency.hearth.net.HearthTrackerPayload;
import dev.amman.proficiency.hearth.net.PoolRow;
import dev.amman.proficiency.hearth.net.SiegePanelPayload;
import dev.amman.proficiency.net.ProficiencyNetwork;
import io.netty.buffer.Unpooled;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The Hearth's wire and config: positions, every payload's codec, the defaults, the protocol. */
class HearthWireTest {

    private static <T> T roundTrip(StreamCodec<FriendlyByteBuf, T> codec, T value) {
        FriendlyByteBuf buf = new FriendlyByteBuf(Unpooled.buffer());
        codec.encode(buf, value);
        T back = codec.decode(buf);
        assertEquals(0, buf.readableBytes(), "the codec read everything it wrote");
        return back;
    }

    @Test
    void corePositionsPackLikeBlockPos() {
        int[][] cases = {{0, 0, 0}, {1, -64, -1}, {-30_000_000, 319, 29_999_999}, {123, 64, -456}, {-1, -1, -1}};
        for (int[] c : cases) {
            assertEquals(BlockPos.asLong(c[0], c[1], c[2]), HearthPos.pack(c[0], c[1], c[2]));
            BlockPos back = BlockPos.of(HearthPos.pack(c[0], c[1], c[2]));
            assertEquals(new BlockPos(c[0], c[1], c[2]), back);
        }
    }

    @Test
    void theProtocolIsEight() {
        assertEquals(9, ProficiencyNetwork.PROTOCOL);
    }

    @Test
    void everyPayloadRoundTrips() {
        HearthState state = new HearthState();
        state.setCharge(HearthPool.HEARTY, 1500);
        state.setStage(4);
        state.setCharacter(20);
        state.setComfortKinds(Set.of("bed", "light"));
        List<PoolRow> rows = PoolRow.of(state, HearthData.DEFAULTS);
        assertEquals(HearthPool.COUNT, rows.size());
        assertEquals(3, rows.get(HearthPool.HEARTY.ordinal()).chargeTier());

        HearthStatePayload s = new HearthStatePayload(HearthStatePayload.PANEL, 123L, "minecraft:overworld", 4, 4,
                false, 2, List.of("bed", "light"), 20, 24, rows, 1, true, 0, 3.5f, 24f, 8, 16);
        assertEquals(s, roundTrip(HearthStatePayload.STREAM_CODEC, s));

        HearthTrackerPayload t = new HearthTrackerPayload(99L, rows);
        assertEquals(t, roundTrip(HearthTrackerPayload.STREAM_CODEC, t));

        HearthGainPayload g = new HearthGainPayload(List.of(new HearthGainPayload.Gain(0, 0.5f, 7),
                new HearthGainPayload.Gain(4, 2f, -1)));
        assertEquals(g, roundTrip(HearthGainPayload.STREAM_CODEC, g));

        HearthLedgerPayload l = new HearthLedgerPayload(5L, List.of(new HearthLedgerPayload.Row(
                UUID.fromString("00000000-0000-0000-0000-0000000000a1"), "Amman", List.of(1f, 2f, 0f, 0f, 3f),
                List.of(10f, 20f, 0f, 0f, 30f), List.of(new HearthLedgerPayload.Source("mining", 1f)),
                List.of(new HearthLedgerPayload.Source("tales", 30f)), 4, 1)));
        assertEquals(l, roundTrip(HearthLedgerPayload.STREAM_CODEC, l));

        SiegePanelPayload p = new SiegePanelPayload(SiegePanelPayload.WAVES, 7L, "minecraft:overworld", 48_000L, 2, 3,
                7, 0, 2, 3);
        assertEquals(p, roundTrip(SiegePanelPayload.STREAM_CODEC, p));

        HearthPingPayload ping = new HearthPingPayload(3, 11L, "minecraft:overworld", 1200);
        assertEquals(ping, roundTrip(HearthPingPayload.STREAM_CODEC, ping));

        HearthHudPayload hud = new HearthHudPayload(true, 11L, 0.5f, 1f / 60f, 0.2f, 100L, 18_100L, 12.5f,
                HearthHudPayload.FLAG_STREAK_SAFE);
        HearthHudPayload hudBack = roundTrip(HearthHudPayload.STREAM_CODEC, hud);
        assertEquals(hud, hudBack);
        assertTrue(hudBack.has(HearthHudPayload.FLAG_STREAK_SAFE));
        assertFalse(hudBack.has(HearthHudPayload.FLAG_SMOULDERING));

        HearthRequestPayload r = new HearthRequestPayload(HearthRequestPayload.LEDGER, 42L);
        assertEquals(r, roundTrip(HearthRequestPayload.STREAM_CODEC, r));
    }

    @Test
    void configDefaultsMatchTheSpec() {
        assertTrue(ProficiencyConfig.hearthEnabled());
        assertEquals(24.0, ProficiencyConfig.hearthRange());
        assertEquals(8, ProficiencyConfig.hearthRangeBelow());
        assertEquals(16, ProficiencyConfig.hearthRangeAbove());
        assertEquals(64, ProficiencyConfig.hearthMinSpacing());
        assertTrue(ProficiencyConfig.hearthSieges());
        assertEquals(HearthGeometry.Range.DEFAULT, HearthRange.range());
        assertFalse(ProficiencyClientConfig.hearthTracker());
        assertEquals(0.5f, ProficiencyClientConfig.hearthTrackerY());
        assertTrue(ProficiencyClientConfig.hearthShowRange());
        assertTrue(ProficiencyClientConfig.hearthSiegePanel());
    }
}
