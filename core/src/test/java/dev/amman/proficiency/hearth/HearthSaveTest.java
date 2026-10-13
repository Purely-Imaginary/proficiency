package dev.amman.proficiency.hearth;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.mojang.serialization.JsonOps;
import org.junit.jupiter.api.Test;

import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The Hearth and player saves, positions, the range cylinder and the per-dimension index. */
class HearthSaveTest {

    private static final HearthData D = HearthData.DEFAULTS;
    private static final UUID A = UUID.fromString("00000000-0000-0000-0000-0000000000a1");

    private static <T> T roundTrip(com.mojang.serialization.Codec<T> codec, T value) {
        JsonElement json = codec.encodeStart(JsonOps.INSTANCE, value).getOrThrow();
        return codec.parse(JsonOps.INSTANCE, json).getOrThrow();
    }

    @Test
    void aHearthSurvivesASave() {
        HearthState state = new HearthState();
        state.setCharge(HearthPool.HEARTY, 1500);
        state.setCharge(HearthPool.HOMECOMING, 42.5);
        state.addCapped(HearthPool.WORKSHOP, 120, 6000, D);
        state.setStage(4);
        state.setHighestOffering(4);
        state.setSmouldering(true);
        state.onLit(100, 5000);
        state.setComfortKinds(Set.of("bed", "light"));
        state.markComfortSeen("bed");
        state.setCharacter(17);
        state.markCharacterSeen("minecraft:oak_planks");
        state.setSieged(true);
        state.setSiegePhase(SiegeRules.Phase.COUNTDOWN);
        state.setSiegeDue(99_000);
        state.setDefenceStreak(3);
        state.setLastAlarm(777);
        state.ledger().add(A, "Amman", HearthPool.HEARTY, "mining", 15, 20);
        state.ledger().addSiegeKill(A, "Amman");
        state.holds().add(HearthPos.pack(3, 70, -4), new HoldQueue.Held(A, "Amman", HearthPool.FORTIFIED, "masonry",
                "minecraft:stone_bricks", 0.4, 12_000));

        HearthState loaded = roundTrip(HearthState.CODEC, state);
        assertEquals(1500, loaded.charge(HearthPool.HEARTY), 1e-9);
        assertEquals(42.5, loaded.charge(HearthPool.HOMECOMING), 1e-9);
        assertEquals(120, loaded.charge(HearthPool.WORKSHOP), 1e-9);
        assertEquals(180, loaded.cap(HearthPool.WORKSHOP).room(6000, 300), 1e-9);
        assertEquals(4, loaded.stage());
        assertEquals(4, loaded.highestOffering());
        assertTrue(loaded.smouldering());
        assertEquals(100, loaded.litAt());
        assertEquals(5000, loaded.lastSiegeEndPlay());
        assertEquals(2, loaded.comfort());
        assertTrue(loaded.comfortSeen().contains("bed"));
        assertEquals(17, loaded.character());
        assertFalse(loaded.markCharacterSeen("minecraft:oak_planks"));
        assertTrue(loaded.sieged());
        assertEquals(SiegeRules.Phase.COUNTDOWN, loaded.siegePhase());
        assertEquals(99_000, loaded.siegeDue());
        assertEquals(3, loaded.defenceStreak());
        assertEquals(777, loaded.lastAlarm());
        assertEquals(15, loaded.ledger().total(A, null, HearthLedger.Window.WEEK), 1e-9);
        assertEquals(1, loaded.ledger().get(A).siegeKills());
        assertEquals("Amman", loaded.ledger().get(A).name());
        assertEquals(1, loaded.holds().size());
        assertEquals(0.4, loaded.holds().pending(HearthPool.FORTIFIED), 1e-9);
    }

    @Test
    void anEmptySaveLoadsAsANewHearth() {
        HearthState state = HearthState.CODEC.parse(JsonOps.INSTANCE, new JsonObject()).getOrThrow();
        assertEquals(0, state.stage());
        assertEquals(HearthState.NEVER, state.litAt());
        assertEquals(SiegeRules.Phase.IDLE, state.siegePhase());
        assertFalse(state.sieged());
    }

    @Test
    void smoulderingTurnsTheBoonsOff() {
        HearthState state = new HearthState();
        state.setCharge(HearthPool.HEARTY, 9000);
        state.setStage(6);
        state.setCharacter(50);
        state.setComfortKinds(Set.copyOf(D.comfort().kinds()));
        assertEquals(6, state.effectiveTier(HearthPool.HEARTY, D));
        state.setSmouldering(true);
        assertEquals(0, state.effectiveTier(HearthPool.HEARTY, D));
        assertEquals(0, state.maxEffectiveTier(D));
    }

