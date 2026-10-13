package dev.amman.proficiency.hearth.client;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.amman.proficiency.client.XpGainDots;
import dev.amman.proficiency.hearth.HearthPool;
import org.junit.jupiter.api.Test;

import java.util.List;

/** The client lane's pure parts: tracker numbers, tier-up timing, the glyph, the clocks, the ring, the ledger. */
class HearthClientMathTest {

    private static final double[] T = {200, 600, 1400, 2800, 5000, 8000};
    private static final int HEARTY = HearthPool.HEARTY.ordinal();
    private static final int WORKSHOP = HearthPool.WORKSHOP.ordinal();

    // ---- XpGainDots budget ------------------------------------------------------------------------

    @Test
    void aBudgetCapsTheNewDotsAndTheRestFillsTheBar() {
        XpGainDots dots = new XpGainDots();
        dots.snap(1.0, 0);
        dots.gain(1.0, 1.5, 0, 3);
        assertEquals(3, dots.inAir());
        dots.gain(1.5, 2.0, 10, 0);
        assertEquals(3, dots.inAir(), "no room: no new dot");
        double shown = 0;
        for (long t = 20; t < 20_000; t += 16) {
            shown = dots.update(2.0, t);
        }
        assertEquals(2.0, shown, 1e-6, "the gain with no dot still reaches the bar");
    }

    @Test
    void theOldThreeArgumentGainIsUnchanged() {
        XpGainDots dots = new XpGainDots();
        dots.snap(5.2, 0);
        dots.gain(5.2, 5.3, 0);
        assertEquals(XpGainDots.dotCount(5.3 - 5.2), dots.inAir());
    }

    @Test
    void aGainCanBeDelayed() {
        XpGainDots dots = new XpGainDots();
        dots.snap(0.5, 0);
        dots.gain(0.5, 0.7, 1000, 6);
        assertTrue(XpGainDots.progress(dots.dots().get(0), 500) < 0, "waits for its launch");
        assertEquals(0.5, dots.update(0.7, 500), 1e-6, "the bar holds while it waits");
    }

    // ---- Tier units -------------------------------------------------------------------------------

    @Test
    void valueIsTierPlusProgress() {
        assertEquals(0, HearthTrackerModel.value(0, T), 1e-9);
        assertEquals(0.5, HearthTrackerModel.value(100, T), 1e-9);
        assertEquals(1.0, HearthTrackerModel.value(200, T), 1e-9);
        assertEquals(2.5, HearthTrackerModel.value(1000, T), 1e-9);
        assertEquals(6.0, HearthTrackerModel.value(8000, T), 1e-9);
        assertEquals(6.0, HearthTrackerModel.value(99_999, T), 1e-9);
        assertEquals(0, HearthTrackerModel.value(Double.NaN, T), 1e-9);
        assertTrue(HearthTrackerModel.value(7999.99, T) < 6.0, "just under VI is still V");
    }

    @Test
    void theBarIsFullAtTheTopTier() {
        HearthTrackerModel model = new HearthTrackerModel();
        assertEquals(1.0, model.fill(6.0), 1e-9);
        assertEquals(0.25, model.fill(3.25), 1e-9);
    }

    @Test
    void badThresholdsAreIgnored() {
        HearthTrackerModel model = new HearthTrackerModel();
        model.thresholds(new double[] {10, 5});
        assertArrayEquals(T, model.thresholdsCopy());
        model.thresholds(new double[] {100, 200});
        assertEquals(2, model.maxTier());
        assertEquals(1.5, model.value(150), 1e-9);
    }

    // ---- Tracker rows -----------------------------------------------------------------------------

    @Test
    void aGainFliesInAndTheBarFillsAsItLands() {
        HearthTrackerModel model = new HearthTrackerModel();
        model.apply(1L, HEARTY, 300, 1, 0, 0, 0);
        assertEquals(1.25, model.row(HEARTY).shown(), 1e-9);
        model.gain(HEARTY, 100, 10, 0, true);
        assertTrue(model.inAir() > 0);
        model.frame(20, true, true);
        assertEquals(1.25, model.row(HEARTY).shown(), 1e-3, "nothing landed yet");
        for (long t = 30; t < 5000; t += 16) {
            model.frame(t, true, true);
        }
        assertEquals(1.5, model.row(HEARTY).shown(), 1e-6);
        assertEquals(400, model.row(HEARTY).charge(), 1e-9);
    }

