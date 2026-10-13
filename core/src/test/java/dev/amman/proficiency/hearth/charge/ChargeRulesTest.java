package dev.amman.proficiency.hearth.charge;

import dev.amman.proficiency.hearth.HearthData;
import dev.amman.proficiency.hearth.HearthLedger;
import dev.amman.proficiency.hearth.HearthPool;
import dev.amman.proficiency.hearth.HearthState;
import dev.amman.proficiency.skill.Skill;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The charge lane's pure rules: routing, shares, splits, tier watching and the decay step. */
class ChargeRulesTest {

    private static final HearthData D = HearthData.DEFAULTS;
    private static final long HOUR = HearthData.TICKS_PER_HOUR;
    private static final UUID AMMAN = UUID.fromString("00000000-0000-0000-0000-0000000000a1");
    private static final UUID ROWAN = UUID.fromString("00000000-0000-0000-0000-0000000000a2");

    // ---- Routing --------------------------------------------------------------------------------

    @Test
    void theDefaultDataMapsTheSpecTable() {
        for (Skill skill : new Skill[] {Skill.MINING, Skill.WOODCUTTING, Skill.EXCAVATION, Skill.FARMING, Skill.FISHING}) {
            assertEquals(HearthPool.HEARTY, ChargeRules.pool(skill, D), skill.id());
        }
        assertEquals(HearthPool.REGEN, ChargeRules.pool(Skill.DECORATING, D));
        assertEquals(HearthPool.FORTIFIED, ChargeRules.pool(Skill.MASONRY, D));
        for (Skill skill : new Skill[] {Skill.SMITHING, Skill.COOKING, Skill.ALCHEMY}) {
            assertEquals(HearthPool.WORKSHOP, ChargeRules.pool(skill, D), skill.id());
        }
        assertEquals(HearthPool.HOMECOMING, ChargeRules.pool(Skill.SOCIAL, D));
        // Movement, Expedition, Survival and Mastery feed nothing in range.
        for (Skill skill : new Skill[] {Skill.RUNNING, Skill.SNEAKING, Skill.JUMPING, Skill.SWIMMING,
                Skill.BEASTSLAYING, Skill.WAYFARING, Skill.SPELUNKING, Skill.NIGHTWALKER, Skill.SPELLCASTING,
                Skill.ENGINEERING}) {
            assertNull(ChargeRules.pool(skill, D), skill.id());
        }
        assertTrue(D.pool(HearthPool.REGEN).holdSeconds() > 0 && D.pool(HearthPool.FORTIFIED).holdSeconds() > 0,
                "Decorating and Masonry are held");
        assertEquals(0, D.pool(HearthPool.HEARTY).holdSeconds());
    }

    @Test
    void onlyPlayFeedsAndNeverTheFirstTimeLine() {
        assertEquals(ChargeRules.Route.POOL, ChargeRules.route(Skill.MINING, "block.minecraft.stone", true, false));
        assertEquals(ChargeRules.Route.NONE, ChargeRules.route(Skill.MINING, "block.minecraft.stone", false, false));
        assertEquals(ChargeRules.Route.NONE, ChargeRules.route(Skill.MINING, "first|block.minecraft.stone", true, true));
        assertEquals(ChargeRules.Route.NONE, ChargeRules.route(null, "x", true, false));
    }

    @Test
    void homebuilderNeverFeedsWhateverTheDataMaps() {
        // A pack that maps #category:construction must not make Homebuilder's share feed: it is a
        // share of work at home, so mirroring it would count that work twice.
        assertTrue(ChargeRules.neverFeeds(Skill.HOMEBUILDER));
        assertTrue(ChargeRules.neverFeeds(Skill.HOMEBUILDER.id()));
        assertEquals(Skill.HOMEBUILDER.id(), ChargeRules.HOMEBUILDER);
        assertFalse(ChargeRules.neverFeeds(Skill.MASONRY) || ChargeRules.neverFeeds(Skill.DECORATING)
                || ChargeRules.neverFeeds(Skill.MASONRY.id()), "Masonry and Decorating, Homebuilder's neighbours, still feed");
        assertEquals(ChargeRules.Route.NONE,
                ChargeRules.route(Skill.HOMEBUILDER, "proficiency.xplog.source.home", true, false));
        assertEquals(ChargeRules.Route.NONE, ChargeRules.route(Skill.HOMEBUILDER, "kill|entity.minecraft.zombie", true,
                false), "not even through a kill line");
        assertNull(ChargeRules.pool(Skill.HOMEBUILDER, D), "the default data maps Homebuilder to no pool");
    }

