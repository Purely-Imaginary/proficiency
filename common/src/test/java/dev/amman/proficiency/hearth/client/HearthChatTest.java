package dev.amman.proficiency.hearth.client;

import dev.amman.proficiency.hearth.HearthPool;
import dev.amman.proficiency.hearth.charge.ChargeText;
import dev.amman.proficiency.hearth.charge.KeeperRows;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.contents.TranslatableContents;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The tier-up chat line is settled by the frame that plays (or does not play) the tracker's moment,
 * not by whether the tracker happened to be drawn when the line came (code review, 2026-10-09).
 */
class HearthChatTest {

    private static final long POS = 42L;
    private static final int HEARTY = HearthPool.HEARTY.ordinal();

    private static HearthChat.Held held(int pool, int tier, long at) {
        return new HearthChat.Held(Component.literal("line"), pool, tier, at);
    }

    /** A tracker at tier II, half way to III. */
    private static HearthTrackerModel atTwoAndAHalf(double[] th) {
        HearthTrackerModel model = new HearthTrackerModel();
        model.apply(POS, HEARTY, (th[1] + th[2]) / 2, 2, 0, 0, 0);
        assertEquals(2, model.row(HEARTY).displayedTier());
        return model;
    }

    @Test
    void theLineNamesItsPoolAndTier() {
        TranslatableContents alone = HearthChat.tierUpLine(ChargeText.tierUp(HearthPool.WORKSHOP, 4, null, List.of()));
        assertNotNull(alone);
        assertEquals(HearthPool.WORKSHOP.ordinal(), HearthChat.poolOf(alone));
        assertEquals(4, HearthChat.tierOf(alone));
        TranslatableContents named = HearthChat.tierUpLine(ChargeText.tierUp(HearthPool.HOMECOMING, 6,
                new KeeperRows.Top(new UUID(1, 2), "Amman", 120, "farming"), List.of()));
        assertNotNull(named);
        assertEquals(HearthPool.HOMECOMING.ordinal(), HearthChat.poolOf(named));
        assertEquals(6, HearthChat.tierOf(named));
        // Off the wire a plain argument can come back as a literal component.
        TranslatableContents wire = HearthChat.tierUpLine(Component.translatable(HearthChat.TIER_UP_ALONE,
                Component.translatable(HearthPool.REGEN.translationKey()), Component.literal("III")));
        assertNotNull(wire);
        assertEquals(HearthPool.REGEN.ordinal(), HearthChat.poolOf(wire));
        assertEquals(3, HearthChat.tierOf(wire));
        TranslatableContents odd = HearthChat.tierUpLine(Component.translatable(HearthChat.TIER_UP_ALONE, "x", "y"));
        assertNotNull(odd);
        assertEquals(-1, HearthChat.poolOf(odd));
        assertEquals(-1, HearthChat.tierOf(odd));
    }

    @Test
    void aMomentPlayedOnTheScreenReplacesTheLine() {
        HearthTrackerModel model = atTwoAndAHalf(new HearthTrackerModel().thresholdsCopy());
        int bit = 1 << HEARTY;
        assertEquals(HearthChat.Verdict.SHOWN, HearthChat.verdict(held(HEARTY, 3, 0), 10, bit, model, true));
        assertEquals(HearthChat.Verdict.WAIT, HearthChat.verdict(held(HEARTY, 3, 0), 10,
                1 << HearthPool.REGEN.ordinal(), model, true), "another pool's moment is not this line's");
        assertEquals(HearthChat.Verdict.SHOWN, HearthChat.verdict(held(-1, -1, 0), 10, bit, model, true),
                "an unreadable line goes with any moment");
    }

