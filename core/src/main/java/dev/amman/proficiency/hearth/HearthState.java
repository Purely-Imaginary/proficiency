package dev.amman.proficiency.hearth;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/**
 * Everything one Hearth knows, as plain data: charge per pool, the build (stage, highest offering
 * paid, smouldering), the visit and decay clocks, the hourly caps, the ledger, the Masonry and
 * Decorating hold queue, the Comfort and Character cache, the siege fields, the Feast and the bell.
 * The block entity in common holds one and saves it with {@link #CODEC}; every rule that changes
 * it lives in the lanes, using the pure helpers of this package.
 *
 * <p>Game times are {@code Long.MIN_VALUE} for "never". Play time (for sieges) is in play ticks of
 * the world's play clock ({@code HearthWorldData}).
 */
public final class HearthState {

    public static final long NEVER = Long.MIN_VALUE;

    private final double[] charge = new double[HearthPool.COUNT];
    private final HourlyCap[] caps = new HourlyCap[HearthPool.COUNT];
    private final HearthLedger ledger = new HearthLedger();
    private final HoldQueue holds = new HoldQueue();
    private final FeastWindow feast = new FeastWindow();

    // Build
    private int stage;
    private int highestOffering;
    /** Reach: Homebuilder XP earned at this Hearth, ever. It never drains; it widens the range (HearthReach). */
    private double reach;
    private boolean smouldering;
    private long litAt = NEVER;
    private long lastVisited = NEVER;
    private long lastDecay = NEVER;

    // Comfort and Character, as the last scan found them, and every id ever seen here
    private int comfort;
    private final Set<String> comfortKinds = new LinkedHashSet<>();
    private final Set<String> comfortSeen = new LinkedHashSet<>();
    private int character;
    private final Set<String> characterSeen = new LinkedHashSet<>();
    private long lastScan = NEVER;

    // Sieges
    private boolean sieged;
    private SiegeRules.Phase siegePhase = SiegeRules.Phase.IDLE;
    private long siegeDue = NEVER;
    private boolean siegeByHorn;
    private int postponed;
    private int wave;
    private int defenceStreak;
    private double douse;
    private long lastSiegeEndPlay = NEVER;
    private long lastSiegeEndTick = NEVER;
    private boolean nightBefore;

    // The bell
    private long lastAlarm = NEVER;
    private long lastComeHome = NEVER;

    public HearthState() {
        for (int i = 0; i < caps.length; i++) {
            caps[i] = new HourlyCap();
        }
    }

    // ---- Charge ---------------------------------------------------------------------------------

    public double charge(HearthPool pool) {
        return charge[pool.ordinal()];
    }

    public void setCharge(HearthPool pool, double value) {
        charge[pool.ordinal()] = Double.isFinite(value) ? Math.max(0.0, value) : 0.0;
    }

    /** Adds charge with no cap (a siege burst, a rated tale). Returns what was added. */
    public double addUncapped(HearthPool pool, double amount) {
        if (!(amount > 0) || Double.isInfinite(amount)) {
            return 0.0;
        }
        charge[pool.ordinal()] += amount;
        return amount;
    }

    /** Adds charge through the pool's rolling hourly cap. Returns what the cap let in. */
    public double addCapped(HearthPool pool, double amount, long now, HearthData data) {
        double taken = caps[pool.ordinal()].take(now, amount, data.hourlyCap());
        charge[pool.ordinal()] += taken;
        return taken;
    }

    public HourlyCap cap(HearthPool pool) {
        return caps[pool.ordinal()];
    }

    public int chargeTier(HearthPool pool, HearthData data) {
        return HearthRules.chargeTier(charge(pool), data);
    }

    /** {@code min(stage, Character-allowed stage)}. */
    public int ceiling(HearthData data) {
        return HearthRules.ceiling(stage, character, data);
    }

    /** The tier boons use; 0 while smouldering. */
    public int effectiveTier(HearthPool pool, HearthData data) {
        if (smouldering) {
            return 0;
        }
        return HearthRules.effectiveTier(pool, charge(pool), ceiling(data), comfort, data);
    }

    /** What holds the pool below its charge tier: {@code CEILING} (stage), {@code CHARACTER}, {@code COMFORT} or none. */
    public HearthRules.Lock lock(HearthPool pool, HearthData data) {
        return HearthRules.lock(pool, charge(pool), stage, character, comfort, data);
    }

