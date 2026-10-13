package dev.amman.proficiency.hearth;

/**
 * Tiers, the ceiling and the Comfort caps (docs/HEARTH-SPEC.md, "Tiers, cap and decay" and
 * "Comfort"). Pure functions over a {@link HearthData}.
 *
 * <ul>
 * <li>A pool's <b>charge tier</b> is how many thresholds its charge has reached (0..6).</li>
 * <li>The <b>ceiling</b> is {@code min(built stage, the stage Character allows)}.</li>
 * <li>The <b>Comfort cap</b> is {@code floor(comfort / 2)} for a {@code "half"} pool (regen,
 * Homecoming) and the {@code comfortForTier} table for the others (V needs 8, VI needs 10).</li>
 * <li>The <b>effective tier</b>, the one boons use, is the least of the three. Charge above it is
 * held, never cut.</li>
 * </ul>
 */
public final class HearthRules {

    /**
     * Why a pool sits below its charge tier. {@code CEILING}: the built stage (the next tier wants the
     * next stage built); {@code CHARACTER}: the Character the next stage needs (the offering is
     * refused until it is there); {@code COMFORT}: the Comfort cap. Sent by ordinal, so new values go
     * at the end.
     */
    public enum Lock {
        NONE, CEILING, COMFORT, CHARACTER;

        public String id() {
            return name().toLowerCase(java.util.Locale.ROOT);
        }
    }

    private HearthRules() {
    }

    /** How many thresholds the charge has reached: 0..6. NaN counts as 0. */
    public static int chargeTier(double charge, HearthData data) {
        int tier = 0;
        for (double threshold : data.thresholds()) {
            if (charge >= threshold) {
                tier++;
            } else {
                break;
            }
        }
        return tier;
    }

    /** The share of the way from the current tier's threshold to the next, 0..1; 1 at tier VI. */
    public static double progress(double charge, HearthData data) {
        int tier = chargeTier(charge, data);
        if (tier >= data.thresholds().size()) {
            return 1.0;
        }
        double from = data.threshold(tier);
        double to = data.threshold(tier + 1);
        if (!(to > from)) {
            return 0.0;
        }
        return Math.max(0.0, Math.min(1.0, (charge - from) / (to - from)));
    }

    /** The highest stage this Character allows: stages with no requirement are always allowed. */
    public static int characterStage(int character, HearthData data) {
        int allowed = 0;
        for (int stage = 1; stage <= HearthData.TIERS; stage++) {
            if (character >= data.character().forStage(stage)) {
                allowed = stage;
            } else {
                break;
            }
        }
        return allowed;
    }

    /** {@code min(built stage, Character-allowed stage)}, 0..6. */
    public static int ceiling(int builtStage, int character, HearthData data) {
        return Math.max(0, Math.min(Math.min(builtStage, HearthData.TIERS), characterStage(character, data)));
    }

    /** The highest tier Comfort lets this pool reach, 0..6. */
    public static int comfortCap(HearthPool pool, int comfort, HearthData data) {
        HearthData.PoolSpec spec = data.pool(pool);
        if (spec != null && spec.comfortCap() == HearthData.ComfortCap.HALF) {
            return Math.max(0, Math.min(HearthData.TIERS, comfort / 2));
        }
        for (int tier = 1; tier <= HearthData.TIERS; tier++) {
            if (comfort < data.comfortForTier(tier)) {
                return tier - 1;
            }
        }
        return HearthData.TIERS;
    }

    /** The Comfort a pool needs to reach {@code tier}; 0 when nothing is asked. */
    public static int comfortNeeded(HearthPool pool, int tier, HearthData data) {
        if (tier <= 0) {
            return 0;
        }
        HearthData.PoolSpec spec = data.pool(pool);
        if (spec != null && spec.comfortCap() == HearthData.ComfortCap.HALF) {
            return 2 * Math.min(tier, HearthData.TIERS);
        }
        int need = 0;
        for (int t = 1; t <= Math.min(tier, HearthData.TIERS); t++) {
            need = Math.max(need, data.comfortForTier(t));
        }
        return need;
    }

    /** The tier boons use: the least of charge tier, ceiling and Comfort cap. */
    public static int effectiveTier(HearthPool pool, double charge, int ceiling, int comfort, HearthData data) {
        return Math.max(0, Math.min(chargeTier(charge, data),
                Math.min(ceiling, comfortCap(pool, comfort, data))));
    }

    /**
     * What holds a pool below its charge tier, from the ceiling alone ({@code CEILING} for either
     * half of it); on a tie the ceiling (the build comes first). {@link #lock(HearthPool, double, int,
     * int, int, HearthData)} also tells the stage from the Character.
     */
    public static Lock lock(HearthPool pool, double charge, int ceiling, int comfort, HearthData data) {
        int byCharge = chargeTier(charge, data);
        int cap = comfortCap(pool, comfort, data);
        if (byCharge <= Math.min(ceiling, cap)) {
            return Lock.NONE;
        }
        return ceiling <= cap ? Lock.CEILING : Lock.COMFORT;
    }

