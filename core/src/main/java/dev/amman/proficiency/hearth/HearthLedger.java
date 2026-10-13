package dev.amman.proficiency.hearth;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Who fed the Hearth (docs/HEARTH-SPEC.md, "Who feeds the Hearth"): charge added per player, per
 * pool and per source, over the Hearth's lifetime and in a ring of seven daily buckets (the last 7
 * days; day 8 drops off). Sources are skill ids or {@link #SIEGE}, {@link #TALES},
 * {@link #CHRONICLE}, {@link #FEAST}. Also per player: siege kills and sieges defended.
 *
 * <p>Every point added lands in exactly one (player, pool, source) cell, so the cells always sum
 * to what was added. Decay and siege losses come off the pool, never the ledger. The day number is
 * the caller's (the charge lane passes {@code RestedMath.today()}, a real day).
 */
public final class HearthLedger {

    public static final String SIEGE = "siege";
    public static final String TALES = "tales";
    public static final String CHRONICLE = "chronicle";
    public static final String FEAST = "feast";

    public static final int DAYS = 7;

    public enum Window { WEEK, LIFETIME }

    /** One (pool, source) cell of one player. */
    public static final class Cell {
        double lifetime;
        final double[] days = new double[DAYS];

        public double lifetime() {
            return lifetime;
        }

        double week() {
            double sum = 0.0;
            for (double day : days) {
                sum += day;
            }
            return sum;
        }

        double of(Window window) {
            return window == Window.LIFETIME ? lifetime : week();
        }
    }

    /** One player's part of the ledger. */
    public static final class Entry {
        String name;
        final Map<String, Cell> cells = new LinkedHashMap<>();
        int siegeKills;
        int siegesDefended;

        public String name() {
            return name;
        }

        public int siegeKills() {
            return siegeKills;
        }

        public int siegesDefended() {
            return siegesDefended;
        }
    }

    /** A player's total, for a ranked list. */
    public record Rank(UUID player, String name, double total) {
    }

    /** A source's total, for "mostly Mining, Masonry, Tales". */
    public record SourceTotal(String source, double total) {
    }

    private final Map<UUID, Entry> players = new LinkedHashMap<>();
    /** The day the newest bucket holds; buckets are indexed {@code day mod 7}. */
    private long currentDay = Long.MIN_VALUE;

    static String key(HearthPool pool, String source) {
        return pool.id() + "|" + source;
    }

    /** Moves the ring to {@code day}, zeroing the buckets of days that fell out of the window. */
    public void rollTo(long day) {
        if (currentDay == Long.MIN_VALUE) {
            currentDay = day;
            return;
        }
        if (day <= currentDay) {
            return;
        }
        long steps = Math.min(DAYS, day - currentDay);
        for (long d = 1; d <= steps; d++) {
            int slot = (int) Math.floorMod(currentDay + d, (long) DAYS);
            for (Entry entry : players.values()) {
                for (Cell cell : entry.cells.values()) {
                    cell.days[slot] = 0.0;
                }
            }
        }
        currentDay = day;
    }

    public long currentDay() {
        return currentDay;
    }

    private Entry entry(UUID player, String name) {
        Entry entry = players.computeIfAbsent(player, id -> new Entry());
        if (name != null && !name.isEmpty()) {
            entry.name = name;
        } else if (entry.name == null) {
            entry.name = "";
        }
        return entry;
    }

    /**
     * Adds charge to a cell. A day older than the ring's newest is filed under the newest (the clock
     * never runs backwards on purpose). NaN, infinite and non-positive amounts add nothing.
     */
    public void add(UUID player, String name, HearthPool pool, String source, double amount, long day) {
        if (!(amount > 0) || Double.isInfinite(amount)) {
            return;
        }
        rollTo(day);
        Cell cell = entry(player, name).cells.computeIfAbsent(key(pool, source), k -> new Cell());
        cell.lifetime += amount;
        cell.days[(int) Math.floorMod(currentDay, (long) DAYS)] += amount;
    }

    public void addSiegeKill(UUID player, String name) {
        entry(player, name).siegeKills++;
    }

    public void addSiegeDefended(UUID player, String name) {
        entry(player, name).siegesDefended++;
    }

    public Map<UUID, Entry> players() {
        return java.util.Collections.unmodifiableMap(players);
    }

    public Entry get(UUID player) {
        return players.get(player);
    }

    /** One player's total in a window, every pool (pool null) or one. */
    public double total(UUID player, HearthPool pool, Window window) {
        Entry entry = players.get(player);
        if (entry == null) {
            return 0.0;
        }
        double sum = 0.0;
        for (Map.Entry<String, Cell> cell : entry.cells.entrySet()) {
            if (pool == null || cell.getKey().startsWith(pool.id() + "|")) {
                sum += cell.getValue().of(window);
            }
        }
        return sum;
    }

    /** Everyone's total in a window, every pool (pool null) or one. */
    public double grandTotal(HearthPool pool, Window window) {
        double sum = 0.0;
        for (UUID player : players.keySet()) {
            sum += total(player, pool, window);
        }
        return sum;
    }

    /** A player's sources, biggest first. */
    public List<SourceTotal> sources(UUID player, HearthPool pool, Window window) {
        Entry entry = players.get(player);
        if (entry == null) {
            return List.of();
        }
        Map<String, Double> bySource = new LinkedHashMap<>();
        for (Map.Entry<String, Cell> cell : entry.cells.entrySet()) {
            int bar = cell.getKey().indexOf('|');
            String poolId = cell.getKey().substring(0, bar);
            if (pool != null && !pool.id().equals(poolId)) {
                continue;
            }
            double value = cell.getValue().of(window);
            if (value > 0) {
                bySource.merge(cell.getKey().substring(bar + 1), value, Double::sum);
            }
        }
        List<SourceTotal> out = new ArrayList<>();
        for (Map.Entry<String, Double> e : bySource.entrySet()) {
            out.add(new SourceTotal(e.getKey(), e.getValue()));
        }
        out.sort(Comparator.comparingDouble(SourceTotal::total).reversed().thenComparing(SourceTotal::source));
        return out;
    }

    /** Players ranked by what they fed in a window, every pool (pool null) or one; zero rows left out. */
    public List<Rank> ranking(HearthPool pool, Window window) {
        List<Rank> out = new ArrayList<>();
        for (Map.Entry<UUID, Entry> entry : players.entrySet()) {
            double total = total(entry.getKey(), pool, window);
            if (total > 0) {
                out.add(new Rank(entry.getKey(), entry.getValue().name, total));
            }
        }
        out.sort(Comparator.comparingDouble(Rank::total).reversed().thenComparing(r -> r.player().toString()));
        return out;
    }

    /** The top feeder of a pool in a window, or null when nobody fed it. */
    public Rank topFeeder(HearthPool pool, Window window) {
        List<Rank> ranks = ranking(pool, window);
        return ranks.isEmpty() ? null : ranks.get(0);
    }

    public boolean isEmpty() {
        return players.isEmpty();
    }

    public void clear() {
        players.clear();
        currentDay = Long.MIN_VALUE;
    }

    // ---- Save -----------------------------------------------------------------------------------

    /** One cell as saved: player, pool|source, lifetime and the seven day buckets. */
    public record SavedCell(UUID player, String key, double lifetime, List<Double> days) {
    }

    /** One player's name and siege counts as saved. */
    public record SavedPlayer(UUID player, String name, int siegeKills, int siegesDefended) {
    }

    public List<SavedCell> savedCells() {
        List<SavedCell> out = new ArrayList<>();
        for (Map.Entry<UUID, Entry> entry : players.entrySet()) {
            for (Map.Entry<String, Cell> cell : entry.getValue().cells.entrySet()) {
                List<Double> days = new ArrayList<>(DAYS);
                for (double day : cell.getValue().days) {
                    days.add(day);
                }
                out.add(new SavedCell(entry.getKey(), cell.getKey(), cell.getValue().lifetime, days));
            }
        }
        return out;
    }

    public List<SavedPlayer> savedPlayers() {
        List<SavedPlayer> out = new ArrayList<>();
        for (Map.Entry<UUID, Entry> entry : players.entrySet()) {
            Entry e = entry.getValue();
            out.add(new SavedPlayer(entry.getKey(), e.name == null ? "" : e.name, e.siegeKills, e.siegesDefended));
        }
        return out;
    }

    /** Replaces the contents with a save. Cells whose pool is unknown are dropped. */
    public void load(long day, List<SavedPlayer> saved, List<SavedCell> cells) {
        clear();
        currentDay = day;
        for (SavedPlayer player : saved) {
            Entry entry = entry(player.player(), player.name());
            entry.siegeKills = Math.max(0, player.siegeKills());
            entry.siegesDefended = Math.max(0, player.siegesDefended());
        }
        for (SavedCell saved1 : cells) {
            int bar = saved1.key().indexOf('|');
            if (bar <= 0 || HearthPool.byId(saved1.key().substring(0, bar)) == null) {
                continue;
            }
            Cell cell = entry(saved1.player(), null).cells.computeIfAbsent(saved1.key(), k -> new Cell());
            cell.lifetime = finite(saved1.lifetime());
            Arrays.fill(cell.days, 0.0);
            for (int i = 0; i < Math.min(DAYS, saved1.days().size()); i++) {
                cell.days[i] = finite(saved1.days().get(i));
            }
        }
    }

    private static double finite(Double value) {
        return value != null && Double.isFinite(value) && value > 0 ? value : 0.0;
    }
}