    /** What the pool's next tier needs from its {@link #lock}: the Comfort, the stage or the Character; 0 when free. */
    public int need(HearthPool pool, HearthData data) {
        return HearthRules.need(lock(pool, data), pool, effectiveTier(pool, data), ceiling(data), data);
    }

    /** The highest effective tier over every pool. */
    public int maxEffectiveTier(HearthData data) {
        int best = 0;
        for (HearthPool pool : HearthPool.VALUES) {
            best = Math.max(best, effectiveTier(pool, data));
        }
        return best;
    }

    /**
     * The once-a-second decay step. With someone home both clocks move to now and nothing decays;
     * otherwise every pool decays over the time since the later of the two clocks.
     */
    public void tickDecay(long now, boolean someoneHome, HearthData data) {
        if (someoneHome) {
            lastVisited = now;
            lastDecay = now;
            return;
        }
        if (lastDecay == NEVER) {
            lastDecay = now;
            return;
        }
        for (HearthPool pool : HearthPool.VALUES) {
            setCharge(pool, HearthDecay.apply(charge(pool), now, lastVisited == NEVER ? -1 : lastVisited, lastDecay,
                    data));
        }
        lastDecay = now;
    }

    /** The Hearth was just lit: every clock starts now, the siege clock from the world's play time. */
    public void onLit(long now, long playNow) {
        litAt = now;
        lastVisited = now;
        lastDecay = now;
        lastSiegeEndPlay = playNow;
        lastSiegeEndTick = now;
    }

    // ---- Accessors ------------------------------------------------------------------------------

    public HearthLedger ledger() {
        return ledger;
    }

    public HoldQueue holds() {
        return holds;
    }

    public FeastWindow feast() {
        return feast;
    }

    /** Homebuilder XP this Hearth has gathered as Reach (HearthReach turns it into a radius). */
    public double reach() {
        return reach;
    }

    /** Adds Homebuilder XP to Reach; anything not finite or not positive is ignored. */
    public void addReach(double xp) {
        if (Double.isFinite(xp) && xp > 0) {
            reach += xp;
        }
    }

    public void setReach(double value) {
        reach = Double.isFinite(value) && value > 0 ? value : 0.0;
    }

    public int stage() {
        return stage;
    }

    public void setStage(int stage) {
        this.stage = Math.max(0, Math.min(HearthData.TIERS, stage));
    }

    public int highestOffering() {
        return highestOffering;
    }

    public void setHighestOffering(int stage) {
        this.highestOffering = Math.max(0, Math.min(HearthData.TIERS, stage));
    }

    public boolean smouldering() {
        return smouldering;
    }

    public void setSmouldering(boolean smouldering) {
        this.smouldering = smouldering;
    }

    public long litAt() {
        return litAt;
    }

    public long lastVisited() {
        return lastVisited;
    }

    public void setLastVisited(long time) {
        this.lastVisited = time;
    }

    public long lastDecay() {
        return lastDecay;
    }

    public void setLastDecay(long time) {
        this.lastDecay = time;
    }

    public int comfort() {
        return comfort;
    }

    /** The Comfort kinds present now (also sets the count); {@link #comfortSeen} keeps every kind ever present. */
    public void setComfortKinds(Set<String> kinds) {
        comfortKinds.clear();
        comfortKinds.addAll(kinds);
        comfort = comfortKinds.size();
    }

    public Set<String> comfortKinds() {
        return java.util.Collections.unmodifiableSet(comfortKinds);
    }

    /** Records a kind as seen here; true the first time (the Homebuilder milestone). */
    public boolean markComfortSeen(String kind) {
        return comfortSeen.add(kind);
    }

    public Set<String> comfortSeen() {
        return java.util.Collections.unmodifiableSet(comfortSeen);
    }

    public int character() {
        return character;
    }

    public void setCharacter(int character) {
        this.character = Math.max(0, character);
    }

    /** Records a Character block id as seen here; true the first time (the Homebuilder milestone). */
    public boolean markCharacterSeen(String id) {
        return characterSeen.add(id);
    }

    public Set<String> characterSeen() {
        return java.util.Collections.unmodifiableSet(characterSeen);
    }

    public long lastScan() {
        return lastScan;
    }