    @Test
    void aStateThatAlreadyHoldsTheGainIsNotCountedTwice() {
        HearthTrackerModel model = new HearthTrackerModel();
        model.apply(1L, HEARTY, 500, 1, 0, 0, 0);
        // The tier change goes out at once; the gain that caused it comes with the next sync.
        model.apply(1L, HEARTY, 700, 2, 0, 0, 5);
        assertEquals(200, model.row(HEARTY).credit(), 1e-9);
        model.gain(HEARTY, 200, 300, 0, true);
        assertEquals(700, model.row(HEARTY).charge(), 1e-9, "the gain spent the credit");
        assertEquals(0, model.row(HEARTY).credit(), 1e-9);
    }

    @Test
    void creditNoGainClaimsFliesInOnItsOwn() {
        HearthTrackerModel model = new HearthTrackerModel();
        model.apply(1L, WORKSHOP, 100, 0, 0, 0, 0);
        model.apply(1L, WORKSHOP, 150, 0, 0, 0, 10);
        model.frame(500, true, true);
        assertEquals(50, model.row(WORKSHOP).credit(), 1e-9, "held while a gain may still come");
        model.frame(10 + HearthTrackerModel.CREDIT_RELEASE_MS, true, true);
        assertEquals(0, model.row(WORKSHOP).credit(), 1e-9);
        for (long t = 1300; t < 6000; t += 16) {
            model.frame(t, true, true);
        }
        assertEquals(0.75, model.row(WORKSHOP).shown(), 1e-6);
    }

    @Test
    void arrivingStartsTheRowOverWithNoTierUp() {
        HearthTrackerModel model = new HearthTrackerModel();
        model.apply(1L, HEARTY, 0, 0, 0, 0, 0);
        model.frame(1, true, true);
        // Back home after others fed it: the arrival state is news, not a gain.
        model.apply(1L, HEARTY, 1880, 3, 0, 0, 10, true);
        assertEquals(0, model.row(HEARTY).credit(), 1e-9);
        int rose = 0;
        for (long t = 11; t < 4000; t += 16) {
            rose |= model.frame(t, true, true);
        }
        assertEquals(0, rose, "no tier-up for what happened while away");
        assertEquals(0, model.inAir());
        assertEquals(3, model.row(HEARTY).displayedTier());
        assertEquals(HearthTrackerModel.value(1880, T), model.row(HEARTY).shown(), 1e-9);
    }

    @Test
    void aLossSnapsDown() {
        HearthTrackerModel model = new HearthTrackerModel();
        model.apply(1L, HEARTY, 1000, 2, 0, 0, 0);
        model.apply(1L, HEARTY, 900, 2, 0, 0, 10);
        model.frame(11, true, true);
        assertEquals(HearthTrackerModel.value(900, T), model.row(HEARTY).shown(), 1e-9);
    }

    @Test
    void theNumeralTurnsWhenTheFillWrapsNotWhenThePacketLands() {
        HearthTrackerModel model = new HearthTrackerModel();
        model.apply(1L, HEARTY, 590, 1, 0, 0, 0);
        model.frame(1, true, true);
        assertEquals(1, model.row(HEARTY).displayedTier());
        model.apply(1L, HEARTY, 610, 2, 0, 0, 2);
        model.gain(HEARTY, 20, 3, 0, true);
        int rose = model.frame(4, true, true);
        assertEquals(0, rose);
        assertEquals(1, model.row(HEARTY).displayedTier(), "the dots are still flying");
        boolean seen = false;
        for (long t = 5; t < 5000; t += 16) {
            rose |= model.frame(t, true, true);
            if (model.row(HEARTY).fx().active(t)) {
                seen = true;
            }
        }
        assertTrue(seen, "the tier-up played");
        assertEquals(1 << HEARTY, rose);
        assertEquals(2, model.row(HEARTY).displayedTier());
    }

