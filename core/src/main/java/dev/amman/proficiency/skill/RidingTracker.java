package dev.amman.proficiency.skill;

import java.util.HashMap;
import java.util.Map;

/**
 * One player's ride, tick by tick: turns positions into the metres that may be paid, and keeps every
 * anti-farm rule for Riding. Pure Java, so the rules are unit tested; {@code RidingEvents} feeds it.
 *
 * <p>A tick pays its metres unless one of these says no ({@link Reason}):
 * <ul>
 * <li>{@code SKIPPED}: the first tick, a new vehicle or dimension, or a step of more than 10 blocks
 * (a portal, a teleport, the Whistle). Nothing is paid and the ride starts again from there.</li>
 * <li>{@code AFK}: a boat or a mount you steer needs steering input in the last 2 seconds. A boat in
 * a current, or a mount dragged by a leash, has none.</li>
 * <li>{@code LOOP}: the net displacement rule. Over a 30 second window, once 120 metres have been
 * ridden, the straight distance from where the window began must be at least 15% of them.
 * Circles and shuttles return to where they were and pay nothing.</li>
 * <li>{@code REPEAT}: the ground rule. The world is cut into 8 block cells and a cell you were paid
 * for riding through in the last 10 minutes pays nothing again. A loop, a shuttle or a ride back along the
 * same road earns once, then waits.</li>
 * </ul>
 * One tick never pays more than 3 blocks (60 m/s), so a stretch of fast elytra counts as fast but
 * not as a teleport.
 */
public final class RidingTracker {

    public enum Reason {
        PAID, SKIPPED, AFK, LOOP, REPEAT, STILL
    }

    /** What one tick came to: the metres to pay (0 when {@code reason} is not PAID) and why. */
    public record Result(double metres, Reason reason) {
    }

    /** A step longer than this many blocks in one tick is a teleport or a portal. */
    public static final double MAX_STEP = 10.0;
    /** One tick pays at most this many blocks. */
    public static final double MAX_PAID_STEP = 3.0;
    /** Steering input counts for this many ticks. */
    public static final long INPUT_WINDOW_TICKS = 40L;
    /** A gap this long between two ticks of one ride is a new ride. */
    public static final long MAX_GAP_TICKS = 5L;

    /** The net displacement window: 30 s. */
    public static final long NET_WINDOW_TICKS = 600L;
    /** The rule starts to bite after this many metres in the window... */
    public static final double NET_MIN_PATH = 120.0;
    /** ...when the straight distance is under this share of them. */
    public static final double NET_MIN_SHARE = 0.15;

    /** Ground cells are this many blocks square. */
    public static final int CELL = 8;
    /** A cell rode through stays spent for 10 minutes. */
    public static final long GROUND_MEMORY_TICKS = 12000L;

    /** Smallest grant, as for the other movement skills: about one a second on a steady ride. */
    public static final double STEP_XP = 0.25;

    private boolean seeded;
    private String context = "";
    private long lastTick;
    private double lastX;
    private double lastY;
    private double lastZ;
    private long lastInput = -1L;

    private long windowStart;
    private double windowX;
    private double windowZ;
    private double windowPath;

    private long cell = Long.MIN_VALUE;
    private boolean cellNovel;
    private final Map<Long, Long> ground = new HashMap<>();

    private double carry;
    private double procCarry;

