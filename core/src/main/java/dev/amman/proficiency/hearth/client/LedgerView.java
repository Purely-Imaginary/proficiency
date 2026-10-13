package dev.amman.proficiency.hearth.client;

import java.util.ArrayList;
import java.util.List;

/**
 * The "Hearth-keepers" tab's ordering and bars (docs/HEARTH-SPEC.md, "Who feeds the Hearth"): a
 * ranked list for this week or for the Hearth's life, all pools or one pool, and each player's
 * stacked bar of shares in the pool colours. Pure, so the ranking and the bar maths are tested.
 */
public final class LedgerView {

    public enum Window { WEEK, LIFETIME }

    /** One keeper: charge per pool (by pool ordinal) in each window. */
    public record Entry(String name, double[] week, double[] lifetime) {

        public double[] in(Window window) {
            return window == Window.WEEK ? week : lifetime;
        }

        /** What they added in the window, to every pool ({@code pool} below 0) or to one. */
        public double total(Window window, int pool) {
            double[] values = in(window);
            if (values == null) {
                return 0;
            }
            if (pool >= 0) {
                return pool < values.length && Double.isFinite(values[pool]) ? Math.max(0, values[pool]) : 0;
            }
            double sum = 0;
            for (double value : values) {
                if (Double.isFinite(value) && value > 0) {
                    sum += value;
                }
            }
            return sum;
        }
    }

    private LedgerView() {
    }

    /**
     * Whether a ledger source can feed {@code pool} under {@code data}: a skill feeds the pool its
     * mapping names; siege defence feeds every pool; Tales and the Chronicle feed the Tales pool (every
     * pool with {@code feedAll}); Feasts the Feast pool. The keepers tab ranked by one pool lists only
     * these among a keeper's top sources. A source it does not know is kept.
     */
    public static boolean feeds(String source, int pool, dev.amman.proficiency.hearth.HearthData data) {
        dev.amman.proficiency.hearth.HearthPool want = dev.amman.proficiency.hearth.HearthPool.byIndex(pool);
        if (want == null || source == null) {
            return true;
        }
        switch (source) {
            case dev.amman.proficiency.hearth.HearthLedger.SIEGE:
                return true;
            case dev.amman.proficiency.hearth.HearthLedger.TALES:
            case dev.amman.proficiency.hearth.HearthLedger.CHRONICLE:
                return data.tales().feedAll() || data.talesPool() == want;
            case dev.amman.proficiency.hearth.HearthLedger.FEAST:
                return data.feastPool() == want;
            default:
                dev.amman.proficiency.skill.Skill skill = dev.amman.proficiency.skill.Skill.byId(source);
                return skill == null || data.poolFor(skill) == want;
        }
    }

    /**
     * Indexes of the entries with anything in the window (and pool), most first; equal totals by
     * name, so the order never flickers between two opens.
     */
    public static List<Integer> rank(List<Entry> entries, Window window, int pool) {
        List<Integer> order = new ArrayList<>();
        for (int i = 0; i < entries.size(); i++) {
            if (entries.get(i).total(window, pool) > 0) {
                order.add(i);
            }
        }
        order.sort((a, b) -> {
            int byTotal = Double.compare(entries.get(b).total(window, pool), entries.get(a).total(window, pool));
            return byTotal != 0 ? byTotal : entries.get(a).name().compareToIgnoreCase(entries.get(b).name());
        });
        return order;
    }

    /**
     * Pixel widths of a stacked bar of {@code width} pixels, one segment per pool in pool order,
     * shaped like {@code perPool}. The widths add up to exactly {@code width} (largest remainder), a
     * pool with any share gets at least one pixel when there is room, and an empty row gets zeros.
     */
    public static int[] segments(double[] perPool, int width) {
        int n = perPool == null ? 0 : perPool.length;
        int[] out = new int[n];
        if (n == 0 || width <= 0) {
            return out;
        }
        double total = 0;
        int nonZero = 0;
        for (double value : perPool) {
            if (Double.isFinite(value) && value > 0) {
                total += value;
                nonZero++;
            }
        }
        if (!(total > 0)) {
            return out;
        }
        double[] rest = new double[n];
        int used = 0;
        for (int i = 0; i < n; i++) {
            double value = Double.isFinite(perPool[i]) && perPool[i] > 0 ? perPool[i] : 0;
            double exact = value / total * width;
            out[i] = (int) Math.floor(exact);
            rest[i] = exact - out[i];
            used += out[i];
        }
        while (used < width) {
            int best = -1;
            for (int i = 0; i < n; i++) {
                if (perPool[i] > 0 && (best < 0 || rest[i] > rest[best])) {
                    best = i;
                }
            }
            out[best]++;
            rest[best] = -1;
            used++;
        }
        if (nonZero <= width) {
            // A sliver still shows: take a pixel from the widest segment for each empty one.
            for (int i = 0; i < n; i++) {
                if (perPool[i] > 0 && out[i] == 0) {
                    int widest = 0;
                    for (int j = 1; j < n; j++) {
                        if (out[j] > out[widest]) {
                            widest = j;
                        }
                    }
                    if (out[widest] > 1) {
                        out[widest]--;
                        out[i] = 1;
                    }
                }
            }
        }
        return out;
    }
}