    @Test
    void aLockHoldsTheNumeral() {
        HearthTrackerModel model = new HearthTrackerModel();
        model.apply(1L, HEARTY, 3000, 3, 1, 4, 0);
        for (long t = 0; t < 2000; t += 16) {
            model.frame(t, true, true);
        }
        assertEquals(3, model.row(HEARTY).displayedTier(), "charge says IV, the ceiling holds III");
        assertFalse(model.row(HEARTY).fx().active(2000));
        // The stage is built: the server sends the higher tier, and the numeral rises at once.
        model.apply(1L, HEARTY, 3000, 4, 0, 0, 2100);
        int rose = model.frame(2116, true, true);
        assertEquals(1 << HEARTY, rose);
        assertEquals(4, model.row(HEARTY).displayedTier());
    }

    @Test
    void theFirstLookAndANewHearthNeverPlayATierUp() {
        HearthTrackerModel model = new HearthTrackerModel();
        model.apply(1L, HEARTY, 5000, 5, 0, 0, 0);
        assertEquals(0, model.frame(1, true, true));
        model.apply(2L, HEARTY, 8000, 6, 0, 0, 5);
        assertEquals(0, model.frame(6, true, true));
        assertEquals(6, model.row(HEARTY).displayedTier());
    }

    @Test
    void theRowsShareOneBudget() {
        HearthTrackerModel model = new HearthTrackerModel();
        for (HearthPool pool : HearthPool.VALUES) {
            model.apply(1L, pool.ordinal(), 100, 0, 0, 0, 0);
        }
        for (HearthPool pool : HearthPool.VALUES) {
            model.gain(pool.ordinal(), 80, 1, 0, true);
        }
        assertEquals(HearthTrackerModel.GLOBAL_BUDGET, model.inAir());
    }

    @Test
    void dotsOffSnapsAndFxOffNeverStartsOne() {
        HearthTrackerModel model = new HearthTrackerModel();
        model.apply(1L, HEARTY, 190, 0, 0, 0, 0);
        model.frame(1, false, false);
        model.apply(1L, HEARTY, 210, 1, 0, 0, 2);
        int rose = model.frame(3, false, false);
        assertEquals(1 << HEARTY, rose, "the tier still rose");
        assertFalse(model.row(HEARTY).fx().active(3), "but nothing plays");
        assertEquals(HearthTrackerModel.value(210, T), model.row(HEARTY).shown(), 1e-9, "shown at once");
        model.gain(HEARTY, 20, 4, 0, false);
        assertEquals(0, model.inAir());
        assertEquals(210, model.row(HEARTY).charge(), 1e-9, "the late gain packet is not counted twice");
    }

    @Test
    void gainsBeforeAnyStateAreIgnored() {
        HearthTrackerModel model = new HearthTrackerModel();
        model.gain(HEARTY, 50, 0, 0, true);
        assertFalse(model.row(HEARTY).seen());
        assertFalse(model.any());
    }

    // ---- TierUpFx ---------------------------------------------------------------------------------

    @Test
    void tierUpTimingFollowsTheLevelUp() {
        TierUpFx fx = new TierUpFx();
        assertFalse(fx.active(0));
        assertEquals(1f, fx.roll(0));
        fx.start(2, 3, 1000);
        assertTrue(fx.active(1000));
        assertFalse(fx.gold());
        assertEquals(0f, fx.roll(1000), 1e-6);
        assertEquals(1f, fx.roll(1000 + TierUpFx.ROLL_MS), 1e-6);
        assertEquals(1f, fx.flash(1000), 1e-6);
        assertTrue(fx.flash(1300) < fx.flash(1100));
        assertFalse(fx.active(1000 + TierUpFx.NORMAL_MS));
        fx.start(5, 6, 5000);
        assertTrue(fx.gold());
        assertTrue(fx.active(5000 + TierUpFx.NORMAL_MS + 100), "the gold one runs longer");
        fx.start(6, 6, 9000);
        assertEquals(5000, fx.startedAt(), "no change, no restart");
    }

    // ---- The glyph --------------------------------------------------------------------------------

    @Test
    void warmthPicksOneOfNineFrames() {
        assertEquals(0, WarmthGlyph.frame(0));
        assertEquals(0, WarmthGlyph.frame(-1));
        assertEquals(0, WarmthGlyph.frame(Double.NaN));
        assertEquals(1, WarmthGlyph.frame(0.13));
        assertEquals(4, WarmthGlyph.frame(0.5));
        assertEquals(7, WarmthGlyph.frame(0.99));
        assertEquals(8, WarmthGlyph.frame(1.0));
        assertEquals("", WarmthGlyph.glyph(0));
        assertEquals("", WarmthGlyph.glyph(1));
    }