    /**
     * One tick of riding.
     *
     * @param tick game time
     * @param context what is being ridden and where: the dimension and the vehicle, so a change reseeds
     * @param needsInput whether this vehicle must be steered ({@link RidingMath.Kind#needsInput})
     * @param inputNow whether the player is steering this tick
     */
    public Result step(long tick, String context, double x, double y, double z, boolean needsInput,
            boolean inputNow) {
        if (inputNow) {
            lastInput = tick;
        }
        if (!seeded || !context.equals(this.context) || tick < lastTick || tick - lastTick > MAX_GAP_TICKS) {
            seed(tick, context, x, y, z);
            return new Result(0.0, Reason.SKIPPED);
        }
        double dx = x - lastX;
        double dz = z - lastZ;
        // y is only for the teleport check: a step of more than 10 blocks straight up or down is a teleport too.
        double horizontal = Math.sqrt(dx * dx + dz * dz);
        if (!Double.isFinite(horizontal) || horizontal > MAX_STEP || Math.abs(y - lastY) > MAX_STEP) {
            seed(tick, context, x, y, z);
            return new Result(0.0, Reason.SKIPPED);
        }
        lastY = y;
        lastTick = tick;
        lastX = x;
        lastZ = z;
        if (horizontal < 1e-4) {
            return new Result(0.0, Reason.STILL);
        }
        double metres = Math.min(horizontal, MAX_PAID_STEP);

        // Net displacement: the window rolls on, whatever the ride is doing.
        if (tick - windowStart >= NET_WINDOW_TICKS) {
            windowStart = tick;
            windowX = x;
            windowZ = z;
            windowPath = 0.0;
        }
        windowPath += metres;
        double netX = x - windowX;
        double netZ = z - windowZ;
        double net = Math.sqrt(netX * netX + netZ * netZ);
        boolean looping = windowPath >= NET_MIN_PATH && net < NET_MIN_SHARE * windowPath;

        // Ground: decide on entering a cell whether it is fresh.
        long here = cellKey(x, z);
        if (here != cell) {
            cell = here;
            Long seen = ground.get(here);
            cellNovel = seen == null || tick < seen || tick - seen >= GROUND_MEMORY_TICKS;
        }

        if (needsInput && (lastInput < 0 || tick - lastInput > INPUT_WINDOW_TICKS)) {
            return new Result(0.0, Reason.AFK);
        }
        if (looping) {
            return new Result(0.0, Reason.LOOP);
        }
        if (!cellNovel) {
            return new Result(0.0, Reason.REPEAT);
        }
        // Only ground that was really paid for is spent: drifting through a cell unsteered leaves it fresh.
        ground.put(here, tick);
        if (ground.size() > 4096) {
            ground.values().removeIf(t -> tick - t >= GROUND_MEMORY_TICKS);
        }
        return new Result(metres, Reason.PAID);
    }

    private void seed(long tick, String context, double x, double y, double z) {
        seeded = true;
        this.context = context;
        lastTick = tick;
        lastX = x;
        lastY = y;
        lastZ = z;
        windowStart = tick;
        windowX = x;
        windowZ = z;
        windowPath = 0.0;
        cell = cellKey(x, z);
        Long seen = ground.get(cell);
        cellNovel = seen == null || tick < seen || tick - seen >= GROUND_MEMORY_TICKS;
    }

    static long cellKey(double x, double z) {
        long cx = Math.floorDiv((long) Math.floor(x), (long) CELL);
        long cz = Math.floorDiv((long) Math.floor(z), (long) CELL);
        return (cx << 32) ^ (cz & 0xFFFFFFFFL);
    }

    /** Forgets the ride (a dismount) but keeps the ground, so getting off and on again is not a fresh start. */
    public void endRide() {
        seeded = false;
        context = "";
    }

    /** Adds XP earned. Returns what to grant now, or 0 while the pile is under {@link #STEP_XP}. */
    public double add(double xp) {
        if (!(xp > 0) || !Double.isFinite(xp)) {
            return 0.0;
        }
        carry += xp;
        if (carry < STEP_XP) {
            return 0.0;
        }
        double pay = carry;
        carry = 0.0;
        return pay;
    }

    /** Proc rolls a grant of {@code paid} XP earns: one per whole point, remainder kept. */
    public int procRolls(double paid) {
        procCarry += paid;
        int rolls = (int) Math.floor(procCarry);
        procCarry -= rolls;
        return rolls;
    }

    /** XP waiting to be paid. */
    public double pending() {
        return carry;
    }

    /** Cells remembered, for tests. */
    public int groundSize() {
        return ground.size();
    }
}