    public void setLastScan(long time) {
        this.lastScan = time;
    }

    public boolean sieged() {
        return sieged;
    }

    /** The first horn siege happened here: random sieges may come from now on. */
    public void setSieged(boolean sieged) {
        this.sieged = sieged;
    }

    public SiegeRules.Phase siegePhase() {
        return siegePhase;
    }

    public void setSiegePhase(SiegeRules.Phase phase) {
        this.siegePhase = phase == null ? SiegeRules.Phase.IDLE : phase;
    }

    public long siegeDue() {
        return siegeDue;
    }

    public void setSiegeDue(long due) {
        this.siegeDue = due;
    }

    public boolean siegeByHorn() {
        return siegeByHorn;
    }

    public void setSiegeByHorn(boolean byHorn) {
        this.siegeByHorn = byHorn;
    }

    public int postponed() {
        return postponed;
    }

    public void setPostponed(int postponed) {
        this.postponed = Math.max(0, postponed);
    }

    public int wave() {
        return wave;
    }

    public void setWave(int wave) {
        this.wave = Math.max(0, wave);
    }

    public int defenceStreak() {
        return defenceStreak;
    }

    public void setDefenceStreak(int streak) {
        this.defenceStreak = Math.max(0, streak);
    }

    public double douse() {
        return douse;
    }

    public void setDouse(double douse) {
        this.douse = Double.isFinite(douse) ? Math.max(0.0, douse) : 0.0;
    }

    public long lastSiegeEndPlay() {
        return lastSiegeEndPlay;
    }

    public void setLastSiegeEndPlay(long playTicks) {
        this.lastSiegeEndPlay = playTicks;
    }

    public long lastSiegeEndTick() {
        return lastSiegeEndTick;
    }

    public void setLastSiegeEndTick(long time) {
        this.lastSiegeEndTick = time;
    }

    public boolean nightBefore() {
        return nightBefore;
    }

    public void setNightBefore(boolean night) {
        this.nightBefore = night;
    }

    public long lastAlarm() {
        return lastAlarm;
    }

    public void setLastAlarm(long time) {
        this.lastAlarm = time;
    }

    public long lastComeHome() {
        return lastComeHome;
    }

    public void setLastComeHome(long time) {
        this.lastComeHome = time;
    }

    /** Stores when the last siege ended, on both cooldown clocks ({@link SiegeRules#endedAt}, {@link SiegeRules#fizzledEnd}). */
    public void setLastSiegeEnd(SiegeRules.SiegeEnd end) {
        this.lastSiegeEndPlay = end.playTicks();
        this.lastSiegeEndTick = end.gameTicks();
    }

    /** Play hours since the last siege ended (or the lighting); infinite when neither is known. */
    public double playHoursSinceSiege(long playNow) {
        if (lastSiegeEndPlay == NEVER) {
            return Double.POSITIVE_INFINITY;
        }
        return (playNow - lastSiegeEndPlay) / (double) HearthData.TICKS_PER_HOUR;
    }

    /** Game ticks since the last siege ended (or the lighting); Long.MAX_VALUE when unknown. */
    public long ticksSinceSiege(long now) {
        return lastSiegeEndTick == NEVER ? Long.MAX_VALUE : now - lastSiegeEndTick;
    }

    // ---- Save -----------------------------------------------------------------------------------

    /** What a saved UUID that does not parse reads as (all zeros): no player has it. */
    public static final UUID NOBODY = new UUID(0L, 0L);

    /**
     * A UUID as a string; a bad one reads as {@link #NOBODY} (never an exception, which would abort
     * the whole load) and is dropped or ignored by the caller.
     */
    static final Codec<UUID> UUID_CODEC = Codec.STRING.xmap(HearthState::uuid, UUID::toString);

    static UUID uuid(String text) {
        try {
            return UUID.fromString(text);
        } catch (IllegalArgumentException e) {
            return NOBODY;
        }
    }

