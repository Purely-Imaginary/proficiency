package dev.amman.proficiency.hearth;

import dev.amman.proficiency.skill.Skill;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Tales, the Chronicle, the Feast and the bell (abuse checks 6, 16, 18). */
class HearthGatheringTest {

    private static final HearthData D = HearthData.DEFAULTS;
    private static final UUID A = UUID.fromString("00000000-0000-0000-0000-0000000000a1");
    private static final UUID B = UUID.fromString("00000000-0000-0000-0000-0000000000a2");
    private static final UUID C = UUID.fromString("00000000-0000-0000-0000-0000000000a3");

    // ---- Tales and the Chronicle ----------------------------------------------------------------

    @Test
    void talesBankFivePercentOfTravelXp() {
        assertEquals(5.0, TalesMath.banked(Skill.WAYFARING, 100, 0, D), 1e-12);
        assertEquals(6.5, TalesMath.banked(Skill.RUNNING, 100, 0.3, D), 1e-12);
        assertEquals(0.0, TalesMath.banked(Skill.MINING, 100, 0, D), 1e-12);
        assertEquals(0.0, TalesMath.banked(Skill.RUNNING, Double.NaN, 0, D), 1e-12);
    }

    @Test
    void talesPayIntoHomecomingOrEveryPool() {
        Map<HearthPool, Double> paid = TalesMath.payout(50, D);
        assertEquals(Map.of(HearthPool.HOMECOMING, 50.0), paid);
        HearthData all = HearthDataLoader.parse("t", "{\"tales\": {\"feedAll\": true}}").data();
        Map<HearthPool, Double> spread = TalesMath.payout(50, all);
        assertEquals(HearthPool.COUNT, spread.size());
        assertEquals(10.0, spread.get(HearthPool.HEARTY), 1e-12);
    }

    @Test
    void aDeathLosesTheBankedTales() {
        PlayerHearth player = new PlayerHearth();
        player.addTales(42);
        player.setWarmth(1);
        assertEquals(42, player.clearOnDeath(), 1e-12);
        assertEquals(0, player.tales(), 1e-12);
        assertEquals(0f, player.warmth());
    }

    @Test
    void theChronicleWriteBonusIsOncePerHomecoming() {
        PlayerHearth player = new PlayerHearth();
        player.addTales(80);
        player.notePaid(player.takeTales());
        assertEquals(20, TalesMath.writeBonus(player.takeChronicleBase(), D), 1e-12);
        // A second page pays nothing.
        assertEquals(0, TalesMath.writeBonus(player.takeChronicleBase(), D), 1e-12);
    }

    @Test
    void aPageRatesOnce() {
        ChronicleBook book = new ChronicleBook();
        ChronicleBook.Page page = book.write(A, "0,64,0", 100, 1234);
        assertEquals(1, page.id());
        assertEquals(2, book.nextId());
        ChronicleBook.Page rated = book.rate(page.id(), 4).orElseThrow();
        assertEquals(20, TalesMath.ratedBurst(rated.tales(), rated.stars(), D), 1e-12);
        assertTrue(book.rate(page.id(), 5).isEmpty());
        assertTrue(book.rate(99, 5).isEmpty());
        assertEquals(25, TalesMath.ratedBurst(100, 9, D), 1e-12);
    }

    // ---- Feast ----------------------------------------------------------------------------------

    @Test
    void soloEatingIsNoFeast() {
        FeastWindow feast = new FeastWindow();
        assertEquals(FeastWindow.Kind.NONE, feast.onEat(A, 100, 1, D).kind());
        assertEquals(FeastWindow.Kind.NONE, feast.onEat(A, 200, 1, D).kind());
    }

    @Test
    void twoEatersWithinThirtySecondsFeastAndAThirdJoins() {
        FeastWindow feast = new FeastWindow();
        feast.onEat(A, 1000, 1, D);
        FeastWindow.Outcome started = feast.onEat(B, 1000 + 25 * 20, 1, D);
        assertEquals(FeastWindow.Kind.STARTED, started.kind());
        assertEquals(20, started.charge(), 1e-12);
        assertTrue(started.warmed().contains(A) && started.warmed().contains(B));
        FeastWindow.Outcome joined = feast.onEat(C, 1000 + 40 * 20, 1, D);
        assertEquals(FeastWindow.Kind.JOINED, joined.kind());
        assertEquals(20, joined.charge(), 1e-12);
        // Once per in-game day.
        assertEquals(FeastWindow.Kind.NONE, feast.onEat(A, 1000 + 400 * 20, 1, D).kind());
        assertTrue(feast.feastedOn(1));
    }

