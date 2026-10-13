package dev.amman.proficiency.hearth.charge;

import dev.amman.proficiency.hearth.HearthLedger;
import dev.amman.proficiency.hearth.HearthPool;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * The "Hearth-keepers" table from a ledger (docs/HEARTH-SPEC.md, "Who feeds the Hearth"): players
 * ranked by what they fed in a window, each with their share per pool and their top sources. One
 * builder for the chat command and the panel's packet, so both print the same table.
 */
public final class KeeperRows {

    /**
     * One player in one window.
     *
     * @param total   what they fed in the window: one pool, or every pool when the table is for all
     * @param perPool what they fed each pool in the window, by {@link HearthPool} ordinal
     * @param sources their sources in the window (for the pool, or every pool), biggest first
     */
    public record Row(UUID player, String name, double total, double[] perPool,
            List<HearthLedger.SourceTotal> sources, int siegeKills, int siegesDefended) {
    }

    private KeeperRows() {
    }

    /**
     * The table, biggest feeder first, at most {@code limit} rows (0 or less for all). Players who fed
     * nothing in the window are left out. {@code pool} null means every pool. The caller rolls the
     * ledger to today first, so the week is the last seven days.
     */
    public static List<Row> of(HearthLedger ledger, HearthPool pool, HearthLedger.Window window, int limit) {
        List<Row> rows = new ArrayList<>();
        for (HearthLedger.Rank rank : ledger.ranking(pool, window)) {
            if (limit > 0 && rows.size() >= limit) {
                break;
            }
            rows.add(row(ledger, rank.player(), pool, window));
        }
        return rows;
    }

    /**
     * Every player the ledger knows, ranked by lifetime total (players with only siege counts last),
     * at most {@code limit}: the panel's packet carries both windows for these.
     */
    public static List<UUID> everyone(HearthLedger ledger, int limit) {
        List<UUID> out = new ArrayList<>();
        for (HearthLedger.Rank rank : ledger.ranking(null, HearthLedger.Window.LIFETIME)) {
            out.add(rank.player());
        }
        List<UUID> rest = new ArrayList<>();
        for (Map.Entry<UUID, HearthLedger.Entry> entry : ledger.players().entrySet()) {
            HearthLedger.Entry e = entry.getValue();
            if (!out.contains(entry.getKey()) && (e.siegeKills() > 0 || e.siegesDefended() > 0)) {
                rest.add(entry.getKey());
            }
        }
        rest.sort(Comparator.comparingInt((UUID id) -> -ledger.get(id).siegeKills()).thenComparing(UUID::toString));
        out.addAll(rest);
        return limit > 0 && out.size() > limit ? new ArrayList<>(out.subList(0, limit)) : out;
    }

    /** One player's row in a window; {@code pool} null for every pool. */
    public static Row row(HearthLedger ledger, UUID player, HearthPool pool, HearthLedger.Window window) {
        HearthLedger.Entry entry = ledger.get(player);
        double[] perPool = new double[HearthPool.COUNT];
        for (HearthPool p : HearthPool.VALUES) {
            perPool[p.ordinal()] = ledger.total(player, p, window);
        }
        double total = ledger.total(player, pool, window);
        return new Row(player, entry == null || entry.name() == null ? "" : entry.name(), total, perPool,
                ledger.sources(player, pool, window), entry == null ? 0 : entry.siegeKills(),
                entry == null ? 0 : entry.siegesDefended());
    }

    /** The top feeder of a pool this week and their biggest source for it, or null when nobody fed it. */
    public static Top top(HearthLedger ledger, HearthPool pool) {
        HearthLedger.Rank rank = ledger.topFeeder(pool, HearthLedger.Window.WEEK);
        if (rank == null) {
            return null;
        }
        List<HearthLedger.SourceTotal> sources = ledger.sources(rank.player(), pool, HearthLedger.Window.WEEK);
        return new Top(rank.player(), rank.name() == null ? "" : rank.name(), rank.total(),
                sources.isEmpty() ? null : sources.get(0).source());
    }

    /** "Carried by Rowan's farming": the week's top feeder of a pool and their biggest source. */
    public record Top(UUID player, String name, double total, String source) {
    }

    /**
     * A stacked bar of {@code cells} cells: how many cells each pool gets of a player's total, by
     * largest remainder so the cells always add up to {@code cells} when the total is above 0.
     */
    public static int[] cells(double[] perPool, int cells) {
        int[] out = new int[perPool == null ? 0 : perPool.length];
        if (perPool == null || cells <= 0) {
            return out;
        }
        double total = 0.0;
        for (double v : perPool) {
            if (Double.isFinite(v) && v > 0) {
                total += v;
            }
        }
        if (total <= 0) {
            return out;
        }
        double[] rest = new double[out.length];
        int given = 0;
        for (int i = 0; i < out.length; i++) {
            double v = Double.isFinite(perPool[i]) && perPool[i] > 0 ? perPool[i] : 0.0;
            double exact = v / total * cells;
            out[i] = (int) Math.floor(exact);
            rest[i] = exact - out[i];
            given += out[i];
        }
        while (given < cells) {
            int best = -1;
            for (int i = 0; i < out.length; i++) {
                if (best < 0 || rest[i] > rest[best]) {
                    best = i;
                }
            }
            out[best]++;
            rest[best] = -1.0;
            given++;
        }
        return out;
    }
}
