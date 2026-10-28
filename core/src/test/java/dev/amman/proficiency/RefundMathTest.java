package dev.amman.proficiency;

import dev.amman.proficiency.skill.RefundMath;
import org.junit.jupiter.api.Test;

import java.util.Random;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RefundMathTest {

    private static final double CAP = RefundMath.DEFAULT_FREE_CAP;
    private static final double CAP_K = RefundMath.DEFAULT_FREE_CAP_KEYSTONE;
    private static final double K = RefundMath.DEFAULT_KEYSTONE_CHANCE;

    /**
     * The cost table of the balance analysis (masonry-balance/scripts/remedy.py, proposal R2): the
     * passive and proc chances of a real build at each level, Bulk Order as rank/10, Stonecutter
     * left out. Cost is items used per block placed.
     */
    @Test
    void costTableOfTheAnalysis() {
        // L40: Thrifty 5, Rapid Build 5, no Bulk Order.
        assertEquals(0.665, RefundMath.cost(0.135, 0.23175, 0, 0, CAP), 0.001);
        // L60: Bulk Order 1.
        assertEquals(0.456, RefundMath.cost(0.215, 0.354375, 0, 0.1, CAP), 0.001);
        // L100 with Master Mason: the higher cap is the whole story.
        double cap = RefundMath.cap(true, CAP, CAP_K);
        assertEquals(0.25, RefundMath.cost(0.375, 0.8316, K, 0.3, cap), 1e-9);
        // L100 without the keystone, every synergy and Expertise: the lower cap.
        assertEquals(0.45, RefundMath.cost(0.4455, 0.70166, 0, 0.3, CAP), 1e-9);
        // L20: no proc yet, the passive alone.
        assertEquals(1 - 0.11475, RefundMath.cost(0, 0.11475, 0, 0, CAP), 1e-9);
    }

    @Test
    void neverBelowZeroCostAtAnyLevel() {
        for (double p = 0; p <= 1.0001; p += 0.05) {
            for (double r = 0; r <= 1.0001; r += 0.05) {
                assertTrue(RefundMath.cost(p, r, K, 0.3, RefundMath.cap(true, CAP, CAP_K)) >= 0.25 - 1e-9);
                assertTrue(RefundMath.cost(p, r, 0, 0.3, CAP) >= 0.45 - 1e-9);
            }
        }
    }

    @Test
    void combineIsIndependentRolls() {
        assertEquals(0.0, RefundMath.combine(), 1e-12);
        assertEquals(0.5, RefundMath.combine(0.5), 1e-12);
        assertEquals(0.75, RefundMath.combine(0.5, 0.5), 1e-12);
        assertEquals(1 - 0.7 * 0.8 * 0.9, RefundMath.combine(0.3, 0.2, 0.1), 1e-12);
        assertEquals(1.0, RefundMath.combine(2.0, 0.1), 1e-12);
        assertEquals(0.1, RefundMath.combine(-1.0, 0.1), 1e-12);
    }

    @Test
    void theKeystoneRaisesTheCapAndAddsItsChance() {
        assertEquals(CAP, RefundMath.cap(false, CAP, CAP_K), 1e-12);
        assertEquals(CAP_K, RefundMath.cap(true, CAP, CAP_K), 1e-12);
        // A keystone cap set below the plain one never lowers it.
        assertEquals(CAP, RefundMath.cap(true, CAP, 0.4), 1e-12);
        // Keystone alone, nothing else: 30%.
        assertEquals(0.30, RefundMath.total(0, 0, K, 0, CAP_K), 1e-12);
    }

    @Test
    void theAbilityWindowIsTheCap() {
        assertEquals(CAP, RefundMath.total(1.0, 0.1, 0, 0, CAP), 1e-12);
        assertEquals(CAP, RefundMath.freeGivenProc(1.0, RefundMath.total(1.0, 0.1, 0, 0, CAP)), 1e-12);
        assertEquals(CAP_K, RefundMath.total(1.0, 0.1, K, 0, CAP_K), 1e-12);
    }

    /** Split into "proc landed" and "proc missed", the two branches add back up to the total. */
    @Test
    void procBranchesAverageToTheTotal() {
        for (double p = 0; p <= 1.0001; p += 0.05) {
            for (double f = 0; f <= 1.0001; f += 0.05) {
                double free = Math.max(Math.min(f, 1), 0);
                double pp = Math.min(p, 1);
                double sum = pp * RefundMath.freeGivenProc(pp, free) + (1 - pp) * RefundMath.freeGivenNoProc(pp, free);
                assertEquals(free, sum, 1e-9, "p=" + pp + " free=" + free);
            }
        }
        // Below the cap a landed proc is always free.
        assertEquals(1.0, RefundMath.freeGivenProc(0.2, 0.5), 1e-12);
        assertEquals((0.5 - 0.2) / 0.8, RefundMath.freeGivenNoProc(0.2, 0.5), 1e-12);
    }

    @Test
    void bulkOrderRanks() {
        assertEquals(0, RefundMath.bulkEvery(0));
        assertEquals(10, RefundMath.bulkEvery(1));
        assertEquals(8, RefundMath.bulkEvery(2));
        assertEquals(6, RefundMath.bulkEvery(3));
        assertEquals(0.1, RefundMath.bulkShare(1), 1e-12);
        assertEquals(0.0, RefundMath.bulkShare(0), 1e-12);
    }

    /** A run's certain block plus the lowered chance of the others is the total, capped or not. */
    @Test
    void bulkOrderKeepsTheRunAtTheTotal() {
        for (int rank = 1; rank <= 3; rank++) {
            double b = RefundMath.bulkShare(rank);
            for (double c : new double[] {0.0, 0.2, 0.4, 0.6, 0.9}) {
                double total = RefundMath.total(c, 0, 0, b, CAP);
                double off = RefundMath.offStreak(total, b);
                assertEquals(total, b + (1 - b) * off, 1e-9);
                if (RefundMath.combine(c, b) <= CAP) {
                    // Uncapped, the other blocks of the run keep their chance in full.
                    assertEquals(c, off, 1e-9);
                }
            }
        }
        // No Bulk Order: nothing changes.
        assertEquals(0.4, RefundMath.offStreak(0.4, 0), 1e-12);
    }

    /** A seeded simulation of the whole rule: the free rate meets the total, at most one item each. */
    @Test
    void simulatedRunMeetsTheTotal() {
        Random rng = new Random(42);
        double p = 0.3;
        double passive = RefundMath.combine(0.6, 0.12);
        double b = RefundMath.bulkShare(3);
        double cap = RefundMath.cap(true, CAP, CAP_K);
        double total = RefundMath.total(p, passive, K, b, cap);
        assertEquals(cap, total, 1e-12);
        int every = RefundMath.bulkEvery(3);
        int free = 0;
        int n = 120_000;
        for (int i = 1; i <= n; i++) {
            int items;
            if (i % every == 0) {
                items = 1;
            } else {
                double off = RefundMath.offStreak(total, b);
                boolean proc = rng.nextDouble() < p;
                double chance = proc ? RefundMath.freeGivenProc(p, off) : RefundMath.freeGivenNoProc(p, off);
                items = rng.nextDouble() < chance ? 1 : 0;
            }
            assertTrue(items <= 1);
            free += items;
        }
        assertEquals(total, free / (double) n, 0.005);
    }
}