    @Test
    void eatersTooFarApartDoNotFeast() {
        FeastWindow feast = new FeastWindow();
        feast.onEat(A, 1000, 1, D);
        assertEquals(FeastWindow.Kind.NONE, feast.onEat(B, 1000 + 31 * 20, 1, D).kind());
    }

    @Test
    void aFeastAcrossMidnightIsJoinedNotStartedAgain() {
        FeastWindow feast = new FeastWindow();
        // A and B eat just before day N+1 begins (day time 23990 of day 0): the Feast starts on day 0.
        feast.onEat(A, 23_980, 0, D);
        FeastWindow.Outcome started = feast.onEat(B, 23_990, 0, D);
        assertEquals(FeastWindow.Kind.STARTED, started.kind());
        assertEquals(20.0, started.charge(), 1e-12);
        // C eats inside the window, already day 1: C joins day 0's Feast (one more eater's charge),
        // the Feast does not start over and pay A and B again.
        FeastWindow.Outcome late = feast.onEat(C, 24_010, 1, D);
        assertEquals(FeastWindow.Kind.JOINED, late.kind());
        assertEquals(java.util.List.of(C), late.warmed());
        assertEquals(20.0, late.charge(), 1e-12);
        assertFalse(feast.feastedOn(1), "day 1's Feast is still to come");
        // Once the window has closed, day 1 gets its own Feast.
        long later = 24_010 + 600 * 20;
        feast.onEat(A, later, 1, D);
        assertEquals(FeastWindow.Kind.STARTED, feast.onEat(B, later + 20, 1, D).kind());
        assertTrue(feast.feastedOn(1));
    }

    @Test
    void theDinnerBellOpensATwoMinuteWindow() {
        FeastWindow feast = new FeastWindow();
        feast.onEat(A, 1000, 1, D);
        assertTrue(feast.someoneAteWithin(1000 + 50 * 20, D));
        feast.openDinner(1000 + 50 * 20, D);
        FeastWindow.Outcome late = feast.onEat(B, 1000 + 50 * 20 + 100 * 20, 1, D);
        assertEquals(FeastWindow.Kind.STARTED, late.kind());
        assertTrue(late.warmed().contains(A));
    }

    // ---- The bell -------------------------------------------------------------------------------

    @Test
    void theBellPicksAlarmThenDinnerThenComeHome() {
        assertEquals(BellRules.Call.ALARM, BellRules.pick(5, true, true, true));
        assertEquals(BellRules.Call.DINNER, BellRules.pick(5, true, false, true));
        assertEquals(BellRules.Call.COME_HOME, BellRules.pick(6, true, false, false));
        assertEquals(BellRules.Call.NONE, BellRules.pick(4, true, true, true));
        assertEquals(BellRules.Call.NONE, BellRules.pick(6, false, true, true));
    }

    @Test
    void eachCallHasItsCooldown() {
        long now = 100_000;
        assertTrue(BellRules.ring(5, true, true, false, false, now, now - 30 * 20, PlayerHearth.NEVER, D).ready());
        assertFalse(BellRules.ring(5, true, true, false, false, now, now - 29 * 20, PlayerHearth.NEVER, D).ready());
        assertFalse(BellRules.ring(5, true, false, false, false, now, PlayerHearth.NEVER, now - 299 * 20, D).ready());
        assertTrue(BellRules.ring(5, true, false, false, false, now, PlayerHearth.NEVER, PlayerHearth.NEVER, D).ready());
        assertFalse(BellRules.ring(5, true, false, true, true, now, PlayerHearth.NEVER, PlayerHearth.NEVER, D).ready());
    }

    @Test
    void theComeHomeDiscountLastsSixtySecondsForThoseWhoHeardIt() {
        PlayerHearth heard = new PlayerHearth();
        heard.heardBell(HearthPos.pack(0, 64, 0), "minecraft:overworld", 1000 + Math.round(D.bell().comeHomeSeconds() * 20));
        assertTrue(heard.heardBell(1000 + 59 * 20));
        assertFalse(heard.heardBell(1000 + 61 * 20));
        assertFalse(new PlayerHearth().heardBell(1000));
    }
}
