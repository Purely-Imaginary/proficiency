package dev.amman.proficiency.hearth.client;

import dev.amman.proficiency.client.XpGainDots;
import dev.amman.proficiency.hearth.HearthData;
import dev.amman.proficiency.hearth.HearthPool;

import java.util.List;

/**
 * The numbers behind the Hearth tracker (docs/HEARTH-SPEC.md, "Hearth tracker"), with no Minecraft
 * in them: one row per pool, each with its own {@link XpGainDots} in "tier units" (charge tier plus
 * the progress to the next threshold, so 2.5 is half way from II to III and a gain across a
 * threshold runs the fill to the end and wraps) and its own {@link TierUpFx}.
 *
 * <p>Two packets feed it. A state or tracker packet says what the server has; a gain packet says
 * what one grant mirrored, a moment later (gains are flushed with the skill sync, a state on a tier
 * change is sent at once). So a state can already hold gains whose dots have not flown yet. The
 * difference is kept as {@code credit}: the bar holds back by it, and the gains that arrive next
 * spend it instead of being counted twice. Credit no gain claims within {@link #CREDIT_RELEASE_MS}
 * (a siege burst, decay caught up) flies in on its own.
 *
 * <p>All the dots of all the rows share {@link #GLOBAL_BUDGET}: the per-instance cap of
 * {@code XpGainDots} would allow 14 per row.
 */
public final class HearthTrackerModel {

    /** Most tracker dots in the air at once, across every row. */
    public static final int GLOBAL_BUDGET = 6;
    /** Credit older than this is not waiting for a gain packet any more. */
    public static final long CREDIT_RELEASE_MS = 1200;

    /** One pool's row. */
    public static final class Row {
        private final int pool;
        private double charge;
        private double credit;
        private long creditAt;
        private int serverTier;
        private int lock;
        private int need;
        private boolean seen;
        private int displayed = -1;
        private double shown;
        private final XpGainDots dots = new XpGainDots();
        private final TierUpFx fx = new TierUpFx();

        Row(int pool) {
            this.pool = pool;
        }

        public int pool() {
            return pool;
        }

        /** The charge as this client knows it: the server's last word plus the gains since. */
        public double charge() {
            return charge;
        }

        /** Charge counted by the server that is still to fly in. */
        public double credit() {
            return credit;
        }

        /** The effective tier the server last reported. */
        public int serverTier() {
            return serverTier;
        }

        /** {@code HearthRules.Lock} ordinal. */
        public int lock() {
            return lock;
        }

        public int need() {
            return need;
        }

        public boolean seen() {
            return seen;
        }

        /** Whether the tracker lists it: it has charge (or a tier, which cannot exist without charge). */
        public boolean listed() {
            return seen && (charge > 0 || serverTier > 0);
        }

        /** The numeral to draw: the effective tier, held back until the fill has visibly reached it. */
        public int displayedTier() {
            return Math.max(0, displayed);
        }

        /** The value drawn this frame, in tier units. */
        public double shown() {
            return shown;
        }

        public XpGainDots dots() {
            return dots;
        }

        public TierUpFx fx() {
            return fx;
        }
    }

    private final Row[] rows = new Row[HearthPool.COUNT];
    private double[] thresholds = defaults();
    private long hearth = Long.MIN_VALUE;

    public HearthTrackerModel() {
        for (int i = 0; i < rows.length; i++) {
            rows[i] = new Row(i);
        }
    }

    private static double[] defaults() {
        List<Double> list = HearthData.DEFAULTS.thresholds();
        double[] out = new double[list.size()];
        for (int i = 0; i < out.length; i++) {
            out[i] = list.get(i);
        }
        return out;
    }

    /** The thresholds of tiers I..VI (from the state packet); empty or broken keeps the last good set. */
    public void thresholds(double[] values) {
        if (values == null || values.length == 0) {
            return;
        }
        for (int i = 0; i < values.length; i++) {
            if (!Double.isFinite(values[i]) || values[i] <= 0 || (i > 0 && values[i] <= values[i - 1])) {
                return;
            }
        }
        thresholds = values.clone();
    }

    public double[] thresholdsCopy() {
        return thresholds.clone();
    }

    /** The highest tier there is (6 by default). */
    public int maxTier() {
        return thresholds.length;
    }

    /** Charge in tier units: the tier the charge reaches plus the share of the way to the next. */
    public double value(double charge) {
        return value(charge, thresholds);
    }

    /** {@link #value(double)} for any threshold set. At the top tier the value is the tier itself. */
    public static double value(double charge, double[] thresholds) {
        if (!(charge > 0)) {
            return 0;
        }
        int tier = 0;
        while (tier < thresholds.length && charge >= thresholds[tier]) {
            tier++;
        }
        if (tier >= thresholds.length) {
            return thresholds.length;
        }
        double from = tier == 0 ? 0 : thresholds[tier - 1];
        double to = thresholds[tier];
        double part = to > from ? (charge - from) / (to - from) : 0;
        return tier + Math.max(0, Math.min(0.9999, part));
    }

    /** The bar's fill for a value in tier units: the part past the tier, full at the top tier. */
    public double fill(double value) {
        if (value >= thresholds.length) {
            return 1;
        }
        return Math.max(0, value - Math.floor(value));
    }

    /** Which Hearth the rows belong to ({@code HearthPos.pack}); {@code Long.MIN_VALUE} for none. */
    public long hearth() {
        return hearth;
    }

