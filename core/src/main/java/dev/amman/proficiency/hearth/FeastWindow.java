package dev.amman.proficiency.hearth;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Predicate;

/**
 * The Feast of one Hearth (docs/HEARTH-SPEC.md, "Gathering"). Two or more players eating in range
 * within {@code windowSeconds} of each other start it: each eater's Warmth goes full and the Feast
 * pool gets {@code chargePerExtraEater} per eater past the first. A third eater inside the window
 * joins (another {@code chargePerExtraEater}). Once per in-game day per Hearth: the day a Feast
 * starts on is spent, and while its window is open (also past midnight) eaters only join it. The
 * dinner bell opens a longer window ({@code dinnerBellSeconds}); anyone eating inside it counts.
 */
public final class FeastWindow {

    public enum Kind { NONE, STARTED, JOINED }

    /** What one meal did: who to warm and what to charge. */
    public record Outcome(Kind kind, List<UUID> warmed, double charge) {
        static final Outcome NONE = new Outcome(Kind.NONE, List.of(), 0.0);
    }

    /** Recent meals in range: player to game time. */
    private final Map<UUID, Long> meals = new LinkedHashMap<>();
    private final Set<UUID> feasters = new LinkedHashSet<>();
    private long lastFeastDay = Long.MIN_VALUE;
    private long openUntil = Long.MIN_VALUE;
    private long dinnerUntil = Long.MIN_VALUE;

    /**
     * A player ate in range.
     *
     * @param gameDay the in-game day ({@code dayTime / 24000}); one Feast per day per Hearth
     */
    public Outcome onEat(UUID player, long now, long gameDay, HearthData data) {
        return onEat(player, now, gameDay, data, id -> true);
    }

    /**
     * A player ate in range. {@code atTable} says whether an earlier eater is still at the Hearth: one who
     * ate and walked off does not make the Feast, and a Feast of fewer than two at the table is not
     * started at all, so the day is not spent on it (the next real meal together can still start it).
     * The player who just ate is always at the table.
     */
    public Outcome onEat(UUID player, long now, long gameDay, HearthData data, Predicate<UUID> atTable) {
        long window = ticks(data.feast().windowSeconds());
        long trigger = ticks(data.feast().dinnerTriggerSeconds());
        long keep = Math.max(window, trigger + ticks(data.feast().dinnerBellSeconds()));
        boolean dinner = now <= dinnerUntil;
        meals.values().removeIf(time -> time < now - keep);
        meals.put(player, now);
        double each = data.feast().chargePerExtraEater();
        // An open Feast is joined, never started again, even when its window runs past the end of
        // the in-game day it started on (a Feast at day time 23990 is still that day's Feast at 24010).
        if (now <= openUntil) {
            if (feasters.add(player)) {
                openUntil = Math.max(openUntil, dinner ? dinnerUntil : now + window);
                return new Outcome(Kind.JOINED, List.of(player), each);
            }
            return Outcome.NONE;
        }
        if (lastFeastDay == gameDay) {
            return Outcome.NONE;
        }
        List<UUID> eaters = new ArrayList<>();
        for (Map.Entry<UUID, Long> meal : meals.entrySet()) {
            // Under the dinner bell the meal that prompted the ring counts too.
            boolean inWindow = dinner ? meal.getValue() >= dinnerStart(data) - trigger
                    : meal.getValue() >= now - window;
            if (inWindow && (meal.getKey().equals(player) || atTable.test(meal.getKey()))) {
                eaters.add(meal.getKey());
            }
        }
        if (eaters.size() < 2) {
            return Outcome.NONE;
        }
        lastFeastDay = gameDay;
        feasters.clear();
        feasters.addAll(eaters);
        openUntil = dinner ? dinnerUntil : now + window;
        return new Outcome(Kind.STARTED, List.copyOf(eaters), each * (eaters.size() - 1));
    }

    private long dinnerStart(HearthData data) {
        return dinnerUntil - ticks(data.feast().dinnerBellSeconds());
    }

    private static long ticks(double seconds) {
        return Math.round(seconds * 20.0);
    }

    /** The dinner bell: opens the long window from now. */
    public void openDinner(long now, HearthData data) {
        dinnerUntil = now + ticks(data.feast().dinnerBellSeconds());
    }

    /** Whether someone ate in range within {@code dinnerTriggerSeconds} (the dinner bell's moment). */
    public boolean someoneAteWithin(long now, HearthData data) {
        long since = now - ticks(data.feast().dinnerTriggerSeconds());
        for (long time : meals.values()) {
            if (time >= since && time <= now) {
                return true;
            }
        }
        return false;
    }

    public boolean feastedOn(long gameDay) {
        return lastFeastDay == gameDay;
    }

    public boolean dinnerOpen(long now) {
        return now <= dinnerUntil;
    }

    public long lastFeastDay() {
        return lastFeastDay;
    }

    public long dinnerUntil() {
        return dinnerUntil;
    }

    /** Restores the parts worth saving (the day and the dinner window); recent meals are not saved. */
    public void restore(long lastFeastDay, long dinnerUntil) {
        this.lastFeastDay = lastFeastDay;
        this.dinnerUntil = dinnerUntil;
        meals.clear();
        feasters.clear();
        openUntil = Long.MIN_VALUE;
    }
}
