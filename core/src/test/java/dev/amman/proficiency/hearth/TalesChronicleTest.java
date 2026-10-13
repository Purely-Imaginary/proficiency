package dev.amman.proficiency.hearth;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.serialization.JsonOps;
import dev.amman.proficiency.skill.Skill;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The tales lane's pure rules: the capped payout, the trip, the Chronicle's text and register, the bell's box. */
class TalesChronicleTest {

    private static final HearthData D = HearthData.DEFAULTS;
    private static final UUID A = UUID.fromString("00000000-0000-0000-0000-0000000000a1");
    private static final UUID B = UUID.fromString("00000000-0000-0000-0000-0000000000a2");
    private static final UUID C = UUID.fromString("00000000-0000-0000-0000-0000000000a3");

    private static <T> T roundTrip(com.mojang.serialization.Codec<T> codec, T value) {
        JsonElement json = codec.encodeStart(JsonOps.INSTANCE, value).getOrThrow();
        return codec.parse(JsonOps.INSTANCE, json).getOrThrow();
    }

    // ---- The payout through the cap ---------------------------------------------------------------

    @Test
    void aHomecomingPaysThroughTheHourlyCapAndKeepsTheRest() {
        HearthState state = new HearthState();
        TalesMath.Settlement first = TalesMath.settle(state, 500, 1000, D);
        assertEquals(300, first.paid(), 1e-9, "the hourly cap is 300 per pool");
        assertEquals(200, first.kept(), 1e-9, "what the cap refused stays banked");
        assertEquals(300, state.charge(HearthPool.HOMECOMING), 1e-9);
        // Still the same hour: nothing more fits.
        TalesMath.Settlement second = TalesMath.settle(state, 40, 1000 + 20 * 60, D);
        assertFalse(second.any());
        assertEquals(40, second.kept(), 1e-9);
        // A rolling hour later it fits again.
        TalesMath.Settlement later = TalesMath.settle(state, 200, 1000 + HearthData.TICKS_PER_HOUR + 1, D);
        assertEquals(200, later.paid(), 1e-9);
        assertEquals(0, later.kept(), 1e-9);
    }

    @Test
    void aSmallBankIsPaidWholeAndFeedAllSplitsEvenly() {
        HearthState state = new HearthState();
        TalesMath.Settlement paid = TalesMath.settle(state, 42, 10, D);
        assertEquals(Map.of(HearthPool.HOMECOMING, 42.0), paid.added());
        assertEquals(0, paid.kept(), 1e-9);
        HearthData all = HearthDataLoader.parse("t", "{\"tales\": {\"feedAll\": true}}").data();
        HearthState spread = new HearthState();
        TalesMath.Settlement split = TalesMath.settle(spread, 50, 10, all);
        assertEquals(HearthPool.COUNT, split.added().size());
        assertEquals(50, split.paid(), 1e-9);
        assertEquals(10, spread.charge(HearthPool.FORTIFIED), 1e-9);
        assertEquals(0, TalesMath.settle(new HearthState(), 0, 10, D).paid(), 1e-9);
        assertEquals(0, TalesMath.settle(new HearthState(), Double.NaN, 10, D).paid(), 1e-9);
    }

    @Test
    void theCapPerPoolHoldsWhenTheBankIsSplit() {
        HearthData all = HearthDataLoader.parse("t", "{\"tales\": {\"feedAll\": true}}").data();
        HearthState state = new HearthState();
        // Hearty already took 290 this hour: its fifth of 100 only fits 10.
        state.addCapped(HearthPool.HEARTY, 290, 10, all);
        TalesMath.Settlement split = TalesMath.settle(state, 500, 20, all);
        assertEquals(10, split.added().get(HearthPool.HEARTY), 1e-9);
        assertEquals(100, split.added().get(HearthPool.REGEN), 1e-9);
        assertEquals(90, split.kept(), 1e-9);
    }

    // ---- The trip ---------------------------------------------------------------------------------

    @Test
    void aPlaceIsRecognisedByItsLanguageKey() {
        assertEquals(TaleTrip.Kind.STRUCTURE, TaleTrip.kindOf("structure.minecraft.stronghold"));
        assertEquals(TaleTrip.Kind.BIOME, TaleTrip.kindOf("biome.minecraft.plains"));
        assertEquals(TaleTrip.Kind.DIMENSION, TaleTrip.kindOf("dimension.minecraft.the_nether"));
        assertNull(TaleTrip.kindOf("block.minecraft.stone"));
        assertNull(TaleTrip.kindOf("first|mining|stone"));
        assertNull(TaleTrip.kindOf(null));
    }

