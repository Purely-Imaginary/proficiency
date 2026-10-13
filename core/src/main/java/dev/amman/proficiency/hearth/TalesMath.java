package dev.amman.proficiency.hearth;

import dev.amman.proficiency.skill.Skill;

import java.util.EnumMap;
import java.util.Map;

/**
 * Tales and the Hearth Chronicle (docs/HEARTH-SPEC.md, "Setting out and coming home").
 *
 * <ul>
 * <li>Away from home, {@code tales.share} of the XP of the Tales feeds (Movement, Expedition) is
 * banked; Storyteller adds a bonus share.</li>
 * <li>Coming into range pays the bank into the Tales pool; with {@code feedAll} it is split evenly
 * over every pool instead. A death loses the bank.</li>
 * <li>Writing a Chronicle page after a homecoming adds {@code writeBonus} of what that homecoming
 * paid, once per homecoming.</li>
 * <li>{@code /mcjournal}'s rating pays {@code stars x perStar} of the page's Tales, outside the
 * hourly cap, once per page.</li>
 * </ul>
 */
public final class TalesMath {

    public static final int MAX_STARS = 5;

    /** A bank smaller than this is not worth a homecoming: it stays banked and grows on the next trip. */
    public static final double MIN_PAYOUT = 1.0;

    private TalesMath() {
    }

    /** What one grant away from home adds to the bank (0 unless the skill is a Tales feed). */
    public static double banked(Skill skill, double xp, double storytellerBonus, HearthData data) {
        if (!(xp > 0) || Double.isInfinite(xp) || !data.tales().banks(skill)) {
            return 0.0;
        }
        return xp * data.tales().share() * (1.0 + Math.max(0.0, storytellerBonus));
    }

    /** Where a payout of {@code tales} goes, per pool. */
    public static Map<HearthPool, Double> payout(double tales, HearthData data) {
        Map<HearthPool, Double> out = new EnumMap<>(HearthPool.class);
        if (!(tales > 0) || Double.isInfinite(tales)) {
            return out;
        }
        if (data.tales().feedAll()) {
            double each = tales / HearthPool.COUNT;
            for (HearthPool pool : HearthPool.VALUES) {
                out.put(pool, each);
            }
        } else {
            out.put(data.talesPool(), tales);
        }
        return out;
    }

    /** What a homecoming did: charge added per pool, the total, and what the hourly cap left unpaid. */
    public record Settlement(Map<HearthPool, Double> added, double paid, double kept) {

        public boolean any() {
            return paid > 0.0;
        }
    }

    /** Adds charge to one pool and returns what went in (the game side: {@code HearthCharge.credit}). */
    @FunctionalInterface
    public interface Credit {

        double add(HearthPool pool, double amount);
    }

    /**
     * Pays a bank through {@code credit}, one call per pool of the payout; the caller's credit is the
     * hourly-capped one (the Tales stream is capped like the mirrored one; only the rated burst and
     * the write bonus sit outside it). Whatever the cap refuses is {@code kept}: it stays banked for the
     * next homecoming instead of vanishing.
     */
    public static Settlement settle(double tales, HearthData data, Credit credit) {
        Map<HearthPool, Double> added = new EnumMap<>(HearthPool.class);
        double paid = 0.0;
        double kept = 0.0;
        for (Map.Entry<HearthPool, Double> share : payout(tales, data).entrySet()) {
            double taken = Math.max(0.0, Math.min(share.getValue(), credit.add(share.getKey(), share.getValue())));
            if (taken > 0.0) {
                added.put(share.getKey(), taken);
                paid += taken;
            }
            kept += share.getValue() - taken;
        }
        return new Settlement(added, paid, Math.max(0.0, kept));
    }

    /** {@link #settle(double, HearthData, Credit)} straight into a state's capped pools (tests, previews). */
    public static Settlement settle(HearthState state, double tales, long now, HearthData data) {
        return settle(tales, data, (pool, amount) -> state.addCapped(pool, amount, now, data));
    }

    /** What {@link #settle} would let in right now, without changing anything. */
    public static double fits(HearthState state, double tales, long now, HearthData data) {
        double fits = 0.0;
        for (Map.Entry<HearthPool, Double> share : payout(tales, data).entrySet()) {
            fits += Math.min(share.getValue(), state.cap(share.getKey()).room(now, data.hourlyCap()));
        }
        return fits;
    }

    /**
     * Adds an amount outside the hourly cap through {@code credit} (the uncapped one), split over the
     * pools like a payout (the Chronicle's write bonus and a rated tale are bursts). Returns what went
     * into each pool.
     */
    public static Map<HearthPool, Double> burst(double amount, HearthData data, Credit credit) {
        Map<HearthPool, Double> added = new EnumMap<>(HearthPool.class);
        for (Map.Entry<HearthPool, Double> share : payout(amount, data).entrySet()) {
            double in = credit.add(share.getKey(), share.getValue());
            if (in > 0.0) {
                added.put(share.getKey(), in);
            }
        }
        return added;
    }

    /** {@link #burst(double, HearthData, Credit)} straight into a state's pools, outside the cap (tests). */
    public static Map<HearthPool, Double> burst(HearthState state, double amount, HearthData data) {
        return burst(amount, data, (pool, share) -> state.addUncapped(pool, share));
    }

    /** The flat Chronicle bonus for a page written after a homecoming that paid {@code lastPaid}. */
    public static double writeBonus(double lastPaid, HearthData data) {
        return lastPaid > 0 && Double.isFinite(lastPaid) ? lastPaid * data.chronicle().writeBonus() : 0.0;
    }

    /** The rated burst: {@code stars x perStar} of the page's Tales, stars clamped to 0..5. */
    public static double ratedBurst(double pageTales, int stars, HearthData data) {
        if (!(pageTales > 0) || Double.isInfinite(pageTales)) {
            return 0.0;
        }
        return pageTales * Math.max(0, Math.min(MAX_STARS, stars)) * data.chronicle().perStar();
    }
}
