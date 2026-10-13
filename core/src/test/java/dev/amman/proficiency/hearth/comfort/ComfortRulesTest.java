package dev.amman.proficiency.hearth.comfort;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.amman.proficiency.hearth.HearthData;
import dev.amman.proficiency.hearth.HearthRules;
import dev.amman.proficiency.hearth.HearthState;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;

/** The pure half of the comfort lane: kinds, Character folding, the scan clock, the tally. */
class ComfortRulesTest {

    private static final HearthData D = HearthData.DEFAULTS;

    @Test
    void theTwelveShippedKindsHaveNamesIconsAndTags() {
        assertEquals(12, D.comfort().max());
        for (String kind : D.comfort().kinds()) {
            assertTrue(ComfortKinds.valid(kind), kind);
            assertTrue(ComfortKinds.shipped(kind), kind);
            assertEquals("proficiency.hearth.comfort.kind." + kind, ComfortKinds.translationKey(kind));
            assertEquals("proficiency.hearth.comfort.kind." + kind + ".desc", ComfortKinds.descriptionKey(kind));
            assertEquals("textures/gui/hearth/comfort/" + kind + ".png", ComfortKinds.iconPath(kind));
            assertEquals("comfort/" + kind, ComfortKinds.tagPath(kind));
        }
    }

    @Test
    void aKindAPackAddsGetsAReadableFallbackAndNoShippedIcon() {
        assertFalse(ComfortKinds.shipped("wall_art"));
        assertEquals("Wall Art", ComfortKinds.fallbackName("wall_art"));
        assertEquals("Rug", ComfortKinds.fallbackName("farmersdelight/rug"));
        assertEquals("Old Rug", ComfortKinds.fallbackName("old-rug"));
        assertTrue(ComfortKinds.valid("pack/wall_art"));
        assertFalse(ComfortKinds.valid("Wall Art"));
        assertFalse(ComfortKinds.valid("ns:kind"));
        assertFalse(ComfortKinds.valid(""));
        assertFalse(ComfortKinds.valid(null));
    }

    @Test
    void missingKindsComeInTheDataOrder() {
        assertEquals(List.of("carpet", "banner", "light", "plant", "art", "books", "music", "storage", "trophy"),
                ComfortKinds.missing(Set.of("bed", "seat", "fire"), D.comfort().kinds()));
        assertEquals(List.of(), ComfortKinds.missing(Set.copyOf(D.comfort().kinds()), D.comfort().kinds()));
    }

    @Test
    void numberedVariantsFoldIntoOneEntry() {
        assertEquals("chipped:acacia_planks", CharacterKeys.foldNumbered("chipped:acacia_planks_12"));
        assertEquals("chipped:acacia_planks", CharacterKeys.foldNumbered("chipped:acacia_planks_1"));
        assertEquals("minecraft:oak_planks", CharacterKeys.foldNumbered("minecraft:oak_planks"));
        assertEquals("minecraft:stone_2x2", CharacterKeys.foldNumbered("minecraft:stone_2x2"));
        assertEquals("chipped:12", CharacterKeys.foldNumbered("chipped:12"));
        assertEquals("chipped:_5", CharacterKeys.foldNumbered("chipped:_5"));
        assertEquals("a", CharacterKeys.foldNumbered("a_5"));
        assertEquals("", CharacterKeys.foldNumbered(""));
        assertEquals("#proficiency:hearth/character_family/chipped_bricks",
                CharacterKeys.familyEntry("proficiency:hearth/character_family/chipped_bricks"));
        assertTrue(CharacterKeys.isFamilyTag("hearth/character_family/chipped_bricks"));
        assertFalse(CharacterKeys.isFamilyTag("hearth/character_family"));
        assertFalse(CharacterKeys.isFamilyTag("hearth/character_family/"));
        assertFalse(CharacterKeys.isFamilyTag("hearth/character_ignore"));
    }

    @Test
    void theScanClockFollowsTheSpec() {
        HearthData.Comfort cfg = D.comfort();
        long now = 100_000;
        // Never scanned: due at once, with or without anyone around.
        assertTrue(ScanPolicy.due(HearthState.NEVER, now, false, false, cfg));
        // Nothing changed and nobody is there: never.
        assertFalse(ScanPolicy.due(now - 10_000_000, now, false, false, cfg));
        // A change waits out the 30 s cooldown.
        assertFalse(ScanPolicy.due(now - 599, now, true, true, cfg));
        assertTrue(ScanPolicy.due(now - 600, now, true, false, cfg));
        // The safety rescan is every 5 minutes, and only with someone in range.
        assertFalse(ScanPolicy.due(now - 5999, now, false, true, cfg));
        assertTrue(ScanPolicy.due(now - 6000, now, false, true, cfg));
        assertFalse(ScanPolicy.due(now - 6000, now, false, false, cfg));
        // A clock set back counts as never scanned.
        assertTrue(ScanPolicy.due(now + 50, now, false, false, cfg));
        assertEquals(600, ScanPolicy.ticks(30));
        assertEquals(1, ScanPolicy.ticks(0.01));
        assertEquals(0, ScanPolicy.ticks(-4));
    }

    @Test
    void aTallyCountsEachKindAndEntryOnceAndCapsCharacter() {
        ComfortTally tally = new ComfortTally();
        assertTrue(tally.addKind("seat"));
        assertFalse(tally.addKind("seat"));
        tally.addKind("bed");
        tally.addKind("not_in_the_data");
        for (int i = 0; i < 70; i++) {
            tally.addEntry("minecraft:block_" + i);
            tally.addEntry("minecraft:block_" + i);
        }
        assertEquals(70, tally.entryCount());
        ComfortTally.Result result = tally.result(D);
        assertEquals(List.of("bed", "seat"), result.kinds(), "the data's order, unknown kinds dropped");
        assertEquals(2, result.comfort());
        assertEquals(50, result.character(), "capped at character.max");
        assertEquals(70, result.entries().size(), "the milestones still see every entry");
        assertEquals(0, new ComfortTally().result(D).character());
        assertFalse(result.pending());
    }

    @Test
    void aTallyRemembersSomethingThatMayCountAnyMoment() {
        ComfortTally tally = new ComfortTally();
        assertFalse(tally.result(D).pending());
        tally.markPending();
        assertTrue(tally.result(D).pending());
    }

    @Test
    void aLawnScoresZeroAndAHouseWithAllTwelveLiftsEveryCap() {
        // The tally of a lawn is empty (the scan skips blocks that see the sky), so the tiers see 0.
        ComfortTally.Result lawn = new ComfortTally().result(D);
        assertEquals(0, lawn.comfort());
        for (var pool : dev.amman.proficiency.hearth.HearthPool.VALUES) {
            boolean half = D.pool(pool).comfortCap() == HearthData.ComfortCap.HALF;
            // Regen and Homecoming need Comfort for every tier; the others only for V and VI.
            assertEquals(half ? 0 : 4, HearthRules.comfortCap(pool, lawn.comfort(), D), pool.id());
        }
        // A roofed house with all twelve reaches tier VI everywhere.
        ComfortTally house = new ComfortTally();
        D.comfort().kinds().forEach(house::addKind);
        assertEquals(12, house.result(D).comfort());
        for (var pool : dev.amman.proficiency.hearth.HearthPool.VALUES) {
            assertEquals(6, HearthRules.comfortCap(pool, house.result(D).comfort(), D), pool.id());
        }
    }
}
