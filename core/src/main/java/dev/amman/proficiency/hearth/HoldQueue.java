package dev.amman.proficiency.hearth;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * The Masonry and Decorating hold (docs/HEARTH-SPEC.md, "Charge"): the mirrored charge of a placed
 * block waits {@code hold} seconds, keyed by the block's position, and is credited only if the
 * block is still there then. Breaking the block drops what waits on it, so shuffling one block
 * along charges nothing. The caller checks "still there" when an entry falls due.
 *
 * <p>Positions are packed longs ({@link HearthPos#pack}); {@code block} is the placed block's id, so
 * the caller can tell a different block at the same spot.
 */
public final class HoldQueue {

    /** One held credit. */
    public record Held(UUID player, String name, HearthPool pool, String source, String block, double amount,
            long due) {
    }

    /** A held credit and where it waits. */
    public record Due(long pos, Held held) {
    }

    private final Map<Long, List<Held>> byPos = new LinkedHashMap<>();
    private int size;
    /**
     * No entry is due before this tick ({@code Long.MAX_VALUE} when empty), so {@link #takeDue} skips
     * the walk until then. A drop can leave it early, never late: the next walk puts it right.
     */
    private long earliest = Long.MAX_VALUE;

    public void add(long pos, Held held) {
        if (!(held.amount() > 0) || Double.isInfinite(held.amount())) {
            return;
        }
        byPos.computeIfAbsent(pos, p -> new ArrayList<>(1)).add(held);
        size++;
        earliest = Math.min(earliest, held.due());
    }

    /** Drops everything waiting on a position (the block was broken); returns what was dropped. */
    public List<Held> drop(long pos) {
        List<Held> removed = byPos.remove(pos);
        if (removed == null) {
            return List.of();
        }
        size -= removed.size();
        return removed;
    }

    /**
     * Removes and returns every entry due at or before {@code now}, oldest positions first. Before
     * the earliest due tick it returns at once, without looking at the entries.
     */
    public List<Due> takeDue(long now) {
        if (size == 0 || now < earliest) {
            return List.of();
        }
        List<Due> out = new ArrayList<>();
        long next = Long.MAX_VALUE;
        Iterator<Map.Entry<Long, List<Held>>> it = byPos.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<Long, List<Held>> entry = it.next();
            Iterator<Held> held = entry.getValue().iterator();
            while (held.hasNext()) {
                Held one = held.next();
                if (one.due() <= now) {
                    out.add(new Due(entry.getKey(), one));
                    held.remove();
                    size--;
                } else {
                    next = Math.min(next, one.due());
                }
            }
            if (entry.getValue().isEmpty()) {
                it.remove();
            }
        }
        earliest = next;
        return out;
    }

    /** The earliest tick an entry may come due ({@code Long.MAX_VALUE} when nothing waits). */
    public long nextDue() {
        return size == 0 ? Long.MAX_VALUE : earliest;
    }

    public int size() {
        return size;
    }

    public boolean isEmpty() {
        return size == 0;
    }

    /** The charge still waiting, per pool. */
    public double pending(HearthPool pool) {
        double sum = 0.0;
        for (List<Held> list : byPos.values()) {
            for (Held held : list) {
                if (held.pool() == pool) {
                    sum += held.amount();
                }
            }
        }
        return sum;
    }

    /** Every entry, for the save. */
    public List<Due> all() {
        List<Due> out = new ArrayList<>(size);
        for (Map.Entry<Long, List<Held>> entry : byPos.entrySet()) {
            for (Held held : entry.getValue()) {
                out.add(new Due(entry.getKey(), held));
            }
        }
        return out;
    }

    public void clear() {
        byPos.clear();
        size = 0;
        earliest = Long.MAX_VALUE;
    }
}
