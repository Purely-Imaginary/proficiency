package dev.amman.proficiency.hearth.charge;

import dev.amman.proficiency.hearth.HearthData;
import dev.amman.proficiency.hearth.HearthPool;
import dev.amman.proficiency.hearth.HearthState;
import dev.amman.proficiency.skill.KillXp;
import dev.amman.proficiency.skill.Skill;
import dev.amman.proficiency.skill.SkillCategory;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * What feeds a Hearth and how much (docs/HEARTH-SPEC.md, "Charge: what feeds what"). Pure rules the
 * charge lane's grant hook, hold queue and Hearth tick use; the Minecraft side only supplies the
 * facts (is the player home, was the victim a siege mob, does the held block still stand).
 *
 * <ul>
 * <li>A grant feeds only when it is play (not an operator's grant, not a teaching payout) and not a
 * first-time bonus line. The caller mirrors {@code beforeRested}: never the rested extra, and never
 * {@code banked}, which is 0 for a skill with every star.</li>
 * <li>A kill line ({@code kill|<victim>}) feeds only through the combat path, and only for a siege
 * mob or a raider in an active raid; every other kill feeds nothing, whatever the skill.</li>
 * <li>Combat skills never feed through the data mapping: hits, blocking, damage taken (Endurance)
 * and the role XP of Guardian, Charger, Tactician and Courage add nothing, even if a pack maps the
 * category, because a mob farm in range would charge the Hearth otherwise.</li>
 * <li>Homebuilder never feeds, even if a pack maps its category: its XP is a share of what was
 * earned at home, so mirroring it would count that work twice (docs/HOMEBUILDER-SPEC.md: "not
 * mirrored into the Hearth (it feeds no pool)").</li>
 * <li>Everything else feeds the pool the data maps the skill to ({@link HearthData#poolFor}).</li>
 * </ul>
 */
public final class ChargeRules {

    /** What a grant does to the Hearth. */
    public enum Route {
        /** Feeds nothing. */
        NONE,
        /** {@code mirrorShare} of it into the pool the data maps its skill to. */
        POOL,
        /** A kill: {@code combatShare} of it into every pool, if the victim was a siege mob or a raider. */
        KILL
    }

    /** Seconds per game tick, for hold times. */
    public static final int TICKS_PER_SECOND = 20;

    /**
     * Someone counts as having just been home when the last visit is at most this old: the Hearth
     * ticks once a second, so 40 ticks leaves room for one late tick.
     */
    public static final long PRESENT_TICKS = 40L;

    /** With nobody home, decay is applied at most this often (it compounds, so the step size changes nothing). */
    public static final long DECAY_EVERY_TICKS = 200L;

    /** Pending holds one Hearth keeps at most; past this, a placement holds nothing (the hourly cap is far lower). */
    public static final int MAX_HOLDS = 2048;

    /** The ledger source for a combat share: siege kills and raid kills alike (defending the home). */
    public static final String COMBAT_SOURCE = dev.amman.proficiency.hearth.HearthLedger.SIEGE;

    /** The id of the Homebuilder skill (docs/HOMEBUILDER-SPEC.md), taken from the skill itself. */
    public static final String HOMEBUILDER = Skill.HOMEBUILDER.id();

    /** The telemetry source for charge the hourly cap turned away (label = pool). */
    public static final String CAPPED_SOURCE = "capped";

    private ChargeRules() {
    }

    /**
     * The path a paid grant takes into the Hearth.
     *
     * @param play      false for an operator's grant or a teaching payout
     * @param firstTime the first-time bonus line ({@code first|...})
     */
    public static Route route(Skill skill, String source, boolean play, boolean firstTime) {
        if (skill == null || !play || firstTime || neverFeeds(skill)) {
            return Route.NONE;
        }
        if (source != null && source.startsWith(KillXp.KILL_PREFIX)) {
            return Route.KILL;
        }
        if (skill.category() == SkillCategory.COMBAT) {
            return Route.NONE;
        }
        return Route.POOL;
    }

    /** A skill whose grants never feed a Hearth, whatever the data maps: Homebuilder. */
    public static boolean neverFeeds(Skill skill) {
        return skill == Skill.HOMEBUILDER;
    }

    /** {@link #neverFeeds(Skill)} by skill id. */
    public static boolean neverFeeds(String skillId) {
        return HOMEBUILDER.equals(skillId);
    }

    /** The pool a {@link Route#POOL} grant of this skill feeds, or null (the data maps it to none). */
    public static HearthPool pool(Skill skill, HearthData data) {
        return skill == null ? null : data.poolFor(skill);
    }

    /** {@code mirrorShare} of the grant: what the Hearth gets on top. 0 for anything not finite or not positive. */
    public static double mirror(double beforeRested, HearthData data) {
        return share(beforeRested, data.mirrorShare());
    }

    /** {@code combatShare} of a siege or raid kill's XP: what each pool gets. */
    public static double combat(double killXp, HearthData data) {
        return share(killXp, data.combatShare());
    }

    static double share(double amount, double share) {
        if (!Double.isFinite(amount) || !Double.isFinite(share) || amount <= 0 || share <= 0) {
            return 0.0;
        }
        return amount * share;
    }

    /**
     * Whether a siege kill feeds this Hearth: the victim was a siege mob, this Hearth was under siege
     * (phase {@code ACTIVE}) when it died, and it died in this Hearth's range. Ranges never overlap
     * (the lighting spacing), so a siege mob killed at someone else's Hearth feeds only that one.
     */
    public static boolean siegeFeeds(boolean siegeMob, boolean hearthUnderSiege, boolean victimInRange) {
        return siegeMob && hearthUnderSiege && victimInRange;
    }

    /**
     * Whether a raid kill feeds this Hearth: the victim was a member of an active raid when it died
     * (not merely a raider near one), and it or the raid's centre is in this Hearth's range.
     */
    public static boolean raidFeeds(boolean raiderInActiveRaid, boolean victimInRange, boolean raidCentreInRange) {
        return raiderInActiveRaid && (victimInRange || raidCentreInRange);
    }

    /** The game tick a hold placed at {@code now} comes due. */
    public static long holdDue(long now, int holdSeconds) {
        return now + Math.max(0, holdSeconds) * (long) TICKS_PER_SECOND;
    }

    /** A held credit pays when the block placed is still the block at its position. */
    public static boolean stillStands(String heldBlock, String blockNow) {
        return heldBlock != null && !heldBlock.isEmpty() && heldBlock.equals(blockNow);
    }

    /**
     * Splits {@code total} over the keys by weight, so that the parts sum to {@code total} exactly
     * (the last key with a weight takes what rounding left). Keys with no positive weight get nothing;
     * when no key has one, every key gets an even part. An empty map, or a total that is not a
     * positive finite number, gives an empty result. Keeps the map's order.
     */
    public static <K> Map<K, Double> split(double total, Map<K, ? extends Number> weights) {
        Map<K, Double> out = new LinkedHashMap<>();
        if (weights == null || weights.isEmpty() || !Double.isFinite(total) || total <= 0) {
            return out;
        }
        double sum = 0.0;
        for (Number weight : weights.values()) {
            double w = weight == null ? 0.0 : weight.doubleValue();
            if (Double.isFinite(w) && w > 0) {
                sum += w;
            }
        }
        List<K> keys = new ArrayList<>();
        List<Double> parts = new ArrayList<>();
        for (Map.Entry<K, ? extends Number> entry : weights.entrySet()) {
            double w = entry.getValue() == null ? 0.0 : entry.getValue().doubleValue();
            boolean counts = sum > 0 ? Double.isFinite(w) && w > 0 : true;
            if (counts) {
                keys.add(entry.getKey());
                parts.add(sum > 0 ? w / sum : 1.0 / weights.size());
            }
        }
        double given = 0.0;
        for (int i = 0; i < keys.size(); i++) {
            double part = i == keys.size() - 1 ? total - given : total * parts.get(i);
            given += part;
            out.put(keys.get(i), part);
        }
        return out;
    }

    /** The pools whose tier is higher in {@code now} than in {@code before} (arrays by pool ordinal). */
    public static List<HearthPool> rose(int[] before, int[] now) {
        if (before == null || now == null) {
            return Collections.emptyList();
        }
        List<HearthPool> out = new ArrayList<>();
        for (HearthPool pool : HearthPool.VALUES) {
            int i = pool.ordinal();
            if (i < before.length && i < now.length && now[i] > before[i]) {
                out.add(pool);
            }
        }
        return out;
    }

    /**
     * The tiers a tier-up line is announced against, per pool: the highest tier announced, lowered
     * to what the charge alone still reaches. So a re-climb after decay or a doused siege is told
     * again, while a pool held down by the ceiling or Comfort (a layer broken and rebuilt, a bed
     * carried out and back) is not told twice.
     *
     * @param high       the high-water marks as stored (by pool ordinal); missing entries count as 0
     * @param chargeTier each pool's charge tier now
     */
    public static int[] lowerHighWater(int[] high, int[] chargeTier) {
        int[] out = new int[HearthPool.COUNT];
        for (int i = 0; i < out.length; i++) {
            int h = high != null && i < high.length ? high[i] : 0;
            int c = chargeTier != null && i < chargeTier.length ? chargeTier[i] : 0;
            out[i] = Math.max(0, Math.min(h, c));
        }
        return out;
    }

    /** Every pool's effective tier (what boons use; 0 while smouldering). */
    public static int[] effectiveTiers(HearthState state, HearthData data) {
        int[] out = new int[HearthPool.COUNT];
        for (HearthPool pool : HearthPool.VALUES) {
            out[pool.ordinal()] = state.effectiveTier(pool, data);
        }
        return out;
    }

    /** Every pool's charge tier. */
    public static int[] chargeTiers(HearthState state, HearthData data) {
        int[] out = new int[HearthPool.COUNT];
        for (HearthPool pool : HearthPool.VALUES) {
            out[pool.ordinal()] = state.chargeTier(pool, data);
        }
        return out;
    }

    /**
     * The once-a-second decay step of one Hearth (docs/HEARTH-SPEC.md, "Tiers, cap and decay").
     *
     * <ul>
     * <li>Someone home (an online, non-AFK player in survival or adventure, in range): the absence
     * before this visit decays first, then both clocks move to now and nothing decays.</li>
     * <li>Nobody home: the time since the later clock decays, at most every
     * {@link #DECAY_EVERY_TICKS}, or at once when {@code force} (the first tick after a load, so an
     * unloaded Hearth catches up on its first second).</li>
     * </ul>
     *
     * <p>The clocks move on every step that runs; the caller saves them now and then (see
     * {@code ChargeTick}), since a step that only moved a clock is not worth dirtying the chunk for.
     *
     * @return whether any pool's charge changed
     */
    public static boolean decayStep(HearthState state, long now, boolean someoneHome, boolean force, HearthData data) {
        long visited = state.lastVisited();
        if (someoneHome) {
            boolean absent = visited == HearthState.NEVER || now - visited > PRESENT_TICKS;
            double before = absent ? totalCharge(state) : 0.0;
            if (absent && state.lastDecay() != HearthState.NEVER) {
                state.tickDecay(now, false, data);
            }
            state.tickDecay(now, true, data);
            return absent && totalCharge(state) != before;
        }
        long last = state.lastDecay();
        if (force || last == HearthState.NEVER || now - last >= DECAY_EVERY_TICKS || now < last) {
            double before = totalCharge(state);
            state.tickDecay(now, false, data);
            return totalCharge(state) != before;
        }
        return false;
    }

    /** The charge of every pool together (decay only ever lowers a pool, so a change shows in the sum). */
    static double totalCharge(HearthState state) {
        double sum = 0.0;
        for (HearthPool pool : HearthPool.VALUES) {
            sum += state.charge(pool);
        }
        return sum;
    }
}