    @Test
    void theLineComesThroughWhenTheMomentIsNotShown() {
        double[] th = new HearthTrackerModel().thresholdsCopy();
        HearthTrackerModel model = atTwoAndAHalf(th);
        assertEquals(HearthChat.Verdict.WAIT, HearthChat.verdict(held(HEARTY, 3, 0), 10, 0, model, true));
        assertEquals(HearthChat.Verdict.RELEASE, HearthChat.verdict(held(HEARTY, 3, 0), 10, 0, model, false),
                "tracker or hud.levelUpFx switched off");
        assertEquals(HearthChat.Verdict.RELEASE, HearthChat.verdict(held(HEARTY, 3, 0), HearthChat.HOLD_MS, 0, model,
                true), "nothing came in time");
        // Arriving in range snaps the rows to the server's numbers: the numeral is there, nothing plays.
        model.apply(POS, HEARTY, th[2] + 1, 3, 0, 0, 20, true);
        assertEquals(HearthChat.Verdict.RELEASE, HearthChat.verdict(held(HEARTY, 3, 0), 30, 0, model, true));
    }

    @Test
    void theTrackersMomentComesWithinTheHoldAfterAGain() {
        double[] th = new HearthTrackerModel().thresholdsCopy();
        HearthTrackerModel model = atTwoAndAHalf(th);
        double before = model.row(HEARTY).charge();
        double after = th[2] + 1;
        // The server's order: the chat line, then the state with the new tier, then the gain with the skill sync.
        HearthChat.Held line = held(HEARTY, 3, 100);
        model.apply(POS, HEARTY, after, 3, 0, 0, 110);
        for (long now = 120; now < 600; now += 16) {
            assertEquals(HearthChat.Verdict.WAIT, HearthChat.verdict(line, now, model.frame(now, true, true), model,
                    true), "the bar holds back by the credit until the gain's dots fly");
        }
        // The gain half a second later, its dots waiting for the skill line's own to land.
        model.gain(HEARTY, after - before, 600, 560, true);
        assertEquals(HearthChat.Verdict.SHOWN, settle(model, line, 600, true),
                "the tier-up plays before the line would be let through");
    }

    @Test
    void theMomentAlsoComesWithinTheHoldWhenNoGainClaimsTheCredit() {
        double[] th = new HearthTrackerModel().thresholdsCopy();
        HearthTrackerModel model = atTwoAndAHalf(th);
        HearthChat.Held line = held(HEARTY, 3, 100);
        model.apply(POS, HEARTY, th[2] + 1, 3, 0, 0, 110);
        assertEquals(HearthChat.Verdict.SHOWN, settle(model, line, 120, true));
    }

    @Test
    void withTheDotsOffTheMomentPlaysAtOnce() {
        double[] th = new HearthTrackerModel().thresholdsCopy();
        HearthTrackerModel model = atTwoAndAHalf(th);
        HearthChat.Held line = held(HEARTY, 3, 100);
        model.apply(POS, HEARTY, th[2] + 1, 3, 0, 0, 110);
        int rose = model.frame(120, false, true);
        assertTrue((rose & (1 << HEARTY)) != 0);
        assertEquals(HearthChat.Verdict.SHOWN, HearthChat.verdict(line, 120, rose, model, true));
    }

    @Test
    void aHiddenTrackerLetsTheLineThroughOnceTheNumeralTurns() {
        double[] th = new HearthTrackerModel().thresholdsCopy();
        HearthTrackerModel model = atTwoAndAHalf(th);
        HearthChat.Held line = held(HEARTY, 3, 100);
        model.apply(POS, HEARTY, th[2] + 1, 3, 0, 0, 110);
        // The GUI is hidden (F1): the model runs with no animation and nothing is played on the screen.
        model.frame(120, false, false);
        assertEquals(HearthChat.Verdict.RELEASE, HearthChat.verdict(line, 120, 0, model, true));
    }

    /** Runs frames as the tracker does (played = the rose bits while drawn) until the line is settled. */
    private static HearthChat.Verdict settle(HearthTrackerModel model, HearthChat.Held line, long from, boolean drawn) {
        for (long now = from; now < line.at() + HearthChat.HOLD_MS + 1000; now += 16) {
            int rose = model.frame(now, true, true);
            HearthChat.Verdict verdict = HearthChat.verdict(line, now, drawn ? rose : 0, model, true);
            if (verdict != HearthChat.Verdict.WAIT) {
                if (verdict == HearthChat.Verdict.SHOWN) {
                    assertTrue(now - line.at() < HearthChat.HOLD_MS, "shown at " + (now - line.at()) + " ms");
                }
                return verdict;
            }
        }
        return HearthChat.Verdict.WAIT;
    }
}