    @Test
    void aPlayerSurvivesASave() {
        PlayerHearth player = new PlayerHearth();
        player.setWarmth(0.75);
        player.seenBy(HearthPos.pack(10, 64, -20), "minecraft:overworld", 500);
        player.addTales(12.5);
        player.notePaid(30);
        player.provision(0.22, 600, 18_600);
        player.setAbsorbModifier(12);
        player.setHomewardDay(19);
        player.setMagnetOff(true);
        player.markMilestone("10,64,-20|stage:3");
        player.setMark("tales.storyteller", 3);
        player.setFlag("effects.cue", true);
        PlayerHearth loaded = roundTrip(PlayerHearth.CODEC, player);
        assertEquals(0.75f, loaded.warmth());
        assertTrue(loaded.inRange("minecraft:overworld", 540));
        assertFalse(loaded.inRange("minecraft:overworld", 541));
        assertFalse(loaded.inRange("minecraft:the_nether", 520));
        assertEquals(HearthPos.pack(10, 64, -20), loaded.inRangePos());
        assertEquals(12.5, loaded.tales(), 1e-9);
        assertTrue(loaded.chronicleOpen());
        assertEquals(0.22, loaded.provisionedAt(600), 1e-6);
        assertEquals(12f, loaded.absorbModifier());
        assertEquals(19, loaded.homewardDay());
        assertTrue(loaded.magnetOff());
        assertTrue(loaded.hasMilestone("10,64,-20|stage:3"));
        assertEquals(3, loaded.mark("tales.storyteller", 0));
        assertTrue(loaded.flag("effects.cue"));
        PlayerHearth copy = new PlayerHearth();
        copy.copyFrom(loaded);
        assertEquals(12.5, copy.tales(), 1e-9);
        assertTrue(copy.hasMilestone("10,64,-20|stage:3"));
    }

    @Test
    void anEmptyPlayerSaveLoads() {
        PlayerHearth player = PlayerHearth.CODEC.parse(JsonOps.INSTANCE, new JsonObject()).getOrThrow();
        assertEquals(0f, player.warmth());
        assertFalse(player.inRange("minecraft:overworld", 0));
    }

    @Test
    void deathKeepsHomebuilderMarks() {
        PlayerHearth player = new PlayerHearth();
        player.setHomewardDay(7);
        player.markMilestone("x");
        player.provision(0.3, 0, 1000);
        player.setAbsorbShare(6);
        player.clearOnDeath();
        assertEquals(7, player.homewardDay());
        assertTrue(player.hasMilestone("x"));
        assertEquals(0, player.provisionedAt(10), 1e-12);
        assertEquals(0f, player.absorbShare());
    }

    // ---- Positions, range and the index ---------------------------------------------------------

    @Test
    void positionsPackLikeMinecraft() {
        int[][] cases = {{0, 0, 0}, {1, -64, -1}, {-30_000_000, 319, 29_999_999}, {123, 64, -456}};
        for (int[] c : cases) {
            long packed = HearthPos.pack(c[0], c[1], c[2]);
            HearthPos back = HearthPos.of(packed);
            assertEquals(new HearthPos(c[0], c[1], c[2]), back);
        }
        assertEquals("1,2,3", new HearthPos(1, 2, 3).key());
    }

    @Test
    void aChronicleWithABadAuthorStillLoads() {
        ChronicleBook book = new ChronicleBook();
        book.write(A, "minecraft:overworld|0,64,0", 40, 100);
        book.write(A, "minecraft:overworld|0,64,0", 60, 200);
        JsonObject saved = ChronicleBook.CODEC.encodeStart(JsonOps.INSTANCE, book).getOrThrow().getAsJsonObject();
        // One author hand-edited to nothing, one to garbage: neither may throw out of the world load.
        saved.getAsJsonArray("pages").get(0).getAsJsonObject().addProperty("author", "");
        saved.getAsJsonArray("pages").get(1).getAsJsonObject().addProperty("author", "not-a-uuid");
        ChronicleBook loaded = ChronicleBook.CODEC.parse(JsonOps.INSTANCE, saved).getOrThrow();
        assertEquals(2, loaded.size());
        assertEquals(3L, loaded.nextId(), "the page counter survives");
        assertEquals(HearthState.NOBODY, loaded.page(1).orElseThrow().author());
        assertEquals(HearthState.NOBODY, loaded.page(2).orElseThrow().author());
        assertEquals(60.0, loaded.page(2).orElseThrow().tales(), 1e-12);
        assertEquals(A, roundTrip(ChronicleBook.CODEC, book).page(1).orElseThrow().author());
    }

