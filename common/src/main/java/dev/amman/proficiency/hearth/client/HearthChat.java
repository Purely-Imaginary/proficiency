package dev.amman.proficiency.hearth.client;

import dev.amman.proficiency.config.ProficiencyClientConfig;
import dev.amman.proficiency.hearth.HearthPool;
import dev.amman.proficiency.hearth.HearthRules;
import net.minecraft.Util;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.contents.TranslatableContents;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

/**
 * "The tracker's tier-up replaces it for you" (docs/HEARTH-SPEC.md, "Chat"): the charge lane's tier-up
 * chat line ({@code proficiency.hearth.charge.tier_up} and {@code .alone}) is not shown on this client when
 * the Hearth tracker plays the same moment.
 *
 * <p>The line comes first and the tracker's moment later: the numeral waits until the fill has reached the
 * tier (the gain's dots, up to about 2.5 s). So the line is not judged on arrival. With the tracker and
 * {@code hud.levelUpFx} on, it is held ({@link #hold}) and settled by the same frame that starts the
 * tracker's {@link TierUpFx} ({@link #settle}):
 * <ul>
 * <li>the tracker played the tier-up of that pool on the screen: the line is dropped;</li>
 * <li>the numeral reached the tier without it (a snap on arriving, the GUI hidden, the tracker faded or
 * switched off), or {@link #HOLD_MS} passed: the line goes to the chat after all.</li>
 * </ul>
 * With the tracker or {@code hud.levelUpFx} off the line is never held.
 */
public final class HearthChat {

    static final String TIER_UP = "proficiency.hearth.charge.tier_up";
    static final String TIER_UP_ALONE = "proficiency.hearth.charge.tier_up.alone";
    /** Longest a line waits for the tracker's moment: a gain's credit release and dot flight are under 2.5 s. */
    static final long HOLD_MS = 4000;

    /** A held line: the pool and tier it names (-1 when the line cannot be read), and when it came. */
    record Held(Component message, int pool, int tier, long at) {
    }

    /** What becomes of a held line this frame. */
    enum Verdict {
        /** Keep it a while yet. */
        WAIT,
        /** The tracker showed the moment: drop the line. */
        SHOWN,
        /** The moment will not be shown: the line goes to the chat. */
        RELEASE
    }

    private static final List<Held> HELD = new ArrayList<>();

    private HearthChat() {
    }

    /**
     * True when the loader should drop this chat (not action-bar) line now: a Hearth tier-up line while the
     * tracker could show the moment. The line is kept and may still reach the chat a few seconds later.
     */
    public static boolean hold(Component message, boolean overlay) {
        if (overlay) {
            return false;
        }
        TranslatableContents line = tierUpLine(message);
        if (line == null || !wanted()) {
            return false;
        }
        HELD.add(new Held(message, poolOf(line), tierOf(line), Util.getMillis()));
        return true;
    }

    /** The tracker can play a tier-up at all: it is on (or the demo forces it) and so is {@code hud.levelUpFx}. */
    private static boolean wanted() {
        return (ProficiencyClientConfig.hearthTracker() || HearthDemo.trackerForced())
                && ProficiencyClientConfig.hudLevelUpFx();
    }

    /**
     * Once a frame, after the tracker's model moved. {@code played} has a bit per pool whose tier-up the
     * tracker started this frame while on the screen (0 when it is hidden).
     */
    static void settle(long now, int played, HearthTrackerModel model) {
        if (HELD.isEmpty()) {
            return;
        }
        boolean wanted = wanted();
        Iterator<Held> it = HELD.iterator();
        while (it.hasNext()) {
            Held held = it.next();
            Verdict verdict = verdict(held, now, played, model, wanted);
            if (verdict == Verdict.WAIT) {
                continue;
            }
            it.remove();
            if (verdict == Verdict.RELEASE) {
                Minecraft.getInstance().gui.getChat().addMessage(held.message());
            }
        }
    }

    /** The rule {@link #settle} applies to one held line. */
    static Verdict verdict(Held held, long now, int played, HearthTrackerModel model, boolean wanted) {
        boolean shown = held.pool() < 0 ? played != 0 : (played & (1 << held.pool())) != 0;
        if (shown) {
            return Verdict.SHOWN;
        }
        if (!wanted || now - held.at() >= HOLD_MS) {
            return Verdict.RELEASE;
        }
        if (held.pool() >= 0 && held.tier() > 0) {
            HearthTrackerModel.Row row = model.row(held.pool());
            // The numeral is there already and nothing was played for it: the line is the only news.
            if (row != null && row.seen() && row.displayedTier() >= held.tier()) {
                return Verdict.RELEASE;
            }
        }
        return Verdict.WAIT;
    }

    /** Leaving a world: lines from it are not news on the next server. */
    static void reset() {
        HELD.clear();
    }

    static int heldCount() {
        return HELD.size();
    }

    /** Whether the line is the Hearth's tier-up line, wherever it sits in the component tree. */
    static boolean isTierUp(Component message) {
        return tierUpLine(message) != null;
    }

    @Nullable
    static TranslatableContents tierUpLine(Component message) {
        if (message.getContents() instanceof TranslatableContents translatable) {
            String key = translatable.getKey();
            if (TIER_UP.equals(key) || TIER_UP_ALONE.equals(key)) {
                return translatable;
            }
        }
        for (Component sibling : message.getSiblings()) {
            TranslatableContents found = tierUpLine(sibling);
            if (found != null) {
                return found;
            }
        }
        return null;
    }

    /** The pool the line names (its first argument, the pool's name), or -1. */
    static int poolOf(TranslatableContents line) {
        Object arg = line.getArgs().length > 0 ? line.getArgs()[0] : null;
        if (arg instanceof Component component && component.getContents() instanceof TranslatableContents name) {
            for (HearthPool pool : HearthPool.VALUES) {
                if (pool.translationKey().equals(name.getKey())) {
                    return pool.ordinal();
                }
            }
        }
        return -1;
    }

    /** The tier the line names (its second argument, a roman numeral; a string or, off the wire, a literal), or -1. */
    static int tierOf(TranslatableContents line) {
        Object arg = line.getArgs().length > 1 ? line.getArgs()[1] : null;
        String text = arg instanceof Component component ? component.getString() : arg instanceof String s ? s : null;
        if (text == null || text.isEmpty()) {
            return -1;
        }
        for (int tier = 1; tier <= dev.amman.proficiency.hearth.HearthData.TIERS; tier++) {
            if (HearthRules.roman(tier).equals(text)) {
                return tier;
            }
        }
        return -1;
    }
}