    @Test
    void theLineNamesStructuresFirstThenDimensionsThenBiomes() {
        List<String> found = List.of("biome.minecraft.plains", "dimension.minecraft.the_nether",
                "biome.minecraft.desert", "structure.minecraft.stronghold", "structure.minecraft.village");
        assertEquals(List.of("structure.minecraft.stronghold", "structure.minecraft.village",
                "dimension.minecraft.the_nether"), TaleTrip.highlights(found, TaleTrip.NAMED));
        assertEquals(List.of("structure.minecraft.stronghold"), TaleTrip.highlights(found, 1));
        assertEquals(List.of(), TaleTrip.highlights(List.of("block.minecraft.stone"), 3));
    }

    @Test
    void aFullTripMakesRoomForABetterPlaceButNotForAWorseOne() {
        List<String> kept = new java.util.ArrayList<>();
        for (int i = 0; i < TaleTrip.MAX_PLACES; i++) {
            String biome = "biome.minecraft.b" + i;
            TaleTrip.Admission admission = TaleTrip.admit(kept, biome);
            assertTrue(admission.add() && admission.drop() == null, "room: " + biome);
            kept.add(biome);
        }
        assertFalse(TaleTrip.admit(kept, "biome.minecraft.late").add(), "a ninth biome is dropped");
        assertFalse(TaleTrip.admit(kept, "biome.minecraft.b3").add(), "a place already kept is not added twice");
        assertFalse(TaleTrip.admit(kept, "block.minecraft.dirt").add(), "not a place");
        // A stronghold found after eight biomes pushes the latest biome out.
        TaleTrip.Admission stronghold = TaleTrip.admit(kept, "structure.minecraft.stronghold");
        assertTrue(stronghold.add());
        assertEquals("biome.minecraft.b7", stronghold.drop(), "the latest of the weakest kind makes room");
        kept.remove(stronghold.drop());
        kept.add("structure.minecraft.stronghold");
        TaleTrip.Admission nether = TaleTrip.admit(kept, "dimension.minecraft.the_nether");
        assertTrue(nether.add() && "biome.minecraft.b6".equals(nether.drop()));
        kept.remove(nether.drop());
        kept.add("dimension.minecraft.the_nether");
        assertEquals(List.of("structure.minecraft.stronghold", "dimension.minecraft.the_nether",
                "biome.minecraft.b0"), TaleTrip.highlights(kept, TaleTrip.NAMED), "the line names the best first");
        // A trip of nothing but structures drops a second one of the same kind: the first found stay.
        List<String> structures = new java.util.ArrayList<>();
        for (int i = 0; i < TaleTrip.MAX_PLACES; i++) {
            structures.add("structure.minecraft.s" + i);
        }
        assertFalse(TaleTrip.admit(structures, "structure.minecraft.extra").add());
        assertFalse(TaleTrip.admit(structures, "dimension.minecraft.the_end").add(), "a lesser kind never displaces");
    }

    @Test
    void blocksAreWholeBlocksFromCentimetres() {
        assertEquals(3200, TaleTrip.blocks(1_000, 321_000));
        assertEquals(0, TaleTrip.blocks(500, 400), "a lower reading is no distance");
        assertEquals(0, TaleTrip.blocks(-1, 5000), "no start reading, no distance");
        assertEquals("3 200", TaleTrip.group(3200));
        assertEquals("12 345 678", TaleTrip.group(12_345_678));
        assertEquals("999", TaleTrip.group(999));
        assertEquals("1", TaleTrip.charge(0.2), "anything paid shows as at least 1");
        assertEquals("142", TaleTrip.charge(141.6));
        assertEquals("0", TaleTrip.charge(Double.NaN));
    }

    // ---- The Chronicle's text ---------------------------------------------------------------------

    @Test
    void aPageIsCleanedBeforeItIsFiled() {
        assertEquals("One\n\nTwo", ChronicleText.clean("One  \r\n\r\n\r\n\r\nTwo\n\n"));
        assertEquals("Red text", ChronicleText.clean("§4Red§r text"));
        assertEquals("a b", ChronicleText.clean("a\tb"));
        assertEquals("", ChronicleText.clean(null));
        String long1 = "x".repeat(3000);
        assertEquals(ChronicleText.MAX_PAGE, ChronicleText.clean(long1).length());
    }