    public Row row(int pool) {
        return pool >= 0 && pool < rows.length ? rows[pool] : null;
    }

    /** Rows of every pool, in pool order. */
    public Row[] rows() {
        return rows;
    }

    /** True when any row is listed. */
    public boolean any() {
        for (Row row : rows) {
            if (row.listed()) {
                return true;
            }
        }
        return false;
    }

    /** Forget everything: a new world, another Hearth. */
    public void clear() {
        hearth = Long.MIN_VALUE;
        for (int i = 0; i < rows.length; i++) {
            rows[i] = new Row(i);
        }
    }

    /**
     * The server's word on one pool. A new Hearth starts every row over (snap, no tier-up). A rise
     * in charge the client has not seen a gain for becomes credit; a fall (decay, a doused Hearth)
     * snaps the bar down.
     */
    public void apply(long hearthPos, int pool, double charge, int tier, int lock, int need, long now) {
        apply(hearthPos, pool, charge, tier, lock, need, now, false);
    }

    /**
     * {@link #apply(long, int, double, int, int, int, long)}, or with {@code snap} the row starts over at
     * the server's numbers: the state sent on arriving in range. What changed while the player was away
     * (decay, others feeding it) is news from before, not a gain to fly in or a tier-up to play.
     */
    public void apply(long hearthPos, int pool, double charge, int tier, int lock, int need, long now,
            boolean snap) {
        Row row = row(pool);
        if (row == null || !Double.isFinite(charge)) {
            return;
        }
        if (hearthPos != hearth) {
            clear();
            hearth = hearthPos;
            row = rows[pool];
        }
        row.serverTier = Math.max(0, tier);
        row.lock = lock;
        row.need = need;
        if (!row.seen || snap) {
            row.seen = true;
            row.charge = Math.max(0, charge);
            row.credit = 0;
            double value = value(row.charge);
            row.dots.snap(value, now);
            row.shown = value;
            row.displayed = Math.min(row.serverTier, (int) Math.floor(value + 1e-6));
            return;
        }
        double delta = charge - row.charge;
        if (delta >= 0) {
            if (delta > 0) {
                row.credit += delta;
                row.creditAt = now;
            }
            row.charge = charge;
        } else {
            row.charge = Math.max(0, charge);
            row.credit = 0;
            row.dots.snap(value(row.charge), now);
        }
    }

    /**
     * A mirrored share reached {@code pool}: spend credit first, add the rest, and launch dots for
     * the whole amount from {@code now + delayMs} (the tracker delays them while the skill line's
     * own dots are still flying into its bar, so they leave the bar as those land).
     */
    public void gain(int pool, double amount, long now, long delayMs, boolean animate) {
        Row row = row(pool);
        if (row == null || !row.seen || !(amount > 0) || !Double.isFinite(amount)) {
            return;
        }
        double before = value(row.charge - row.credit);
        double spent = Math.min(row.credit, amount);
        row.credit -= spent;
        row.charge += amount - spent;
        double after = value(row.charge - row.credit);
        launch(row, before, after, now + Math.max(0, delayMs), animate);
    }

    private void launch(Row row, double before, double after, long at, boolean animate) {
        if (!animate) {
            row.dots.snap(after, at);
            return;
        }
        if (after > before) {
            row.dots.gain(before, after, at, Math.max(0, GLOBAL_BUDGET - inAir()));
        }
    }

    /** Dots in the air across every row. */
    public int inAir() {
        int count = 0;
        for (Row row : rows) {
            count += row.dots.inAir();
        }
        return count;
    }

    /**
     * Advances every row to {@code now}: releases stale credit, lands dots, moves the fills, and
     * starts a row's {@link TierUpFx} when its numeral turns over. {@code animate} is the client's
     * {@code hud.xpDots}, {@code fxOn} its {@code hud.levelUpFx}. Returns a bit per pool whose tier
     * rose this frame (for the chime).
     */
    public int frame(long now, boolean animate, boolean fxOn) {
        int rose = 0;
        for (Row row : rows) {
            if (!row.seen) {
                continue;
            }
            if (row.credit > 0 && now - row.creditAt >= CREDIT_RELEASE_MS) {
                double before = value(row.charge - row.credit);
                row.credit = 0;
                launch(row, before, value(row.charge), now, animate);
            }
            // With the dots off nothing waits to fly in: show the server's number as it is (the
            // credit is still kept, so the gain packet that follows is not counted twice).
            double real = value(row.charge - (animate ? row.credit : 0));
            if (!animate) {
                row.dots.snap(real, now);
            }
            row.shown = row.dots.update(real, now);
            int tier = Math.min(row.serverTier, (int) Math.floor(row.shown + 1e-6));
            if (row.displayed >= 0 && tier > row.displayed) {
                if (fxOn) {
                    row.fx.start(row.displayed, tier, now);
                }
                rose |= 1 << row.pool;
            }
            if (!fxOn) {
                row.fx.reset();
            }
            row.displayed = tier;
        }
        return rose;
    }

    /** Anything still moving: dots, a fill catching up, or a tier-up playing. */
    public boolean busy(long now) {
        for (Row row : rows) {
            if (row.seen && (row.dots.busy(value(row.charge - row.credit), now) || row.fx.active(now))) {
                return true;
            }
        }
        return false;
    }
}
