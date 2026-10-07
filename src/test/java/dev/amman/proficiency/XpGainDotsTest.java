package dev.amman.proficiency;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import dev.amman.proficiency.client.XpGainDots;
import org.junit.jupiter.api.Test;

/** The HUD's XP-gain dots, driven with a fake clock. */
class XpGainDotsTest {

    @Test
    void dotCountScalesWithTheGainAndIsCapped() {
        assertEquals(0, XpGainDots.dotCount(0));
        assertEquals(0, XpGainDots.dotCount(-0.3));
        assertEquals(0, XpGainDots.dotCount(Double.NaN));
        assertEquals(2, XpGainDots.dotCount(0.01));
        assertEquals(4, XpGainDots.dotCount(0.08));
        assertEquals(XpGainDots.MAX_PER_GAIN, XpGainDots.dotCount(0.5));
        assertEquals(XpGainDots.MAX_PER_GAIN, XpGainDots.dotCount(3.0));
    }

    @Test
    void easingRunsFromZeroToOneAndNeverBacktracks() {
        assertEquals(0, XpGainDots.ease(-1));
        assertEquals(1, XpGainDots.ease(2));
        assertEquals(0.5, XpGainDots.ease(0.5), 1e-9);
        double last = 0;
        for (int i = 1; i <= 100; i++) {
            double e = XpGainDots.ease(i / 100.0);
            assertTrue(e >= last);
            last = e;
        }
    }

    @Test
    void theBarFillsOnlyAsDotsLand() {
        XpGainDots dots = new XpGainDots();
        dots.snap(5.20, 0);
        dots.gain(5.20, 5.30, 0);
        int count = dots.dots().size();
        assertEquals(XpGainDots.dotCount(5.30 - 5.20), count);
        assertTrue(count >= 4 && count <= 5);

        // Nothing has landed yet: the bar holds at the old value although the server says 5.30.
        assertEquals(5.20, dots.update(5.30, 100), 1e-6);
        assertTrue(dots.busy(5.30, 100));

        // After the last one lands and the fill settles, the bar shows the real value.
        long end = XpGainDots.FLIGHT_MS + 60 + count * XpGainDots.STAGGER_MS + 2000;
        double shown = 0;
        for (long t = 100; t <= end; t += 16) {
            double next = dots.update(5.30, t);
            assertTrue(next >= shown - 1e-9, "the fill never goes backwards");
            shown = next;
        }
        assertEquals(5.30, shown, 1e-6);
        assertTrue(dots.dots().isEmpty());
        assertFalse(dots.busy(5.30, end + 1000));
    }

    @Test
    void aLossOrTheFirstLookSnaps() {
        XpGainDots dots = new XpGainDots();
        dots.gain(0, 3.5, 0);
        assertTrue(dots.dots().isEmpty());
        assertEquals(3.5, dots.update(3.5, 10), 1e-9);

        dots.gain(3.5, 3.7, 20);
        assertFalse(dots.dots().isEmpty());
        dots.gain(3.7, 3.0, 30); // death wipes the bar
        assertTrue(dots.dots().isEmpty());
        assertEquals(3.0, dots.update(3.0, 40), 1e-9);
    }

    @Test
    void theSkyNeverHoldsMoreThanTheCap() {
        XpGainDots dots = new XpGainDots();
        dots.snap(1.0, 0);
        double value = 1.0;
        for (int i = 0; i < 50; i++) {
            dots.gain(value, value + 0.5, i);
            value += 0.5;
        }
        assertEquals(XpGainDots.MAX_LIVE, dots.dots().size());
        // Gains past the cap are not lost: they reach the bar through the fill.
        double shown = 0;
        for (long t = 50; t < 20_000; t += 16) {
            shown = dots.update(value, t);
        }
        assertEquals(value, shown, 1e-6);
    }
}