    /**
     * What holds a pool below its charge tier, telling the two halves of the ceiling apart: when the
     * ceiling holds it, {@code CHARACTER} if the next stage's Character is missing (the offering is
     * refused until it is there, so it comes first even when that stage is not built either), else
     * {@code CEILING} (build the next stage).
     */
    public static Lock lock(HearthPool pool, double charge, int builtStage, int character, int comfort,
            HearthData data) {
        int ceiling = ceiling(builtStage, character, data);
        Lock lock = lock(pool, charge, ceiling, comfort, data);
        return lock == Lock.CEILING ? ceilingLock(ceiling, character, data) : lock;
    }

    /** Which half of the ceiling the next stage waits on: the Character it needs, or the build. */
    public static Lock ceilingLock(int ceiling, int character, HearthData data) {
        int next = Math.min(HearthData.TIERS, ceiling + 1);
        return character < data.character().forStage(next) ? Lock.CHARACTER : Lock.CEILING;
    }

    /**
     * What the next tier needs from {@code lock}, for the panel: the Comfort ({@code COMFORT}), the
     * stage to build ({@code CEILING}), the Character the next stage wants ({@code CHARACTER}); 0 for
     * {@code NONE}.
     */
    public static int need(Lock lock, HearthPool pool, int tier, int ceiling, HearthData data) {
        return switch (lock) {
            case COMFORT -> comfortNeeded(pool, tier + 1, data);
            case CEILING -> Math.min(HearthData.TIERS, ceiling + 1);
            case CHARACTER -> data.character().forStage(Math.min(HearthData.TIERS, ceiling + 1));
            case NONE -> 0;
        };
    }

    /** The boon number of a pool at a tier (0 at tier 0). */
    public static double boonValue(HearthPool pool, int tier, HearthData data) {
        HearthData.PoolSpec spec = data.pool(pool);
        return spec == null ? 0.0 : spec.value(tier);
    }

    /** Seconds Warmth takes to fill with Homecoming (the readiness pool) at this tier. */
    public static double warmthRiseSeconds(int readinessTier, HearthData data) {
        HearthPool pool = data.poolWithBoon(BoonType.READINESS);
        double base = data.warmth().riseSeconds();
        if (pool == null || readinessTier <= 0) {
            return base;
        }
        double value = HearthData.PoolSpec.at(data.pool(pool).warmthSeconds(), readinessTier);
        return value > 0 ? value : base;
    }

    /** Minutes Provisioned lasts with Homecoming (the readiness pool) at this tier. */
    public static double provisionedMinutes(int readinessTier, HearthData data) {
        HearthPool pool = data.poolWithBoon(BoonType.READINESS);
        double base = data.provisioned().minutes();
        if (pool == null || readinessTier <= 0) {
            return base;
        }
        double value = HearthData.PoolSpec.at(data.pool(pool).provisionedMinutes(), readinessTier);
        return value > 0 ? value : base;
    }

    /**
     * The Hearth block's light, from the blockstate alone (the light engine reads only the state):
     * 6 at stage I up to 15 at stage VI, 3 while it smoulders. The art contract's blockstate has
     * stage and smouldering only, so the glow follows the ceiling the stage property shows.
     *
     * @deprecated not the block's light since the integration task: {@code HearthBlock} reads
     *             {@link #chargeLight} (its {@code glow} property is the highest effective tier). Kept
     *             because the contract lists it; only its own test calls it.
     */
    @Deprecated
    public static int light(int stage, boolean smouldering) {
        if (smouldering) {
            return 3;
        }
        int s = Math.max(1, Math.min(HearthData.TIERS, stage));
        return 6 + (int) Math.round((s - 1) * 9.0 / 5.0);
    }

    /**
     * The Hearth block's light as the spec has it (integration, 2026-10-09): the block "glows with
     * charge", 6 with no pool at a tier up to 15 at any tier VI, 3 while it smoulders. {@code maxTier}
     * is the highest effective tier of any pool (the block's {@code glow} property). This is the formula
     * that is live; {@link #light} (by stage) is the old one.
     */
    public static int chargeLight(int maxTier, boolean smouldering) {
        if (smouldering) {
            return 3;
        }
        int t = Math.max(0, Math.min(HearthData.TIERS, maxTier));
        return 6 + (int) Math.round(t * 9.0 / HearthData.TIERS);
    }

    /**
     * The pool fed most in the rolling hour, by what each pool's hourly cap let in, or null when no
     * pool was fed (the block's {@code feed} property: the colour of its particles). A tie goes to the
     * pool first in wire order. Bursts outside the cap (a siege victory, a rated tale) are not counted.
     */
    public static HearthPool feedingMost(HearthState state, long gameTime) {
        HearthPool best = null;
        double most = 0.0;
        for (HearthPool pool : HearthPool.VALUES) {
            double used = state.cap(pool).used(gameTime);
            if (used > most + 1e-9) {
                most = used;
                best = pool;
            }
        }
        return best;
    }

    /** Roman numeral 0..6 ("" for 0), for chat lines and the panel. */
    public static String roman(int tier) {
        return switch (tier) {
            case 1 -> "I";
            case 2 -> "II";
            case 3 -> "III";
            case 4 -> "IV";
            case 5 -> "V";
            case 6 -> "VI";
            default -> "";
        };
    }
}