    @Test
    void warmthMovesOnBetweenPackets() {
        assertEquals(0.5, WarmthGlyph.at(0.25, 1 / 60.0, 15_000), 1e-9);
        assertEquals(1.0, WarmthGlyph.at(0.9, 1 / 60.0, 60_000), 1e-9);
        assertEquals(0.0, WarmthGlyph.at(0.1, -1 / 30.0, 10_000), 1e-9);
        assertEquals(0.4, WarmthGlyph.at(0.4, 1 / 60.0, -500), 1e-9, "a clock step back counts as no time");
        assertTrue(WarmthGlyph.filled(0.98, 1.0));
        assertFalse(WarmthGlyph.filled(1.0, 1.0));
        assertEquals(1f, WarmthGlyph.glow(100, 100), 1e-6);
        assertEquals(0f, WarmthGlyph.glow(100, 100 + WarmthGlyph.CUE_MS), 1e-6);
    }

    @Test
    void provisionedDrainsOverItsWindow() {
        assertEquals(1, WarmthGlyph.provisionedLeft(0.2, 100, 300, 50), 1e-9);
        assertEquals(0.5, WarmthGlyph.provisionedLeft(0.2, 100, 300, 200), 1e-9);
        assertEquals(0, WarmthGlyph.provisionedLeft(0.2, 100, 300, 300), 1e-9);
        assertEquals(0, WarmthGlyph.provisionedLeft(0, 100, 300, 150), 1e-9);
    }

    // ---- Siege clock -------------------------------------------------------------------------------

    @Test
    void theCountdownReadsMinutesAndSeconds() {
        assertEquals("18:20", SiegeClock.clock(1100 * 20));
        // The banner's memory: the same Hearth's countdown re-sent (Warm n/m, a slept night, a postponement)
        // is the same siege; another Hearth, a closed one, or a start days later is a new one.
        assertTrue(SiegeClock.sameSiege(7L, 50_000, 7L, 50_000));
        assertTrue(SiegeClock.sameSiege(7L, 50_000 - 11_000, 7L, 50_000), "a slept night");
        assertTrue(SiegeClock.sameSiege(7L, 50_000 + 24_000, 7L, 50_000), "postponed a night");
        assertTrue(SiegeClock.sameSiege(7L, 50_000 + 48_000, 7L, 50_000), "a postponement across a slept dusk");
        assertFalse(SiegeClock.sameSiege(8L, 50_000, 7L, 50_000), "another Hearth");
        assertFalse(SiegeClock.sameSiege(7L, 50_000, Long.MIN_VALUE, Long.MIN_VALUE), "nothing announced");
        assertFalse(SiegeClock.sameSiege(7L, 50_000 + 90_000, 7L, 50_000), "a later siege");
        assertFalse(SiegeClock.sameSiege(7L, 50_000 - 30_000, 7L, 50_000), "an earlier one");
        assertEquals("0:01", SiegeClock.clock(1));
        assertEquals("0:00", SiegeClock.clock(0));
        assertEquals("1:02:05", SiegeClock.clock((3600 + 125) * 20));
        assertEquals(0.5f, SiegeClock.bar(12_000, 0, 24_000), 1e-6);
        assertEquals(0f, SiegeClock.bar(100, 200, 24_000), 1e-6);
        assertEquals(1f, SiegeClock.bar(99_999, 0, 0), 1e-6, "unknown warning: one day");
    }

    @Test
    void aCountdownBarStartsFullAndDrainsOverItsOwnSpan() {
        // A horn's siege 3 600 ticks off: the bar starts full and is half empty halfway, not 15% from the start.
        long span = SiegeClock.span(0L, 3_600, 24_000);
        assertEquals(3_600, span);
        assertEquals(1f, SiegeClock.bar(3_600, 0, 24_000, span), 1e-6);
        assertEquals(0.5f, SiegeClock.bar(3_600, 1_800, 24_000, span), 1e-6);
        assertEquals(0f, SiegeClock.bar(3_600, 3_600, 24_000, span), 1e-6);
        // A rolled siege more than a day off: the span is the warning, so the bar stays full until the last day.
        long rolled = SiegeClock.span(0L, 30_000, 24_000);
        assertEquals(24_000, rolled);
        assertEquals(1f, SiegeClock.bar(30_000, 0, 24_000, rolled), 1e-6);
        assertEquals(0.5f, SiegeClock.bar(30_000, 18_000, 24_000, rolled), 1e-6);
        // The same siege seen again later (a relog, Warm n/m) keeps its span; a postponement widens it.
        assertEquals(3_600, SiegeClock.span(span, 1_000, 24_000));
        assertEquals(20_000, SiegeClock.span(span, 20_000, 24_000));
        assertEquals(0.5f, SiegeClock.bar(12_000, 0, 24_000, 0L), 1e-6, "no span: the warning");
    }