    @Test
    void theRangeIsACylinderSoTheMineIsNotHome() {
        HearthGeometry.Range r = HearthGeometry.Range.DEFAULT;
        HearthPos hearth = new HearthPos(0, 64, 0);
        assertTrue(HearthGeometry.inRange(hearth, 24, 64, 0, r));
        assertFalse(HearthGeometry.inRange(hearth, 24, 64, 1, r));
        assertTrue(HearthGeometry.inRange(hearth, 0, 56, 0, r));
        assertFalse(HearthGeometry.inRange(hearth, 0, 44, 0, r));
        assertTrue(HearthGeometry.inRange(hearth, 0, 80, 0, r));
        assertFalse(HearthGeometry.inRange(hearth, 0, 81, 0, r));
        assertTrue(HearthGeometry.inRange(hearth, 0.5, 64.0, 24.4, r));
        assertEquals(63.0, HearthGeometry.horizontalDistance(hearth, new HearthPos(63, 10, 0)), 1e-12);
    }

    @Test
    void theIndexFindsTheHearthOfABlock() {
        HearthIndexStore index = new HearthIndexStore();
        long a = HearthPos.pack(0, 64, 0);
        long b = HearthPos.pack(200, 64, 0);
        assertTrue(index.add(a));
        assertTrue(index.add(b));
        assertFalse(index.add(a));
        HearthGeometry.Range r = HearthGeometry.Range.DEFAULT;
        assertEquals(a, index.hearthAt(10, 64, -10, r));
        assertEquals(b, index.hearthAt(190, 70, 5, r));
        assertEquals(HearthIndexStore.NONE, index.hearthAt(100, 64, 0, r));
        assertEquals(HearthIndexStore.NONE, index.hearthAt(0, 40, 0, r));
        // Spacing is horizontal only, and the one check lives here.
        assertEquals(a, index.tooClose(HearthPos.pack(30, 64, 30), 64));
        assertEquals(a, index.tooClose(HearthPos.pack(63, 10, 0), 64));
        assertEquals(HearthIndexStore.NONE, index.tooClose(HearthPos.pack(64, 200, 0), 64));
        assertEquals(HearthIndexStore.NONE, index.tooClose(HearthPos.pack(100, 64, 100), 64));
        assertEquals(HearthIndexStore.NONE, index.tooClose(a, 64), "a Hearth is never too close to itself");
        // A caller that found one gone skips it and gets the next, or none.
        assertEquals(HearthIndexStore.NONE, index.tooClose(HearthPos.pack(30, 64, 30), 64, pos -> pos == a));
        HearthIndexStore loaded = new HearthIndexStore();
        loaded.decode(index.encode());
        assertEquals(2, loaded.size());
        assertTrue(loaded.remove(b));
        assertEquals(HearthIndexStore.NONE, loaded.hearthAt(190, 70, 5, r));
        assertEquals(a, loaded.hearthAt(1, 64, 1, r));
    }

    @Test
    void theNoneSentinelIsNoRealPosition() {
        // Long.MIN_VALUE unpacks far outside the 30 000 000 world border.
        assertTrue(HearthPos.unpackX(HearthIndexStore.NONE) < -30_000_000);
        assertTrue(HearthPos.pack(-30_000_000, -2048, -30_000_000) != HearthIndexStore.NONE);
        HearthIndexStore index = new HearthIndexStore();
        assertFalse(index.add(HearthIndexStore.NONE));
        index.decode(new long[] {HearthIndexStore.NONE, HearthPos.pack(5, 64, 5)});
        assertEquals(1, index.size());
    }

    @Test
    void theNearestHearthWinsWhereRangesOverlapAndTheLookupFollowsTheRadius() {
        HearthIndexStore index = new HearthIndexStore();
        long west = HearthPos.pack(0, 64, 0);
        long east = HearthPos.pack(30, 64, 0);
        index.add(west);
        index.add(east);
        HearthGeometry.Range r = HearthGeometry.Range.DEFAULT;
        assertEquals(west, index.hearthAt(14, 64, 0, r));
        assertEquals(east, index.hearthAt(16, 64, 0, r));
        // A wider range re-indexes the chunks it covers.
        HearthGeometry.Range wide = new HearthGeometry.Range(100, 8, 16);
        assertEquals(west, index.hearthAt(-90, 64, 0, wide));
        assertEquals(HearthIndexStore.NONE, index.hearthAt(-90, 64, 0, r));
    }
}