    @Test
    void aBlankPageIsNotFiled() {
        assertTrue(ChronicleText.blank(""));
        assertTrue(ChronicleText.blank("  \n \t "));
        assertTrue(ChronicleText.blank("... --- !!!"));
        assertFalse(ChronicleText.blank("Stronghold at dusk"));
        assertFalse(ChronicleText.blank("7"));
    }

    @Test
    void theTitleIsTheFirstLineCutAtAWord() {
        assertEquals("The stronghold at dusk", ChronicleText.title("The stronghold at dusk\nWe went in."));
        String title = ChronicleText.title("We walked for three whole days before we saw the first torch");
        assertTrue(title.endsWith("...") && title.length() <= ChronicleText.TITLE_MAX + 3, title);
        assertEquals("We walked for three whole days before we", title.substring(0, title.length() - 3));
        assertEquals("", ChronicleText.title("\n\n"));
    }

    @Test
    void aPageIsKnownByItsCleanedText() {
        assertEquals(ChronicleText.hash("Hello\nworld"), ChronicleText.hash("Hello  \r\nworld\n"));
        assertFalse(ChronicleText.hash("Hello") == ChronicleText.hash("Hello!"));
    }

    @Test
    void onlyNewPagesAreFiledInBookOrder() {
        List<String> pages = List.of("First page", "", "Second page", "First page", "   ", "Third page");
        List<ChronicleText.Fresh> all = ChronicleText.fresh(Set.of(), pages);
        assertEquals(List.of(0, 2, 5), all.stream().map(ChronicleText.Fresh::index).toList(),
                "blank pages and a repeated page are not new");
        Set<Long> filed = ChronicleText.hashes(List.of("First page", "Second page"));
        List<ChronicleText.Fresh> some = ChronicleText.fresh(filed, pages);
        assertEquals(List.of("Third page"), some.stream().map(ChronicleText.Fresh::text).toList());
        // An edited page is a new page.
        assertEquals(1, ChronicleText.fresh(filed, List.of("First page!", "Second page")).size());
        assertTrue(ChronicleText.fresh(filed, List.of("First page", "Second page")).isEmpty());
    }

    @Test
    void theJsonLineCarriesEverythingMcjournalNeeds() {
        String line = ChronicleText.jsonLine(7, A, "Amman \"the Red\"", "minecraft:overworld|10,64,-20", 123_456L,
                1_791_570_125_000L, 142.456, "The stronghold", "Line one\nLine \"two\" <b> & more");
        assertFalse(line.contains("\n"), "one line per page");
        JsonObject json = JsonParser.parseString(line).getAsJsonObject();
        assertEquals(7, json.get("id").getAsLong());
        assertEquals(A.toString(), json.get("author").getAsString());
        assertEquals("Amman \"the Red\"", json.get("name").getAsString());
        assertEquals("minecraft:overworld|10,64,-20", json.get("hearth").getAsString());
        assertEquals(123_456L, json.get("gameTime").getAsLong());
        assertEquals("2026-10-09T18:22:05Z", json.get("real").getAsString());
        assertEquals(142.46, json.get("tales").getAsDouble(), 1e-9);
        assertEquals("The stronghold", json.get("title").getAsString());
        assertEquals("Line one\nLine \"two\" <b> & more", json.get("text").getAsString());
        assertEquals(ChronicleText.LINE_VERSION, json.get("v").getAsInt());
        assertTrue(line.contains("<b> &"), "no HTML escaping in a journal file");
    }

    // ---- The Feast: who is at the table -----------------------------------------------------------

    @Test
    void aFeastOfOneIsNotAFeastAndDoesNotSpendTheDay() {
        FeastWindow feast = new FeastWindow();
        feast.onEat(A, 1000, 1, D);
        // B eats 5 s later, but A has already walked off: nobody to feast with, and the day is not spent.
        FeastWindow.Outcome alone = feast.onEat(B, 1100, 1, D, id -> !A.equals(id));
        assertEquals(FeastWindow.Kind.NONE, alone.kind());
        assertTrue(alone.warmed().isEmpty() && alone.charge() == 0.0);
        assertFalse(feast.feastedOn(1), "the day's Feast is still there to be had");
        // A comes back and eats with B still at the table: now it is a Feast, of both.
        FeastWindow.Outcome real = feast.onEat(A, 1200, 1, D, id -> true);
        assertEquals(FeastWindow.Kind.STARTED, real.kind());
        assertEquals(Set.of(A, B), Set.copyOf(real.warmed()));
        assertTrue(feast.feastedOn(1));
        assertEquals(FeastWindow.Kind.NONE, feast.onEat(B, 1300, 1, D, id -> true).kind(), "and once per day");
    }