    @Test
    void thePanelStaysAboveTheHotbarAtAnySetting() {
        // The default 0.06, GUI scale 4 at 1080p (240 high), a 30 px countdown panel: near the top.
        assertEquals(14, SiegeClock.panelTop(240, 30, 0.06f, 4, 0, 0));
        // The setting's cap 0.9 (and 0.8) on that screen would put the panel over the hotbar or off the
        // bottom (216 + 30 > 240): it stops HUD_CLEARANCE above the bottom instead.
        assertEquals(240 - SiegeClock.HUD_CLEARANCE - 30, SiegeClock.panelTop(240, 30, 0.9f, 4, 0, 0));
        assertEquals(240 - SiegeClock.HUD_CLEARANCE - 30, SiegeClock.panelTop(240, 30, 0.8f, 4, 0, 0));
        // GUI scale 2 at 1080p (540 high): 0.9 is 486, also clamped; 0.32 (under a minimap) is kept.
        assertEquals(540 - SiegeClock.HUD_CLEARANCE - 30, SiegeClock.panelTop(540, 30, 0.9f, 4, 0, 0));
        assertEquals(173, SiegeClock.panelTop(540, 30, 0.32f, 4, 0, 0));
        assertEquals(4, SiegeClock.panelTop(540, 30, 0f, 4, 0, 0), "0 is the margin");
        // A banner over the panel moves it just under the banner, still above the hotbar.
        assertEquals(54, SiegeClock.panelTop(240, 30, 0.06f, 4, 10, 50));
        assertEquals(240 - SiegeClock.HUD_CLEARANCE - 30, SiegeClock.panelTop(240, 30, 0.06f, 4, 10, 200));
        // A banner above a low panel leaves it where it is.
        assertEquals(173, SiegeClock.panelTop(540, 30, 0.32f, 4, 10, 50));
        // A screen too short for the panel: its top stays on screen.
        assertEquals(4, SiegeClock.panelTop(60, 30, 0.9f, 4, 0, 0));
    }

    @Test
    void thePanelFadesInAndOut() {
        assertEquals(0f, SiegeClock.opacity(1000, Long.MAX_VALUE, 999), 1e-6);
        assertEquals(1f, SiegeClock.opacity(1000, Long.MAX_VALUE, 2000), 1e-6);
        long end = 1000 + SiegeClock.OUTCOME_MS;
        assertEquals(1f, SiegeClock.opacity(1000, end, 5000), 1e-6);
        assertTrue(SiegeClock.opacity(1000, end, end - 100) < 0.2f);
        assertEquals(0f, SiegeClock.opacity(1000, end, end), 1e-6);
    }

    // ---- Range ring --------------------------------------------------------------------------------

    @Test
    void theRingThinsWithTheParticlesSetting() {
        assertEquals(75, RangeRing.points(RangeRing.Particles.ALL));
        assertEquals(38, RangeRing.points(RangeRing.Particles.DECREASED));
        assertEquals(19, RangeRing.points(RangeRing.Particles.MINIMAL));
    }

    @Test
    void ringPointsSitOnTheCircle() {
        double[] north = RangeRing.point(10, 20, 24, 0, 4, 0);
        assertEquals(10, north[0], 1e-9);
        assertEquals(-4, north[1], 1e-9);
        double[] east = RangeRing.point(10, 20, 24, 1, 4, 0);
        assertEquals(34, east[0], 1e-9);
        assertEquals(20, east[1], 1e-9);
        assertTrue(RangeRing.pulseDue(Long.MIN_VALUE, 0));
        assertFalse(RangeRing.pulseDue(1000, 2000));
        assertTrue(RangeRing.pulseDue(1000, 3000));
        assertTrue(RangeRing.near(3, 4, 0, 0, 5));
        assertFalse(RangeRing.near(3, 4.1, 0, 0, 5));
    }