    @Test
    void combatFeedsOnlyThroughKills() {
        // Hits, blocking, damage taken and role XP: combat skills never feed through the mapping.
        for (Skill skill : new Skill[] {Skill.SWORDS, Skill.BLOCKING, Skill.ENDURANCE, Skill.GUARDIAN,
                Skill.CHARGER, Skill.TACTICIAN, Skill.COURAGE, Skill.ARCHERY}) {
            assertEquals(ChargeRules.Route.NONE, ChargeRules.route(skill, "entity.minecraft.zombie", true, false),
                    skill.id());
        }
        assertEquals(ChargeRules.Route.NONE,
                ChargeRules.route(Skill.ENDURANCE, "proficiency.xplog.damage.fall", true, false));
        // A kill line takes the kill route whatever the skill (a spell kill too).
        assertEquals(ChargeRules.Route.KILL, ChargeRules.route(Skill.SWORDS, "kill|entity.minecraft.zombie", true, false));
        assertEquals(ChargeRules.Route.KILL,
                ChargeRules.route(Skill.SPELLCASTING, "kill|entity.minecraft.zombie", true, false));
        assertEquals(ChargeRules.Route.NONE, ChargeRules.route(Skill.SWORDS, "kill|entity.minecraft.zombie", false, false));
    }

    @Test
    void sharesAreTenAndThreePercentAndNeverNegative() {
        assertEquals(1.5, ChargeRules.mirror(15, D), 1e-9);
        assertEquals(0.18, ChargeRules.combat(6, D), 1e-9);
        assertEquals(0.0, ChargeRules.mirror(-3, D));
        assertEquals(0.0, ChargeRules.mirror(Double.NaN, D));
        assertEquals(0.0, ChargeRules.mirror(Double.POSITIVE_INFINITY, D));
    }

    @Test
    void aRaidKillFeedsWhenTheRaiderOrItsRaidIsHome() {
        assertTrue(ChargeRules.raidFeeds(true, true, false));
        assertTrue(ChargeRules.raidFeeds(true, false, true));
        assertFalse(ChargeRules.raidFeeds(true, false, false));
        assertFalse(ChargeRules.raidFeeds(false, true, true), "a pillager patrol with no raid is an ordinary kill");
    }

    @Test
    void aSiegeKillFeedsOnlyTheHearthUnderSiegeWhereItDied() {
        assertTrue(ChargeRules.siegeFeeds(true, true, true));
        assertFalse(ChargeRules.siegeFeeds(false, true, true), "an ordinary mob at a Hearth under siege");
        assertFalse(ChargeRules.siegeFeeds(true, false, true), "a siege mob at a Hearth that is not under siege");
        assertFalse(ChargeRules.siegeFeeds(true, true, false), "a siege mob killed outside this Hearth's range");
    }

    // ---- Holds ----------------------------------------------------------------------------------

    @Test
    void aHoldComesDueAfterItsSecondsAndPaysOnlyForTheSameBlock() {
        assertEquals(1000 + 600 * 20L, ChargeRules.holdDue(1000, 600));
        assertTrue(ChargeRules.stillStands("minecraft:stone_bricks", "minecraft:stone_bricks"));
        assertFalse(ChargeRules.stillStands("minecraft:stone_bricks", "minecraft:air"));
        assertFalse(ChargeRules.stillStands("minecraft:stone_bricks", "minecraft:cobblestone"));
        assertFalse(ChargeRules.stillStands("", ""));
        assertFalse(ChargeRules.stillStands(null, null));
    }

    // ---- Splits ---------------------------------------------------------------------------------

    @Test
    void aSplitAlwaysSumsToTheTotal() {
        Random random = new Random(11);
        for (int round = 0; round < 500; round++) {
            Map<String, Integer> kills = new LinkedHashMap<>();
            int players = 1 + random.nextInt(5);
            for (int i = 0; i < players; i++) {
                kills.put("p" + i, random.nextInt(4));
            }
            double total = random.nextDouble() * 900;
            Map<String, Double> parts = ChargeRules.split(total, kills);
            double sum = 0;
            for (double part : parts.values()) {
                assertTrue(part >= -1e-9, "no negative part");
                sum += part;
            }
            assertEquals(total, sum, 1e-9);
        }
    }

    @Test
    void aSplitFollowsTheKillsAndFallsBackToEven() {
        Map<String, Integer> kills = new LinkedHashMap<>();
        kills.put("amman", 3);
        kills.put("rowan", 1);
        kills.put("idle", 0);
        Map<String, Double> parts = ChargeRules.split(100, kills);
        assertEquals(75, parts.get("amman"), 1e-9);
        assertEquals(25, parts.get("rowan"), 1e-9);
        assertFalse(parts.containsKey("idle"), "no kills, no share while someone has kills");
        Map<String, Integer> none = new LinkedHashMap<>();
        none.put("a", 0);
        none.put("b", 0);
        Map<String, Double> even = ChargeRules.split(10, none);
        assertEquals(5, even.get("a"), 1e-9);
        assertEquals(5, even.get("b"), 1e-9);
        assertTrue(ChargeRules.split(10, new LinkedHashMap<String, Integer>()).isEmpty());
        assertTrue(ChargeRules.split(Double.NaN, kills).isEmpty());
    }