    @Test
    void anEaterWhoLeftIsLeftOutOfAFeastTheOthersStart() {
        FeastWindow feast = new FeastWindow();
        feast.onEat(A, 1000, 1, D);
        assertEquals(FeastWindow.Kind.NONE, feast.onEat(B, 1020, 1, D, id -> !A.equals(id)).kind());
        FeastWindow.Outcome started = feast.onEat(C, 1040, 1, D, id -> !A.equals(id));
        assertEquals(FeastWindow.Kind.STARTED, started.kind());
        assertEquals(Set.of(B, C), Set.copyOf(started.warmed()), "A left the table before the third meal");
        assertEquals(20, started.charge(), 1e-9, "one extra eater");
    }

    // ---- The register -----------------------------------------------------------------------------

    @Test
    void aPageCarriesItsNameTitleAndNoticeStateThroughASave() {
        ChronicleBook book = new ChronicleBook();
        ChronicleBook.Page page = book.write(A, "Amman", "minecraft:overworld|1,2,3", 142, 999, "The stronghold");
        assertEquals("Amman", page.name());
        assertEquals("The stronghold", page.title());
        assertFalse(page.told() || page.rated());
        ChronicleBook.Page rated = book.rate(page.id(), 4).orElseThrow();
        assertEquals("Amman", rated.name(), "rating keeps the name and title");
        assertEquals("The stronghold", rated.title());
        assertFalse(rated.told());
        assertEquals(List.of(rated), book.unannouncedFor(A));
        assertTrue(book.unannouncedFor(B).isEmpty(), "only the author is told");

        ChronicleBook loaded = roundTrip(ChronicleBook.CODEC, book);
        assertEquals(rated, loaded.page(page.id()).orElseThrow());
        assertTrue(loaded.markTold(page.id()));
        assertFalse(loaded.markTold(page.id()), "told once");
        assertTrue(loaded.unannouncedFor(A).isEmpty());
        assertTrue(roundTrip(ChronicleBook.CODEC, loaded).page(page.id()).orElseThrow().told());
        assertFalse(loaded.markTold(404));
    }

    @Test
    void anOldPageWithNoNameLoadsAndStillRatesOnce() {
        JsonObject old = new JsonObject();
        old.addProperty("id", 3);
        old.addProperty("author", A.toString());
        old.addProperty("hearth", "minecraft:overworld|0,64,0");
        old.addProperty("tales", 80.0);
        ChronicleBook.Page page = ChronicleBook.Page.CODEC.parse(JsonOps.INSTANCE, old).getOrThrow();
        assertEquals("", page.name());
        assertEquals("", page.title());
        assertEquals(-1, page.stars());
        assertFalse(page.told());
        assertEquals(new ChronicleBook.Page(3, A, "minecraft:overworld|0,64,0", 80.0, 0, -1), page);
    }

    @Test
    void anOldRatedPageIsNotRetoldAtTheNextLogin() {
        // Saved before the notice state existed: rated, with no "told" field. Its author is not told again.
        JsonObject oldRated = new JsonObject();
        oldRated.addProperty("id", 4);
        oldRated.addProperty("author", A.toString());
        oldRated.addProperty("hearth", "minecraft:overworld|0,64,0");
        oldRated.addProperty("tales", 80.0);
        oldRated.addProperty("stars", 3);
        ChronicleBook.Page rated = ChronicleBook.Page.CODEC.parse(JsonOps.INSTANCE, oldRated).getOrThrow();
        assertTrue(rated.rated() && rated.told(), "a page rated before the notice state reads as told");
        assertFalse(rated.unannounced());
        // The same page with the field present is believed: a rating this code made and has not announced waits.
        oldRated.addProperty("told", false);
        assertTrue(ChronicleBook.Page.CODEC.parse(JsonOps.INSTANCE, oldRated).getOrThrow().unannounced());
        // A whole saved book gets the same reading, and a page that was never rated has nothing to tell yet.
        JsonObject unrated = new JsonObject();
        unrated.addProperty("id", 5);
        unrated.addProperty("author", A.toString());
        assertFalse(ChronicleBook.Page.CODEC.parse(JsonOps.INSTANCE, unrated).getOrThrow().told());
        ChronicleBook book = new ChronicleBook();
        ChronicleBook.Page fresh = book.write(A, "Amman", "k", 80, 1, "t");
        book.rate(fresh.id(), 2);
        assertEquals(List.of(book.page(fresh.id()).orElseThrow()), roundTrip(ChronicleBook.CODEC, book).unannouncedFor(A),
                "what this code saved as not told is still waiting after a reload");
    }

