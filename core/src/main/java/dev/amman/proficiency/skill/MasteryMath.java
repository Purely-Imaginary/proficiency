package dev.amman.proficiency.skill;

/**
 * The numbers behind the Mastery riders that are not the headline value in {@link MasteryEffects}:
 * the fixed damage of a phantom hit, the speed that carries a dash a given distance, the launch
 * velocity of a rebound, the stretch of a potion. Pure arithmetic, so the unit tests can hold the
 * game code to the table without a world.
 */
public final class MasteryMath {

    /** Magic damage of one arc of Arcing Tide, one Ricochet, one splinter of Splinter Bolt, one Whirl. */
    public static final float HIT_DAMAGE = 4.0f;
    /** Damage of one Flurry jab. */
    public static final float JAB_DAMAGE = 2.0f;
    /** Damage of one Magic Missile. */
    public static final float MISSILE_DAMAGE = 3.0f;
    /** Damage of a Shield Bash and of a Stampede. */
    public static final float BASH_DAMAGE = 2.0f;
    /** Ricochet damage of the arrow, per hop. */
    public static final float RICOCHET_DAMAGE = 5.0f;
    /** How far Echo Slash reaches beside the foe it echoes on, in blocks. */
    public static final double ECHO_ARC = 2.5;
    /** Reach of an Arcing Tide hop, a Ricochet hop and a Magic Missile, in blocks. */
    public static final double HOP_RANGE = 8.0;
    public static final double MISSILE_RANGE = 10.0;
    /** Upward speed Tremor gives a hostile. */
    public static final double TREMOR_LIFT = 0.75;
    /** Hostiles that War Cry rattles stay weakened this long, in ticks. */
    public static final int WAR_CRY_TICKS = 120;
    /** Pinning Fire slows and lights up this long, in ticks. */
    public static final int PIN_TICKS = 100;
    /** Moon Gaze lights up this long, in ticks. */
    public static final int GAZE_TICKS = 160;
    /** How long a glow marker (ore, chest, spawner) shines, in ticks. */
    public static final int MARK_TICKS = 120;
    /** Most markers one Motherlode Ping or Cave Sense places. */
    public static final int MAX_MARKS = 12;
    /** Most foes one area or chain rider touches, however many stand around. */
    public static final int MAX_VICTIMS = 8;
    /** Most dropped items one Tractor Beam pulls. */
    public static final int MAX_PULLED = 24;
    /** Most crops, saplings, flowers, blocks one placing rider touches is the table value; this caps the search. */
    public static final int SEARCH_RADIUS = 6;
    /** Longest stretch one Reagent Echo gives one effect, in ticks, and the most any effect may reach. */
    public static final int ECHO_MAX_ADD_TICKS = 20 * 120;
    public static final int ECHO_MIN_ADD_TICKS = 20 * 5;
    public static final int ECHO_CAP_TICKS = 20 * 60 * 20;
    /** Per-tick velocity left after drag on the ground, in the air and in water. */
    public static final double GROUND_DRAG = 0.546;
    public static final double WATER_DRAG = 0.8;
    /** Highest speed a dash starts with, in blocks per tick: well under the server's "moved too quickly" line. */
    public static final double MAX_DASH_SPEED = 3.5;

    /** Harmful effects Shrug It Off washes off, in the order the stars open them up. */
    public static final String[] SHRUG_ORDER = {"slowness", "weakness", "mining_fatigue", "nausea", "blindness",
            "hunger", "poison", "darkness", "levitation"};

    private MasteryMath() {
    }

    /**
     * Starting speed, blocks per tick, that carries something about {@code blocks} blocks before drag
     * stops it. {@code drag} is the fraction of speed kept every tick.
     */
    public static double dashSpeed(double blocks, double drag) {
        return Math.min(MAX_DASH_SPEED, Math.max(0.0, blocks) * (1.0 - drag));
    }

    /** Peak height, in blocks, of a body launched upward at {@code v0} blocks per tick under vanilla gravity. */
    public static double peakHeight(double v0) {
        double y = 0;
        double v = v0;
        double peak = 0;
        for (int i = 0; i < 400 && v > 0; i++) {
            y += v;
            peak = Math.max(peak, y);
            v = (v - 0.08) * 0.98;
        }
        return peak;
    }

    /** Upward speed that carries a body about {@code blocks} blocks high. */
    public static double launchSpeed(double blocks) {
        double lo = 0.0;
        double hi = 3.0;
        for (int i = 0; i < 40; i++) {
            double mid = (lo + hi) / 2;
            if (peakHeight(mid) < blocks) {
                lo = mid;
            } else {
                hi = mid;
            }
        }
        return hi;
    }

    /** How far a Reagent Echo of {@code percent} stretches an effect with {@code remaining} ticks left. */
    public static int echoAdd(int remaining, double percent) {
        int add = (int) Math.round(remaining * percent / 100.0);
        add = Math.max(ECHO_MIN_ADD_TICKS, Math.min(ECHO_MAX_ADD_TICKS, add));
        return Math.max(0, Math.min(add, ECHO_CAP_TICKS - remaining));
    }

    /** Durability Quench restores on a piece with this maximum durability. */
    public static int repairAmount(int maxDamage, double percent) {
        return Math.max(1, (int) Math.round(maxDamage * percent / 100.0));
    }

    /** Seconds of growth Open Barn gives each calf at these stars. */
    public static int calfGrowthSeconds(int stars) {
        return 20 * Math.max(0, stars);
    }

    /** Saturation Hearthglow warms the cook with at these stars. */
    public static float warmBite(int stars) {
        return Math.max(0, stars);
    }

    /** The named harmful effects the first {@code kinds} stars of Shrug It Off remove. */
    public static String[] shrugKinds(int kinds) {
        int n = Math.max(0, Math.min(kinds, SHRUG_ORDER.length));
        return java.util.Arrays.copyOf(SHRUG_ORDER, n);
    }
}