    // ---- Tier watching --------------------------------------------------------------------------

    @Test
    void roseNamesOnlyThePoolsThatWentUp() {
        assertEquals(List.of(HearthPool.HEARTY, HearthPool.HOMECOMING),
                ChargeRules.rose(new int[] {0, 2, 1, 0, 3}, new int[] {1, 2, 0, 0, 4}));
        assertTrue(ChargeRules.rose(null, new int[5]).isEmpty());
    }

    @Test
    void theHighWaterFollowsTheChargeDownButNotTheCeiling() {
        // Hearty was told III; decay took the charge to I: a re-climb to II is news again.
        assertArrayEquals(new int[] {1, 3, 0, 0, 0},
                ChargeRules.lowerHighWater(new int[] {3, 3, 0, 0, 0}, new int[] {1, 4, 2, 0, 0}));
        // Missing entries (an old save) count as never told.
        assertArrayEquals(new int[5], ChargeRules.lowerHighWater(null, new int[] {2, 2, 2, 2, 2}));
    }

    // ---- The decay step (abuse check 14) --------------------------------------------------------

    private static HearthState charged(double charge, long visited, long decayed) {
        HearthState state = new HearthState();
        state.setCharge(HearthPool.HEARTY, charge);
        state.setLastVisited(visited);
        state.setLastDecay(decayed);
        return state;
    }

    @Test
    void someoneHomeHoldsDecayOff() {
        long now = 100 * HOUR;
        HearthState state = charged(1000, now - 20, now - 20);
        assertFalse(ChargeRules.decayStep(state, now, true, false, D), "no charge moved, so nothing to save");
        assertEquals(1000, state.charge(HearthPool.HEARTY), 1e-9);
        assertEquals(now, state.lastVisited());
        assertEquals(now, state.lastDecay());
        // An empty Hearth that only moved its clocks reports no change either.
        HearthState empty = charged(0, now - 30 * HOUR, now - 10 * HOUR);
        assertFalse(ChargeRules.decayStep(empty, now, false, true, D));
        assertEquals(now, empty.lastDecay(), "the decay clock still moved");
    }

    @Test
    void anAbsenceDecaysWhenSomeoneComesBack() {
        long now = 100 * HOUR;
        // Last visit 30 h ago (past the grace), decay last applied 10 h ago: ten free hours.
        HearthState state = charged(1000, now - 30 * HOUR, now - 10 * HOUR);
        assertTrue(ChargeRules.decayStep(state, now, true, false, D), "the absence decayed");
        assertEquals(1000 * Math.pow(0.98, 10), state.charge(HearthPool.HEARTY), 1e-6);
        assertEquals(now, state.lastVisited());
        assertFalse(ChargeRules.decayStep(state, now + 20, true, false, D), "the next second, still home: no change");
    }

    @Test
    void anUnloadedHearthCatchesUpOnItsFirstTick() {
        long now = 100 * HOUR;
        HearthState state = charged(1000, now - 30 * HOUR, now - 10 * HOUR);
        // Not due by the clock (it is), and forced anyway: the whole gap at once.
        assertTrue(ChargeRules.decayStep(state, now, false, true, D));
        assertEquals(1000 * Math.pow(0.98, 10), state.charge(HearthPool.HEARTY), 1e-6);
        assertEquals(now - 30 * HOUR, state.lastVisited(), "nobody visited");
        // Inside the grace day the tier floor holds: 1000 is tier II (floor 600).
        HearthState recent = charged(1000, now - 20 * HOUR, now - 20 * HOUR);
        ChargeRules.decayStep(recent, now, false, true, D);
        assertEquals(Math.max(600, 1000 * Math.pow(0.98, 20)), recent.charge(HearthPool.HEARTY), 1e-6);
    }

    @Test
    void nobodyHomeDecaysEveryTenSecondsAndTheStepSizeChangesNothing() {
        long start = 100 * HOUR;
        HearthState stepped = charged(1000, start - 30 * HOUR, start);
        HearthState once = charged(1000, start - 30 * HOUR, start);
        boolean changed = ChargeRules.decayStep(stepped, start + 20, false, false, D);
        assertFalse(changed, "not due after one second");
        for (long t = start + 20; t <= start + HOUR; t += 20) {
            ChargeRules.decayStep(stepped, t, false, false, D);
        }
        ChargeRules.decayStep(once, start + HOUR, false, true, D);
        assertEquals(once.charge(HearthPool.HEARTY), stepped.charge(HearthPool.HEARTY), 1e-6);
        assertEquals(1000 * 0.98, once.charge(HearthPool.HEARTY), 1e-6);
    }