    private record Build(int stage, int offering, boolean smouldering, long litAt, long lastVisited, long lastDecay) {
        static final Codec<Build> CODEC = RecordCodecBuilder.create(i -> i.group(
                Codec.INT.optionalFieldOf("stage", 0).forGetter(Build::stage),
                Codec.INT.optionalFieldOf("offering", 0).forGetter(Build::offering),
                Codec.BOOL.optionalFieldOf("smouldering", false).forGetter(Build::smouldering),
                Codec.LONG.optionalFieldOf("litAt", NEVER).forGetter(Build::litAt),
                Codec.LONG.optionalFieldOf("lastVisited", NEVER).forGetter(Build::lastVisited),
                Codec.LONG.optionalFieldOf("lastDecay", NEVER).forGetter(Build::lastDecay)
        ).apply(i, Build::new));
    }

    private record Cache(int character, List<String> kinds, List<String> kindsSeen, List<String> characterSeen,
            long lastScan) {
        static final Codec<Cache> CODEC = RecordCodecBuilder.create(i -> i.group(
                Codec.INT.optionalFieldOf("character", 0).forGetter(Cache::character),
                Codec.STRING.listOf().optionalFieldOf("kinds", List.of()).forGetter(Cache::kinds),
                Codec.STRING.listOf().optionalFieldOf("kindsSeen", List.of()).forGetter(Cache::kindsSeen),
                Codec.STRING.listOf().optionalFieldOf("characterSeen", List.of()).forGetter(Cache::characterSeen),
                Codec.LONG.optionalFieldOf("lastScan", NEVER).forGetter(Cache::lastScan)
        ).apply(i, Cache::new));
    }

    private record SiegeSave(boolean sieged, int phase, long due, boolean byHorn, int postponed, int wave,
            int streak, double douse, long lastEndPlay, long lastEndTick, boolean nightBefore) {
        static final Codec<SiegeSave> CODEC = RecordCodecBuilder.create(i -> i.group(
                Codec.BOOL.optionalFieldOf("sieged", false).forGetter(SiegeSave::sieged),
                Codec.INT.optionalFieldOf("phase", 0).forGetter(SiegeSave::phase),
                Codec.LONG.optionalFieldOf("due", NEVER).forGetter(SiegeSave::due),
                Codec.BOOL.optionalFieldOf("byHorn", false).forGetter(SiegeSave::byHorn),
                Codec.INT.optionalFieldOf("postponed", 0).forGetter(SiegeSave::postponed),
                Codec.INT.optionalFieldOf("wave", 0).forGetter(SiegeSave::wave),
                Codec.INT.optionalFieldOf("streak", 0).forGetter(SiegeSave::streak),
                Codec.DOUBLE.optionalFieldOf("douse", 0.0).forGetter(SiegeSave::douse),
                Codec.LONG.optionalFieldOf("lastEndPlay", NEVER).forGetter(SiegeSave::lastEndPlay),
                Codec.LONG.optionalFieldOf("lastEndTick", NEVER).forGetter(SiegeSave::lastEndTick),
                Codec.BOOL.optionalFieldOf("nightBefore", false).forGetter(SiegeSave::nightBefore)
        ).apply(i, SiegeSave::new));
    }

    private record Social(long feastDay, long dinnerUntil, long lastAlarm, long lastComeHome) {
        static final Codec<Social> CODEC = RecordCodecBuilder.create(i -> i.group(
                Codec.LONG.optionalFieldOf("feastDay", NEVER).forGetter(Social::feastDay),
                Codec.LONG.optionalFieldOf("dinnerUntil", NEVER).forGetter(Social::dinnerUntil),
                Codec.LONG.optionalFieldOf("lastAlarm", NEVER).forGetter(Social::lastAlarm),
                Codec.LONG.optionalFieldOf("lastComeHome", NEVER).forGetter(Social::lastComeHome)
        ).apply(i, Social::new));
    }

    private static final Codec<HearthLedger.SavedPlayer> LEDGER_PLAYER = RecordCodecBuilder.create(i -> i.group(
            UUID_CODEC.fieldOf("id").forGetter(HearthLedger.SavedPlayer::player),
            Codec.STRING.optionalFieldOf("name", "").forGetter(HearthLedger.SavedPlayer::name),
            Codec.INT.optionalFieldOf("kills", 0).forGetter(HearthLedger.SavedPlayer::siegeKills),
            Codec.INT.optionalFieldOf("defended", 0).forGetter(HearthLedger.SavedPlayer::siegesDefended)
    ).apply(i, HearthLedger.SavedPlayer::new));

