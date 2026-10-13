package dev.amman.proficiency.hearth;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * The siege rules (docs/HEARTH-SPEC.md, "Sieges"), pure. The Sieges lane gathers the facts from the
 * world and asks these.
 *
 * <ul>
 * <li><b>Condition</b>: first horn heard (unless the data says otherwise), the whole active group
 * home at full Warmth, ceiling III+, some pool at tier I+, cooldown served (play hours and in-game
 * days), sieges on, not peaceful, no siege already pending or running.</li>
 * <li><b>Roll</b>: {@code pMax x min(1, (H - cooldown) / ramp)}, H in play hours since the last siege
 * ended; from {@code pityHours} on, a sure hit.</li>
 * <li><b>Horn</b>: ceiling III+, {@code hornCooldownPlayHours} since the last siege, nothing pending.</li>
 * <li><b>Clock</b>: a hit is due {@code warningTicks} of game time later (beds skip day time, not game
 * time), then starts at the next dusk.</li>
 * <li><b>Nightfall</b>: nobody home postpones a night, at most {@code postponeNights}; then it
 * fizzles and the cooldown counts as half served ({@link #fizzledEnd}, both clocks, in ticks).</li>
 * </ul>
 */
public final class SiegeRules {

    /** Night begins (monsters spawn) at day time 13000 and ends at 23000. */
    public static final long DUSK = 13_000L;
    public static final long DAWN = 23_000L;

    /** A siege's place in its life. */
    public enum Phase {
        IDLE, COUNTDOWN, ACTIVE;

        public static Phase byOrdinal(int ordinal) {
            Phase[] values = values();
            return ordinal >= 0 && ordinal < values.length ? values[ordinal] : IDLE;
        }
    }

    /** What happens at a nightfall when the siege is due. */
    public enum Nightfall { START, POSTPONE, FIZZLE }

    /**
     * How a siege ended. {@code UNEARNED} (added by the Sieges lane): every wave is gone before dawn,
     * but not every mob was killed by a player or a pet (a moat, a golem, a portal took some), so it is
     * not a defence: neither won nor lost.
     */
    public enum Outcome {
        DEFENDED, DOUSED, DAWN, ABANDONED, FIZZLED, UNEARNED;

        public String id() {
            return name().toLowerCase(java.util.Locale.ROOT);
        }
    }

    /**
     * The facts the condition needs.
     *
     * @param activePlayers   online, non-AFK players in survival or adventure, any dimension
     * @param homeAndWarm     of those, how many are in range of this Hearth at full Warmth
     * @param playHoursSince  play hours since the last siege here ended (or since lighting, with none)
     * @param ticksSince      game ticks since the last siege here ended
     * @param busy            a siege here is already counting down or running
     */
    public record Facts(boolean firstHornDone, int activePlayers, int homeAndWarm, int ceiling, int maxPoolTier,
            double playHoursSince, long ticksSince, boolean siegesEnabled, boolean peaceful, boolean busy) {
    }

    private SiegeRules() {
    }

    /** Whether the whole condition holds right now. */
    public static boolean conditionHolds(Facts f, HearthData.Siege s) {
        if (s.requiresFirstHorn() && !f.firstHornDone()) {
            return false;
        }
        return f.activePlayers() > 0 && f.homeAndWarm() >= f.activePlayers()
                && f.ceiling() >= 3 && f.maxPoolTier() >= 1
                && cooldownServed(f.playHoursSince(), f.ticksSince(), s)
                && f.siegesEnabled() && !f.peaceful() && !f.busy();
    }

    /** At least {@code cooldownPlayHours} of play and {@code cooldownDays} in-game days since the last one. */
    public static boolean cooldownServed(double playHoursSince, long ticksSince, HearthData.Siege s) {
        return playHoursSince >= s.cooldownPlayHours()
                && ticksSince >= Math.round(s.cooldownDays() * HearthData.TICKS_PER_DAY);
    }

    /** The chance of one roll at {@code playHoursSince}: 0 before the cooldown, ramping to pMax, 1 from the pity point. */
    public static double chance(double playHoursSince, HearthData.Siege s) {
        if (!(playHoursSince >= s.cooldownPlayHours())) {
            return 0.0;
        }
        if (playHoursSince >= s.pityHours()) {
            return 1.0;
        }
        double ramp = s.rampHours() <= 0 ? 1.0 : Math.min(1.0, (playHoursSince - s.cooldownPlayHours()) / s.rampHours());
        return s.pMax() * ramp;
    }

    /** One roll with a uniform {@code random} in [0, 1). */
    public static boolean roll(double playHoursSince, double random, HearthData.Siege s) {
        return random < chance(playHoursSince, s);
    }

    // ---- Siege levels ----------------------------------------------------------------------------
    //
    // A siege has a level, I..V. The data maps (baseMobs, mobs) are keyed 3..7: the key is the level plus
    // two, so key 3..6 is what ceiling III..VI always meant and key 7 is level V. A rolled siege takes the
    // level of the ceiling (III -> I .. VI -> IV); a horn brings its own, gated by the ceiling.

    /** The highest siege level: the Horn of the Warhost. */
    public static final int MAX_LEVEL = 5;
    /** The data key of {@link #MAX_LEVEL}. */
    public static final int MAX_KEY = MAX_LEVEL + 2;

    /** The data key (baseMobs, mobs) of a siege level. */
    public static int levelKey(int level) {
        return Math.max(1, Math.min(MAX_LEVEL, level)) + 2;
    }

    /** The level a rolled siege has at a ceiling: III -> I .. VI -> IV (0 below III: no siege). */
    public static int levelForCeiling(int ceiling) {
        return ceiling < 3 ? 0 : Math.min(HearthData.TIERS, ceiling) - 2;
    }

    /** The ceiling a horn of {@code level} needs: I -> III, II -> IV, III -> V, IV and V -> VI. */
    public static int ceilingForLevel(int level) {
        return Math.min(HearthData.TIERS, Math.max(1, level) + 2);
    }

    /** The trophy tier (3..6) of a siege level: V's trophy is VI's. */
    public static int trophyTier(int level) {
        return Math.min(HearthData.TIERS, levelKey(level));
    }

    /**
     * How the victory burst scales with the siege's level against the level the ceiling would roll:
     * a weaker horn at a strong Hearth earns less, the Warhost at VI earns a quarter more.
     */
    public static double burstScale(int level, int ceiling) {
        int natural = levelForCeiling(ceiling);
        if (natural <= 0 || level <= 0) {
            return 1.0;
        }
        return Math.max(0.25, Math.min(1.25, level / (double) natural));
    }

    /** Whether a Siege Horn may call a siege here now. */
    public static boolean hornAllowed(int ceiling, double playHoursSince, boolean busy, HearthData.Siege s) {
        return ceiling >= 3 && !busy && playHoursSince >= s.hornCooldownPlayHours();
    }

    /** When a rolled siege is due: {@code warningTicks} of game time from now. */
    public static long rolledDue(long now, HearthData.Siege s) {
        return now + Math.max(0L, s.warningTicks());
    }

    /** When a horn's siege is due: {@code hornMinDelaySeconds} from now, then the next dusk. */
    public static long hornDue(long now, HearthData.Siege s) {
        return now + Math.max(0L, Math.round(s.hornMinDelaySeconds() * 20.0));
    }

    public static boolean isNight(long dayTime) {
        long t = Math.floorMod(dayTime, HearthData.TICKS_PER_DAY);
        return t >= DUSK && t < DAWN;
    }

    /** A due siege starts on the transition into night after its due time. */
    public static boolean startsNow(long now, long due, boolean nightNow, boolean nightBefore) {
        return now >= due && nightNow && !nightBefore;
    }

    /** At a nightfall the siege is due: start if someone is home, else postpone, at most {@code postponeNights}, then fizzle. */
    public static Nightfall atNightfall(boolean someoneHome, int postponedSoFar, HearthData.Siege s) {
        if (someoneHome) {
            return Nightfall.START;
        }
        return postponedSoFar < s.postponeNights() ? Nightfall.POSTPONE : Nightfall.FIZZLE;
    }

    /**
     * The "last siege ended" moment on both cooldown clocks, in the units {@link HearthState} stores:
     * {@code playTicks} on the world's play clock and {@code gameTicks} of game time.
     */
    public record SiegeEnd(long playTicks, long gameTicks) {
    }

    /** A siege ended now (defended, doused, dawn or abandoned): both clocks start from here. */
    public static SiegeEnd endedAt(long playNow, long gameNow) {
        return new SiegeEnd(playNow, gameNow);
    }

    /**
     * After a fizzle the cooldown counts as half served, on both of its clocks: the end to store is
     * half of {@code cooldownPlayHours} of play before now and half of {@code cooldownDays} of game
     * time before now, so the next roll waits the other half (and the pity clock starts there too).
     *
     * @param playNow the world's play clock now, in play ticks ({@code HearthWorldData.playTicks()})
     * @param gameNow game time now ({@code level.getGameTime()})
     */
    public static SiegeEnd fizzledEnd(long playNow, long gameNow, HearthData.Siege s) {
        long halfPlay = Math.round(Math.max(0.0, s.cooldownPlayHours()) * HearthData.TICKS_PER_HOUR / 2.0);
        long halfDays = Math.round(Math.max(0.0, s.cooldownDays()) * HearthData.TICKS_PER_DAY / 2.0);
        return new SiegeEnd(playNow - halfPlay, gameNow - halfDays);
    }

    /** Mobs in one wave: the ceiling's base, +{@code perExtraPlayer} per extra non-AFK player in range. */
    public static int waveSize(int ceiling, int playersHome, HearthData.Siege s) {
        int base = s.baseMobs(Math.min(MAX_KEY, ceiling));
        if (base <= 0) {
            return 0;
        }
        int extra = Math.max(0, playersHome - 1);
        return (int) Math.round(base * (1.0 + s.perExtraPlayer() * extra));
    }

    /** The next wave comes once the share {@code nextWaveAt} of the current one is dead. */
    public static boolean nextWaveDue(int spawned, int alive, HearthData.Siege s) {
        if (spawned <= 0) {
            return true;
        }
        return (spawned - alive) >= Math.ceil(spawned * s.nextWaveAt());
    }

    /** The mob mix at a ceiling: every lower ceiling's list plus its own, without repeats. */
    public static List<String> mobsFor(int ceiling, HearthData.Siege s) {
        Set<String> out = new LinkedHashSet<>();
        for (Map.Entry<Integer, List<String>> entry : s.mobs().entrySet()) {
            if (entry.getKey() <= ceiling) {
                out.addAll(entry.getValue());
            }
        }
        return new ArrayList<>(out);
    }

    /** The defence streak after an outcome: +1 for a defence, back to 0 when doused, else kept. */
    public static int streakAfter(int streak, Outcome outcome) {
        return switch (outcome) {
            case DEFENDED -> streak + 1;
            case DOUSED -> 0;
            default -> streak;
        };
    }

    /**
     * The victory burst for one pool: {@code burst + streakBonus x min(streak, streakCap)} of the
     * pool's next tier threshold (VI's own threshold at VI), outside the hourly cap. {@code streak}
     * is the streak including this defence.
     */
    public static double burst(double charge, int streak, HearthData data, double scale) {
        return burst(charge, streak, data) * Math.max(0.0, scale);
    }

    public static double burst(double charge, int streak, HearthData data) {
        HearthData.Siege s = data.siege();
        int tier = HearthRules.chargeTier(charge, data);
        double next = data.threshold(Math.min(HearthData.TIERS, tier + 1));
        double share = s.burst() + s.streakBonus() * Math.max(0, Math.min(streak, s.streakCap()));
        return next * share;
    }

    /** A pool's charge after a doused siege: {@code dousedLoss} of it gone. */
    public static double doused(double charge, HearthData.Siege s) {
        return Math.max(0.0, charge * (1.0 - s.dousedLoss()));
    }
}