    // ---- The hold queue's due shortcut -----------------------------------------------------------

    @Test
    void theHoldQueueSkipsItsWalkUntilTheEarliestHoldIsDue() {
        dev.amman.proficiency.hearth.HoldQueue queue = new dev.amman.proficiency.hearth.HoldQueue();
        assertEquals(Long.MAX_VALUE, queue.nextDue());
        for (int i = 0; i < 100; i++) {
            queue.add(i, new dev.amman.proficiency.hearth.HoldQueue.Held(AMMAN, "Amman", HearthPool.FORTIFIED,
                    "masonry", "minecraft:stone", 1.0, 1000 + i));
        }
        assertEquals(1000, queue.nextDue());
        assertTrue(queue.takeDue(999).isEmpty());
        assertEquals(100, queue.size());
        assertEquals(10, queue.takeDue(1009).size());
        assertEquals(1010, queue.nextDue(), "the walk moves the earliest due tick on");
        // A drop can leave it early; the next walk puts it right and loses nothing.
        queue.drop(10);
        assertEquals(1010, queue.nextDue());
        assertTrue(queue.takeDue(1010).isEmpty());
        assertEquals(1011, queue.nextDue());
        // An earlier hold added later still counts.
        queue.add(500, new dev.amman.proficiency.hearth.HoldQueue.Held(AMMAN, "Amman", HearthPool.REGEN,
                "decorating", "minecraft:lantern", 1.0, 900));
        assertEquals(900, queue.nextDue());
        assertEquals(500L, queue.takeDue(900).get(0).pos());
        assertEquals(89, queue.takeDue(Long.MAX_VALUE).size());
        assertTrue(queue.isEmpty());
        assertEquals(Long.MAX_VALUE, queue.nextDue());
    }

    // ---- The keepers table ----------------------------------------------------------------------

    @Test
    void theKeepersTableRanksAndSplitsBySource() {
        HearthLedger ledger = new HearthLedger();
        ledger.add(AMMAN, "Amman", HearthPool.HEARTY, "mining", 30, 10);
        ledger.add(AMMAN, "Amman", HearthPool.FORTIFIED, "masonry", 10, 10);
        ledger.add(ROWAN, "Rowan", HearthPool.HEARTY, "farming", 50, 10);
        ledger.add(ROWAN, "Rowan", HearthPool.HOMECOMING, HearthLedger.TALES, 5, 10);
        ledger.addSiegeKill(AMMAN, "Amman");

        List<KeeperRows.Row> all = KeeperRows.of(ledger, null, HearthLedger.Window.WEEK, 0);
        assertEquals(2, all.size());
        assertEquals(ROWAN, all.get(0).player());
        assertEquals(55, all.get(0).total(), 1e-9);
        assertEquals("farming", all.get(0).sources().get(0).source());
        assertEquals(1, all.get(1).siegeKills());
        assertEquals(10, all.get(1).perPool()[HearthPool.FORTIFIED.ordinal()], 1e-9);

        List<KeeperRows.Row> hearty = KeeperRows.of(ledger, HearthPool.HEARTY, HearthLedger.Window.WEEK, 1);
        assertEquals(1, hearty.size(), "the limit");
        assertEquals(50, hearty.get(0).total(), 1e-9);

        KeeperRows.Top top = KeeperRows.top(ledger, HearthPool.HEARTY);
        assertEquals("Rowan", top.name());
        assertEquals("farming", top.source());
        assertNull(KeeperRows.top(ledger, HearthPool.WORKSHOP));

        // Day 8 leaves the week, never the lifetime.
        ledger.rollTo(18);
        assertTrue(KeeperRows.of(ledger, null, HearthLedger.Window.WEEK, 0).isEmpty());
        assertEquals(2, KeeperRows.of(ledger, null, HearthLedger.Window.LIFETIME, 0).size());
        assertEquals(List.of(ROWAN, AMMAN), KeeperRows.everyone(ledger, 0));
    }

    @Test
    void aStackedBarAlwaysFillsItsCells() {
        Random random = new Random(3);
        for (int round = 0; round < 300; round++) {
            double[] perPool = new double[HearthPool.COUNT];
            for (int i = 0; i < perPool.length; i++) {
                perPool[i] = random.nextInt(3) == 0 ? 0 : random.nextDouble() * 100;
            }
            int[] cells = KeeperRows.cells(perPool, 20);
            int sum = 0;
            boolean any = false;
            for (int i = 0; i < cells.length; i++) {
                sum += cells[i];
                any |= perPool[i] > 0;
                assertTrue(perPool[i] > 0 || cells[i] == 0, "an empty pool takes no cell");
            }
            assertEquals(any ? 20 : 0, sum);
        }
    }
}
