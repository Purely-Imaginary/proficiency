package dev.amman.proficiency.skill;

import java.util.ArrayDeque;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

/**
 * One player's trading memory: the anti-farm rules for Commerce. Pure Java; {@code CommerceEvents}
 * feeds it a game time and an offer signature.
 *
 * <p>Two rules decide a trade's share of its XP, and they multiply:
 * <ul>
 * <li>the repeat rule: the same offer (what is paid and what comes back) pays less each time within
 * one restock cycle ({@link CommerceMath#repeatFactor}), so buying and selling the same paper again and
 * again to restock-cycle a villager is worth a tenth by the fifth time;</li>
 * <li>the burst rule: more than 20 trades in 5 minutes fade ({@link CommerceMath#burstFactor}), so
 * walking down a trading hall of different offers does not make a farm either.</li>
 * </ul>
 * Bartering has its own fade ({@link CommerceMath#barterFactor}).
 */
public final class CommerceTracker {

    private static final int MAX_SIGNATURES = 512;

    private final Map<String, ArrayDeque<Long>> repeats = new HashMap<>();
    private final ArrayDeque<Long> trades = new ArrayDeque<>();
    private final ArrayDeque<Long> barters = new ArrayDeque<>();

    /** The share of a trade's XP that is paid now, and the trade is remembered (even when it pays little). */
    public synchronized double tradeFactor(String signature, long now) {
        purge(trades, now, CommerceMath.BURST_WINDOW_TICKS);
        int burst = trades.size();
        ArrayDeque<Long> seen = repeats.computeIfAbsent(signature, key -> new ArrayDeque<>());
        purge(seen, now, CommerceMath.REPEAT_WINDOW_TICKS);
        int prior = seen.size();
        seen.addLast(now);
        trades.addLast(now);
        if (repeats.size() > MAX_SIGNATURES) {
            for (Iterator<Map.Entry<String, ArrayDeque<Long>>> it = repeats.entrySet().iterator(); it.hasNext();) {
                ArrayDeque<Long> times = it.next().getValue();
                purge(times, now, CommerceMath.REPEAT_WINDOW_TICKS);
                if (times.isEmpty()) {
                    it.remove();
                }
            }
        }
        return CommerceMath.repeatFactor(prior) * CommerceMath.burstFactor(burst);
    }

    /** The share of a barter's XP that is paid now; the barter is remembered. */
    public synchronized double barterFactor(long now) {
        purge(barters, now, CommerceMath.BARTER_WINDOW_TICKS);
        int recent = barters.size();
        barters.addLast(now);
        return CommerceMath.barterFactor(recent);
    }

    public synchronized void clear() {
        repeats.clear();
        trades.clear();
        barters.clear();
    }

    private static void purge(ArrayDeque<Long> times, long now, long window) {
        while (!times.isEmpty()) {
            long first = times.peekFirst();
            if (now < first || now - first >= window) {
                times.pollFirst();
            } else {
                break;
            }
        }
    }
}