    @Test
    void theRingFindsTheGround() {
        // Ground is solid up to y 63.
        assertEquals(64, RangeRing.surface(70, 50, 80, y -> y <= 63));
        assertEquals(64, RangeRing.surface(60, 50, 80, y -> y <= 63));
        // A cliff face: solid all the way up the range.
        assertEquals(Integer.MIN_VALUE, RangeRing.surface(60, 50, 80, y -> true));
        // Open air all the way down.
        assertEquals(Integer.MIN_VALUE, RangeRing.surface(60, 50, 80, y -> false));
    }

    // ---- Arrow and ledger --------------------------------------------------------------------------

    @Test
    void theArrowPointsRelativeToTheView() {
        assertEquals(0f, HomeArrow.relative(0, 0, 0, 10, 0f), 1e-4, "south, facing south");
        assertEquals(180f, Math.abs(HomeArrow.relative(0, 0, 0, -10, 0f)), 1e-4);
        assertEquals(90f, HomeArrow.relative(0, 0, -10, 0, 0f), 1e-4, "west is to the right when facing south");
        assertEquals(0f, HomeArrow.relative(0, 0, -10, 0, 90f), 1e-4, "facing west");
        assertEquals(-170f, HomeArrow.wrap(190f), 1e-4);
        assertEquals(10, HomeArrow.distance(0, 0, 6, 8));
    }

    @Test
    void keepersRankByTheWindowAndPool() {
        List<LedgerView.Entry> entries = List.of(
                new LedgerView.Entry("Rowan", new double[] {10, 0, 0, 0, 0}, new double[] {500, 0, 0, 0, 0}),
                new LedgerView.Entry("Amman", new double[] {5, 30, 0, 0, 0}, new double[] {100, 30, 0, 0, 0}),
                new LedgerView.Entry("Zed", new double[] {0, 0, 0, 0, 0}, new double[] {1, 0, 0, 0, 0}));
        assertEquals(List.of(1, 0), LedgerView.rank(entries, LedgerView.Window.WEEK, -1));
        assertEquals(List.of(0, 1, 2), LedgerView.rank(entries, LedgerView.Window.LIFETIME, -1));
        assertEquals(List.of(0, 1), LedgerView.rank(entries, LedgerView.Window.WEEK, 0));
        assertEquals(List.of(1), LedgerView.rank(entries, LedgerView.Window.WEEK, 1));
    }

    @Test
    void stackedBarsAddUpExactly() {
        int[] seg = LedgerView.segments(new double[] {1, 1, 1, 0, 0}, 10);
        assertEquals(10, seg[0] + seg[1] + seg[2] + seg[3] + seg[4]);
        assertEquals(0, seg[3]);
        int[] sliver = LedgerView.segments(new double[] {1000, 1, 0, 0, 0}, 40);
        assertEquals(1, sliver[1], "a sliver still shows");
        assertEquals(40, sliver[0] + sliver[1]);
        assertArrayEquals(new int[] {0, 0, 0}, LedgerView.segments(new double[] {0, 0, 0}, 40));
    }

    @Test
    void aPoolRankingNamesOnlyTheSourcesThatFeedIt() {
        dev.amman.proficiency.hearth.HearthData d = dev.amman.proficiency.hearth.HearthData.DEFAULTS;
        assertTrue(LedgerView.feeds("mining", HEARTY, d));
        assertFalse(LedgerView.feeds("smithing", HEARTY, d), "Smithing feeds Workshop");
        assertTrue(LedgerView.feeds("smithing", WORKSHOP, d));
        assertTrue(LedgerView.feeds("siege", HEARTY, d), "defending the home feeds every pool");
        assertTrue(LedgerView.feeds("tales", dev.amman.proficiency.hearth.HearthPool.HOMECOMING.ordinal(), d));
        assertFalse(LedgerView.feeds("tales", HEARTY, d));
        assertTrue(LedgerView.feeds("feast", dev.amman.proficiency.hearth.HearthPool.HOMECOMING.ordinal(), d));
        assertTrue(LedgerView.feeds("some_pack_source", HEARTY, d), "an unknown source is kept");
    }
}