    private static final Codec<HearthLedger.SavedCell> LEDGER_CELL = RecordCodecBuilder.create(i -> i.group(
            UUID_CODEC.fieldOf("id").forGetter(HearthLedger.SavedCell::player),
            Codec.STRING.fieldOf("key").forGetter(HearthLedger.SavedCell::key),
            Codec.DOUBLE.optionalFieldOf("life", 0.0).forGetter(HearthLedger.SavedCell::lifetime),
            Codec.DOUBLE.listOf().optionalFieldOf("days", List.of()).forGetter(HearthLedger.SavedCell::days)
    ).apply(i, HearthLedger.SavedCell::new));

    private record LedgerSave(long day, List<HearthLedger.SavedPlayer> players, List<HearthLedger.SavedCell> cells) {
        static final Codec<LedgerSave> CODEC = RecordCodecBuilder.create(i -> i.group(
                Codec.LONG.optionalFieldOf("day", NEVER).forGetter(LedgerSave::day),
                LEDGER_PLAYER.listOf().optionalFieldOf("players", List.of()).forGetter(LedgerSave::players),
                LEDGER_CELL.listOf().optionalFieldOf("cells", List.of()).forGetter(LedgerSave::cells)
        ).apply(i, LedgerSave::new));
    }

    private record HoldSave(long pos, UUID player, String name, String pool, String source, String block,
            double amount, long due) {
        static final Codec<HoldSave> CODEC = RecordCodecBuilder.create(i -> i.group(
                Codec.LONG.fieldOf("pos").forGetter(HoldSave::pos),
                UUID_CODEC.fieldOf("id").forGetter(HoldSave::player),
                Codec.STRING.optionalFieldOf("name", "").forGetter(HoldSave::name),
                Codec.STRING.fieldOf("pool").forGetter(HoldSave::pool),
                Codec.STRING.optionalFieldOf("source", "").forGetter(HoldSave::source),
                Codec.STRING.optionalFieldOf("block", "").forGetter(HoldSave::block),
                Codec.DOUBLE.fieldOf("amount").forGetter(HoldSave::amount),
                Codec.LONG.fieldOf("due").forGetter(HoldSave::due)
        ).apply(i, HoldSave::new));
    }

    private record Saved(List<Double> charge, List<List<Long>> caps, Build build, Cache cache, SiegeSave siege,
            Social social, LedgerSave ledger, List<HoldSave> holds, double reach) {
        static final Codec<Saved> CODEC = RecordCodecBuilder.create(i -> i.group(
                Codec.DOUBLE.listOf().optionalFieldOf("charge", List.of()).forGetter(Saved::charge),
                Codec.LONG.listOf().listOf().optionalFieldOf("caps", List.of()).forGetter(Saved::caps),
                Build.CODEC.optionalFieldOf("build", new Build(0, 0, false, NEVER, NEVER, NEVER)).forGetter(Saved::build),
                Cache.CODEC.optionalFieldOf("cache", new Cache(0, List.of(), List.of(), List.of(), NEVER))
                        .forGetter(Saved::cache),
                SiegeSave.CODEC.optionalFieldOf("siege", new SiegeSave(false, 0, NEVER, false, 0, 0, 0, 0.0, NEVER, NEVER,
                        false)).forGetter(Saved::siege),
                Social.CODEC.optionalFieldOf("social", new Social(NEVER, NEVER, NEVER, NEVER)).forGetter(Saved::social),
                LedgerSave.CODEC.optionalFieldOf("ledger", new LedgerSave(NEVER, List.of(), List.of()))
                        .forGetter(Saved::ledger),
                HoldSave.CODEC.listOf().optionalFieldOf("holds", List.of()).forGetter(Saved::holds),
                Codec.DOUBLE.optionalFieldOf("reach", 0.0).forGetter(Saved::reach)
        ).apply(i, Saved::new));
    }

    public static final Codec<HearthState> CODEC = Saved.CODEC.xmap(HearthState::fromSaved, HearthState::toSaved);