    @Test
    void aRatedPagePaysStarsTimesFivePercentOfItsTales() {
        ChronicleBook book = new ChronicleBook();
        ChronicleBook.Page page = book.write(A, "Amman", "k", 80, 1, "t");
        ChronicleBook.Page rated = book.rate(page.id(), 5).orElseThrow();
        assertEquals(20, TalesMath.ratedBurst(rated.tales(), rated.stars(), D), 1e-9);
        // A page that went with no Tales pays nothing however well it rates.
        ChronicleBook.Page bare = book.rate(book.write(A, "Amman", "k", 0, 2, "").id(), 5).orElseThrow();
        assertEquals(0, TalesMath.ratedBurst(bare.tales(), bare.stars(), D), 1e-9);
        assertEquals(0, TalesMath.ratedBurst(80, 0, D), 1e-9);
    }

    // ---- The bell's box ---------------------------------------------------------------------------

    @Test
    void theHearthBellIsInsideTheNineByNineBox() {
        assertTrue(BellRules.inBox(0, 2, 0));
        assertTrue(BellRules.inBox(4, 5, -4), "the outer corner of the designs' grid");
        assertTrue(BellRules.inBox(-4, -1, 4), "down to the floor");
        assertFalse(BellRules.inBox(5, 2, 0), "one block outside the box");
        assertFalse(BellRules.inBox(0, 2, -5));
        assertFalse(BellRules.inBox(0, 6, 0));
        assertFalse(BellRules.inBox(0, -2, 0));
    }

    @Test
    void aSkillTheTalesLikeIsStillTheDataDefault() {
        assertTrue(D.tales().banks(Skill.RUNNING) && D.tales().banks(Skill.WAYFARING));
        assertFalse(D.tales().banks(Skill.MINING));
    }

    // ---- Keys and the trip's saved state ----------------------------------------------------------

    @Test
    void aHearthKeyParsesBackToItsPlace() {
        HearthKey key = HearthKey.parse("minecraft:the_nether|-12,64,300");
        assertEquals(new HearthKey("minecraft:the_nether", -12, 64, 300), key);
        assertEquals(HearthPos.pack(-12, 64, 300), key.packed());
        assertEquals("minecraft:the_nether|-12,64,300", key.toString());
        assertEquals(new HearthKey("mod:some|dim", 1, 2, 3), HearthKey.parse("mod:some|dim|1,2,3"), "the last bar splits");
        assertNull(HearthKey.parse("0,64,0"), "a key with no dimension is none");
        assertNull(HearthKey.parse("minecraft:overworld|1,2"));
        assertNull(HearthKey.parse("minecraft:overworld|a,b,c"));
        assertNull(HearthKey.parse(null));
    }

    @Test
    void aTripSurvivesASaveAndAFlagPrefixClearsOnlyItsOwn() {
        PlayerHearth player = new PlayerHearth();
        player.setFlag("tales.away", true);
        player.setFlag("tales.place.structure.minecraft.stronghold", true);
        player.setFlag("tales.place.biome.minecraft.plains", true);
        player.setFlag("homebuilder.other", true);
        player.setMark("tales.tripCm", 123_456L);
        player.addTales(42.5);
        PlayerHearth loaded = roundTrip(PlayerHearth.CODEC, player);
        assertTrue(loaded.flag("tales.away") && loaded.flag("homebuilder.other"));
        assertEquals(123_456L, loaded.mark("tales.tripCm", -1));
        assertEquals(42.5, loaded.tales(), 1e-9);
        assertEquals(List.of("tales.away", "tales.place.structure.minecraft.stronghold", "tales.place.biome.minecraft.plains",
                "homebuilder.other"), List.copyOf(loaded.flags()), "set order is kept");
        assertEquals(2, loaded.clearFlags("tales.place."));
        assertTrue(loaded.flag("tales.away") && loaded.flag("homebuilder.other"));
        assertEquals(0, loaded.clearFlags("tales.place."));
        // A death keeps the free flags (a lane clears its own) but not the Tales.
        PlayerHearth died = roundTrip(PlayerHearth.CODEC, player);
        assertEquals(42.5, died.clearOnDeath(), 1e-9);
        assertTrue(died.flag("tales.away"), "the lane clears its trip on the DEATH hook");
        // The copy a respawn or an End exit makes keeps them.
        PlayerHearth copy = new PlayerHearth();
        copy.copyFrom(player);
        assertEquals(player.flags(), copy.flags());
        assertEquals(123_456L, copy.mark("tales.tripCm", -1));
    }
}
