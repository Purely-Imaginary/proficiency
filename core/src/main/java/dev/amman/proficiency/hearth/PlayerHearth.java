package dev.amman.proficiency.hearth;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * A player's Hearth state, saved with the player as the loader attachment {@code proficiency:hearth}
 * ({@link #CODEC}). Server side only; the client hears the parts it shows through the HUD payload.
 *
 * <ul>
 * <li><b>Warmth</b> 0..1, owned by the per-player loop.</li>
 * <li><b>In range</b>: the Hearth's tick writes {@code inRangeOf = pos, dimension, until = now + 40}
 * on each player it sees; anything older than {@code until} means "not home". So a broken,
 * unloaded or switched-off Hearth, a teleport or a dimension change all end "home" within 2 s.</li>
 * <li><b>Tales</b> banked away, the last homecoming's payout and whether its Chronicle bonus is
 * still open.</li>
 * <li><b>Provisioned</b>: strength and the from/until game times it fades between.</li>
 * <li><b>Temp HP</b>: the Hearth's share of absorption (1.20.1 has no cap attribute) and the
 * 1.21.1 modifier size, which may only grow while absorption lasts.</li>
 * <li><b>Feast and bell</b> marks, <b>combat</b> (last damage), <b>edge</b> flags for the enter/leave
 * cues, and room for Homebuilder: the Homeward day, the magnet switch, milestones.</li>
 * <li>{@code marks} and {@code flags}: free per-lane extras, keys named {@code <lane>.<what>}, so a
 * lane can add a saved field without touching this class.</li>
 * </ul>
 *
 * <p>Copied on every respawn and End exit by the Clone handler; a death then clears what death
 * costs ({@link #clearOnDeath}).
 */
public final class PlayerHearth {

    public static final long NEVER = Long.MIN_VALUE;

    /** How long one sighting by a Hearth's tick keeps a player "home": 40 ticks, two Hearth seconds. */
    public static final long IN_RANGE_TICKS = 40L;

    private float warmth;
    private long inRangePos;
    private String inRangeDim = "";
    private long inRangeUntil = NEVER;
    private boolean wasHome;

    private double tales;
    private double lastTalesPaid;
    private boolean chronicleOpen;

    private float provisionedStrength;
    private long provisionedFrom = NEVER;
    private long provisionedUntil = NEVER;

    private float absorbShare;
    private float absorbModifier;

    private long lastDamage = NEVER;
    private long lastMeal = NEVER;
    private long lastFeast = NEVER;
    private long bellHeardUntil = NEVER;
    private long bellPos;
    private String bellDim = "";

    private long homewardDay = NEVER;
    private boolean magnetOff;
    private final Set<String> milestones = new LinkedHashSet<>();

    private final Map<String, Long> marks = new LinkedHashMap<>();
    private final Set<String> flags = new LinkedHashSet<>();

    // ---- In range -------------------------------------------------------------------------------

    /** Called by a Hearth's tick for each player it sees. */
    public void seenBy(long hearthPos, String dimension, long now) {
        inRangePos = hearthPos;
        inRangeDim = dimension == null ? "" : dimension;
        inRangeUntil = now + IN_RANGE_TICKS;
    }

    /** Whether a Hearth saw this player recently enough, in this dimension. */
    public boolean inRange(String dimension, long now) {
        return inRangeUntil != NEVER && now <= inRangeUntil && inRangeDim.equals(dimension);
    }

    /** The packed position of the Hearth that last saw this player (check {@link #inRange} first). */
    public long inRangePos() {
        return inRangePos;
    }

    public String inRangeDim() {
        return inRangeDim;
    }

    public long inRangeUntil() {
        return inRangeUntil;
    }

    public void clearInRange() {
        inRangeUntil = NEVER;
    }

    /** The last state the edge cues saw, so "By the Hearth" plays on the edge, not every second. */
    public boolean wasHome() {
        return wasHome;
    }

    public void setWasHome(boolean home) {
        this.wasHome = home;
    }

    // ---- Warmth ---------------------------------------------------------------------------------

    public float warmth() {
        return warmth;
    }

    public void setWarmth(double value) {
        warmth = (float) (Double.isNaN(value) ? 0.0 : Math.max(0.0, Math.min(1.0, value)));
    }

    // ---- Tales ----------------------------------------------------------------------------------

    public double tales() {
        return tales;
    }

    public void setTales(double value) {
        tales = Double.isFinite(value) ? Math.max(0.0, value) : 0.0;
    }

    public void addTales(double amount) {
        if (amount > 0 && Double.isFinite(amount)) {
            tales += amount;
        }
    }

    /** Empties the bank and returns what it held. */
    public double takeTales() {
        double held = tales;
        tales = 0.0;
        return held;
    }

    public double lastTalesPaid() {
        return lastTalesPaid;
    }

    /** A homecoming paid {@code amount}: remember it and open the one Chronicle bonus it allows. */
    public void notePaid(double amount) {
        lastTalesPaid = amount > 0 && Double.isFinite(amount) ? amount : 0.0;
        chronicleOpen = lastTalesPaid > 0;
    }

    public boolean chronicleOpen() {
        return chronicleOpen;
    }

    /** Takes the open Chronicle bonus base (the last payout), once; 0 when it was already used. */
    public double takeChronicleBase() {
        if (!chronicleOpen) {
            return 0.0;
        }
        chronicleOpen = false;
        return lastTalesPaid;
    }

    // ---- Provisioned ----------------------------------------------------------------------------

    public void provision(double strength, long from, long until) {
        provisionedStrength = (float) (Double.isFinite(strength) ? Math.max(0.0, strength) : 0.0);
        provisionedFrom = from;
        provisionedUntil = until;
    }

    public double provisionedAt(long now) {
        return WarmthMath.provisioned(provisionedStrength, provisionedFrom, provisionedUntil, now);
    }

    public float provisionedStrength() {
        return provisionedStrength;
    }

    public long provisionedFrom() {
        return provisionedFrom;
    }

    public long provisionedUntil() {
        return provisionedUntil;
    }

    // ---- Temp HP --------------------------------------------------------------------------------

    public float absorbShare() {
        return absorbShare;
    }

    public void setAbsorbShare(double value) {
        absorbShare = (float) (Double.isFinite(value) ? Math.max(0.0, value) : 0.0);
    }

    public float absorbModifier() {
        return absorbModifier;
    }

    public void setAbsorbModifier(double value) {
        absorbModifier = (float) (Double.isFinite(value) ? Math.max(0.0, value) : 0.0);
    }

    // ---- Combat, Feast, bell --------------------------------------------------------------------

    public long lastDamage() {
        return lastDamage;
    }

    public void setLastDamage(long time) {
        lastDamage = time;
    }

    public long lastMeal() {
        return lastMeal;
    }

    public void setLastMeal(long time) {
        lastMeal = time;
    }

    public long lastFeast() {
        return lastFeast;
    }

    public void setLastFeast(long time) {
        lastFeast = time;
    }

    /** The come-home bell: where it rang and until when this player counts as having heard it. */
    public void heardBell(long hearthPos, String dimension, long until) {
        bellPos = hearthPos;
        bellDim = dimension == null ? "" : dimension;
        bellHeardUntil = until;
    }

    public boolean heardBell(long now) {
        return bellHeardUntil != NEVER && now <= bellHeardUntil;
    }

    public long bellPos() {
        return bellPos;
    }

    public String bellDim() {
        return bellDim;
    }

    public long bellHeardUntil() {
        return bellHeardUntil;
    }

    // ---- Homebuilder ----------------------------------------------------------------------------

    public long homewardDay() {
        return homewardDay;
    }

    public void setHomewardDay(long day) {
        homewardDay = day;
    }

    public boolean magnetOff() {
        return magnetOff;
    }

    public void setMagnetOff(boolean off) {
        magnetOff = off;
    }

    /** Records a milestone key ({@code <hearth key>|<milestone>}); true the first time. */
    public boolean markMilestone(String key) {
        return milestones.add(key);
    }

    public boolean hasMilestone(String key) {
        return milestones.contains(key);
    }

    // ---- Free extras ----------------------------------------------------------------------------

    public long mark(String key, long fallback) {
        Long value = marks.get(key);
        return value == null ? fallback : value;
    }

    public void setMark(String key, long value) {
        marks.put(key, value);
    }

    public void clearMark(String key) {
        marks.remove(key);
    }

    /** Every mark key, a copy (Homebuilder's Homeward reads its {@code homebuilder.fed|...} marks). */
    public List<String> markKeys() {
        return List.copyOf(marks.keySet());
    }

    public boolean flag(String key) {
        return flags.contains(key);
    }

    public void setFlag(String key, boolean on) {
        if (on) {
            flags.add(key);
        } else {
            flags.remove(key);
        }
    }

    /** The flags that are on, in the order they were set (read only): a lane lists its own by prefix. */
    public Set<String> flags() {
        return java.util.Collections.unmodifiableSet(flags);
    }

    /** Turns off every flag that starts with {@code prefix}; returns how many were on. */
    public int clearFlags(String prefix) {
        int before = flags.size();
        flags.removeIf(flag -> flag.startsWith(prefix));
        return before - flags.size();
    }

    // ---- Life cycle -----------------------------------------------------------------------------

    /**
     * What a death takes (abuse check 6: no boon softens death): the banked Tales, Warmth, the open
     * Chronicle bonus, Provisioned, the temp-HP share and "home". Returns the Tales lost, for
     * telemetry. Feast, bell and Homebuilder marks stay.
     */
    public double clearOnDeath() {
        double lost = tales;
        tales = 0.0;
        warmth = 0f;
        chronicleOpen = false;
        provisionedStrength = 0f;
        provisionedFrom = NEVER;
        provisionedUntil = NEVER;
        absorbShare = 0f;
        absorbModifier = 0f;
        inRangeUntil = NEVER;
        wasHome = false;
        lastDamage = NEVER;
        return lost;
    }

    public void copyFrom(PlayerHearth other) {
        warmth = other.warmth;
        inRangePos = other.inRangePos;
        inRangeDim = other.inRangeDim;
        inRangeUntil = other.inRangeUntil;
        wasHome = other.wasHome;
        tales = other.tales;
        lastTalesPaid = other.lastTalesPaid;
        chronicleOpen = other.chronicleOpen;
        provisionedStrength = other.provisionedStrength;
        provisionedFrom = other.provisionedFrom;
        provisionedUntil = other.provisionedUntil;
        absorbShare = other.absorbShare;
        absorbModifier = other.absorbModifier;
        lastDamage = other.lastDamage;
        lastMeal = other.lastMeal;
        lastFeast = other.lastFeast;
        bellHeardUntil = other.bellHeardUntil;
        bellPos = other.bellPos;
        bellDim = other.bellDim;
        homewardDay = other.homewardDay;
        magnetOff = other.magnetOff;
        milestones.clear();
        milestones.addAll(other.milestones);
        marks.clear();
        marks.putAll(other.marks);
        flags.clear();
        flags.addAll(other.flags);
    }

    // ---- Save -----------------------------------------------------------------------------------

    private record Home(float warmth, long pos, String dim, long until, boolean wasHome) {
        static final Codec<Home> CODEC = RecordCodecBuilder.create(i -> i.group(
                Codec.FLOAT.optionalFieldOf("warmth", 0f).forGetter(Home::warmth),
                Codec.LONG.optionalFieldOf("pos", 0L).forGetter(Home::pos),
                Codec.STRING.optionalFieldOf("dim", "").forGetter(Home::dim),
                Codec.LONG.optionalFieldOf("until", NEVER).forGetter(Home::until),
                Codec.BOOL.optionalFieldOf("wasHome", false).forGetter(Home::wasHome)
        ).apply(i, Home::new));
    }

    private record Trip(double tales, double lastPaid, boolean chronicleOpen, float provStrength, long provFrom,
            long provUntil, float absorbShare, float absorbModifier) {
        static final Codec<Trip> CODEC = RecordCodecBuilder.create(i -> i.group(
                Codec.DOUBLE.optionalFieldOf("tales", 0.0).forGetter(Trip::tales),
                Codec.DOUBLE.optionalFieldOf("lastPaid", 0.0).forGetter(Trip::lastPaid),
                Codec.BOOL.optionalFieldOf("chronicleOpen", false).forGetter(Trip::chronicleOpen),
                Codec.FLOAT.optionalFieldOf("provStrength", 0f).forGetter(Trip::provStrength),
                Codec.LONG.optionalFieldOf("provFrom", NEVER).forGetter(Trip::provFrom),
                Codec.LONG.optionalFieldOf("provUntil", NEVER).forGetter(Trip::provUntil),
                Codec.FLOAT.optionalFieldOf("absorbShare", 0f).forGetter(Trip::absorbShare),
                Codec.FLOAT.optionalFieldOf("absorbModifier", 0f).forGetter(Trip::absorbModifier)
        ).apply(i, Trip::new));
    }

    private record Times(long lastDamage, long lastMeal, long lastFeast, long bellUntil, long bellPos, String bellDim) {
        static final Codec<Times> CODEC = RecordCodecBuilder.create(i -> i.group(
                Codec.LONG.optionalFieldOf("lastDamage", NEVER).forGetter(Times::lastDamage),
                Codec.LONG.optionalFieldOf("lastMeal", NEVER).forGetter(Times::lastMeal),
                Codec.LONG.optionalFieldOf("lastFeast", NEVER).forGetter(Times::lastFeast),
                Codec.LONG.optionalFieldOf("bellUntil", NEVER).forGetter(Times::bellUntil),
                Codec.LONG.optionalFieldOf("bellPos", 0L).forGetter(Times::bellPos),
                Codec.STRING.optionalFieldOf("bellDim", "").forGetter(Times::bellDim)
        ).apply(i, Times::new));
    }

    private record Builder(long homewardDay, boolean magnetOff, List<String> milestones) {
        static final Codec<Builder> CODEC = RecordCodecBuilder.create(i -> i.group(
                Codec.LONG.optionalFieldOf("homewardDay", NEVER).forGetter(Builder::homewardDay),
                Codec.BOOL.optionalFieldOf("magnetOff", false).forGetter(Builder::magnetOff),
                Codec.STRING.listOf().optionalFieldOf("milestones", List.of()).forGetter(Builder::milestones)
        ).apply(i, Builder::new));
    }

    private record Saved(Home home, Trip trip, Times times, Builder builder, Map<String, Long> marks,
            List<String> flags) {
        static final Codec<Saved> CODEC = RecordCodecBuilder.create(i -> i.group(
                Home.CODEC.optionalFieldOf("home", new Home(0f, 0L, "", NEVER, false)).forGetter(Saved::home),
                Trip.CODEC.optionalFieldOf("trip", new Trip(0.0, 0.0, false, 0f, NEVER, NEVER, 0f, 0f))
                        .forGetter(Saved::trip),
                Times.CODEC.optionalFieldOf("times", new Times(NEVER, NEVER, NEVER, NEVER, 0L, ""))
                        .forGetter(Saved::times),
                Builder.CODEC.optionalFieldOf("homebuilder", new Builder(NEVER, false, List.of()))
                        .forGetter(Saved::builder),
                Codec.unboundedMap(Codec.STRING, Codec.LONG).optionalFieldOf("marks", Map.of()).forGetter(Saved::marks),
                Codec.STRING.listOf().optionalFieldOf("flags", List.of()).forGetter(Saved::flags)
        ).apply(i, Saved::new));
    }

    public static final Codec<PlayerHearth> CODEC = Saved.CODEC.xmap(PlayerHearth::fromSaved, PlayerHearth::toSaved);

    private Saved toSaved() {
        return new Saved(new Home(warmth, inRangePos, inRangeDim, inRangeUntil, wasHome),
                new Trip(tales, lastTalesPaid, chronicleOpen, provisionedStrength, provisionedFrom, provisionedUntil,
                        absorbShare, absorbModifier),
                new Times(lastDamage, lastMeal, lastFeast, bellHeardUntil, bellPos, bellDim),
                new Builder(homewardDay, magnetOff, List.copyOf(milestones)),
                new LinkedHashMap<>(marks), List.copyOf(flags));
    }

    private static PlayerHearth fromSaved(Saved saved) {
        PlayerHearth p = new PlayerHearth();
        p.setWarmth(saved.home().warmth());
        p.inRangePos = saved.home().pos();
        p.inRangeDim = saved.home().dim();
        p.inRangeUntil = saved.home().until();
        p.wasHome = saved.home().wasHome();
        p.setTales(saved.trip().tales());
        p.lastTalesPaid = Double.isFinite(saved.trip().lastPaid()) ? Math.max(0.0, saved.trip().lastPaid()) : 0.0;
        p.chronicleOpen = saved.trip().chronicleOpen();
        p.provision(saved.trip().provStrength(), saved.trip().provFrom(), saved.trip().provUntil());
        p.setAbsorbShare(saved.trip().absorbShare());
        p.setAbsorbModifier(saved.trip().absorbModifier());
        p.lastDamage = saved.times().lastDamage();
        p.lastMeal = saved.times().lastMeal();
        p.lastFeast = saved.times().lastFeast();
        p.bellHeardUntil = saved.times().bellUntil();
        p.bellPos = saved.times().bellPos();
        p.bellDim = saved.times().bellDim();
        p.homewardDay = saved.builder().homewardDay();
        p.magnetOff = saved.builder().magnetOff();
        p.milestones.addAll(saved.builder().milestones());
        p.marks.putAll(saved.marks());
        p.flags.addAll(saved.flags());
        return p;
    }
}