    private Saved toSaved() {
        List<Double> charges = new ArrayList<>(HearthPool.COUNT);
        for (double value : charge) {
            charges.add(value);
        }
        List<List<Long>> capLists = new ArrayList<>(HearthPool.COUNT);
        for (HourlyCap cap : caps) {
            List<Long> flat = new ArrayList<>();
            for (long[] bucket : cap.encode()) {
                flat.add(bucket[0]);
                flat.add(bucket[1]);
            }
            capLists.add(flat);
        }
        List<HoldSave> holdList = new ArrayList<>();
        for (HoldQueue.Due due : holds.all()) {
            HoldQueue.Held h = due.held();
            holdList.add(new HoldSave(due.pos(), h.player(), h.name() == null ? "" : h.name(), h.pool().id(),
                    h.source() == null ? "" : h.source(), h.block() == null ? "" : h.block(), h.amount(), h.due()));
        }
        return new Saved(charges, capLists,
                new Build(stage, highestOffering, smouldering, litAt, lastVisited, lastDecay),
                new Cache(character, new ArrayList<>(comfortKinds), new ArrayList<>(comfortSeen),
                        new ArrayList<>(characterSeen), lastScan),
                new SiegeSave(sieged, siegePhase.ordinal(), siegeDue, siegeByHorn, postponed, wave, defenceStreak, douse,
                        lastSiegeEndPlay, lastSiegeEndTick, nightBefore),
                new Social(feast.lastFeastDay(), feast.dinnerUntil(), lastAlarm, lastComeHome),
                new LedgerSave(ledger.currentDay(), ledger.savedPlayers(), ledger.savedCells()),
                holdList, reach);
    }

    private static HearthState fromSaved(Saved saved) {
        HearthState state = new HearthState();
        for (int i = 0; i < Math.min(HearthPool.COUNT, saved.charge().size()); i++) {
            state.setCharge(HearthPool.VALUES[i], saved.charge().get(i));
        }
        for (int i = 0; i < Math.min(HearthPool.COUNT, saved.caps().size()); i++) {
            List<Long> flat = saved.caps().get(i);
            List<long[]> buckets = new ArrayList<>();
            for (int j = 0; j + 1 < flat.size(); j += 2) {
                buckets.add(new long[] {flat.get(j), flat.get(j + 1)});
            }
            state.caps[i].decode(buckets);
        }
        Build b = saved.build();
        state.setStage(b.stage());
        state.setHighestOffering(b.offering());
        state.setReach(saved.reach());
        state.smouldering = b.smouldering();
        state.litAt = b.litAt();
        state.lastVisited = b.lastVisited();
        state.lastDecay = b.lastDecay();
        Cache c = saved.cache();
        state.setComfortKinds(new LinkedHashSet<>(c.kinds()));
        state.comfortSeen.addAll(c.kindsSeen());
        state.setCharacter(c.character());
        state.characterSeen.addAll(c.characterSeen());
        state.lastScan = c.lastScan();
        SiegeSave s = saved.siege();
        state.sieged = s.sieged();
        state.siegePhase = SiegeRules.Phase.byOrdinal(s.phase());
        state.siegeDue = s.due();
        state.siegeByHorn = s.byHorn();
        state.setPostponed(s.postponed());
        state.setWave(s.wave());
        state.setDefenceStreak(s.streak());
        state.setDouse(s.douse());
        state.lastSiegeEndPlay = s.lastEndPlay();
        state.lastSiegeEndTick = s.lastEndTick();
        state.nightBefore = s.nightBefore();
        Social so = saved.social();
        state.feast.restore(so.feastDay(), so.dinnerUntil());
        state.lastAlarm = so.lastAlarm();
        state.lastComeHome = so.lastComeHome();
        LedgerSave l = saved.ledger();
        List<HearthLedger.SavedPlayer> players = new ArrayList<>();
        for (HearthLedger.SavedPlayer p : l.players()) {
            if (!NOBODY.equals(p.player())) {
                players.add(p);
            }
        }
        List<HearthLedger.SavedCell> cells = new ArrayList<>();
        for (HearthLedger.SavedCell cell : l.cells()) {
            if (!NOBODY.equals(cell.player())) {
                cells.add(cell);
            }
        }
        state.ledger.load(l.day(), players, cells);
        for (HoldSave h : saved.holds()) {
            HearthPool pool = HearthPool.byId(h.pool());
            if (pool != null && !NOBODY.equals(h.player())) {
                state.holds.add(h.pos(), new HoldQueue.Held(h.player(), h.name(), pool, h.source(), h.block(),
                        h.amount(), h.due()));
            }
        }
        return state;
    }
}
