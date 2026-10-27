package dev.amman.proficiency.hearth;

import dev.amman.proficiency.config.ProficiencyClientConfig;
import dev.amman.proficiency.config.ProficiencyConfig;
import dev.amman.proficiency.hearth.net.HearthGainPayload;
import dev.amman.proficiency.hearth.net.HearthHudPayload;
import dev.amman.proficiency.hearth.net.HearthLedgerPayload;
import dev.amman.proficiency.hearth.net.HearthPingPayload;
import dev.amman.proficiency.hearth.net.HearthRequestPayload;
import dev.amman.proficiency.hearth.net.HearthStatePayload;
import dev.amman.proficiency.hearth.net.HearthTalesPayload;
import dev.amman.proficiency.hearth.net.HearthTrackerPayload;
import dev.amman.proficiency.hearth.net.PoolRow;
import dev.amman.proficiency.hearth.net.SiegePanelPayload;
import dev.amman.proficiency.homebuilder.net.HomebuilderActionPayload;
import dev.amman.proficiency.homebuilder.net.HomebuilderPayload;
import dev.amman.proficiency.net.ProficiencyNetwork;
import dev.amman.proficiency.net.codec.StreamCodec;
import io.netty.buffer.Unpooled;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.FriendlyByteBuf;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The Forge 1.20.1 Hearth wire and saves (common's {@code HearthWireTest} plus the line's own parts):
 * positions, every payload's codec through the port's {@link StreamCodec} shim, protocol 9, the config
 * defaults, and the two world SavedData files round-tripping their NBT.
 */
class HearthForgeWireTest {

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
            assertEquals(new BlockPos(c[0], c[1], c[2]), BlockPos.of(HearthPos.pack(c[0], c[1], c[2])));
        }
    }

    @Test
    void theProtocolIsEight() {
        assertEquals("15", ProficiencyNetwork.PROTOCOL);
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
        HearthStatePayload withExtra = new HearthStatePayload(HearthStatePayload.UPDATE, 123L, "minecraft:overworld",
                4, 4, false, 2, List.of("bed"), 20, 24, rows, 1, true, 0, 3.5f, 24f, 8, 16,
                HearthStatePayload.Extra.of(state, HearthData.DEFAULTS, 64));
        assertEquals(withExtra, roundTrip(HearthStatePayload.STREAM_CODEC, withExtra));
        // The six offerings (stages fix round 2) ride last on the wire, for the client's click prediction.
        assertEquals(HearthData.TIERS, withExtra.extra().offerings().size());
        assertEquals("minecraft:flint_and_steel", withExtra.extra().offerings().get(0));

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
                HearthHudPayload.FLAG_STREAK_SAFE | HearthHudPayload.FLAG_EDGE_HOME);
        HearthHudPayload hudBack = roundTrip(HearthHudPayload.STREAM_CODEC, hud);
        assertEquals(hud, hudBack);
        assertTrue(hudBack.has(HearthHudPayload.FLAG_STREAK_SAFE));
        assertFalse(hudBack.has(HearthHudPayload.FLAG_SMOULDERING));
        assertEquals(HearthHudPayload.FLAG_EDGE_HOME, hudBack.edge());

        HearthTalesPayload tales = new HearthTalesPayload(HearthTalesPayload.RETOLD, 4, 12.5f, 3200, 4,
                List.of("biome.minecraft.desert"), "A long walk");
        assertEquals(tales, roundTrip(HearthTalesPayload.STREAM_CODEC, tales));

        HearthRequestPayload r = new HearthRequestPayload(HearthRequestPayload.LEDGER, 42L);
        assertEquals(r, roundTrip(HearthRequestPayload.STREAM_CODEC, r));

        HomebuilderPayload h = new HomebuilderPayload(HomebuilderPayload.SHARED, 24_000L, 1, "Amman");
        assertEquals(h, roundTrip(HomebuilderPayload.STREAM_CODEC, h));
        HomebuilderActionPayload a = new HomebuilderActionPayload(HomebuilderActionPayload.HOMEWARD);
        assertEquals(a, roundTrip(HomebuilderActionPayload.STREAM_CODEC, a));
    }

    /** Every payload names itself as on 1.21.1 ({@code proficiency:<id>}), so logs and docs agree. */
    @Test
    void payloadIdsMatchTheOtherLines() throws ReflectiveOperationException {
        Object[][] ids = {{HearthStatePayload.class, "hearth_state"}, {HearthTrackerPayload.class, "hearth_tracker"},
                {HearthGainPayload.class, "hearth_gain"}, {HearthLedgerPayload.class, "hearth_ledger"},
                {SiegePanelPayload.class, "siege_panel"}, {HearthPingPayload.class, "hearth_ping"},
                {HearthHudPayload.class, "hearth_hud"}, {HearthTalesPayload.class, "hearth_tales"},
                {HearthRequestPayload.class, "hearth_request"}, {HomebuilderPayload.class, "homebuilder"},
                {HomebuilderActionPayload.class, "homebuilder_action"}};
        for (Object[] id : ids) {
            Field field = ((Class<?>) id[0]).getField("ID");
            assertEquals(id[1], field.get(null), ((Class<?>) id[0]).getSimpleName());
        }
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
        assertEquals(dev.amman.proficiency.skill.HomebuilderMath.DEFAULT_SHARE, ProficiencyConfig.homeShare());
        assertEquals(dev.amman.proficiency.skill.HomebuilderMath.DEFAULT_HOUSE_PROUD_TICKS,
                ProficiencyConfig.houseProudTicks());
        assertFalse(ProficiencyClientConfig.hearthTracker());
        assertEquals(0.5f, ProficiencyClientConfig.hearthTrackerY());
        assertTrue(ProficiencyClientConfig.hearthShowRange());
        assertTrue(ProficiencyClientConfig.hearthSiegePanel());
        assertEquals(0.06f, ProficiencyClientConfig.hearthSiegePanelY());
        long rows = ProficiencyClientConfig.entries().stream().filter(e -> e.section().equals("hearth")).count();
        assertEquals(5, rows, "the in-game settings screen shows the five Hearth settings");
    }

    @Test
    void theWorldDataSavesAndLoads() {
        HearthWorldData world = new HearthWorldData();
        world.addPlayTicks(72_000);
        long page = world.chronicle().write(UUID.fromString("00000000-0000-0000-0000-0000000000a1"), "dim|1,2,3",
                12.5, 100L).id();
        world.mirrorSiegeDue("minecraft:overworld|1,2,3", 48_000L);
        HearthWorldData back = HearthWorldData.load(world.save(new CompoundTag()));
        assertEquals(72_000L, back.playTicks());
        assertEquals(48_000L, back.siegeDue("minecraft:overworld|1,2,3"));
        assertEquals(HearthState.NEVER, back.siegeDue("minecraft:overworld|9,9,9"));
        assertTrue(back.chronicle().page(page).isPresent(), "the Chronicle page came back");

        HearthIndexData index = new HearthIndexData();
        index.add(new BlockPos(10, 64, -20));
        index.add(new BlockPos(-300, 70, 5));
        HearthIndexData indexBack = HearthIndexData.load(index.save(new CompoundTag()));
        assertTrue(indexBack.contains(new BlockPos(10, 64, -20)) && indexBack.contains(new BlockPos(-300, 70, 5)));
        assertFalse(indexBack.contains(new BlockPos(0, 0, 0)));
    }
}
