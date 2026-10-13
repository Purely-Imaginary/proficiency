package dev.amman.proficiency.hearth;

import org.junit.jupiter.api.Test;

import java.util.Random;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

/** The ledger: every point in one cell, sums match, the week drops day 8 (abuse check 17). */
class HearthLedgerTest {

    private static final UUID AMMAN = UUID.fromString("00000000-0000-0000-0000-0000000000a1");
    private static final UUID ROWAN = UUID.fromString("00000000-0000-0000-0000-0000000000a2");

    @Test
    void theCellsAlwaysSumToWhatWasAdded() {
        HearthLedger ledger = new HearthLedger();
        Random random = new Random(7);
        String[] sources = {"mining", "cooking", HearthLedger.TALES, HearthLedger.SIEGE, HearthLedger.FEAST};
        double added = 0;
        double[] perPool = new double[HearthPool.COUNT];
        for (int i = 0; i < 2000; i++) {
            UUID who = random.nextBoolean() ? AMMAN : ROWAN;
            HearthPool pool = HearthPool.VALUES[random.nextInt(HearthPool.COUNT)];
            double amount = random.nextDouble() * 3;
            ledger.add(who, who == AMMAN ? "Amman" : "Rowan", pool, sources[random.nextInt(sources.length)],
                    amount, 100 + i / 300);
            added += amount;
            perPool[pool.ordinal()] += amount;
        }
        assertEquals(added, ledger.grandTotal(null, HearthLedger.Window.LIFETIME), 1e-6);
        double poolSum = 0;
        for (HearthPool pool : HearthPool.VALUES) {
            assertEquals(perPool[pool.ordinal()], ledger.grandTotal(pool, HearthLedger.Window.LIFETIME), 1e-6);
            poolSum += ledger.grandTotal(pool, HearthLedger.Window.LIFETIME);
        }
        assertEquals(added, poolSum, 1e-6);
        double sourceSum = 0;
        for (UUID who : ledger.players().keySet()) {
            for (HearthLedger.SourceTotal source : ledger.sources(who, null, HearthLedger.Window.LIFETIME)) {
                sourceSum += source.total();
            }
        }
        assertEquals(added, sourceSum, 1e-6);
    }

    @Test
    void theWeekDropsDayEight() {
        HearthLedger ledger = new HearthLedger();
        for (int day = 1; day <= 8; day++) {
            ledger.add(AMMAN, "Amman", HearthPool.HEARTY, "mining", 10, day);
        }
        assertEquals(80, ledger.total(AMMAN, null, HearthLedger.Window.LIFETIME), 1e-9);
        assertEquals(70, ledger.total(AMMAN, null, HearthLedger.Window.WEEK), 1e-9);
        // A long gap empties the week but not the lifetime.
        ledger.rollTo(30);
        assertEquals(0, ledger.total(AMMAN, null, HearthLedger.Window.WEEK), 1e-9);
        assertEquals(80, ledger.total(AMMAN, null, HearthLedger.Window.LIFETIME), 1e-9);
    }

    @Test
    void rankingAndTopFeederAndSources() {
        HearthLedger ledger = new HearthLedger();
        ledger.add(AMMAN, "Amman", HearthPool.HEARTY, "mining", 30, 1);
        ledger.add(AMMAN, "Amman", HearthPool.FORTIFIED, "masonry", 20, 1);
        ledger.add(AMMAN, "Amman", HearthPool.HOMECOMING, HearthLedger.TALES, 5, 1);
        ledger.add(ROWAN, "Rowan", HearthPool.HEARTY, "farming", 40, 1);
        assertEquals(ROWAN, ledger.topFeeder(HearthPool.HEARTY, HearthLedger.Window.WEEK).player());
        assertEquals(AMMAN, ledger.ranking(null, HearthLedger.Window.LIFETIME).get(0).player());
        assertEquals("mining", ledger.sources(AMMAN, null, HearthLedger.Window.LIFETIME).get(0).source());
        assertNull(ledger.topFeeder(HearthPool.WORKSHOP, HearthLedger.Window.WEEK));
        ledger.addSiegeKill(ROWAN, "Rowan");
        ledger.addSiegeDefended(ROWAN, "Rowan");
        assertEquals(1, ledger.get(ROWAN).siegeKills());
        assertEquals(1, ledger.get(ROWAN).siegesDefended());
    }

    @Test
    void nothingBadGetsIn() {
        HearthLedger ledger = new HearthLedger();
        ledger.add(AMMAN, "Amman", HearthPool.HEARTY, "mining", Double.NaN, 1);
        ledger.add(AMMAN, "Amman", HearthPool.HEARTY, "mining", -5, 1);
        ledger.add(AMMAN, "Amman", HearthPool.HEARTY, "mining", Double.POSITIVE_INFINITY, 1);
        assertEquals(0, ledger.grandTotal(null, HearthLedger.Window.LIFETIME), 1e-12);
    }
}
